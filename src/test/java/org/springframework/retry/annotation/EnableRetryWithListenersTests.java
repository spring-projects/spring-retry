/*
 * Copyright 2012-2015 the original author or authors.
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

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.listener.RetryListenerSupport;

/**
 * @author Dave Syer
 *
 */
public class EnableRetryWithListenersTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		Service service = context.getBean(Service.class);
		service.service();
		assertEquals(1, context.getBean(TestConfiguration.class).count);
		context.close();
	}

	@Test
	public void overrideListener() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfigurationMultipleListeners.class);
		ServiceWithOverriddenListener service = context.getBean(ServiceWithOverriddenListener.class);
		service.service();
		assertEquals(1, context.getBean(TestConfigurationMultipleListeners.class).count1);
		assertEquals(0, context.getBean(TestConfigurationMultipleListeners.class).count2);
		context.close();
	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class TestConfiguration {

		private int count = 0;

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RetryListener listener() {
			return new RetryListenerSupport() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context,
						RetryCallback<T, E> callback, Throwable throwable) {
					count++;
				}
			};
		}

	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class TestConfigurationMultipleListeners {

		private int count1 = 0;
		private int count2 = 0;

		@Bean
		public ServiceWithOverriddenListener service() {
			return new ServiceWithOverriddenListener();
		}

		@Bean
		public RetryListener listener1() {
			return new RetryListenerSupport() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context,
														   RetryCallback<T, E> callback, Throwable throwable) {
					count1++;
				}
			};
		}

		@Bean
		public RetryListener listener2() {
			return new RetryListenerSupport() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context,
														   RetryCallback<T, E> callback, Throwable throwable) {
					count2++;
				}
			};
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000))
		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class ServiceWithOverriddenListener {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000), listeners = "listener1")
		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

}
