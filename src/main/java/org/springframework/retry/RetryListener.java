/*
 * Copyright 2006-2022 the original author or authors.
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

package org.springframework.retry;

/**
 * Interface for listener that can be used to add behaviour to a retry. Implementations of
 * {@link RetryOperations} can chose to issue callbacks to an interceptor during the retry
 * lifecycle.
 *
 * @author Dave Syer
 * @author Gary Russell
 * @author Henning Pöttker
 *
 */
public interface RetryListener {

	/**
	 * Called before the first attempt in a retry. For instance, implementers can set up
	 * state that is needed by the policies in the {@link RetryOperations}. The whole
	 * retry can be vetoed by returning false from this method, in which case a
	 * {@link TerminatedRetryException} will be thrown.
	 * @param <T> the type of object returned by the callback
	 * @param <E> the type of exception it declares may be thrown
	 * @param context the current {@link RetryContext}.
	 * @param callback the current {@link RetryCallback}.
	 * @return true if the retry should proceed.
	 */
	default <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
		return true;
	}

	/**
	 * Called after the final attempt (successful or not). Allow the listener to clean up
	 * any resource it is holding before control returns to the retry caller.
	 * @param context the current {@link RetryContext}.
	 * @param callback the current {@link RetryCallback}.
	 * @param throwable the last exception that was thrown by the callback.
	 * @param <E> the exception type
	 * @param <T> the return value
	 */
	default <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
			Throwable throwable) {
	}

	/**
	 * Called after a successful attempt; allow the listener to throw a new exception to
	 * cause a retry (according to the retry policy), based on the result returned by the
	 * {@link RetryCallback#doWithRetry(RetryContext)}
	 * @param <T> the return type.
	 * @param context the current {@link RetryContext}.
	 * @param callback the current {@link RetryCallback}.
	 * @param result the result returned by the callback method.
	 * @since 2.0
	 */
	default <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
	}

	/**
	 * Called after every unsuccessful attempt at a retry.
	 * @param context the current {@link RetryContext}.
	 * @param callback the current {@link RetryCallback}.
	 * @param throwable the last exception that was thrown by the callback.
	 * @param <T> the return value
	 * @param <E> the exception to throw
	 */
	default <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
			Throwable throwable) {
	}

}
