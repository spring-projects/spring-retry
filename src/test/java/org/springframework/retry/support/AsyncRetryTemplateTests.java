/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.retry.support;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

import org.springframework.classify.SubclassClassifier;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Dave Syer
 */
public class AsyncRetryTemplateTests {

	private RetryTemplate retryTemplate;

	@Before
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void init() {
		this.retryTemplate = new RetryTemplate();
		Map<Class<?>, RetryResultProcessor<?>> map = new HashMap<>();
		map.put(Future.class, new FutureRetryResultProcessor());
		map.put(CompletableFuture.class, new CompletableFutureRetryResultProcessor());
		SubclassClassifier processors = new SubclassClassifier(map,
				(RetryResultProcessor<?>) null);
		this.retryTemplate.setRetryResultProcessors(processors);
	}

	@Test
	public void testSuccessfulRetryCompletable() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(x);
			this.retryTemplate.setRetryPolicy(policy);
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			assertEquals(CompletableFutureRetryCallback.RESULT,
					result.get(1000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.attempts);
		}
	}

	@Test
	public void testSuccessfulRetryFuture() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			FutureRetryCallback callback = new FutureRetryCallback();
			callback.setAttemptsBeforeSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(x);
			this.retryTemplate.setRetryPolicy(policy);
			Future<Object> result = this.retryTemplate.execute(callback);
			assertEquals(FutureRetryCallback.RESULT,
					result.get(1000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.attempts);
		}
	}

	@Test
	public void testBackOffInvoked() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
			MockBackOffStrategy backOff = new MockBackOffStrategy();
			callback.setAttemptsBeforeSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(10);
			this.retryTemplate.setRetryPolicy(policy);
			this.retryTemplate.setBackOffPolicy(backOff);
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			assertEquals(CompletableFutureRetryCallback.RESULT,
					result.get(1000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.attempts);
			assertEquals(1, backOff.startCalls);
			assertEquals(x - 1, backOff.backOffCalls);
		}
	}

	@Test
	public void testNoSuccessRetry() throws Throwable {
		CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
		// Something that won't be thrown by JUnit...
		callback.setExceptionToThrow(new IllegalArgumentException());
		callback.setAttemptsBeforeSuccess(Integer.MAX_VALUE);
		int retryAttempts = 2;
		this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryAttempts));
		try {
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			result.get(1000L, TimeUnit.MILLISECONDS);
			fail("Expected IllegalArgumentException");
		}
		catch (ExecutionException e) {
			assertTrue("Expected IllegalArgumentException",
					e.getCause() instanceof IllegalArgumentException);
			assertEquals(retryAttempts, callback.attempts);
			return;
		}
		fail("Expected IllegalArgumentException");
	}

	private static class CompletableFutureRetryCallback
			implements RetryCallback<CompletableFuture<Object>, Exception> {

		public static Object RESULT = new Object();

		private int attempts;

		private int attemptsBeforeSuccess;

		private RuntimeException exceptionToThrow = new RuntimeException();

		@Override
		public CompletableFuture<Object> doWithRetry(RetryContext status)
				throws Exception {
			// !!!! Don't do this in real life - use a thread pool
			return CompletableFuture.supplyAsync(() -> {
				this.attempts++;
				if (this.attempts < this.attemptsBeforeSuccess) {
					throw this.exceptionToThrow;
				}
				return RESULT;
			});
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

		public void setExceptionToThrow(RuntimeException exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

	private static class FutureRetryCallback
			implements RetryCallback<Future<Object>, Exception> {

		public static Object RESULT = new Object();

		private int attempts;

		private int attemptsBeforeSuccess;

		private RuntimeException exceptionToThrow = new RuntimeException();

		@Override
		public Future<Object> doWithRetry(RetryContext status) throws Exception {
			// !!!! Don't do this in real life - use a thread pool
			return ForkJoinTask.adapt(() -> {
				this.attempts++;
				if (this.attempts < this.attemptsBeforeSuccess) {
					throw this.exceptionToThrow;
				}
				return RESULT;
			}).fork();
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

	}

	private static class MockBackOffStrategy implements BackOffPolicy {

		public int backOffCalls;

		public int startCalls;

		@Override
		public BackOffContext start(RetryContext status) {
			if (!status.hasAttribute(MockBackOffStrategy.class.getName())) {
				this.startCalls++;
				status.setAttribute(MockBackOffStrategy.class.getName(), true);
			}
			return null;
		}

		@Override
		public void backOff(BackOffContext backOffContext)
				throws BackOffInterruptedException {
			this.backOffCalls++;
		}

	}

}
