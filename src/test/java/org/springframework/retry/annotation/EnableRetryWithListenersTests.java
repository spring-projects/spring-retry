/*
 * Copyright 2012-2023 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Gary Russell
 * @author Henning Pöttker
 * @author Roman Akentev
 *
 */
public class EnableRetryWithListenersTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		service.service();
		assertThat(context.getBean(TestConfiguration.class).count).isEqualTo(1);
		context.close();
	}

	@Test
	public void overrideListener() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfigurationMultipleListeners.class);
		ServiceWithOverriddenListener service = context.getBean(ServiceWithOverriddenListener.class);
		service.service();
		assertThat(context.getBean(TestConfigurationMultipleListeners.class).count1).isEqualTo(1);
		assertThat(context.getBean(TestConfigurationMultipleListeners.class).count2).isEqualTo(0);
		context.close();
	}

	@Test
	public void excludedListeners() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfigurationExcludedListeners.class);
		ServiceWithExcludedListeners service = context.getBean(ServiceWithExcludedListeners.class);
		service.service();
		assertThat(context.getBean(TestConfigurationExcludedListeners.class).count).isEqualTo(0);
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
			return new RetryListener() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
						Throwable throwable) {
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
			return new RetryListener() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
						Throwable throwable) {
					count1++;
				}
			};
		}

		@Bean
		public RetryListener listener2() {
			return new RetryListener() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
						Throwable throwable) {
					count2++;
				}
			};
		}

	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class TestConfigurationExcludedListeners {

		private int count = 0;

		@Bean
		public ServiceWithExcludedListeners service() {
			return new ServiceWithExcludedListeners();
		}

		@Bean
		public RetryListener listener1() {
			return new RetryListener() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
						Throwable throwable) {
					count++;
				}
			};
		}

		@Bean
		public RetryListener listener2() {
			return new RetryListener() {
				@Override
				public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
						Throwable throwable) {
					count++;
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

	protected static class ServiceWithExcludedListeners {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000), listeners = "")
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
