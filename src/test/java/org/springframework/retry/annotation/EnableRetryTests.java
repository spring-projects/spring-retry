/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.retry.annotation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
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
 * @since 1.1
 */
public class EnableRetryTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		Service service = context.getBean(Service.class);
		Foo foo = context.getBean(Foo.class);
		assertFalse(AopUtils.isAopProxy(foo));
		assertTrue(AopUtils.isAopProxy(service));
		service.service();
		assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	public void multipleMethods() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
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
		context.close();
	}

	@Test
	public void marker() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		Service service = context.getBean(Service.class);
		assertTrue(AopUtils.isCglibProxy(service));
		assertTrue(service instanceof org.springframework.retry.interceptor.Retryable);
		context.close();
	}

	@Test
	public void recovery() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		RecoverableService service = context.getBean(RecoverableService.class);
		service.service();
		assertEquals(3, service.getCount());
		assertNotNull(service.getCause());
		context.close();
	}

	@Test
	public void type() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		RetryableService service = context.getBean(RetryableService.class);
		service.service();
		assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	public void excludes() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
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
	public void stateful() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
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
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		InterceptableService service = context.getBean(InterceptableService.class);
		service.service();
		assertEquals(5, service.getCount());
		context.close();
	}

	@Test
	public void testInterface() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		TheInterface service = context.getBean(TheInterface.class);
		service.service1();
		service.service2();
		assertEquals(4, service.getCount());
		context.close();
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
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
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
		SimpleRetryPolicy retryPolicy = (SimpleRetryPolicy) templateAccessor
				.getPropertyValue("retryPolicy");
		assertEquals(5, retryPolicy.getMaxAttempts());
		context.close();
	}

	private Object target(Object target) {
		if (!AopUtils.isAopProxy(target)) {
			return target;
		}
		try {
			return target(((Advised)target).getTargetSource().getTarget());
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
		public NotAnnotatedInterface notAnnotatedInterface() {
			return new RetryableImplementation();
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable(RuntimeException.class)
		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class MultiService {

		private int count = 0;

		@Retryable(RuntimeException.class)
		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Retryable(RuntimeException.class)
		public void other() {
			if (count++ < 3) {
				throw new RuntimeException("Other");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class RecoverableService {

		private int count = 0;

		private Throwable cause;

		@Retryable(RuntimeException.class)
		public void service() {
			count++;
			throw new RuntimeException("Planned");
		}

		@Recover
		public void recover(Throwable cause) {
			this.cause = cause;
		}

		public Throwable getCause() {
			return cause;
		}

		public int getCount() {
			return count;
		}

	}

	@Retryable(RuntimeException.class)
	protected static class RetryableService {

		private int count = 0;

		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class ExcludesService {

		private int count = 0;

		@Retryable(include = RuntimeException.class, exclude = IllegalStateException.class)
		public void service() {
			if (count++ < 2) {
				throw new IllegalStateException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class StatefulService {

		private int count = 0;

		@Retryable(stateful = true)
		public void service(int value) {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	private static class InterceptableService {

		private int count = 0;

		@Retryable(interceptor = "retryInterceptor")
		public void service() {
			if (count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	private static class ExpressionService {

		private int count = 0;

		@Retryable(exceptionExpression = "#{message.contains('this can be retried')}")
		public void service1() {
			if (count++ < 2) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(exceptionExpression = "#{message.contains('this can be retried')}")
		public void service2() {
			count++;
			throw new RuntimeException("this cannot be retried");
		}

		@Retryable(exceptionExpression = "#{@exceptionChecker.${retryMethod}(#root)}", maxAttemptsExpression = "#{@integerFiveBean}", backoff = @Backoff(delayExpression = "#{${one}}", maxDelayExpression = "#{${five}}", multiplierExpression = "#{${onePointOne}}"))
		public void service3() {
			if (count++ < 8) {
				throw new RuntimeException();
			}
		}

		public int getCount() {
			return count;
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

	}

	public static class TheClass implements TheInterface {

		private int count = 0;

		@Override
		@Retryable
		public void service1() {
			if (count++ < 1) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (count++ < 3) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return count;
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
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return count;
		}

	}


}
