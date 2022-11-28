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

package org.springframework.retry.stats;

import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryState;
import org.springframework.retry.RetryStatistics;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Henning Pöttker
 *
 */
public class StatisticsListenerTests {

	private final StatisticsRepository repository = new DefaultStatisticsRepository();

	private final StatisticsListener listener = new StatisticsListener(repository);

	@Test
	public void testStatelessSuccessful() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback);
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertThat(stats).isNotNull();
			assertThat(stats.getCompleteCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount() + x).isEqualTo(stats.getStartedCount());
		}
	}

	@Test
	public void testStatefulSuccessful() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		RetryState state = new DefaultRetryState("foo");
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			for (int i = 0; i < x; i++) {
				try {
					retryTemplate.execute(callback, state);
				}
				catch (Exception e) {
					// don't care
				}
			}
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertThat(stats).isNotNull();
			assertThat(stats.getCompleteCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount() + x).isEqualTo(stats.getStartedCount());
		}
	}

	@Test
	public void testStatelessUnsuccessful() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			try {
				retryTemplate.execute(callback);
			}
			catch (Exception e) {
				// not interested
			}
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			assertThat(stats).isNotNull();
			assertThat(stats.getAbortCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount()).isEqualTo(stats.getStartedCount());
		}
	}

	@Test
	public void testStatefulUnsuccessful() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		RetryState state = new DefaultRetryState("foo");
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			for (int i = 0; i < x + 1; i++) {
				try {
					retryTemplate.execute(callback, state);
				}
				catch (Exception e) {
					// don't care
				}
			}
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertThat(stats).isNotNull();
			assertThat(stats.getAbortCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount()).isEqualTo(stats.getStartedCount());
		}
	}

	@Test
	public void testStatelessRecovery() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback, context -> null);
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertThat(stats).isNotNull();
			assertThat(stats.getRecoveryCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount()).isEqualTo(stats.getStartedCount());
		}
	}

	@Test
	public void testStatefulRecovery() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListener[] { listener });
		RetryState state = new DefaultRetryState("foo");
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			for (int i = 0; i < x + 1; i++) {
				try {
					retryTemplate.execute(callback, context -> null, state);
				}
				catch (Exception e) {
					// don't care
				}
			}
			assertThat(callback.attempts).isEqualTo(x);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertThat(stats).isNotNull();
			assertThat(stats.getRecoveryCount()).isEqualTo(x);
			assertThat(stats.getStartedCount()).isEqualTo((x + 1) * x / 2);
			assertThat(stats.getErrorCount()).isEqualTo(stats.getStartedCount());
		}
	}

	private static class MockRetryCallback implements RetryCallback<Object, Exception> {

		private int attempts;

		private int attemptsBeforeSuccess;

		private final Exception exceptionToThrow = new Exception();

		@Override
		public Object doWithRetry(RetryContext status) throws Exception {
			status.setAttribute(RetryContext.NAME, "test");
			this.attempts++;
			if (this.attempts < this.attemptsBeforeSuccess) {
				throw this.exceptionToThrow;
			}
			return null;
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

	}

}
