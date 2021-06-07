/*
 * Copyright 2014-2021 the original author or authors.
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

package org.springframework.retry.annotation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.Ordered;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @author Aldo Sinanaj
 * @since 1.1
 */
public class EnableRetryTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		Foo foo = context.getBean(Foo.class);
		assertFalse(AopUtils.isAopProxy(foo));
		assertTrue(AopUtils.isAopProxy(service));
		service.service();
		assertEquals(3, service.getCount());
		TestConfiguration config = context.getBean(TestConfiguration.class);
		assertTrue(config.listener1);
		assertTrue(config.listener2);
		assertTrue(config.twoFirst);
		context.close();
	}

	@Test
	public void multipleMethods() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		MultiService service = context.getBean(MultiService.class);
		service.service();
		assertEquals(3, service.getCount());
		service.other();
		assertEquals(4, service.getCount());
		context.close();
	}

	@Test
	public void proxyTargetClass() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestProxyConfiguration.class);
		Service service = context.getBean(Service.class);
		assertTrue(AopUtils.isCglibProxy(service));
		RecoverableService recoverable = context.getBean(RecoverableService.class);
		recoverable.service();
		assertTrue(recoverable.isOtherAdviceCalled());
		context.close();
	}

	@Test
	public void marker() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		assertTrue(AopUtils.isCglibProxy(service));
		assertTrue(service instanceof org.springframework.retry.interceptor.Retryable);
		context.close();
	}

	@Test
	public void recovery() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		RecoverableService service = context.getBean(RecoverableService.class);
		service.service();
		assertEquals(3, service.getCount());
		assertNotNull(service.getCause());
		context.close();
	}

	@Test
	public void type() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		RetryableService service = context.getBean(RetryableService.class);
		service.service();
		assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	public void excludes() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExcludesService service = context.getBean(ExcludesService.class);
		try {
			service.service();
			fail("Expected IllegalStateException");
		}
		catch (IllegalStateException e) {
		}
		assertEquals(1, service.getCount());
		context.close();
	}

	@Test
	public void excludesOnly() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExcludesOnlyService service = context.getBean(ExcludesOnlyService.class);
		service.setExceptionToThrow(new IllegalStateException());
		try {
			service.service();
			fail("Expected IllegalStateException");
		}
		catch (IllegalStateException e) {
		}
		assertEquals(1, service.getCount());

		service.setExceptionToThrow(new IllegalArgumentException());
		service.service();
		assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	public void stateful() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		StatefulService service = context.getBean(StatefulService.class);
		for (int i = 0; i < 3; i++) {
			try {
				service.service(1);
			}
			catch (Exception e) {
				assertEquals("Planned", e.getMessage());
			}
		}
		assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	public void testExternalInterceptor() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		InterceptableService service = context.getBean(InterceptableService.class);
		service.service();
		assertEquals(5, service.getCount());
		context.close();
	}

	@Test
	public void testInterface() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		TheInterface service = context.getBean(TheInterface.class);
		service.service1();
		service.service2();
		assertEquals(4, service.getCount());
		service.service3();
		assertTrue(service.isRecovered());
		context.close();
	}

	@Test
	public void testInterfaceWithNoRecover() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		NoRecoverInterface service = context.getBean(NoRecoverInterface.class);
		service.service();
		assertTrue(service.isRecovered());
	}

	@Test
	public void testImplementation() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		NotAnnotatedInterface service = context.getBean(NotAnnotatedInterface.class);
		service.service1();
		service.service2();
		assertEquals(5, service.getCount());
		context.close();
	}

	@Test
	public void testExpression() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExpressionService service = context.getBean(ExpressionService.class);
		service.service1();
		assertEquals(3, service.getCount());
		try {
			service.service2();
			fail("expected exception");
		}
		catch (RuntimeException e) {
			assertEquals("this cannot be retried", e.getMessage());
		}
		assertEquals(4, service.getCount());
		service.service3();
		assertEquals(9, service.getCount());
		RetryConfiguration config = context.getBean(RetryConfiguration.class);
		AnnotationAwareRetryOperationsInterceptor advice = (AnnotationAwareRetryOperationsInterceptor) new DirectFieldAccessor(
				config).getPropertyValue("advice");
		@SuppressWarnings("unchecked")
		Map<Object, Map<Method, MethodInterceptor>> delegates = (Map<Object, Map<Method, MethodInterceptor>>) new DirectFieldAccessor(
				advice).getPropertyValue("delegates");
		MethodInterceptor interceptor = delegates.get(target(service))
				.get(ExpressionService.class.getDeclaredMethod("service3"));
		RetryTemplate template = (RetryTemplate) new DirectFieldAccessor(interceptor)
				.getPropertyValue("retryOperations");
		DirectFieldAccessor templateAccessor = new DirectFieldAccessor(template);
		ExponentialBackOffPolicy backOff = (ExponentialBackOffPolicy) templateAccessor
				.getPropertyValue("backOffPolicy");
		assertEquals(1, backOff.getInitialInterval());
		assertEquals(5, backOff.getMaxInterval());
		assertEquals(1.1, backOff.getMultiplier(), 0.1);
		SimpleRetryPolicy retryPolicy = (SimpleRetryPolicy) templateAccessor.getPropertyValue("retryPolicy");
		assertEquals(5, retryPolicy.getMaxAttempts());
		service.service4();
		assertEquals(11, service.getCount());
		service.service5();
		assertEquals(12, service.getCount());
		context.close();
	}

	private Object target(Object target) {
		if (!AopUtils.isAopProxy(target)) {
			return target;
		}
		try {
			return target(((Advised) target).getTargetSource().getTarget());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class TestProxyConfiguration {

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RecoverableService recoverable() {
			return new RecoverableService();
		}

		@Bean
		public static AdviceBPP bpp() {
			return new AdviceBPP();
		}

		static class AdviceBPP implements BeanPostProcessor, Ordered {

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(final Object bean, String beanName) throws BeansException {

				if (bean instanceof RecoverableService) {
					Advised advised = (Advised) bean;
					advised.addAdvice(new MethodInterceptor() {

						@Override
						public Object invoke(MethodInvocation invocation) throws Throwable {

							if (invocation.getMethod().getName().equals("recover")) {
								((RecoverableService) bean).setOtherAdviceCalled();
							}
							return invocation.proceed();
						}

					});
					return bean;
				}
				return bean;
			}

			@Override
			public int getOrder() {
				return Integer.MAX_VALUE;
			}

		}

	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public static PropertySourcesPlaceholderConfigurer pspc() {
			PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
			Properties properties = new Properties();
			properties.setProperty("one", "1");
			properties.setProperty("five", "5");
			properties.setProperty("onePointOne", "1.1");
			properties.setProperty("retryMethod", "shouldRetry");
			pspc.setProperties(properties);
			return pspc;
		}

		boolean listener1;

		boolean listener2;

		protected boolean twoFirst;

		@SuppressWarnings("serial")
		@Bean
		public Sleeper sleeper() {
			return new Sleeper() {
				@Override
				public void sleep(long period) throws InterruptedException {
				}
			};
		}

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RetryListener listener1() {
			return new OrderedListener() {

				@Override
				public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

					TestConfiguration.this.listener1 = true;
					TestConfiguration.this.twoFirst = true;
					return super.open(context, callback);
				}

				@Override
				public int getOrder() {
					return Integer.MAX_VALUE;
				}

			};
		}

		@Bean
		public RetryListener listener2() {
			return new OrderedListener() {

				private boolean listener1;

				@Override
				public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

					TestConfiguration.this.listener2 = true;
					TestConfiguration.this.twoFirst = false;
					return super.open(context, callback);
				}

				@Override
				public int getOrder() {
					return Integer.MIN_VALUE;
				}

			};
		}

		@Bean
		public MultiService multiService() {
			return new MultiService();
		}

		@Bean
		public RecoverableService recoverable() {
			return new RecoverableService();
		}

		@Bean
		public RetryableService retryable() {
			return new RetryableService();
		}

		@Bean
		public StatefulService stateful() {
			return new StatefulService();
		}

		@Bean
		public ExcludesService excludes() {
			return new ExcludesService();
		}

		@Bean
		public ExcludesOnlyService excludesOnly() {
			return new ExcludesOnlyService();
		}

		@Bean
		public MethodInterceptor retryInterceptor() {
			return RetryInterceptorBuilder.stateless().maxAttempts(5).build();
		}

		@Bean
		public InterceptableService serviceWithExternalInterceptor() {
			return new InterceptableService();
		}

		@Bean
		public ExpressionService expressionService() {
			return new ExpressionService();
		}

		@Bean
		public ExceptionChecker exceptionChecker() {
			return new ExceptionChecker();
		}

		@Bean
		public Integer integerFiveBean() {
			return Integer.valueOf(5);
		}

		@Bean
		public Foo foo() {
			return new Foo();
		}

		@Bean
		public TheInterface anInterface() {
			return new TheClass();
		}

		@Bean
		public NoRecoverInterface anInterfaceWithNoRecover() {
			return new NoRecoverClass();
		}

		@Bean
		public NotAnnotatedInterface notAnnotatedInterface() {
			return new RetryableImplementation();
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable(RuntimeException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class MultiService {

		private int count = 0;

		@Retryable(RuntimeException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Retryable(RuntimeException.class)
		public void other() {
			if (this.count++ < 3) {
				throw new RuntimeException("Other");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class RecoverableService {

		private int count = 0;

		private Throwable cause;

		boolean otherAdviceCalled;

		@Retryable(RuntimeException.class)
		public void service() {
			this.count++;
			throw new RuntimeException("Planned");
		}

		@Recover
		public void recover(Throwable cause) {
			this.cause = cause;
		}

		public Throwable getCause() {
			return this.cause;
		}

		public int getCount() {
			return this.count;
		}

		public void setOtherAdviceCalled() {
			this.otherAdviceCalled = true;
		}

		public boolean isOtherAdviceCalled() {
			return this.otherAdviceCalled;
		}

	}

	@Retryable(RuntimeException.class)
	protected static class RetryableService {

		private int count = 0;

		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class ExcludesService {

		private int count = 0;

		@Retryable(include = RuntimeException.class, exclude = IllegalStateException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new IllegalStateException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class ExcludesOnlyService {

		private int count = 0;

		private RuntimeException exceptionToThrow;

		@Retryable(exclude = IllegalStateException.class)
		public void service() {
			if (this.count++ < 2) {
				throw this.exceptionToThrow;
			}
		}

		public int getCount() {
			return this.count;
		}

		public void setExceptionToThrow(RuntimeException exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

	protected static class StatefulService {

		private int count = 0;

		@Retryable(stateful = true)
		public void service(int value) {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	private static class InterceptableService {

		private int count = 0;

		@Retryable(interceptor = "retryInterceptor")
		public void service() {
			if (this.count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	private static class ExpressionService {

		private int count = 0;

		@Retryable(exceptionExpression = "#{message.contains('this can be retried')}")
		public void service1() {
			if (this.count++ < 2) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(exceptionExpression = "#{message.contains('this can be retried')}")
		public void service2() {
			this.count++;
			throw new RuntimeException("this cannot be retried");
		}

		@Retryable(exceptionExpression = "#{@exceptionChecker.${retryMethod}(#root)}",
				maxAttemptsExpression = "#{@integerFiveBean}", backoff = @Backoff(delayExpression = "#{${one}}",
						maxDelayExpression = "#{${five}}", multiplierExpression = "#{${onePointOne}}"))
		public void service3() {
			if (this.count++ < 8) {
				throw new RuntimeException();
			}
		}

		@Retryable(exceptionExpression = "message.contains('this can be retried')")
		public void service4() {
			if (this.count++ < 10) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(exceptionExpression = "message.contains('this can be retried')", include = RuntimeException.class)
		public void service5() {
			if (this.count++ < 11) {
				throw new RuntimeException("this can be retried");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	public static class ExceptionChecker {

		public boolean shouldRetry(Throwable t) {
			return true;
		}

	}

	private static class Foo {

	}

	public static interface TheInterface {

		void service1();

		@Retryable
		void service2();

		int getCount();

		void service3();

		void recover(Exception e);

		boolean isRecovered();

	}

	public static class TheClass implements TheInterface {

		private int count = 0;

		private boolean recovered;

		@Override
		@Retryable
		public void service1() {
			if (this.count++ < 1) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (this.count++ < 3) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return this.count;
		}

		@Override
		@Retryable
		public void service3() {
			throw new RuntimeException("planned");
		}

		@Override
		@Recover
		public void recover(Exception e) {
			this.recovered = true;
		}

		@Override
		public boolean isRecovered() {
			return this.recovered;
		}

	}

	public static interface NoRecoverInterface {

		void service();

		boolean isRecovered();

	}

	public static class NoRecoverClass implements NoRecoverInterface {

		private boolean recovered;

		@Override
		@Retryable
		public void service() {
			throw new RuntimeException("Planned");
		}

		@Recover
		public void recover(Exception e) {
			this.recovered = true;
		}

		@Override
		public boolean isRecovered() {
			return this.recovered;
		}

	}

	public static interface NotAnnotatedInterface {

		void service1();

		void service2();

		int getCount();

	}

	@Retryable
	public static class RetryableImplementation implements NotAnnotatedInterface {

		private int count = 0;

		@Override
		public void service1() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (this.count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return this.count;
		}

	}

	public abstract static class OrderedListener extends RetryListenerSupport implements Ordered {

	}

}
