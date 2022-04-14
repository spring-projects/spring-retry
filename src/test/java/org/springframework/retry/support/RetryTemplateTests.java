/*
 * Copyright 2006-2007 the original author or authors.
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

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.StatelessBackOffPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * @author Rob Harrop
 * @author Dave Syer
 */
public class RetryTemplateTests {

	RetryContext context;

	int count = 0;

	@Test
	public void testSuccessfulRetry() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback);
			assertEquals(x, callback.attempts);
		}
	}

	@Test
	public void testSpecificExceptionRetry() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			final int attemptsBeforeSuccess = x;
			final AtomicInteger attempts = new AtomicInteger(0);
			RetryCallback<String, IllegalStateException> callback = new RetryCallback<String, IllegalStateException>() {
				@Override
				public String doWithRetry(RetryContext context) throws IllegalStateException {
					if (attempts.incrementAndGet() < attemptsBeforeSuccess) {
						// The parametrized exception type in the callback is really just
						// syntactic sugar since rules of erasure mean that the handler
						// can't really tell the difference between runtime exceptions.
						throw new IllegalArgumentException("Planned");
					}
					return "foo";
				}
			};
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback);
			assertEquals(x, attempts.get());
		}
	}

	@Test
	public void testSuccessfulRecovery() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		callback.setAttemptsBeforeSuccess(3);
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		final Object value = new Object();
		Object result = retryTemplate.execute(callback, new RecoveryCallback<Object>() {
			@Override
			public Object recover(RetryContext context) throws Exception {
				return value;
			}
		});
		assertEquals(2, callback.attempts);
		assertEquals(value, result);
	}

	@Test
	public void testAlwaysTryAtLeastOnce() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		retryTemplate.execute(callback);
		assertEquals(1, callback.attempts);
	}

	@Test
	public void testNoSuccessRetry() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		// Something that won't be thrown by JUnit...
		callback.setExceptionToThrow(new IllegalArgumentException());
		callback.setAttemptsBeforeSuccess(Integer.MAX_VALUE);
		RetryTemplate retryTemplate = new RetryTemplate();
		int retryAttempts = 2;
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryAttempts));
		try {
			retryTemplate.execute(callback);
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e) {
			assertNotNull(e);
			assertEquals(retryAttempts, callback.attempts);
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	@Test
	public void testDefaultConfigWithExceptionSubclass() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		int attempts = 3;
		callback.setAttemptsBeforeSuccess(attempts);
		callback.setExceptionToThrow(new IllegalArgumentException());

		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(attempts));
		retryTemplate.execute(callback);
		assertEquals(attempts, callback.attempts);
	}

	@Test
	public void testRollbackClassifierOverridesRetryPolicy() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		int attempts = 3;
		callback.setAttemptsBeforeSuccess(attempts);
		callback.setExceptionToThrow(new IllegalArgumentException());

		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(attempts,
				Collections.<Class<? extends Throwable>, Boolean>singletonMap(Exception.class, true)));
		BinaryExceptionClassifier classifier = new BinaryExceptionClassifier(
				Collections.<Class<? extends Throwable>>singleton(IllegalArgumentException.class), false);
		retryTemplate.execute(callback, new DefaultRetryState("foo", classifier));
		assertEquals(attempts, callback.attempts);
	}

	@Test
	public void testSetExceptions() throws Throwable {
		RetryTemplate template = new RetryTemplate();
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3,
				Collections.<Class<? extends Throwable>, Boolean>singletonMap(RuntimeException.class, true));
		template.setRetryPolicy(policy);

		int attempts = 3;

		MockRetryCallback callback = new MockRetryCallback();
		callback.setAttemptsBeforeSuccess(attempts);

		try {
			template.execute(callback);
		}
		catch (Exception e) {
			assertNotNull(e);
			assertEquals(1, callback.attempts);
		}
		callback.setExceptionToThrow(new RuntimeException());

		template.execute(callback);
		assertEquals(attempts, callback.attempts);
	}

	@Test
	public void testBackOffInvoked() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			MockBackOffStrategy backOff = new MockBackOffStrategy();
			callback.setAttemptsBeforeSuccess(x);
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(10));
			retryTemplate.setBackOffPolicy(backOff);
			retryTemplate.execute(callback);
			assertEquals(x, callback.attempts);
			assertEquals(1, backOff.startCalls);
			assertEquals(x - 1, backOff.backOffCalls);
		}
	}

	@Test
	public void testEarlyTermination() throws Throwable {
		try {
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.execute(new RetryCallback<Object, Exception>() {
				@Override
				public Object doWithRetry(RetryContext status) throws Exception {
					status.setExhaustedOnly();
					throw new IllegalStateException("Retry this operation");
				}
			});
			fail("Expected ExhaustedRetryException");
		}
		catch (IllegalStateException ex) {
			// Expected for internal retry policy (external would recover
			// gracefully)
			assertEquals("Retry this operation", ex.getMessage());
		}
	}

	@Test
	public void testEarlyTerminationWithOriginalException() throws Throwable {
		try {
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setThrowLastExceptionOnExhausted(true);
			retryTemplate.execute(new RetryCallback<Object, Throwable>() {
				@Override
				public Object doWithRetry(RetryContext status) throws Exception {
					status.setExhaustedOnly();
					throw new IllegalStateException("Retry this operation");
				}
			});
			fail("Expected ExhaustedRetryException");
		}
		catch (IllegalStateException ex) {
			// Expected for internal retry policy (external would recover
			// gracefully)
			assertEquals("Retry this operation", ex.getMessage());
		}
	}

	@Test
	public void testNestedContexts() throws Throwable {
		RetryTemplate outer = new RetryTemplate();
		final RetryTemplate inner = new RetryTemplate();
		outer.execute(new RetryCallback<Object, Throwable>() {
			@Override
			public Object doWithRetry(RetryContext status) throws Throwable {
				RetryTemplateTests.this.context = status;
				RetryTemplateTests.this.count++;
				Object result = inner.execute(new RetryCallback<Object, Throwable>() {
					@Override
					public Object doWithRetry(RetryContext status) throws Throwable {
						RetryTemplateTests.this.count++;
						assertNotNull(RetryTemplateTests.this.context);
						assertNotSame(status, RetryTemplateTests.this.context);
						assertSame(RetryTemplateTests.this.context, status.getParent());
						assertSame("The context should be the child", status, RetrySynchronizationManager.getContext());
						return null;
					}
				});
				assertSame("The context should be restored", status, RetrySynchronizationManager.getContext());
				return result;
			}
		});
		assertEquals(2, this.count);
	}

	@Test
	public void testRethrowError() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		try {
			retryTemplate.execute(new RetryCallback<Object, Exception>() {
				@Override
				public Object doWithRetry(RetryContext context) throws Exception {
					throw new Error("Realllly bad!");
				}
			});
			fail("Expected Error");
		}
		catch (Error e) {
			assertEquals("Realllly bad!", e.getMessage());
		}
	}

	@SuppressWarnings("serial")
	@Test
	public void testFailedPolicy() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy() {
			@Override
			public void registerThrowable(RetryContext context, Throwable throwable) {
				throw new RuntimeException("Planned");
			}
		});
		try {
			retryTemplate.execute(new RetryCallback<Object, Exception>() {
				@Override
				public Object doWithRetry(RetryContext context) throws Exception {
					throw new RuntimeException("Realllly bad!");
				}
			});
			fail("Expected Error");
		}
		catch (TerminatedRetryException e) {
			assertEquals("Planned", e.getCause().getMessage());
		}
	}

	@Test
	public void testBackOffInterrupted() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setBackOffPolicy(new StatelessBackOffPolicy() {
			@Override
			protected void doBackOff() throws BackOffInterruptedException {
				throw new BackOffInterruptedException("foo");
			}
		});
		try {
			retryTemplate.execute(new RetryCallback<Object, Exception>() {
				@Override
				public Object doWithRetry(RetryContext context) throws Exception {
					throw new RuntimeException("Bad!");
				}
			});
			fail("Expected RuntimeException");
		}
		catch (BackOffInterruptedException e) {
			assertEquals("foo", e.getMessage());
		}
	}

	/**
	 * {@link BackOffPolicy} should apply also for exceptions that are re-thrown.
	 * @throws Throwable on error
	 */
	@Test
	public void testNoBackOffForRethrownException() throws Throwable {

		RetryTemplate tested = new RetryTemplate();
		tested.setRetryPolicy(new SimpleRetryPolicy(1));

		BackOffPolicy bop = createStrictMock(BackOffPolicy.class);
		@SuppressWarnings("serial")
		BackOffContext backOffContext = new BackOffContext() {
		};
		tested.setBackOffPolicy(bop);

		expect(bop.start(isA(RetryContext.class))).andReturn(backOffContext);
		replay(bop);

		try {
			tested.execute(new RetryCallback<Object, Exception>() {

				@Override
				public Object doWithRetry(RetryContext context) throws Exception {
					throw new Exception("maybe next time!");
				}

			}, null, new DefaultRetryState(tested) {

				@Override
				public boolean rollbackFor(Throwable exception) {
					return true;
				}

			});
			fail();
		}
		catch (Exception expected) {
			assertEquals("maybe next time!", expected.getMessage());
		}

		verify(bop);
	}

	private static class MockRetryCallback implements RetryCallback<Object, Exception> {

		private int attempts;

		private int attemptsBeforeSuccess;

		private Exception exceptionToThrow = new Exception();

		@Override
		public Object doWithRetry(RetryContext status) throws Exception {
			this.attempts++;
			if (this.attempts < this.attemptsBeforeSuccess) {
				throw this.exceptionToThrow;
			}
			return null;
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

		public void setExceptionToThrow(Exception exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

	private static class MockBackOffStrategy implements BackOffPolicy {

		public int backOffCalls;

		public int startCalls;

		@Override
		public BackOffContext start(RetryContext status) {
			this.startCalls++;
			return null;
		}

		@Override
		public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
			this.backOffCalls++;
		}

	}

}
