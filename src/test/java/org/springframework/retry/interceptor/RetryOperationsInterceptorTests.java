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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.listener.MethodInvocationRetryListenerSupport;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * @author Dave Syer
 * @author Marius Grama
 * @author Gary Russell
 * @author Stéphane Nicoll
 * @author Henning Pöttker
 * @author Artem Bilan
 * @author Kim Jun Hyeong
 */
public class RetryOperationsInterceptorTests {

	private static int count;

	private static int transactionCount;

	private RetryOperationsInterceptor interceptor;

	private Service service;

	private ServiceImpl target;

	private RetryContext context;

	@BeforeEach
	public void setUp() {
		this.interceptor = new RetryOperationsInterceptor();
		RetryTemplate retryTemplate = new RetryTemplate();
		final AtomicBoolean calledFirst = new AtomicBoolean();
		retryTemplate.registerListener(new RetryListener() {

			@Override
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

				calledFirst.set(true);
				return true;

			}

			@Override
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				RetryOperationsInterceptorTests.this.context = context;
			}

		});
		retryTemplate.registerListener(new RetryListener() {

			@Override
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

				assertThat(calledFirst.get()).isFalse();
				return true;
			}

		}, 0);
		this.interceptor.setRetryOperations(retryTemplate);
		this.target = new ServiceImpl();
		this.service = ProxyFactory.getProxy(Service.class, new SingletonTargetSource(this.target));
		count = 0;
		transactionCount = 0;
	}

	@Test
	public void testDefaultInterceptorSunnyDay() throws Exception {
		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertThat(count).isEqualTo(2);
	}

	@Test
	public void testDefaultInterceptorWithLabel() throws Exception {
		this.interceptor.setLabel("FOO");
		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertThat(count).isEqualTo(2);
		assertThat(this.context.getAttribute(RetryContext.NAME)).isEqualTo("FOO");
		assertThat(this.context.getAttribute(RetryOperationsInterceptor.METHOD)).isNotNull()
			.extracting("name")
			.isEqualTo("service");
		assertThat(this.context.getAttribute(RetryOperationsInterceptor.METHOD_ARGS)).isNotNull()
			.extracting("args")
			.isEqualTo(new Object[0]);
	}

	@Test
	public void testDefaultInterceptorWithRetryListenerInspectingTheMethodInvocation() throws Exception {

		final String label = "FOO";
		final String classTagName = "class";
		final String methodTagName = "method";
		final String labelTagName = "label";
		final Map<String, String> monitoringTags = new HashMap<>();
		AtomicBoolean argumentsAsExpected = new AtomicBoolean();
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(2));
		template.registerListener(new MethodInvocationRetryListenerSupport() {

			@Override
			protected <T, E extends Throwable> void doClose(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
				monitoringTags.put(labelTagName, callback.getLabel());
				Method method = callback.getInvocation().getMethod();
				monitoringTags.put(classTagName, method.getDeclaringClass().getSimpleName());
				monitoringTags.put(methodTagName, method.getName());
			}

			@Override
			protected <T, E extends Throwable> void doOnSuccess(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback, T result) {

				argumentsAsExpected.set(callback.getInvocation().getArguments().length == 0);
			}

		});

		this.interceptor.setLabel(label);
		this.interceptor.setRetryOperations(template);

		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertThat(count).isEqualTo(2);
		assertThat(monitoringTags.entrySet()).hasSize(3);
		assertThat(monitoringTags.get(labelTagName)).isEqualTo(label);
		assertThat(monitoringTags.get(classTagName))
			.isEqualTo(RetryOperationsInterceptorTests.Service.class.getSimpleName());
		assertThat(monitoringTags.get(methodTagName)).isEqualTo("service");
		assertThat(argumentsAsExpected.get()).isTrue();
	}

	@Test
	public void testDefaultInterceptorWithRecovery() throws Exception {
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(1));
		this.interceptor.setRetryOperations(template);
		this.interceptor.setRecoverer((args, cause) -> null);
		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testInterceptorChainWithRetry() throws Exception {
		((Advised) this.service).addAdvice(this.interceptor);
		final List<String> list = new ArrayList<>();
		((Advised) this.service).addAdvice((MethodInterceptor) invocation -> {
			list.add("chain");
			return invocation.proceed();
		});
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(2));
		this.interceptor.setRetryOperations(template);
		this.service.service();
		assertThat(count).isEqualTo(2);
		assertThat(list).hasSize(2);
	}

	@Test
	public void testRetryExceptionAfterTooManyAttempts() {
		((Advised) this.service).addAdvice(this.interceptor);
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new NeverRetryPolicy());
		this.interceptor.setRetryOperations(template);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		assertThat(count).isEqualTo(1);
	}

	@Test
	public void testOutsideTransaction() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				ClassUtils.addResourcePathToPackagePath(getClass(), "retry-transaction-test.xml"));
		Object object = context.getBean("bean");
		assertThat(object).isInstanceOf(Service.class);
		Service bean = (Service) object;
		bean.doTransactional();
		assertThat(count).isEqualTo(2);
		// Expect 2 separate transactions...
		assertThat(transactionCount).isEqualTo(2);
		context.close();
	}

	@Test
	public void testIllegalMethodInvocationType() {
		assertThatIllegalStateException().isThrownBy(() -> this.interceptor.invoke(new MethodInvocation() {
			@Override
			public Method getMethod() {
				return ClassUtils.getMethod(RetryOperationsInterceptorTests.class, "testIllegalMethodInvocationType");
			}

			@Override
			public Object[] getArguments() {
				return null;
			}

			@Override
			public AccessibleObject getStaticPart() {
				return null;
			}

			@Override
			public Object getThis() {
				return null;
			}

			@Override
			public Object proceed() {
				return null;
			}
		})).withMessageContaining("MethodInvocation");
	}

	@Test
	public void testProxyAttributeCleanupWithCorrectKey() {
		RetryContext directContext = new RetryContextSupport(null);
		Object mockProxy = new Object();
		RetrySynchronizationManager.register(directContext);
		directContext.setAttribute("___proxy___", mockProxy);
		RetryContext retrievedContext = RetrySynchronizationManager.getContext();
		retrievedContext.removeAttribute("___proxy___");
		assertThat(RetrySynchronizationManager.getContext().getAttribute("___proxy___")).isNull();
		RetrySynchronizationManager.clear();
	}

	@Test
	public void testProxyAttributeRemainWithWrongKey() {
		RetryContext directContext = new RetryContextSupport(null);
		Object mockProxy = new Object();
		RetrySynchronizationManager.register(directContext);
		directContext.setAttribute("___proxy___", mockProxy);
		RetryContext retrievedContext = RetrySynchronizationManager.getContext();
		retrievedContext.removeAttribute("__proxy__");
		Object remainingProxy = RetrySynchronizationManager.getContext().getAttribute("___proxy___");
		assertThat(remainingProxy).isNotNull();
		assertThat(remainingProxy).isSameAs(mockProxy);
		RetrySynchronizationManager.clear();
	}

	@Test
	public void testProxyAttributeCleanupEvenWhenIllegalStateExceptionThrown() {
		RetryContext context = new RetryContextSupport(null);
		Object mockProxy = new Object();
		RetrySynchronizationManager.register(context);
		context.setAttribute("___proxy___", mockProxy);
		assertThat(context.getAttribute("___proxy___")).isNotNull();
		assertThatIllegalStateException().isThrownBy(() -> this.interceptor.invoke(new MethodInvocation() {
			@Override
			public Method getMethod() {
				return ClassUtils.getMethod(RetryOperationsInterceptorTests.class,
						"testProxyAttributeCleanupEvenWhenIllegalStateExceptionThrown");
			}

			@Override
			public Object[] getArguments() {
				return new Object[0];
			}

			@Override
			public Object proceed() {
				return null;
			}

			@Override
			public Object getThis() {
				return new Object();
			}

			@Override
			public AccessibleObject getStaticPart() {
				return null;
			}
		})).withMessageContaining("MethodInvocation");
		assertThat(context.getAttribute("___proxy___")).isNull();
		RetrySynchronizationManager.clear();
	}

	public static interface Service {

		void service() throws Exception;

		void doTransactional();

	}

	public static class ServiceImpl implements Service {

		private boolean enteredTransaction = false;

		@Override
		public void service() throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
		}

		@Override
		public void doTransactional() {
			if (TransactionSynchronizationManager.isActualTransactionActive() && !this.enteredTransaction) {
				transactionCount++;
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

					@Override
					public void beforeCompletion() {
						ServiceImpl.this.enteredTransaction = false;
					}

				});
				this.enteredTransaction = true;
			}
			count++;
			if (count == 1) {
				throw new RuntimeException("Rollback please");
			}
		}

	}

}
