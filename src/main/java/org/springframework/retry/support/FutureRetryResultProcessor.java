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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.LastBackoffPeriodSupplier;

/**
 * todo: check or remove after discussion
 *
 * A {@link RetryResultProcessor} for a plain {@link Future}. If a {@link RetryCallback}
 * returns a <code>Future</code> this processor can be used internally by the
 * {@link RetryTemplate} to wrap it and process the result.
 *	
 * @author Dave Syer
 */
public class FutureRetryResultProcessor<V> extends AsyncRetryResultProcessor<Future<V>> {

	@Override
	public Result<Future<V>> process(Future<V> future,
			Supplier<Result<Future<V>>> supplier, Consumer<Throwable> handler,
			ScheduledExecutorService reschedulingExecutor, LastBackoffPeriodSupplier lastBackoffPeriodSupplier,
			RetryContext ctx) {
		return new Result<>(new FutureWrapper(future, supplier, handler, this, reschedulingExecutor,
				lastBackoffPeriodSupplier, ctx));
	}

	@Override
	protected Future<V> scheduleNewAttemptAfterDelay(Supplier<Result<Future<V>>> supplier,
			ScheduledExecutorService reschedulingExecutor, long rescheduleAfterMillis, RetryContext ctx)
			throws Throwable
	{
		ScheduledFuture<Future<V>> scheduledFuture = reschedulingExecutor.schedule(() -> {
			try {
				return doNewAttempt(supplier);
			} catch (Throwable t) {
				throw RetryTemplate.runtimeException(t);
			}
		}, rescheduleAfterMillis, TimeUnit.MILLISECONDS);

		return new FutureFlatter(scheduledFuture);
	}

	private class FutureWrapper implements Future<V> {

		private Future<V> delegate;

		private Supplier<Result<Future<V>>> supplier;

		private Consumer<Throwable> handler;
		private AsyncRetryResultProcessor<Future<V>> processor;
		private final ScheduledExecutorService reschedulingExecutor;
		private final LastBackoffPeriodSupplier lastBackoffPeriodSupplier;
		private RetryContext ctx;

		FutureWrapper(Future<V> delegate, Supplier<Result<Future<V>>> supplier,
				Consumer<Throwable> handler, AsyncRetryResultProcessor<Future<V>> processor,
				ScheduledExecutorService reschedulingExecutor, LastBackoffPeriodSupplier lastBackoffPeriodSupplier,
				RetryContext ctx) {
			this.delegate = delegate;
			this.supplier = supplier;
			this.handler = handler;
			this.processor = processor;
			this.reschedulingExecutor = reschedulingExecutor;
			this.lastBackoffPeriodSupplier = lastBackoffPeriodSupplier;
			this.ctx = ctx;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return this.delegate.cancel(mayInterruptIfRunning);
		}

		@Override
		public boolean isCancelled() {
			return this.delegate.isCancelled();
		}

		@Override
		public boolean isDone() {
			return this.delegate.isDone();
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			try {
				return this.delegate.get();
			}
			catch (Throwable e) {
				return processor.handleException(supplier, handler, e, reschedulingExecutor, lastBackoffPeriodSupplier, ctx)
						.get();
			}
		}

		@Override
		public V get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			try {
				return this.delegate.get(timeout, unit);
			}
			catch (Throwable e) {
				return processor.handleException(supplier, handler, e, reschedulingExecutor, lastBackoffPeriodSupplier, ctx)
						.get(timeout, unit);
			}
		}

	}

	private class FutureFlatter implements Future<V> {

		private Future<Future<V>> nestedFuture;

		FutureFlatter(Future<Future<V>> nestedFuture) {
			this.nestedFuture = nestedFuture;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			try {
			if (this.nestedFuture.isDone()) {
				return this.nestedFuture.get().cancel(mayInterruptIfRunning);
			} else {
				return this.nestedFuture.cancel(mayInterruptIfRunning);
			}
			} catch (Throwable t) {
				throw RetryTemplate.runtimeException(t);
			}
	}

		@Override
		public boolean isCancelled() {
			try {
				return this.nestedFuture.isCancelled()
						|| (this.nestedFuture.isDone() && this.nestedFuture.get().isCancelled());
			} catch (Throwable t) {
				throw RetryTemplate.runtimeException(t);
			}
		}

		@Override
		public boolean isDone() {
			try {
				return this.nestedFuture.isDone() && this.nestedFuture.get().isDone();
			} catch (Throwable t) {
				throw RetryTemplate.runtimeException(t);
			}
		}

		@Override
		public V get() throws InterruptedException, ExecutionException {
			return this.nestedFuture.get().get();
		}

		@Override
		public V get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			return this.nestedFuture.get(timeout, unit).get(timeout, unit);
		}

	}

}