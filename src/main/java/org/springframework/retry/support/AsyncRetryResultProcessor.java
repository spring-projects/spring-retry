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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryException;
import org.springframework.retry.backoff.LastBackoffPeriodSupplier;

/**
 * @author Dave Syer
 */
public abstract class AsyncRetryResultProcessor<T> implements RetryResultProcessor<T> {
    private static final Log logger = LogFactory.getLog(AsyncRetryResultProcessor.class);

    protected T doNewAttempt(Supplier<Result<T>> supplier) throws Throwable {
        logger.debug("Performing the next async callback invocation...");
        return supplier.get().getOrThrow();
    }

    protected abstract T scheduleNewAttemptAfterDelay(
            Supplier<Result<T>> supplier,
            ScheduledExecutorService reschedulingExecutor,
            long rescheduleAfterMillis,
            RetryContext ctx
    ) throws Throwable;

    protected T handleException(Supplier<Result<T>> supplier,
            Consumer<Throwable> handler,
            Throwable throwable,
            ScheduledExecutorService reschedulingExecutor,
            LastBackoffPeriodSupplier lastBackoffPeriodSupplier,
            RetryContext ctx) {
        try {
            handler.accept(unwrapIfNeed(throwable));

            if (reschedulingExecutor == null || lastBackoffPeriodSupplier == null) {
                return doNewAttempt(supplier);
            } else {
                long rescheduleAfterMillis = lastBackoffPeriodSupplier.get();
                logger.debug("Scheduling a next retry with a delay = " + rescheduleAfterMillis + " millis...");
                return scheduleNewAttemptAfterDelay(supplier, reschedulingExecutor, rescheduleAfterMillis, ctx);
            }
        }
        catch (Throwable t) {
            throw RetryTemplate.runtimeException(unwrapIfNeed(t));
        }
    }

    static Throwable unwrapIfNeed(Throwable throwable) {
        if (throwable instanceof ExecutionException
                || throwable instanceof CompletionException
                || throwable instanceof RetryException) {
            return throwable.getCause();
        } else {
            return throwable;
        }
    }
}
