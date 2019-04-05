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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryException;

/**
 * A {@link RetryResultProcessor} for a {@link CompletableFuture}. If a
 * {@link RetryCallback} returns a <code>CompletableFuture</code> this processor can be
 * used internally by the {@link RetryTemplate} to wrap it and process the result.
 *
 * @author Dave Syer
 */
public class CompletableFutureRetryResultProcessor
		implements RetryResultProcessor<CompletableFuture<?>> {

	@Override
	public Result<CompletableFuture<?>> process(CompletableFuture<?> completable,
			Supplier<Result<CompletableFuture<?>>> supplier,
			Consumer<Throwable> handler) {
		@SuppressWarnings("unchecked")
		CompletableFuture<Object> typed = (CompletableFuture<Object>) completable;
		CompletableFuture<?> handle = typed
				.thenApply(value -> CompletableFuture.completedFuture(value))
				.exceptionally(throwable -> apply(supplier, handler, throwable))
				.thenCompose(Function.identity());
		return new Result<>(handle);
	}

	private CompletableFuture<Object> apply(
			Supplier<Result<CompletableFuture<?>>> supplier, Consumer<Throwable> handler,
			Throwable throwable) {
		Throwable error = throwable;
		try {
			if (throwable instanceof ExecutionException
					|| throwable instanceof CompletionException) {
				error = throwable.getCause();
			}
			handler.accept(error);
			Result<CompletableFuture<?>> result = supplier.get();
			if (result.isComplete()) {
				@SuppressWarnings("unchecked")
				CompletableFuture<Object> output = (CompletableFuture<Object>) result
						.getResult();
				return output;
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