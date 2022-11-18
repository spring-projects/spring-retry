/*
 * Copyright 2006-2022 the original author or authors.
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
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

public class CircuitBreakerResetTimeoutTests {

	private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
			CircuitBreakerResetTimeoutTests.TestConfiguration.class);

	private final TestService serviceInTest = context.getBean(TestService.class);

	@Test
	public void circuitBreakerShouldBeClosedAfterResetTimeout() throws InterruptedException {
		incorrectStep();
		incorrectStep();
		incorrectStep();
		incorrectStep();

		final long timeOfLastFailure = System.currentTimeMillis();
		correctStep(timeOfLastFailure);
		correctStep(timeOfLastFailure);
		correctStep(timeOfLastFailure);
		assertThat((Boolean) serviceInTest.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isFalse();
	}

	private void incorrectStep() {
		doFailedUpload(serviceInTest);
		System.out.println();
	}

	private void correctStep(final long timeOfLastFailure) throws InterruptedException {
		Thread.sleep(6000L);
		printTime(timeOfLastFailure);
		doCorrectUpload(serviceInTest);
		System.out.println();
	}

	private void printTime(final long timeOfLastFailure) {
		System.out.println(String.format("%d ms after last failure", (System.currentTimeMillis() - timeOfLastFailure)));
	}

	private void doFailedUpload(TestService externalService) {
		externalService.service("FAIL");
	}

	private void doCorrectUpload(TestService externalService) {
		externalService.service("");
	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public TestService externalService() {
			return new TestService();
		}

	}

	static class TestService {

		private RetryContext context;

		@CircuitBreaker(retryFor = { RuntimeException.class }, openTimeout = 10000, resetTimeout = 15000)
		String service(String payload) {
			this.context = RetrySynchronizationManager.getContext();
			System.out.println("real service called");
			if (payload.contentEquals("FAIL")) {
				throw new RuntimeException("");
			}
			return payload;
		}

		@Recover
		public String recover() {
			System.out.println("recovery action");
			return "";
		}

		public RetryContext getContext() {
			return this.context;
		}

	}

}
