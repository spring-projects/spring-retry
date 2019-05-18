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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

public class AsyncReschedulingTests extends AbstractAsyncRetryTest {

	/**
	 * Scheduling retry + job immediate success.
	 *
	 * - async callback succeeds at 3rd attempt
	 * - actual job succeeds on 1st attempt
	 * - no backoff
	 */

	@Test
	public void testInitialSchedulingEventualSuccessCF() throws Throwable {
		doTestInitialSchedulingEventualSuccess(new CompletableFutureRetryCallback());
	}

	@Test
	public void testInitialSchedulingEventualSuccessF() throws Throwable {
		doTestInitialSchedulingEventualSuccess(new FutureRetryCallback());
	}

	private <A> void doTestInitialSchedulingEventualSuccess(AbstractRetryCallback<A> callback) throws Throwable {
		RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(5)
				.noBackoff()
				.asyncRetry()
				.build();

		callback.setAttemptsBeforeSchedulingSuccess(3);
		callback.setAttemptsBeforeJobSuccess(1);
		assertEquals(callback.defaultResult, callback.awaitItself(template.execute(callback)));

		// All invocations before first successful scheduling should be performed by the caller thread
		assertEquals(Collections.nCopies(3, Thread.currentThread().getName()), callback.schedulerThreadNames);

		assertEquals(1, callback.jobAttempts.get());
	}

    /**
	 * Immediate success of both scheduling and job.
	 *
     * - async callback, that does not fail itself
     * - actual job succeeds on 1st attempt
     * - backoff is not necessary
     */
    
	@Test
	public void testImmediateSuccessCF() throws Throwable {
		doTestImmediateSuccess(new CompletableFutureRetryCallback());
	}

	@Test
	public void testImmediateSuccessF() throws Throwable {
		doTestImmediateSuccess(new FutureRetryCallback());
	}

	private <A> void doTestImmediateSuccess(AbstractRetryCallback<A> callback) throws Throwable {
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

		RetryTemplate template = RetryTemplate.builder()
				.fixedBackoff(10000)
				.asyncRetry(executor)
				.build();

		callback.setAttemptsBeforeSchedulingSuccess(1);
		callback.setAttemptsBeforeJobSuccess(1);
		assertEquals(callback.defaultResult, callback.awaitItself(template.execute(callback)));

		// Single invocation should be performed by the caller thread
		assertEquals(Collections.singletonList(Thread.currentThread().getName()), callback.schedulerThreadNames);

		assertEquals(1, callback.jobAttempts.get());

		// No interaction with the rescheduling executor should be performed if the first execution of the job succeeds.
		verifyZeroInteractions(executor);
	}

    /**
	 * Async retry with rescheduler.
	 * 
     * - async callback, that does not fail itself
     * - actual job succeeds on 3rd attempt
     * - backoff is performed using executor, without Thread.sleep()
     */

	@Test
	public void testAsyncRetryWithReschedulerCF() throws Throwable {
		doTestAsyncRetryWithRescheduler(new CompletableFutureRetryCallback());
	}

	@Test
	public void testAsyncRetryWithReschedulerF() throws Throwable {
		doTestAsyncRetryWithRescheduler(new FutureRetryCallback());
	}
	
	private <A> void doTestAsyncRetryWithRescheduler(AbstractRetryCallback<A> callback) throws Throwable {

        int targetFixedBackoff = 150;

		ScheduledExecutorService executor = getNamedScheduledExecutor();

        RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(4)
				.fixedBackoff(targetFixedBackoff)
				.asyncRetry(executor)
				.build();
        
        callback.setAttemptsBeforeSchedulingSuccess(1);
		callback.setAttemptsBeforeJobSuccess(3);
		assertEquals(callback.defaultResult, callback.awaitItself(template.execute(callback)));
		assertEquals(3, callback.jobAttempts.get());

		// All invocations after the first successful scheduling should be performed by the the rescheduler thread
		assertEquals(Arrays.asList(
				Thread.currentThread().getName(),
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME
		), callback.schedulerThreadNames);

		assertRememberingSleeper(template);

        // Expected backoff should be performed
        List<Long> moments = callback.invocationMoments;
        for (int i = 0; i < moments.size() - 1; i++) {
            long approxBackoff = moments.get(i + 1) - moments.get(i);
            assertTrue(approxBackoff > targetFixedBackoff);
        }
    }

    /**
	 * Async retry without backoff
	 * 
     * - async callback succeeds on 2nd attempt
     * - actual job succeeds on 3nd attempt
     * - default zero backoff is used (which has no sleeper at all),
     *   and therefore rescheduler executor is not used at all
     */

	@Test
	public void testAsyncRetryWithoutBackoffCF() throws Throwable {
		doTestAsyncRetryWithoutBackoff(new CompletableFutureRetryCallback());
	}

	// todo: problem: a Future can start retrying only when user calls get(). Consider to not support Future at all.
	/*@Test
	public void testAsyncRetryWithoutBackoffF() throws Throwable {
		doTestAsyncRetryWithoutBackoff(new FutureRetryCallback());
	}*/
	
    private <A> void doTestAsyncRetryWithoutBackoff(AbstractRetryCallback<A> callback) throws Throwable {
        RetryTemplate template = RetryTemplate.builder()
                .maxAttempts(4)
				.asyncRetry()
                .build();

        callback.setAttemptsBeforeSchedulingSuccess(2);
        callback.setAttemptsBeforeJobSuccess(3);
        assertEquals(callback.defaultResult, callback.awaitItself(template.execute(callback)));
        assertEquals(4, callback.schedulingAttempts.get());
        assertEquals(3, callback.jobAttempts.get());

        // All invocations after the first successful scheduling should be performed by the
		// the worker thread (because not backoff and no rescheduler thread)
        assertEquals(Arrays.asList(
                Thread.currentThread().getName(),
                Thread.currentThread().getName(),
				WORKER_THREAD_NAME,
				WORKER_THREAD_NAME
        ), callback.schedulerThreadNames);
    }

	/**
	 * Exhausted on scheduling retries
	 */

	@Test
	public void testExhaustOnSchedulingCF() throws Throwable {
		doTestExhaustOnScheduling(new CompletableFutureRetryCallback());
	}

	@Test
	public void testExhaustOnSchedulingF() throws Throwable {
		doTestExhaustOnScheduling(new FutureRetryCallback());
	}
	
	private <A> void doTestExhaustOnScheduling(AbstractRetryCallback<A> callback) throws Throwable {
		RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(2)
				.asyncRetry()
				.fixedBackoff(100)
				.build();

		callback.setAttemptsBeforeSchedulingSuccess(5);
		callback.setAttemptsBeforeJobSuccess(5);

		try {
			callback.awaitItself(template.execute(callback));
			fail("An exception should be thrown above");
		} catch (Exception e) {
			assertSame(e, callback.exceptionToThrow);
		}

		assertEquals(Arrays.asList(
				Thread.currentThread().getName(),
				Thread.currentThread().getName()
		), callback.schedulerThreadNames);
	}

	/**
	 * Exhausted on job retries
	 */
	
	@Test
	public void testExhaustOnJobWithReschedulerCF() throws Throwable {
		doTestExhaustOnJobWithRescheduler(new CompletableFutureRetryCallback());
	}

	@Test
	public void testExhaustOnJobWithReschedulerF() throws Throwable {
		doTestExhaustOnJobWithRescheduler(new FutureRetryCallback());
	}

	private <A> void doTestExhaustOnJobWithRescheduler(AbstractRetryCallback<A> callback) throws Throwable {
		RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(5)
				.asyncRetry(getNamedScheduledExecutor())
				.exponentialBackoff(10, 2, 100)
				.build();

		callback.setAttemptsBeforeSchedulingSuccess(1);
		callback.setAttemptsBeforeJobSuccess(6);

		try {
			Object v = callback.awaitItself(template.execute(callback));
			fail("An exception should be thrown above");
			// Single wrapping by CompletionException is expected by CompletableFuture contract
		} catch (Exception ce) {
			assertSame(ce.getCause(), callback.exceptionToThrow);
		}

		assertEquals(Arrays.asList(
				Thread.currentThread().getName(),
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME
		), callback.schedulerThreadNames);
	}

	// todo: rejected execution
	// todo: interrupt executor
	// rethrow not too late


	/*
	 * Nested rescheduling
	 */

	@Test
	public void testNested() throws Throwable {
		ScheduledExecutorService executor = getNamedScheduledExecutor();

		RetryTemplate outerTemplate = RetryTemplate.builder()
				.infiniteRetry()
				.asyncRetry(executor)
				.fixedBackoff(10)
				.build();

		RetryTemplate innerTemplate = RetryTemplate.builder()
				.infiniteRetry()
				.asyncRetry(executor)
				.fixedBackoff(10)
				.build();

		CompletableFutureRetryCallback innerCallback = new CompletableFutureRetryCallback();
		innerCallback.setAttemptsBeforeSchedulingSuccess(3);
		innerCallback.setAttemptsBeforeJobSuccess(3);
		innerCallback.setCustomCodeBeforeScheduling(ctx -> {
			// The current context should be available via RetrySynchronizationManager while scheduling
			// (withing user's async callback itself)
			assertEquals(ctx, RetrySynchronizationManager.getContext());

			// We have no control over user's worker thread, so we can not implicitly set/get the parent
			// context via RetrySynchronizationManager.
			assertNull(ctx.getParent());
		});
		innerCallback.setResultSupplier(ctx -> {
			// There is no way to implicitly pass the context into the worker thread, because the worker executor,
			// thread and callback are fully controlled by the user. The retry engine deals with only
			// scheduling/rescheduling and their result (e.g. CompletableFuture)
			assertNull(RetrySynchronizationManager.getContext());

			return innerCallback.defaultResult;
		});

		CompletableFutureRetryCallback outerCallback = new CompletableFutureRetryCallback();
		outerCallback.setAttemptsBeforeSchedulingSuccess(3);
		outerCallback.setAttemptsBeforeJobSuccess(3);
		outerCallback.setCustomCodeBeforeScheduling(ctx -> {
			// The current context should be available via RetrySynchronizationManager while scheduling
			// (withing user's async callback itself)
			assertEquals(ctx, RetrySynchronizationManager.getContext());
		});
		outerCallback.setResultSupplier(ctx -> {
			try {
				assertNull(RetrySynchronizationManager.getContext());
				CompletableFuture<Object> innerResultFuture = innerTemplate.execute(innerCallback);
				assertNull(RetrySynchronizationManager.getContext());

				Object innerResult = innerCallback.awaitItself(innerResultFuture);
				assertNull(RetrySynchronizationManager.getContext());

				// Return inner result as outer result
				return innerResult;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});


		Object outerResult = outerCallback.awaitItself(outerTemplate.execute(outerCallback));
		assertEquals(innerCallback.defaultResult, outerResult);

		assertEquals(Arrays.asList(
				// initial scheduling of the outer callback
				Thread.currentThread().getName(),
				Thread.currentThread().getName(),
				Thread.currentThread().getName(),
				// rescheduling of the outer callback
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME
		), outerCallback.schedulerThreadNames);

		assertEquals(Arrays.asList(
				// initial scheduling of the inner callback
				WORKER_THREAD_NAME,
				WORKER_THREAD_NAME,
				WORKER_THREAD_NAME,
				// rescheduling of the inner callback
				SCHEDULER_THREAD_NAME,
				SCHEDULER_THREAD_NAME
		), innerCallback.schedulerThreadNames);
	}


	/**
	 * Test with additional chained completable futures.
	 */
	
	@Test
	public void testAdditionalChainedCF() throws Throwable {

		Object additionalInnerResult = new Object();
		CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback() {
			@Override
			public CompletableFuture<Object> schedule(Supplier<Object> callback, ExecutorService workerExecutor) {
				return super.schedule(callback, workerExecutor)
						// Additional inner cf
						.thenApply(r -> {
							assertEquals(this.defaultResult, r);
							return additionalInnerResult;
						});
			}
		};
		RetryTemplate template = RetryTemplate.builder()
				.maxAttempts(4)
				.asyncRetry()
				.build();

		callback.setAttemptsBeforeSchedulingSuccess(2);
		callback.setAttemptsBeforeJobSuccess(3);

		Object additionalOuterResult = new Object();
		CompletableFuture<Object> cf = template.execute(callback)
				// Additional step
				.thenApply(r -> {
					assertEquals(additionalInnerResult, r);
					return additionalOuterResult;
				});

		assertEquals(additionalOuterResult, callback.awaitItself(cf));
		assertEquals(4, callback.schedulingAttempts.get());
		assertEquals(3, callback.jobAttempts.get());

		// All invocations after the first successful scheduling should be performed by the
		// the worker thread (because not backoff and no rescheduler thread)
		assertEquals(Arrays.asList(
				Thread.currentThread().getName(),
				Thread.currentThread().getName(),
				WORKER_THREAD_NAME,
				WORKER_THREAD_NAME
		), callback.schedulerThreadNames);
	}


	// todo: test stateful rescheduling
	// todo: test RejectedExecutionException on rescheduler
	// todo: test InterruptedException
	// todo: support declarative async
}
