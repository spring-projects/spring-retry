/*
 * Copyright 2015 the original author or authors.
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

import org.junit.Before;
import org.junit.Test;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Dave Syer
 *
 */
public class CircuitBreakerStatisticsTests {

	private static final String RECOVERED = "RECOVERED";
	private static final String RESULT = "RESULT";
	private RetryTemplate retryTemplate;
	private RecoveryCallback<Object> recovery;
	private MockRetryCallback callback;
	private DefaultRetryState state;

	private StatisticsRepository repository = new DefaultStatisticsRepository();
	private StatisticsListener listener = new StatisticsListener(repository);
	private RetryContextCache cache;

	@Before
	public void init() {
		this.callback = new MockRetryCallback();
		this.recovery = new RecoveryCallback<Object>() {
			@Override
			public Object recover(RetryContext context) throws Exception {
				return RECOVERED;
			}
		};
		this.retryTemplate = new RetryTemplate();
		this.cache = new MapRetryContextCache();
		this.retryTemplate.setRetryContextCache(this.cache);
		retryTemplate.setListeners(new RetryListener[] { listener });
		this.callback.setAttemptsBeforeSuccess(1);
		// No rollback by default (so exceptions are not rethrown)
		this.state = new DefaultRetryState("retry", new BinaryExceptionClassifier(false));
	}

	@Test
	public void testCircuitOpenWhenNotRetryable() throws Throwable {
		this.retryTemplate
				.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		Object result = this.retryTemplate.execute(this.callback, this.recovery,
				this.state);
		MutableRetryStatistics stats = (MutableRetryStatistics) repository
				.findOne("test");
		assertEquals(1, stats.getStartedCount());
		assertEquals(RECOVERED, result);
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertEquals(RECOVERED, result);
		assertEquals("There should be two recoveries", 2, stats.getRecoveryCount());
		assertEquals("There should only be one error because the circuit is now open", 1,
				stats.getErrorCount());
		assertEquals(true, stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
		// Both recoveries are through a short circuit because we used NeverRetryPolicy
		assertEquals(2,
				stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_SHORT_COUNT));
		resetAndAssert(this.cache, stats);
	}

	@Test
	public void testFailedRecoveryCountsAsAbort() throws Throwable {
		this.retryTemplate
				.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		this.recovery = new RecoveryCallback<Object>() {
			@Override
			public Object recover(RetryContext context) throws Exception {
				throw new ExhaustedRetryException("Planned exhausted");
			}
		};
		try {
			this.retryTemplate.execute(this.callback, this.recovery, this.state);
			fail("Expected ExhaustedRetryException");
		}
		catch (ExhaustedRetryException e) {
			// Fine
		}
		MutableRetryStatistics stats = (MutableRetryStatistics) repository
				.findOne("test");
		assertEquals(1, stats.getStartedCount());
		assertEquals(1, stats.getAbortCount());
		assertEquals(0, stats.getRecoveryCount());
	}

	@Test
	public void testCircuitOpenWithNoRecovery() throws Throwable {
		this.retryTemplate
				.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		this.retryTemplate.setThrowLastExceptionOnExhausted(true);
		try {
			this.retryTemplate.execute(this.callback, this.state);
		}
		catch (Exception e) {
		}
		try {
			this.retryTemplate.execute(this.callback, this.state);
		}
		catch (Exception e) {
		}
		MutableRetryStatistics stats = (MutableRetryStatistics) repository
				.findOne("test");
		assertEquals("There should be two aborts", 2, stats.getAbortCount());
		assertEquals("There should only be one error because the circuit is now open", 1,
				stats.getErrorCount());
		assertEquals(true, stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
		resetAndAssert(this.cache, stats);
	}

	private void resetAndAssert(RetryContextCache cache, MutableRetryStatistics stats) {
		reset(cache.get("retry"));
		listener.close(cache.get("retry"), callback, null);
		assertEquals(0,
				stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_SHORT_COUNT));
	}

	private void reset(RetryContext retryContext) {
		ReflectionTestUtils.invokeMethod(retryContext, "reset");
	}

	protected static class MockRetryCallback implements RetryCallback<Object, Exception> {

		private int attemptsBeforeSuccess;

		private Exception exceptionToThrow = new Exception();

		private RetryContext status;

		@Override
		public Object doWithRetry(RetryContext status) throws Exception {
			status.setAttribute(RetryContext.NAME, "test");
			this.status = status;
			int attempts = (Integer) status.getAttribute("attempts");
			attempts++;
			status.setAttribute("attempts", attempts);
			if (attempts <= this.attemptsBeforeSuccess) {
				throw this.exceptionToThrow;
			}
			return RESULT;
		}

		public boolean isOpen() {
			return status != null && status.getAttribute("open") == Boolean.TRUE;
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

		public void setExceptionToThrow(Exception exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}
	}

}
