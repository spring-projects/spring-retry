/*
 * Copyright 2006-2019 the original author or authors.
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

package org.springframework.retry.policy;

import java.util.Map;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryPolicy;
import org.springframework.util.ClassUtils;

/**
 * Simple retry policy that retries a fixed number of times for a set of named
 * exceptions (and subclasses). The number of attempts includes the initial try,
 * so e.g.
 *
 * <pre>
 * retryTemplate = new RetryTemplate(new SimpleRetryPolicy(3));
 * retryTemplate.execute(callback);
 * </pre>
 *
 * will execute the callback at least once, and as many as 3 times.
 *
 * Since version 1.3 it is not necessary to use this class. The same behaviour
 * can be achieved by constructing a {@link CompositeRetryPolicy} with {@link MaxAttemptsRetryPolicy}
 * and {@link BinaryExceptionClassifierRetryPolicy} inside, that is actually performed by:
 * <pre> {@code:
 * RetryTemplate.newBuilder()
 *                  .maxAttempts(3)
 *                  .retryOn(Exception.class)
 *                  .build();
 * }</pre>
 * or by {@link org.springframework.retry.support.RetryTemplate#newDefaultInstance()}
 *
 * @author Dave Syer
 * @author Rob Harrop
 * @author Gary Russell
 * @author Aleksandr Shamukov
 */
@SuppressWarnings("serial")
public class SimpleRetryPolicy extends CompositeRetryPolicy {

	/**
	 * The default limit to the number of attempts for a new policy.
	 */
	public final static int DEFAULT_MAX_ATTEMPTS = 3;

	// The reference to maxAttemptsRetryPolicy is held here to allow usage of public
	// simpleRetryPolicy.setMaxAttempts(), while the link to exceptionClassifierRetryPolicy
	// is held just for symmetry.
	private final MaxAttemptsRetryPolicy maxAttemptsRetryPolicy;
	private final BinaryExceptionClassifierRetryPolicy exceptionClassifierRetryPolicy;

	/**
	 * Create a {@link SimpleRetryPolicy} with the default number of retry
	 * attempts, retrying all exceptions.
	 */
	public SimpleRetryPolicy() {
		this(DEFAULT_MAX_ATTEMPTS, BinaryExceptionClassifier.getDefaultClassifier());
	}

	/**
	 * Create a {@link SimpleRetryPolicy} with the specified number of retry
	 * attempts, retrying all exceptions.
	 */
	public SimpleRetryPolicy(int maxAttempts) {
		this(maxAttempts, BinaryExceptionClassifier.getDefaultClassifier());
	}

	/**
	 * Create a {@link SimpleRetryPolicy} with the specified number of retry
	 * attempts.
	 *
	 * @param maxAttempts the maximum number of attempts
	 * @param retryableExceptions the map of exceptions that are retryable
	 */
	public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions) {
		this(maxAttempts, retryableExceptions, false, false);
	}

	/**
	 * Create a {@link SimpleRetryPolicy} with the specified number of retry
	 * attempts. If traverseCauses is true, the exception causes will be traversed until
	 * a match is found.
	 *
	 * @param maxAttempts the maximum number of attempts
	 * @param retryableExceptions the map of exceptions that are retryable based on the
	 * map value (true/false).
	 * @param traverseCauses is this clause traversable
	 */
	public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
			boolean traverseCauses) {
		this(maxAttempts, retryableExceptions, traverseCauses, false);
	}

	/**
	 * Create a {@link SimpleRetryPolicy} with the specified number of retry
	 * attempts. If traverseCauses is true, the exception causes will be traversed until
	 * a match is found. The default value indicates whether to retry or not for exceptions
	 * (or super classes) are not found in the map.
	 *
	 * @param maxAttempts the maximum number of attempts
	 * @param retryableExceptions the map of exceptions that are retryable based on the
	 * map value (true/false).
	 * @param traverseCauses is this clause traversable
	 * @param defaultValue the default action.
	 */
	public SimpleRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
			boolean traverseCauses, boolean defaultValue) {
		this(maxAttempts, new BinaryExceptionClassifier(retryableExceptions, defaultValue, traverseCauses));
	}

	/**
	 * Create a {@link SimpleRetryPolicy} with the specified number of retry
	 * attempts and provided exception classifier.
	 *
	 * @param maxAttempts the maximum number of attempts
	 * @param classifier custom exception classifier
	 */
	public SimpleRetryPolicy(int maxAttempts, BinaryExceptionClassifier classifier) {
		maxAttemptsRetryPolicy = new MaxAttemptsRetryPolicy(maxAttempts);
		exceptionClassifierRetryPolicy = new BinaryExceptionClassifierRetryPolicy(classifier);
		setPolicies(new RetryPolicy[] {
				maxAttemptsRetryPolicy,
				exceptionClassifierRetryPolicy
		});
		setOptimistic(false);
	}

	/**
	 * Set the number of attempts before retries are exhausted. Includes the initial
	 * attempt before the retries begin so, generally, will be {@code >= 1}. For example
	 * setting this property to 3 means 3 attempts total (initial + 2 retries).
	 *
	 * @param maxAttempts the maximum number of attempts including the initial attempt.
	 */
	public void setMaxAttempts(int maxAttempts) {
		this.maxAttemptsRetryPolicy.setMaxAttempts(maxAttempts);
	}

	/**
	 * The maximum number of attempts before failure.
	 *
	 * @return the maximum number of attempts
	 */
	public int getMaxAttempts() {
		return this.maxAttemptsRetryPolicy.getMaxAttempts();
	}

	@Override
	public String toString() {
		// implement fully if need
		return ClassUtils.getShortName(getClass()) + "[maxAttempts=" + maxAttemptsRetryPolicy.getMaxAttempts() + "]";
	}
}
