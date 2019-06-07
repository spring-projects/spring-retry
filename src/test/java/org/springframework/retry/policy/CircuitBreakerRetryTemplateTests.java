/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.retry.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy.CircuitBreakerRetryContext;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * @author Dave Syer
 *
 */
public class CircuitBreakerRetryTemplateTests {

	private static final String RECOVERED = "RECOVERED";

	private static final String RESULT = "RESULT";

	private RetryTemplate retryTemplate;

	private RecoveryCallback<Object> recovery;

	private MockRetryCallback callback;

	private DefaultRetryState state;

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
		this.callback.setAttemptsBeforeSuccess(1);
		// No rollback by default (so exceptions are not rethrown)
		this.state = new DefaultRetryState("retry", new BinaryExceptionClassifier(false));
	}

	@Test
	public void testCircuitOpenWhenNotRetryable() throws Throwable {
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		Object result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertEquals(1, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		// circuit is now open so no more attempts
		assertEquals(1, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
	}

	@Test
	public void testCircuitOpenWithNoRecovery() throws Throwable {
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
		this.retryTemplate.setThrowLastExceptionOnExhausted(true);
		try {
			this.retryTemplate.execute(this.callback, this.state);
		}
		catch (Exception e) {
			assertEquals(this.callback.exceptionToThrow, e);
			assertEquals(1, this.callback.getAttempts());
		}
		try {
			this.retryTemplate.execute(this.callback, this.state);
		}
		catch (Exception e) {
			assertEquals(this.callback.exceptionToThrow, e);
			// circuit is now open so no more attempts
			assertEquals(1, this.callback.getAttempts());
		}
	}

	@Test
	public void testCircuitOpensWhenDelegateNotRetryable() throws Throwable {
		this.retryTemplate.setRetryPolicy(new CircuitBreakerRetryPolicy(new SimpleRetryPolicy()));
		this.callback.setAttemptsBeforeSuccess(10);
		Object result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertEquals(1, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		assertFalse(this.callback.status.isOpen());
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		// circuit is now open so no more attempts
		assertEquals(3, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		assertTrue(this.callback.status.isOpen());
	}

	@Test
	public void testWindowResetsAfterTimeout() throws Throwable {
		CircuitBreakerRetryPolicy retryPolicy = new CircuitBreakerRetryPolicy(new SimpleRetryPolicy());
		this.retryTemplate.setRetryPolicy(retryPolicy);
		retryPolicy.setOpenTimeout(100);
		this.callback.setAttemptsBeforeSuccess(10);
		Object result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertEquals(1, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		assertFalse(this.callback.status.isOpen());
		Thread.sleep(200L);
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		// circuit is reset after sleep window
		assertEquals(2, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		assertFalse(this.callback.status.isOpen());
	}

	@Test
	public void testCircuitClosesAfterTimeout() throws Throwable {
		CircuitBreakerRetryPolicy retryPolicy = new CircuitBreakerRetryPolicy(new NeverRetryPolicy());
		this.retryTemplate.setRetryPolicy(retryPolicy);
		retryPolicy.setResetTimeout(100);
		Object result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		assertEquals(1, this.callback.getAttempts());
		assertEquals(RECOVERED, result);
		assertTrue(this.callback.status.isOpen());
		// Sleep longer than the timeout
		Thread.sleep(200L);
		assertFalse(this.callback.status.isOpen());
		result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
		// circuit closed again now
		assertEquals(RESULT, result);
	}

	protected static class MockRetryCallback implements RetryCallback<Object, Exception> {

		private int attemptsBeforeSuccess;

		private Exception exceptionToThrow = new Exception();

		private CircuitBreakerRetryContext status;

		@Override
		public Object doWithRetry(RetryContext status) throws Exception {
			this.status = (CircuitBreakerRetryContext) status;
			int attempts = getAttempts();
			attempts++;
			status.setAttribute("attempts", attempts);
			if (attempts <= this.attemptsBeforeSuccess) {
				throw this.exceptionToThrow;
			}
			return RESULT;
		}

		public int getAttempts() {
			if (!this.status.hasAttribute("attempts")) {
				this.status.setAttribute("attempts", 0);
			}
			int attempts = (Integer) this.status.getAttribute("attempts");
			return attempts;
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

		public void setExceptionToThrow(Exception exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

}
