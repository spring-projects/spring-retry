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

package org.springframework.retry.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryState;
import org.springframework.retry.RetryStatistics;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * @author Dave Syer
 *
 */
public class StatisticsListenerTests {

	private StatisticsRepository repository = new DefaultStatisticsRepository();
	private StatisticsListener listener = new StatisticsListener(repository);

	@Test
	public void testStatelessSuccessful() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback);
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertNotNull(stats);
			assertEquals(x, stats.getCompleteCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount() + x);
		}
	}

	@Test
	public void testStatefulSuccessful() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
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
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertNotNull(stats);
			assertEquals(x, stats.getCompleteCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount() + x);
		}
	}

	@Test
	public void testStatelessUnsuccessful() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
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
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			assertNotNull(stats);
			assertEquals(x, stats.getAbortCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount());
		}
	}

	@Test
	public void testStatefulUnsuccessful() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
		RetryState state = new DefaultRetryState("foo");
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			for (int i = 0; i < x+1; i++) {
				try {
					retryTemplate.execute(callback, state);
				}
				catch (Exception e) {
					// don't care
				}
			}
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertNotNull(stats);
			assertEquals(x, stats.getAbortCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount());
		}
	}

	@Test
	public void testStatelessRecovery() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback, new RecoveryCallback<Object>() {
				@Override
				public Object recover(RetryContext context) throws Exception {
					return null;
				}
			});
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertNotNull(stats);
			assertEquals(x, stats.getRecoveryCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount());
		}
	}

	@Test
	public void testStatefulRecovery() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setListeners(new RetryListenerSupport[] { listener });
		RetryState state = new DefaultRetryState("foo");
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x + 1);
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			for (int i = 0; i < x+1; i++) {
				try {
					retryTemplate.execute(callback, new RecoveryCallback<Object>() {
						@Override
						public Object recover(RetryContext context) throws Exception {
							return null;
						}
					}, state);
				}
				catch (Exception e) {
					// don't care
				}
			}
			assertEquals(x, callback.attempts);
			RetryStatistics stats = repository.findOne("test");
			// System.err.println(stats);
			assertNotNull(stats);
			assertEquals(x, stats.getRecoveryCount());
			assertEquals((x + 1) * x / 2, stats.getStartedCount());
			assertEquals(stats.getStartedCount(), stats.getErrorCount());
		}
	}

	private static class MockRetryCallback implements RetryCallback<Object, Exception> {

		private int attempts;

		private int attemptsBeforeSuccess;

		private Exception exceptionToThrow = new Exception();

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
