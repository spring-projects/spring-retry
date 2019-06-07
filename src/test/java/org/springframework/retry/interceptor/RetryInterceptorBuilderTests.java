/*
 * Copyright 2014 the original author or authors.
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

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Test;

import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.util.test.TestUtils;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 1.1
 *
 */
public class RetryInterceptorBuilderTests {

	@Test
	public void testBasic() {
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful().build();
		assertEquals(3, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
	}

	@Test
	public void testWithCustomRetryTemplate() {
		RetryOperations retryOperations = new RetryTemplate();
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful()
				.retryOperations(retryOperations).build();
		assertEquals(3, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
		assertSame(retryOperations, TestUtils.getPropertyValue(interceptor, "retryOperations"));
	}

	@Test
	public void testWithMoreAttempts() {
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful().maxAttempts(5).build();
		assertEquals(5, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
	}

	@Test
	public void testWithCustomizedBackOffMoreAttempts() {
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful().maxAttempts(5)
				.backOffOptions(1, 2, 10).build();

		assertEquals(5, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
		assertEquals(1L, TestUtils.getPropertyValue(interceptor, "retryOperations.backOffPolicy.initialInterval"));
		assertEquals(2.0, TestUtils.getPropertyValue(interceptor, "retryOperations.backOffPolicy.multiplier"));
		assertEquals(10L, TestUtils.getPropertyValue(interceptor, "retryOperations.backOffPolicy.maxInterval"));
	}

	@Test
	public void testWithCustomBackOffPolicy() {
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful().maxAttempts(5)
				.backOffPolicy(new FixedBackOffPolicy()).build();

		assertEquals(5, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
		assertEquals(1000L, TestUtils.getPropertyValue(interceptor, "retryOperations.backOffPolicy.backOffPeriod"));
	}

	@Test
	public void testWithCustomNewMessageIdentifier() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful().maxAttempts(5)
				.newMethodArgumentsIdentifier(new NewMethodArgumentsIdentifier() {

					@Override
					public boolean isNew(Object[] args) {
						latch.countDown();
						return false;
					}
				}).backOffPolicy(new FixedBackOffPolicy()).build();

		assertEquals(5, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
		assertEquals(1000L, TestUtils.getPropertyValue(interceptor, "retryOperations.backOffPolicy.backOffPeriod"));
		final AtomicInteger count = new AtomicInteger();
		Foo delegate = createDelegate(interceptor, count);
		Object message = "";
		try {
			delegate.onMessage("", message);
		}
		catch (RuntimeException e) {
			assertEquals("foo", e.getMessage());
		}
		assertEquals(1, count.get());
		assertTrue(latch.await(0, TimeUnit.SECONDS));
	}

	@Test
	public void testWitCustomRetryPolicyTraverseCause() {
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful()
				.retryPolicy(new SimpleRetryPolicy(15,
						Collections.<Class<? extends Throwable>, Boolean>singletonMap(Exception.class, true), true))
				.build();
		assertEquals(15, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
	}

	@Test
	public void testWithCustomKeyGenerator() throws Exception {
		final CountDownLatch latch = new CountDownLatch(1);
		StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful()
				.keyGenerator(new MethodArgumentsKeyGenerator() {

					@Override
					public Object getKey(Object[] item) {
						latch.countDown();
						return "foo";
					}
				}).build();

		assertEquals(3, TestUtils.getPropertyValue(interceptor, "retryOperations.retryPolicy.maxAttempts"));
		final AtomicInteger count = new AtomicInteger();
		Foo delegate = createDelegate(interceptor, count);
		Object message = "";
		try {
			delegate.onMessage("", message);
		}
		catch (RuntimeException e) {
			assertEquals("foo", e.getMessage());
		}
		assertEquals(1, count.get());
		assertTrue(latch.await(0, TimeUnit.SECONDS));
	}

	private Foo createDelegate(MethodInterceptor interceptor, final AtomicInteger count) {
		Foo delegate = new Foo() {

			@Override
			public void onMessage(String s, Object message) {
				count.incrementAndGet();
				throw new RuntimeException("foo", new RuntimeException("bar"));
			}

		};
		ProxyFactory factory = new ProxyFactory();
		factory.addAdvisor(new DefaultPointcutAdvisor(Pointcut.TRUE, interceptor));
		factory.setProxyTargetClass(false);
		factory.addInterface(Foo.class);
		factory.setTarget(delegate);
		delegate = (Foo) factory.getProxy();
		return delegate;
	}

	static interface Foo {

		void onMessage(String s, Object message);

	}

}
