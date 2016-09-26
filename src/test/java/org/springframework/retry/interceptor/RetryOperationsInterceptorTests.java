/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

public class RetryOperationsInterceptorTests {

	private RetryOperationsInterceptor interceptor;

	private Service service;

	private ServiceImpl target;

	private RetryContext context;

	private static int count;

	private static int transactionCount;

	@Before
	public void setUp() throws Exception {
		interceptor = new RetryOperationsInterceptor();
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.registerListener(new RetryListenerSupport() {
			@Override
			public <T, E extends Throwable> void close(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				RetryOperationsInterceptorTests.this.context = context;
			}
		});
		interceptor.setRetryOperations(retryTemplate);
		target = new ServiceImpl();
		service = (Service) ProxyFactory.getProxy(Service.class, new SingletonTargetSource(target));
		count = 0;
		transactionCount = 0;
	}

	@Test
	public void testDefaultInterceptorSunnyDay() throws Exception {
		((Advised) service).addAdvice(interceptor);
		service.service();
		assertEquals(2, count);
	}

	@Test
	public void testDefaultInterceptorWithLabel() throws Exception {
		interceptor.setLabel("FOO");
		((Advised) service).addAdvice(interceptor);
		service.service();
		assertEquals(2, count);
		assertEquals("FOO", context.getAttribute(RetryContext.NAME));
	}

	@Test
	public void testDefaultInterceptorWithRecovery() throws Exception {
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(1));
		interceptor.setRetryOperations(template);
		interceptor.setRecoverer(new MethodInvocationRecoverer<Void>() {
			public Void recover(Object[] args, Throwable cause) {
				return null;
			}
		});
		((Advised) service).addAdvice(interceptor);
		service.service();
		assertEquals(1, count);
	}

	@Test
	public void testInterceptorChainWithRetry() throws Exception {
		((Advised) service).addAdvice(interceptor);
		final List<String> list = new ArrayList<String>();
		((Advised) service).addAdvice(new MethodInterceptor() {
			public Object invoke(MethodInvocation invocation) throws Throwable {
				list.add("chain");
				return invocation.proceed();
			}
		});
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(2));
		interceptor.setRetryOperations(template);
		service.service();
		assertEquals(2, count);
		assertEquals(2, list.size());
	}

	@Test
	public void testRetryExceptionAfterTooManyAttempts() throws Exception {
		((Advised) service).addAdvice(interceptor);
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new NeverRetryPolicy());
		interceptor.setRetryOperations(template);
		try {
			service.service();
			fail("Expected Exception.");
		}
		catch (Exception e) {
			assertTrue(e.getMessage().startsWith("Not enough calls"));
		}
		assertEquals(1, count);
	}

	@Test
	public void testOutsideTransaction() throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				ClassUtils.addResourcePathToPackagePath(getClass(),
						"retry-transaction-test.xml"));
		Object object = context.getBean("bean");
		assertNotNull(object);
		assertTrue(object instanceof Service);
		Service bean = (Service) object;
		bean.doTansactional();
		assertEquals(2, count);
		// Expect 2 separate transactions...
		assertEquals(2, transactionCount);
		context.close();
	}

	@Test
	public void testIllegalMethodInvocationType() throws Throwable {
		try {
			interceptor.invoke(new MethodInvocation() {
				public Method getMethod() {
					return ClassUtils.getMethod(RetryOperationsInterceptorTests.class,
							"testIllegalMethodInvocationType");
				}

				public Object[] getArguments() {
					return null;
				}

				public AccessibleObject getStaticPart() {
					return null;
				}

				public Object getThis() {
					return null;
				}

				public Object proceed() throws Throwable {
					return null;
				}
			});
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException e) {
			assertTrue(
					"Exception message should contain MethodInvocation: "
							+ e.getMessage(),
					e.getMessage().indexOf("MethodInvocation") >= 0);
		}
	}

	public static interface Service {
		void service() throws Exception;

		void doTansactional() throws Exception;
	}

	public static class ServiceImpl implements Service {

		private boolean enteredTransaction = false;

		public void service() throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
		}

		public void doTansactional() throws Exception {
			if (TransactionSynchronizationManager.isActualTransactionActive()
					&& !enteredTransaction) {
				transactionCount++;
				TransactionSynchronizationManager
						.registerSynchronization(new TransactionSynchronizationAdapter() {
							public void beforeCompletion() {
								enteredTransaction = false;
							}
						});
				enteredTransaction = true;
			}
			count++;
			if (count == 1) {
				throw new RuntimeException("Rollback please");
			}
		}

	}
}
