/*
 * Copyright 2006-2019 the original author or authors.
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
import org.springframework.retry.listener.MethodInvocationRetryListenerSupport;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ClassUtils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RetryOperationsInterceptorTests {

	private static int count;

	private static int transactionCount;

	private RetryOperationsInterceptor interceptor;

	private Service service;

	private ServiceImpl target;

	private RetryContext context;

	@Before
	public void setUp() throws Exception {
		this.interceptor = new RetryOperationsInterceptor();
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.registerListener(new RetryListenerSupport() {
			@Override
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				RetryOperationsInterceptorTests.this.context = context;
			}
		});
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
		assertEquals(2, count);
	}

	@Test
	public void testDefaultInterceptorWithLabel() throws Exception {
		this.interceptor.setLabel("FOO");
		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertEquals(2, count);
		assertEquals("FOO", this.context.getAttribute(RetryContext.NAME));
	}

	@Test
	public void testDefaultInterceptorWithRetryListenerInspectingTheMethodInvocation() throws Exception {

		final String label = "FOO";
		final String classTagName = "class";
		final String methodTagName = "method";
		final String labelTagName = "label";
		final Map<String, String> monitoringTags = new HashMap<String, String>();
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
		});

		this.interceptor.setLabel(label);
		this.interceptor.setRetryOperations(template);

		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertEquals(2, count);
		assertEquals(3, monitoringTags.entrySet().size());
		assertThat(monitoringTags.get(labelTagName), equalTo(label));
		assertThat(monitoringTags.get(classTagName),
				equalTo(RetryOperationsInterceptorTests.Service.class.getSimpleName()));
		assertThat(monitoringTags.get(methodTagName), equalTo("service"));
	}

	@Test
	public void testDefaultInterceptorWithRecovery() throws Exception {
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(1));
		this.interceptor.setRetryOperations(template);
		this.interceptor.setRecoverer(new MethodInvocationRecoverer<Void>() {
			@Override
			public Void recover(Object[] args, Throwable cause) {
				return null;
			}
		});
		((Advised) this.service).addAdvice(this.interceptor);
		this.service.service();
		assertEquals(1, count);
	}

	@Test
	public void testInterceptorChainWithRetry() throws Exception {
		((Advised) this.service).addAdvice(this.interceptor);
		final List<String> list = new ArrayList<String>();
		((Advised) this.service).addAdvice(new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				list.add("chain");
				return invocation.proceed();
			}
		});
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new SimpleRetryPolicy(2));
		this.interceptor.setRetryOperations(template);
		this.service.service();
		assertEquals(2, count);
		assertEquals(2, list.size());
	}

	@Test
	public void testRetryExceptionAfterTooManyAttempts() throws Exception {
		((Advised) this.service).addAdvice(this.interceptor);
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(new NeverRetryPolicy());
		this.interceptor.setRetryOperations(template);
		try {
			this.service.service();
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
				ClassUtils.addResourcePathToPackagePath(getClass(), "retry-transaction-test.xml"));
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
			this.interceptor.invoke(new MethodInvocation() {
				@Override
				public Method getMethod() {
					return ClassUtils.getMethod(RetryOperationsInterceptorTests.class,
							"testIllegalMethodInvocationType");
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
				public Object proceed() throws Throwable {
					return null;
				}
			});
			fail("IllegalStateException expected");
		}
		catch (IllegalStateException e) {
			assertTrue("Exception message should contain MethodInvocation: " + e.getMessage(),
					e.getMessage().indexOf("MethodInvocation") >= 0);
		}
	}

	public static interface Service {

		void service() throws Exception;

		void doTansactional() throws Exception;

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
		public void doTansactional() throws Exception {
			if (TransactionSynchronizationManager.isActualTransactionActive() && !this.enteredTransaction) {
				transactionCount++;
				TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
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
