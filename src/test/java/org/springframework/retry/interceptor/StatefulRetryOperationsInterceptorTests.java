/*
 * Copyright 2006-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.retry.interceptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 * @author Gary Russell
 * @author Henning Pöttker
 *
 */
public class StatefulRetryOperationsInterceptorTests {

	private StatefulRetryOperationsInterceptor interceptor;

	private final RetryTemplate retryTemplate = new RetryTemplate();

	private Service service;

	private Transformer transformer;

	private RetryContext context;

	private static int count;

	@BeforeEach
	public void setUp() {
		interceptor = new StatefulRetryOperationsInterceptor();
		retryTemplate.registerListener(new RetryListener() {
			@Override
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				StatefulRetryOperationsInterceptorTests.this.context = context;
			}
		});
		interceptor.setRetryOperations(retryTemplate);
		service = ProxyFactory.getProxy(Service.class, new SingletonTargetSource(new ServiceImpl()));
		transformer = ProxyFactory.getProxy(Transformer.class, new SingletonTargetSource(new TransformerImpl()));
		count = 0;
	}

	@Test
	public void testDefaultInterceptorSunnyDay() {
		((Advised) service).addAdvice(interceptor);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
	}

	@Test
	public void testDefaultInterceptorWithLabel() {
		interceptor.setLabel("FOO");
		((Advised) service).addAdvice(interceptor);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		assertThat(context.getAttribute(RetryContext.NAME)).isEqualTo("FOO");
	}

	@Test
	public void testDefaultTransformerInterceptorSunnyDay() {
		((Advised) transformer).addAdvice(interceptor);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> transformer.transform("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testDefaultInterceptorAlwaysRetry() {
		retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
		interceptor.setRetryOperations(retryTemplate);
		((Advised) service).addAdvice(interceptor);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testInterceptorChainWithRetry() throws Exception {
		((Advised) service).addAdvice(interceptor);
		final List<String> list = new ArrayList<>();
		((Advised) service).addAdvice((MethodInterceptor) invocation -> {
			list.add("chain");
			return invocation.proceed();
		});
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		service.service("foo");
		assertThat(count).isEqualTo(2);
		assertThat(list).hasSize(2);
	}

	@Test
	public void testTransformerWithSuccessfulRetry() throws Exception {
		((Advised) transformer).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> transformer.transform("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		Collection<String> result = transformer.transform("foo");
		assertThat(count).isEqualTo(2);
		assertThat(result).hasSize(1);
	}

	@Test
	public void testRetryExceptionAfterTooManyAttemptsWithNoRecovery() throws Exception {
		((Advised) service).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		assertThatExceptionOfType(ExhaustedRetryException.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Retry exhausted");
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testRecoveryAfterTooManyAttempts() throws Exception {
		((Advised) service).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		interceptor.setRecoverer((data, cause) -> {
			count++;
			return null;
		});
		service.service("foo");
		assertThat(count).isEqualTo(2);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testKeyGeneratorReturningNull() throws Throwable {
		this.interceptor.setKeyGenerator(mock(MethodArgumentsKeyGenerator.class));
		this.interceptor.setLabel("foo");
		RetryOperations template = mock(RetryOperations.class);
		this.interceptor.setRetryOperations(template);
		MethodInvocation invocation = mock(MethodInvocation.class);
		when(invocation.getArguments()).thenReturn(new Object[] { new Object() });
		this.interceptor.invoke(invocation);
		ArgumentCaptor<DefaultRetryState> captor = ArgumentCaptor.forClass(DefaultRetryState.class);
		verify(template).execute(any(RetryCallback.class), eq(null), captor.capture());
		assertThat(captor.getValue().getKey()).isNull();
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testKeyGeneratorAndRawKey() throws Throwable {
		this.interceptor.setKeyGenerator(item -> "bar");
		this.interceptor.setLabel("foo");
		this.interceptor.setUseRawKey(true);
		RetryOperations template = mock(RetryOperations.class);
		this.interceptor.setRetryOperations(template);
		MethodInvocation invocation = mock(MethodInvocation.class);
		when(invocation.getArguments()).thenReturn(new Object[] { new Object() });
		this.interceptor.invoke(invocation);
		ArgumentCaptor<DefaultRetryState> captor = ArgumentCaptor.forClass(DefaultRetryState.class);
		verify(template).execute(any(RetryCallback.class), eq(null), captor.capture());
		assertThat(captor.getValue().getKey()).isEqualTo("bar");
	}

	@Test
	public void testTransformerRecoveryAfterTooManyAttempts() throws Exception {
		((Advised) transformer).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> transformer.transform("foo"))
			.withMessageStartingWith("Not enough calls");
		assertThat(count).isEqualTo(1);
		interceptor.setRecoverer((data, cause) -> {
			count++;
			return Collections.singleton((String) data[0]);
		});
		Collection<String> result = transformer.transform("foo");
		assertThat(count).isEqualTo(2);
		assertThat(result.size()).isEqualTo(1);
	}

	public static interface Service {

		void service(String in) throws Exception;

	}

	public static class ServiceImpl implements Service {

		@Override
		public void service(String in) throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
		}

	}

	public static interface Transformer {

		Collection<String> transform(String in) throws Exception;

	}

	public static class TransformerImpl implements Transformer {

		@Override
		public Collection<String> transform(String in) throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
			return Collections.singleton(in + ":" + count);
		}

	}

}
