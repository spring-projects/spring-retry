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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.RememberPeriodSleeper;
import org.springframework.retry.backoff.Sleeper;

import static org.junit.Assert.assertTrue;
import static org.springframework.retry.util.test.TestUtils.getPropertyValue;

/**
 * @author Dave Syer
 */
public class AbstractAsyncRetryTest {
	
	/* ---------------- Async callbacks implementations for different types -------------- */

	static class CompletableFutureRetryCallback
			extends AbstractRetryCallback<CompletableFuture<Object>> {

		@Override
		public CompletableFuture<Object> schedule(Supplier<Object> callback, ExecutorService workerExecutor) {
			return CompletableFuture.supplyAsync(callback, workerExecutor);
		}

		@Override
		Object awaitItself(CompletableFuture<Object> asyncType) {
			return asyncType.join();
		}
	}

	static class FutureRetryCallback
			extends AbstractRetryCallback<Future<Object>> {
		
		@Override
		public Future<Object> schedule(Supplier<Object> callback, ExecutorService executor) {
			return executor.submit(callback::get);
		}

		@Override
		Object awaitItself(Future<Object> asyncType) throws Throwable {
			return asyncType.get();
		}
	}

	static abstract class AbstractRetryCallback<A>
			implements RetryCallback<A, Exception> {

		final Object defaultResult = new Object();
		final Log logger = LogFactory.getLog(getClass());

		final AtomicInteger jobAttempts = new AtomicInteger();
		final AtomicInteger schedulingAttempts = new AtomicInteger();

		volatile int attemptsBeforeSchedulingSuccess;
		volatile int attemptsBeforeJobSuccess;

		volatile RuntimeException exceptionToThrow = new RuntimeException();

		volatile Function<RetryContext, Object> resultSupplier = ctx -> defaultResult;
		volatile Consumer<RetryContext> customCodeBeforeScheduling = ctx -> {};

		final List<String> schedulerThreadNames = new CopyOnWriteArrayList<>();
		final List<Long> invocationMoments = new CopyOnWriteArrayList<>();

		final ExecutorService workerExecutor = Executors.newSingleThreadExecutor(
				getNamedThreadFactory(WORKER_THREAD_NAME)
		);

		public abstract A schedule(Supplier<Object> callback, ExecutorService executor);

		abstract Object awaitItself(A asyncType) throws Throwable;

		@Override
		public A doWithRetry(RetryContext ctx)
				throws Exception {
			rememberThreadName();
			rememberInvocationMoment();

			throwIfSchedulingTooEarly();

			customCodeBeforeScheduling.accept(ctx);

			return schedule(() -> {
				try {
					// a hack to avoid running CompletableFuture#thenApplyAsync in the caller thread
					Thread.sleep(100L);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				throwIfJobTooEarly();
				logger.debug("Succeeding the callback...");
				return resultSupplier.apply(ctx);
			}, workerExecutor);
		}
		
		void rememberInvocationMoment() {
			invocationMoments.add(System.currentTimeMillis());
		}

		void rememberThreadName() {
			schedulerThreadNames.add(Thread.currentThread().getName());
		}

		void throwIfJobTooEarly() {
			if (this.jobAttempts.incrementAndGet() < this.attemptsBeforeJobSuccess) {
				logger.debug("Failing job...");
				throw this.exceptionToThrow;
			}
		}

		void throwIfSchedulingTooEarly() {
			if (this.schedulingAttempts.incrementAndGet() < this.attemptsBeforeSchedulingSuccess) {
				logger.debug("Failing scheduling...");
				throw this.exceptionToThrow;
			}
		}

		void setAttemptsBeforeJobSuccess(int attemptsBeforeJobSuccess) {
			this.attemptsBeforeJobSuccess = attemptsBeforeJobSuccess;
		}

		void setAttemptsBeforeSchedulingSuccess(int attemptsBeforeSchedulingSuccess) {
			this.attemptsBeforeSchedulingSuccess = attemptsBeforeSchedulingSuccess;
		}

		void setExceptionToThrow(RuntimeException exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

		void setResultSupplier(Function<RetryContext, Object> resultSupplier) {
			this.resultSupplier = resultSupplier;
		}

		void setCustomCodeBeforeScheduling(Consumer<RetryContext> customCodeBeforeScheduling) {
			this.customCodeBeforeScheduling = customCodeBeforeScheduling;
		}
	}


	static class MockBackOffStrategy implements BackOffPolicy {

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

	/* ---------------- Utilities -------------- */

	static final String SCHEDULER_THREAD_NAME = "scheduler";
    static final String WORKER_THREAD_NAME = "worker";

	static ScheduledExecutorService getNamedScheduledExecutor() {
		return Executors.newScheduledThreadPool(
				1,
                getNamedThreadFactory(AbstractAsyncRetryTest.SCHEDULER_THREAD_NAME)
		);
	}

    static ThreadFactory getNamedThreadFactory(String threadName) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName(threadName);
                return thread;
            }
        };
    }

    void assertRememberingSleeper(RetryTemplate template) {
        // The sleeper of the backoff policy should be an instance of RememberPeriodSleeper, means not Thread.sleep()
        BackOffPolicy backOffPolicy = getPropertyValue(template, "backOffPolicy", BackOffPolicy.class);
        Sleeper sleeper = getPropertyValue(backOffPolicy, "sleeper", Sleeper.class);
        assertTrue(sleeper instanceof RememberPeriodSleeper);
    }
}
