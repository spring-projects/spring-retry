/*
 * Copyright 2006-2019 the original author or authors.
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

package org.springframework.retry.policy;

import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.retry.support.RetryTemplate;

/**
 * Simple retry policy that is aware only about attempt count and retries a fixed number
 * of times. The number of attempts includes the initial try.
 * <p>
 * It is not recommended to use it directly, because usually exception classification is
 * strongly recommended (to not retry on OutOfMemoryError, for example).
 * <p>
 * For daily usage see {@link RetryTemplate#builder()}
 * <p>
 * Volatility of maxAttempts allows concurrent modification and does not require safe
 * publication of new instance after construction.
 */
@SuppressWarnings("serial")
public class MaxAttemptsRetryPolicy implements RetryPolicy {

	/**
	 * The default limit to the number of attempts for a new policy.
	 */
	public final static int DEFAULT_MAX_ATTEMPTS = 3;

	private volatile int maxAttempts;

	/**
	 * Create a {@link MaxAttemptsRetryPolicy} with the default number of retry attempts
	 * (3), retrying all throwables.
	 */
	public MaxAttemptsRetryPolicy() {
		this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
	}

	/**
	 * Create a {@link MaxAttemptsRetryPolicy} with the specified number of retry
	 * attempts, retrying all throwables.
	 * @param maxAttempts the maximum number of attempts
	 */
	public MaxAttemptsRetryPolicy(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	/**
	 * Set the number of attempts before retries are exhausted. Includes the initial
	 * attempt before the retries begin so, generally, will be {@code >= 1}. For example
	 * setting this property to 3 means 3 attempts total (initial + 2 retries).
	 * @param maxAttempts the maximum number of attempts including the initial attempt.
	 */
	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	/**
	 * The maximum number of attempts before failure.
	 * @return the maximum number of attempts
	 */
	public int getMaxAttempts() {
		return this.maxAttempts;
	}

	/**
	 * Test for retryable operation based on the status.
	 *
	 * @see RetryPolicy#canRetry(RetryContext)
	 * @return true if the last exception was retryable and the number of attempts so far
	 * is less than the limit.
	 */
	@Override
	public boolean canRetry(RetryContext context) {
		return context.getRetryCount() < this.maxAttempts;
	}

	@Override
	public void close(RetryContext status) {
	}

	/**
	 * Update the status with another attempted retry and the latest exception.
	 *
	 * @see RetryPolicy#registerThrowable(RetryContext, Throwable)
	 */
	@Override
	public void registerThrowable(RetryContext context, Throwable throwable) {
		((RetryContextSupport) context).registerThrowable(throwable);
	}

	/**
	 * Get a status object that can be used to track the current operation according to
	 * this policy. Has to be aware of the latest exception and the number of attempts.
	 *
	 * @see RetryPolicy#open(RetryContext)
	 */
	@Override
	public RetryContext open(RetryContext parent) {
		return new RetryContextSupport(parent);
	}

}
