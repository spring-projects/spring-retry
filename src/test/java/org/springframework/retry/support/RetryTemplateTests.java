/*
 * Copyright 2006-2024 the original author or authors.
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.StatelessBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Rob Harrop
 * @author Dave Syer
 * @author Gary Russell
 * @author Henning Pöttker
 * @author Emanuele Ivaldi
 * @author Morulai Planinski
 * @author Tobias Soloschenko
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
			assertThat(callback.attempts).isEqualTo(x);
		}
	}

	@Test
	public void testSpecificExceptionRetry() {
		for (int x = 1; x <= 10; x++) {
			final int attemptsBeforeSuccess = x;
			final AtomicInteger attempts = new AtomicInteger(0);
			RetryCallback<String, IllegalStateException> callback = context -> {
				if (attempts.incrementAndGet() < attemptsBeforeSuccess) {
					// The parametrized exception type in the callback is really just
					// syntactic sugar since rules of erasure mean that the handler
					// can't really tell the difference between runtime exceptions.
					throw new IllegalArgumentException("Planned");
				}
				return "foo";
			};
			RetryTemplate retryTemplate = new RetryTemplate();
			retryTemplate.setRetryPolicy(new SimpleRetryPolicy(x));
			retryTemplate.execute(callback);
			assertThat(attempts.get()).isEqualTo(x);
		}
	}

	@Test
	public void testRetryOnPredicateWithRetry() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			MockRetryCallback callback = new MockRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			callback.setExceptionToThrow(new IllegalStateException("retry"));
			RetryTemplate retryTemplate = RetryTemplate.builder()
				.maxAttempts(x)
				.retryOn(classifiable -> classifiable instanceof IllegalStateException
						&& classifiable.getMessage().equals("retry"))
				.build();

			retryTemplate.execute(callback);
			assertThat(callback.attempts).isEqualTo(x);
		}
	}

	@Test
	public void testRetryOnPredicateWithoutRetry() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		callback.setAttemptsBeforeSuccess(0);
		callback.setExceptionToThrow(new IllegalStateException("no retry"));
		RetryTemplate retryTemplate = RetryTemplate.builder()
			.maxAttempts(3)
			.retryOn(classifiable -> classifiable instanceof IllegalStateException
					&& classifiable.getMessage().equals("retry"))
			.build();

		retryTemplate.execute(callback);
		assertThat(callback.attempts).isEqualTo(1);
	}

	@Test
	public void testSuccessfulRecovery() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		callback.setAttemptsBeforeSuccess(3);
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		final Object value = new Object();
		Object result = retryTemplate.execute(callback, context -> value);
		assertThat(callback.attempts).isEqualTo(2);
		assertThat(result).isEqualTo(value);
	}

	@Test
	public void testAlwaysTryAtLeastOnce() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		retryTemplate.execute(callback);
		assertThat(callback.attempts).isEqualTo(1);
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
		assertThatIllegalArgumentException().isThrownBy(() -> retryTemplate.execute(callback));
		assertThat(callback.attempts).isEqualTo(retryAttempts);
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
		assertThat(callback.attempts).isEqualTo(attempts);
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
		assertThat(callback.attempts).isEqualTo(attempts);
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
			assertThat(e).isNotNull();
			assertThat(callback.attempts).isEqualTo(1);
		}
		callback.setExceptionToThrow(new RuntimeException());

		template.execute(callback);
		assertThat(callback.attempts).isEqualTo(attempts);
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
			assertThat(callback.attempts).isEqualTo(x);
			assertThat(backOff.startCalls).isEqualTo(1);
			assertThat(backOff.backOffCalls).isEqualTo(x - 1);
		}
	}

	@Test
	public void testEarlyTermination() {
		RetryTemplate retryTemplate = new RetryTemplate();
		assertThatIllegalStateException().isThrownBy(() -> retryTemplate.execute(status -> {
			status.setExhaustedOnly();
			throw new IllegalStateException("Retry this operation");
		})).withMessage("Retry this operation");
	}

	@Test
	public void testEarlyTerminationWithOriginalException() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setThrowLastExceptionOnExhausted(true);
		assertThatIllegalStateException().isThrownBy(() -> retryTemplate.execute(status -> {
			status.setExhaustedOnly();
			throw new IllegalStateException("Retry this operation");
		})).withMessage("Retry this operation");
	}

	@Test
	public void testNestedContexts() throws Throwable {
		RetryTemplate outer = new RetryTemplate();
		final RetryTemplate inner = new RetryTemplate();
		outer.execute(status -> {
			RetryTemplateTests.this.context = status;
			RetryTemplateTests.this.count++;
			Object result = inner.execute((RetryCallback<Object, Throwable>) status1 -> {
				RetryTemplateTests.this.count++;
				assertThat(RetryTemplateTests.this.context).isNotNull();
				assertThat(RetryTemplateTests.this.context).isNotSameAs(status1);
				assertThat(status1.getParent()).isSameAs(RetryTemplateTests.this.context);
				assertThat(RetrySynchronizationManager.getContext()).describedAs("The context should be the child")
					.isSameAs(status1);
				return null;
			});
			assertThat(RetrySynchronizationManager.getContext()).describedAs("The context should be restored")
				.isSameAs(status);
			return result;
		});
		assertThat(this.count).isEqualTo(2);
	}

	@Test
	public void testRethrowError() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		try {
			retryTemplate.execute(context -> {
				throw new Error("Realllly bad!");
			});
			fail("Expected Error");
		}
		catch (Error e) {
			assertThat(e.getMessage()).isEqualTo("Realllly bad!");
		}
	}

	@Test
	public void testFailedPolicy() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy() {
			@Override
			public void registerThrowable(RetryContext context, Throwable throwable) {
				throw new RuntimeException("Planned");
			}
		});
		assertThatExceptionOfType(TerminatedRetryException.class).isThrownBy(() -> retryTemplate.execute(context -> {
			throw new RuntimeException("Realllly bad!");
		})).withCauseInstanceOf(RuntimeException.class).withStackTraceContaining("Planned");
	}

	@Test
	public void testBackOffInterrupted() {
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setBackOffPolicy(new StatelessBackOffPolicy() {
			@Override
			protected void doBackOff() throws BackOffInterruptedException {
				throw new BackOffInterruptedException("foo");
			}
		});
		assertThatExceptionOfType(BackOffInterruptedException.class).isThrownBy(() -> retryTemplate.execute(context -> {
			throw new RuntimeException("Bad!");
		})).withMessage("foo");
	}

	/**
	 * {@link BackOffPolicy} should apply also for exceptions that are re-thrown.
	 */
	@Test
	public void testNoBackOffForRethrownException() {

		RetryTemplate tested = new RetryTemplate();
		tested.setRetryPolicy(new SimpleRetryPolicy(1));

		BackOffPolicy bop = mock(BackOffPolicy.class);
		BackOffContext backOffContext = new BackOffContext() {
		};
		tested.setBackOffPolicy(bop);

		given(bop.start(any())).willReturn(backOffContext);

		assertThatExceptionOfType(Exception.class).isThrownBy(() -> tested.execute(context -> {
			throw new Exception("maybe next time!");
		}, null, new DefaultRetryState(tested) {

			@Override
			public boolean rollbackFor(Throwable exception) {
				return true;
			}

		})).withMessage("maybe next time!");
		verify(bop).start(any());
	}

	@Test
	public void testRetryOnBadResult() {
		RetryTemplate template = new RetryTemplate();
		template.registerListener(new RetryListener() {

			@Override
			public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback,
					T result) {

				if (result.equals("bad")) {
					throw new IllegalStateException("test");
				}
			}

		});
		AtomicBoolean first = new AtomicBoolean(true);
		AtomicInteger callCount = new AtomicInteger();
		template.execute((ctx) -> {
			callCount.incrementAndGet();
			return first.getAndSet(false) ? "bad" : "good";
		});
		assertThat(callCount.get()).isEqualTo(2);
	}

	@Test
	public void testContextForPolicyWithMaximumNumberOfAttempts() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		RetryPolicy retryPolicy = new SimpleRetryPolicy(2);
		retryTemplate.setRetryPolicy(retryPolicy);

		Integer result = retryTemplate.execute((RetryCallback<Integer, Throwable>) context -> (Integer) context
			.getAttribute(RetryContext.MAX_ATTEMPTS), context -> RetryPolicy.NO_MAXIMUM_ATTEMPTS_SET);

		assertThat(result).isEqualTo(2);
	}

	@Test
	public void testContextForPolicyWithNoMaximumNumberOfAttempts() throws Throwable {
		RetryTemplate retryTemplate = new RetryTemplate();
		RetryPolicy retryPolicy = new AlwaysRetryPolicy();
		retryTemplate.setRetryPolicy(retryPolicy);

		Integer result = retryTemplate.execute((RetryCallback<Integer, Throwable>) context -> (Integer) context
			.getAttribute(RetryContext.MAX_ATTEMPTS), context -> RetryPolicy.NO_MAXIMUM_ATTEMPTS_SET);

		assertThat(result).isEqualTo(RetryPolicy.NO_MAXIMUM_ATTEMPTS_SET);
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

	@Test
	public void testLoggingAppliedCorrectly() throws Exception {
		ArgumentCaptor<String> logOutputCaptor = ArgumentCaptor.forClass(String.class);
		RetryTemplate retryTemplate = new RetryTemplate();
		Log logMock = mock(Log.class);
		when(logMock.isTraceEnabled()).thenReturn(false);
		when(logMock.isDebugEnabled()).thenReturn(true);
		retryTemplate.setLogger(logMock);
		retryTemplate.execute(new MockRetryCallback());
		verify(logMock).debug(logOutputCaptor.capture());
		assertThat(logOutputCaptor.getValue()).contains("Retry: count=0");
	}

}
