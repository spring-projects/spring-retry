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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class CircuitBreakerStatisticsTests {

	private static final String RECOVERED = "RECOVERED";

	private static final String RESULT = "RESULT";

	private RetryTemplate retryTemplate;

	private RecoveryCallback<Object> recovery;

	private MockRetryCallback callback;

	private DefaultRetryState state;

	private final StatisticsRepository repository = new DefaultStatisticsRepository();

	private final StatisticsListener listener = new StatisticsListener(repository);

	private RetryContextCache cache;

	@BeforeEach
	public void init() {
		this.callback = new MockRetryCallback();
		this.recovery = context -> RECOVERED;
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
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		Object result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		MutableRetryStatistics stats = (MutableRetryStatistics) repository.findOne("test");
		assertThat(stats.getStartedCount()).isEqualTo(1);
		assertThat(result).isEqualTo(RECOVERED);
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertThat(result).isEqualTo(RECOVERED);
		assertThat(stats.getRecoveryCount()).describedAs("There should be two recoveries", null).isEqualTo(2);
		assertThat(stats.getErrorCount())
				.describedAs("There should only be one error because the circuit is now open", null).isEqualTo(1);
		assertThat(stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isEqualTo(Boolean.TRUE);
		// Both recoveries are through a short circuit because we used NeverRetryPolicy
		assertThat(stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_SHORT_COUNT)).isEqualTo(2);
		resetAndAssert(this.cache, stats);
	}

	@Test
	public void testFailedRecoveryCountsAsAbort() throws Throwable {
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		this.recovery = context -> {
			throw new ExhaustedRetryException("Planned exhausted");
		};
		assertThatExceptionOfType(ExhaustedRetryException.class)
				.isThrownBy(() -> this.retryTemplate.execute(this.callback, this.recovery, this.state));
		MutableRetryStatistics stats = (MutableRetryStatistics) repository.findOne("test");
		assertThat(stats.getStartedCount()).isEqualTo(1);
		assertThat(stats.getAbortCount()).isEqualTo(1);
		assertThat(stats.getRecoveryCount()).isEqualTo(0);
	}

	@Test
	public void testCircuitOpenWithNoRecovery() {
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
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
		MutableRetryStatistics stats = (MutableRetryStatistics) repository.findOne("test");
		assertThat(stats.getAbortCount()).describedAs("There should be two aborts").isEqualTo(2);
		assertThat(stats.getErrorCount())
				.describedAs("There should only be one error because the circuit is now open", null).isEqualTo(1);
		assertThat(stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isEqualTo(true);
		resetAndAssert(this.cache, stats);
	}

	private void resetAndAssert(RetryContextCache cache, MutableRetryStatistics stats) {
		reset(cache.get("retry"));
		listener.close(cache.get("retry"), callback, null);
		assertThat(stats.getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_SHORT_COUNT)).isEqualTo(0);
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
