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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryException;
import org.springframework.retry.backoff.LastBackoffPeriodSupplier;

/**
 * A {@link RetryResultProcessor} for a {@link CompletableFuture}. If a
 * {@link RetryCallback} returns a <code>CompletableFuture</code> this processor can be
 * used internally by the {@link RetryTemplate} to wrap it and process the result.
 *
 * @author Dave Syer
 */
public class CompletableFutureRetryResultProcessor<V>
		extends AsyncRetryResultProcessor<CompletableFuture<V>> {

	protected final Log logger = LogFactory.getLog(getClass());

	@Override
	public Result<CompletableFuture<V>> process(CompletableFuture<V> completable,
			Supplier<Result<CompletableFuture<V>>> supplier,
			Consumer<Throwable> handler, ScheduledExecutorService reschedulingExecutor,
			LastBackoffPeriodSupplier lastBackoffPeriodSupplier,
			RetryContext ctx) {

		CompletableFuture<V> handle = completable
				.thenApply(CompletableFuture::completedFuture)
				.exceptionally(throwable -> handleException(
						supplier, handler, throwable, reschedulingExecutor, lastBackoffPeriodSupplier, ctx)
				)
				.thenCompose(Function.identity());

		return new Result<>(handle);
	}

	protected CompletableFuture<V> scheduleNewAttemptAfterDelay(
			Supplier<Result<CompletableFuture<V>>> supplier,
			ScheduledExecutorService reschedulingExecutor, long rescheduleAfterMillis,
			RetryContext ctx)
	{
		CompletableFuture<CompletableFuture<V>> futureOfFurtherScheduling = new CompletableFuture<>();

		reschedulingExecutor.schedule(() -> {
			try {
				RetrySynchronizationManager.register(ctx);
				futureOfFurtherScheduling.complete(doNewAttempt(supplier));
			} catch (Throwable t) {
				futureOfFurtherScheduling.completeExceptionally(t);
				throw RetryTemplate.runtimeException(t);
			} finally {
				RetrySynchronizationManager.clear();
			}
		}, rescheduleAfterMillis, TimeUnit.MILLISECONDS);

		return futureOfFurtherScheduling.thenCompose(Function.identity());
	}
}