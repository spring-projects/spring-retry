package org.springframework.retry.annotation;

import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;

public class CircuitBreakerResetTimeoutTest {

	private AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
			CircuitBreakerResetTimeoutTest.TestConfiguration.class);

	private TestService serviceInTest = context.getBean(TestService.class);

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
		assertFalse((Boolean) serviceInTest.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
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

		@CircuitBreaker(include = { RuntimeException.class }, openTimeout = 10000, resetTimeout = 15000)
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
