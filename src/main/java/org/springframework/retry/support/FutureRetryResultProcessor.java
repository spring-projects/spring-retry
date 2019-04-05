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

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryException;

/**
 * A {@link RetryResultProcessor} for a plain {@link Future}. If a {@link RetryCallback}
 * returns a <code>Future</code> this processor can be used internally by the
 * {@link RetryTemplate} to wrap it and process the result.
 *
 * @author Dave Syer
 */
public class FutureRetryResultProcessor implements RetryResultProcessor<Future<?>> {

	@Override
	public Result<Future<?>> process(Future<?> future,
			Supplier<Result<Future<?>>> supplier, Consumer<Throwable> handler) {
		return new Result<Future<?>>(new FutureWrapper(future, supplier, handler));
	}

	private class FutureWrapper implements Future<Object> {

		private Future<?> delegate;

		private Supplier<Result<Future<?>>> supplier;

		private Consumer<Throwable> handler;

		FutureWrapper(Future<?> delegate, Supplier<Result<Future<?>>> supplier,
				Consumer<Throwable> handler) {
			this.delegate = delegate;
			this.supplier = supplier;
			this.handler = handler;
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
		public Object get() throws InterruptedException, ExecutionException {
			try {
				return this.delegate.get();
			}
			catch (ExecutionException e) {
				return handle(e);
			}
		}

		@Override
		public Object get(long timeout, TimeUnit unit)
				throws InterruptedException, ExecutionException, TimeoutException {
			try {
				return this.delegate.get(timeout, unit);
			}
			catch (ExecutionException e) {
				return handle(e, timeout, unit);
			}
		}

		private Object handle(ExecutionException throwable) {
			return handle(throwable, -1, null);
		}

		private Object handle(ExecutionException throwable, long timeout, TimeUnit unit) {
			Throwable error = throwable.getCause();
			try {
				this.handler.accept(error);
				Result<Future<?>> result = this.supplier.get();
				if (result.isComplete()) {
					if (timeout < 0) {
						return result.getResult().get();
					}
					else {
						return result.getResult().get(timeout, unit);
					}
				}
				throw result.exception;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				error = e;
			}
			catch (CompletionException e) {
				error = e.getCause();
			}
			catch (ExecutionException e) {
				error = e.getCause();
			}
			catch (RetryException e) {
				error = e.getCause();
			}
			catch (Throwable e) {
				error = e;
			}
			throw RetryTemplate.runtimeException(error);
		}

	}

}