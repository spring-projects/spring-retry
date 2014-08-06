/*
 * Copyright 2014 the original author or authors.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Test;

import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;

/**
 * @author Dave Syer
 * @author Artem Bilan
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
		service.service();
		assertEquals(3, service.getCount());
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
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		InterceptableService service = context.getBean(InterceptableService.class);
		service.service();
		assertEquals(5, service.getCount());
		context.close();
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
			return RetryInterceptorBuilder.stateless()
					.maxAttempts(5)
					.build();
		}

		@Bean
		public InterceptableService serviceWithExternalInterceptor() {
			return new InterceptableService();
		}

		@Bean
		public Foo foo() {
			return new Foo();
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

	private static class Foo {

	}

}
