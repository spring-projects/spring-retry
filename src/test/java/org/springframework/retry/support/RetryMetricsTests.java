/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.retry.support;

import java.util.concurrent.CompletableFuture;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * @author Artem Bilan
 * @since 2.0.8
 */
@SpringJUnitConfig
public class RetryMetricsTests {

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	Service service;

	@Test
	void metricsAreCollectedForRetryable() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.afterPropertiesSet();

		CompletableFuture<?> future1 = executor
			.submitCompletable(() -> assertThatNoException().isThrownBy(this.service::service1));
		CompletableFuture<?> future2 = executor
			.submitCompletable(() -> assertThatNoException().isThrownBy(this.service::service1));
		CompletableFuture<?> future3 = executor
			.submitCompletable(() -> assertThatNoException().isThrownBy(this.service::service2));
		CompletableFuture<?> future4 = executor.submitCompletable(
				() -> assertThatExceptionOfType(RetryException.class).isThrownBy(this.service::service3));

		CompletableFuture.allOf(future1, future2, future3, future4).join();

		assertThat(this.meterRegistry.get(MetricsRetryListener.TIMER_NAME)
			.tags(Tags.of("name", "org.springframework.retry.support.RetryMetricsTests$Service.service1", "retry.count",
					"0", "exception", "none"))
			.timer()
			.count()).isEqualTo(2);

		assertThat(this.meterRegistry.get(MetricsRetryListener.TIMER_NAME)
			.tags(Tags.of("name", "org.springframework.retry.support.RetryMetricsTests$Service.service2", "retry.count",
					"2", "exception", "none"))
			.timer()
			.count()).isEqualTo(1);

		assertThat(this.meterRegistry.get(MetricsRetryListener.TIMER_NAME)
			.tags(Tags.of("name", "org.springframework.retry.support.RetryMetricsTests$Service.service3", "retry.count",
					"3", "exception", "RetryException"))
			.timer()
			.count()).isEqualTo(1);

		executor.destroy();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableRetry
	public static class TestConfiguration {

		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		MetricsRetryListener metricsRetryListener(MeterRegistry meterRegistry) {
			return new MetricsRetryListener(meterRegistry);
		}

		@Bean
		Service service() {
			return new Service();
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable
		public void service1() {

		}

		@Retryable
		public void service2() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Retryable
		public void service3() {
			throw new RetryException("Planned");
		}

	}

}
