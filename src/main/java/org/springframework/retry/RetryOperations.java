/*
 * Copyright 2006-2007 the original author or authors.
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

import org.springframework.retry.support.DefaultRetryState;

/**
 * Defines the basic set of operations implemented by {@link RetryOperations} to execute
 * operations with configurable retry behaviour.
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public interface RetryOperations {

	/**
	 * Execute the supplied {@link RetryCallback} with the configured retry semantics. See
	 * implementations for configuration details.
	 * @param <T> the return value
	 * @param retryCallback the {@link RetryCallback}
	 * @param <E> the exception to throw
	 * @return the value returned by the {@link RetryCallback} upon successful invocation.
	 * @throws E any {@link Exception} raised by the {@link RetryCallback} upon
	 * unsuccessful retry.
	 * @throws E the exception thrown
	 */
	<T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback) throws E;

	/**
	 * Execute the supplied {@link RetryCallback} with a fallback on exhausted retry to
	 * the {@link RecoveryCallback}. See implementations for configuration details.
	 * @param recoveryCallback the {@link RecoveryCallback}
	 * @param retryCallback the {@link RetryCallback} {@link RecoveryCallback} upon
	 * @param <T> the type to return
	 * @param <E> the type of the exception
	 * @return the value returned by the {@link RetryCallback} upon successful invocation,
	 * and that returned by the {@link RecoveryCallback} otherwise.
	 * @throws E any {@link Exception} raised by the unsuccessful retry.
	 */
	<T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback)
			throws E;

	/**
	 * A simple stateful retry. Execute the supplied {@link RetryCallback} with a target
	 * object for the attempt identified by the {@link DefaultRetryState}. Exceptions
	 * thrown by the callback are always propagated immediately so the state is required
	 * to be able to identify the previous attempt, if there is one - hence the state is
	 * required. Normal patterns would see this method being used inside a transaction,
	 * where the callback might invalidate the transaction if it fails.
	 *
	 * See implementations for configuration details.
	 * @param retryCallback the {@link RetryCallback}
	 * @param retryState the {@link RetryState}
	 * @param <T> the type of the return value
	 * @param <E> the type of the exception to return
	 * @return the value returned by the {@link RetryCallback} upon successful invocation,
	 * and that returned by the {@link RecoveryCallback} otherwise.
	 * @throws E any {@link Exception} raised by the {@link RecoveryCallback}.
	 * @throws ExhaustedRetryException if the last attempt for this state has already been
	 * reached
	 */
	<T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RetryState retryState)
			throws E, ExhaustedRetryException;

	/**
	 * A stateful retry with a recovery path. Execute the supplied {@link RetryCallback}
	 * with a fallback on exhausted retry to the {@link RecoveryCallback} and a target
	 * object for the retry attempt identified by the {@link DefaultRetryState}.
	 * @param recoveryCallback the {@link RecoveryCallback}
	 * @param retryState the {@link RetryState}
	 * @param retryCallback the {@link RetryCallback}
	 * @param <T> the return value type
	 * @param <E> the exception type
	 * @see #execute(RetryCallback, RetryState)
	 * @return the value returned by the {@link RetryCallback} upon successful invocation,
	 * and that returned by the {@link RecoveryCallback} otherwise.
	 * @throws E any {@link Exception} raised by the {@link RecoveryCallback} upon
	 * unsuccessful retry.
	 */
	<T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback, RecoveryCallback<T> recoveryCallback,
			RetryState retryState) throws E;

}
