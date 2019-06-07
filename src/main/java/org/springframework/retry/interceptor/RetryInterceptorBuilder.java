/*
 * Copyright 2014 the original author or authors.
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
package org.springframework.retry.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.classify.Classifier;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * <p>
 * Simplified facade to make it easier and simpler to build a
 * {@link StatefulRetryOperationsInterceptor} or (stateless)
 * {@link RetryOperationsInterceptor} by providing a fluent interface to defining the
 * behavior on error.
 * <p>
 * Typical example:
 * </p>
 *
 * <pre class="code">
 * StatefulRetryOperationsInterceptor interceptor = RetryInterceptorBuilder.stateful()
 * 		.maxAttempts(5).backOffOptions(1, 2, 10) // initialInterval, multiplier,
 * 													// maxInterval
 * 		.build();
 * </pre>
 *
 * @author James Carr
 * @author Gary Russell
 * @author Artem Bilan
 * @since 1.1
 * @param <T> The type of {@link org.aopalliance.intercept.MethodInterceptor} returned by
 * the builder's {@link #build()} method.
 */
public abstract class RetryInterceptorBuilder<T extends MethodInterceptor> {

	protected final RetryTemplate retryTemplate = new RetryTemplate();

	protected final SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();

	protected RetryOperations retryOperations;

	protected MethodInvocationRecoverer<?> recoverer;

	private boolean templateAltered;

	private boolean backOffPolicySet;

	private boolean retryPolicySet;

	private boolean backOffOptionsSet;

	protected String label;

	/**
	 * Create a builder for a stateful retry interceptor.
	 * @return The interceptor builder.
	 */
	public static StatefulRetryInterceptorBuilder stateful() {
		return new StatefulRetryInterceptorBuilder();
	}

	/**
	 * Create a builder for a circuit breaker retry interceptor.
	 * @return The interceptor builder.
	 */
	public static CircuitBreakerInterceptorBuilder circuitBreaker() {
		return new CircuitBreakerInterceptorBuilder();
	}

	/**
	 * Create a builder for a stateless retry interceptor.
	 * @return The interceptor builder.
	 */
	public static StatelessRetryInterceptorBuilder stateless() {
		return new StatelessRetryInterceptorBuilder();
	}

	/**
	 * Apply the retry operations - once this is set, other properties can no longer be
	 * set; can't be set if other properties have been applied.
	 * @param retryOperations The retry operations.
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> retryOperations(RetryOperations retryOperations) {
		Assert.isTrue(!this.templateAltered, "Cannot set retryOperations when the default has been modified");
		this.retryOperations = retryOperations;
		return this;
	}

	/**
	 * Apply the max attempts - a SimpleRetryPolicy will be used. Cannot be used if a
	 * custom retry operations or retry policy has been set.
	 * @param maxAttempts the max attempts (including the initial attempt).
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> maxAttempts(int maxAttempts) {
		Assert.isNull(this.retryOperations, "cannot alter the retry policy when a custom retryOperations has been set");
		Assert.isTrue(!this.retryPolicySet, "cannot alter the retry policy when a custom retryPolicy has been set");
		this.simpleRetryPolicy.setMaxAttempts(maxAttempts);
		this.retryTemplate.setRetryPolicy(this.simpleRetryPolicy);
		this.templateAltered = true;
		return this;
	}

	/**
	 * Apply the backoff options. Cannot be used if a custom retry operations, or back off
	 * policy has been set.
	 * @param initialInterval The initial interval.
	 * @param multiplier The multiplier.
	 * @param maxInterval The max interval.
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> backOffOptions(long initialInterval, double multiplier, long maxInterval) {
		Assert.isNull(this.retryOperations,
				"cannot set the back off policy when a custom retryOperations has been set");
		Assert.isTrue(!this.backOffPolicySet, "cannot set the back off options when a back off policy has been set");
		ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
		policy.setInitialInterval(initialInterval);
		policy.setMultiplier(multiplier);
		policy.setMaxInterval(maxInterval);
		this.retryTemplate.setBackOffPolicy(policy);
		this.backOffOptionsSet = true;
		this.templateAltered = true;
		return this;
	}

	/**
	 * Apply the retry policy - cannot be used if a custom retry template has been
	 * provided, or the max attempts or back off options or policy have been applied.
	 * @param policy The policy.
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> retryPolicy(RetryPolicy policy) {
		Assert.isNull(this.retryOperations, "cannot set the retry policy when a custom retryOperations has been set");
		Assert.isTrue(!this.templateAltered,
				"cannot set the retry policy if max attempts or back off policy or options changed");
		this.retryTemplate.setRetryPolicy(policy);
		this.retryPolicySet = true;
		this.templateAltered = true;
		return this;
	}

	/**
	 * Apply the back off policy. Cannot be used if a custom retry operations, or back off
	 * policy has been applied.
	 * @param policy The policy.
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> backOffPolicy(BackOffPolicy policy) {
		Assert.isNull(this.retryOperations,
				"cannot set the back off policy when a custom retryOperations has been set");
		Assert.isTrue(!this.backOffOptionsSet,
				"cannot set the back off policy when the back off policy options have been set");
		this.retryTemplate.setBackOffPolicy(policy);
		this.templateAltered = true;
		this.backOffPolicySet = true;
		return this;
	}

	/**
	 * Apply a {@link MethodInvocationRecoverer} for the Retry interceptor.
	 * @param recoverer The recoverer.
	 * @return this.
	 */
	public RetryInterceptorBuilder<T> recoverer(MethodInvocationRecoverer<?> recoverer) {
		this.recoverer = recoverer;
		return this;
	}

	public RetryInterceptorBuilder<T> label(String label) {
		this.label = label;
		return this;
	}

	public abstract T build();

	private RetryInterceptorBuilder() {
	}

	public static class StatefulRetryInterceptorBuilder
			extends RetryInterceptorBuilder<StatefulRetryOperationsInterceptor> {

		private final StatefulRetryOperationsInterceptor interceptor = new StatefulRetryOperationsInterceptor();

		private MethodArgumentsKeyGenerator keyGenerator;

		private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

		private Classifier<? super Throwable, Boolean> rollbackClassifier;

		/**
		 * Stateful retry requires items to be identifiable.
		 * @param keyGenerator The key generator.
		 * @return this.
		 */
		public StatefulRetryInterceptorBuilder keyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
			this.keyGenerator = keyGenerator;
			return this;
		}

		/**
		 * Apply a custom new item identifier.
		 * @param newMethodArgumentsIdentifier The new item identifier.
		 * @return this.
		 */
		public StatefulRetryInterceptorBuilder newMethodArgumentsIdentifier(
				NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
			this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
			return this;
		}

		/**
		 * Control the rollback of an ongoing transaction. In a stateful retry, normally
		 * all exceptions cause a rollback (i.e. re-throw).
		 * @param rollbackClassifier The rollback classifier (return true for exceptions
		 * that should be re-thrown).
		 * @return this.
		 */
		public StatefulRetryInterceptorBuilder rollbackFor(Classifier<? super Throwable, Boolean> rollbackClassifier) {
			this.rollbackClassifier = rollbackClassifier;
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder retryOperations(RetryOperations retryOperations) {
			super.retryOperations(retryOperations);
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder maxAttempts(int maxAttempts) {
			super.maxAttempts(maxAttempts);
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder backOffOptions(long initialInterval, double multiplier,
				long maxInterval) {
			super.backOffOptions(initialInterval, multiplier, maxInterval);
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder retryPolicy(RetryPolicy policy) {
			super.retryPolicy(policy);
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder backOffPolicy(BackOffPolicy policy) {
			super.backOffPolicy(policy);
			return this;
		}

		@Override
		public StatefulRetryInterceptorBuilder recoverer(MethodInvocationRecoverer<?> recoverer) {
			super.recoverer(recoverer);
			return this;
		}

		@Override
		public StatefulRetryOperationsInterceptor build() {
			if (this.recoverer != null) {
				this.interceptor.setRecoverer(this.recoverer);
			}
			if (this.retryOperations != null) {
				this.interceptor.setRetryOperations(this.retryOperations);
			}
			else {
				this.interceptor.setRetryOperations(this.retryTemplate);
			}
			if (this.keyGenerator != null) {
				this.interceptor.setKeyGenerator(this.keyGenerator);
			}
			if (this.rollbackClassifier != null) {
				this.interceptor.setRollbackClassifier(this.rollbackClassifier);
			}
			if (this.newMethodArgumentsIdentifier != null) {
				this.interceptor.setNewItemIdentifier(this.newMethodArgumentsIdentifier);
			}
			if (this.label != null) {
				this.interceptor.setLabel(this.label);
			}
			return this.interceptor;
		}

		private StatefulRetryInterceptorBuilder() {
		}

	}

	public static class CircuitBreakerInterceptorBuilder
			extends RetryInterceptorBuilder<StatefulRetryOperationsInterceptor> {

		private final StatefulRetryOperationsInterceptor interceptor = new StatefulRetryOperationsInterceptor();

		private MethodArgumentsKeyGenerator keyGenerator;

		@Override
		public CircuitBreakerInterceptorBuilder retryOperations(RetryOperations retryOperations) {
			super.retryOperations(retryOperations);
			return this;
		}

		@Override
		public CircuitBreakerInterceptorBuilder maxAttempts(int maxAttempts) {
			super.maxAttempts(maxAttempts);
			return this;
		}

		@Override
		public CircuitBreakerInterceptorBuilder retryPolicy(RetryPolicy policy) {
			super.retryPolicy(policy);
			return this;
		}

		public CircuitBreakerInterceptorBuilder keyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
			this.keyGenerator = keyGenerator;
			return this;
		}

		@Override
		public CircuitBreakerInterceptorBuilder recoverer(MethodInvocationRecoverer<?> recoverer) {
			super.recoverer(recoverer);
			return this;
		}

		@Override
		public StatefulRetryOperationsInterceptor build() {
			if (this.recoverer != null) {
				this.interceptor.setRecoverer(this.recoverer);
			}
			if (this.retryOperations != null) {
				this.interceptor.setRetryOperations(this.retryOperations);
			}
			else {
				this.interceptor.setRetryOperations(this.retryTemplate);
			}
			if (this.keyGenerator != null) {
				this.interceptor.setKeyGenerator(this.keyGenerator);
			}
			if (this.label != null) {
				this.interceptor.setLabel(this.label);
			}
			this.interceptor.setRollbackClassifier(new BinaryExceptionClassifier(false));
			return this.interceptor;
		}

		private CircuitBreakerInterceptorBuilder() {
		}

	}

	public static class StatelessRetryInterceptorBuilder extends RetryInterceptorBuilder<RetryOperationsInterceptor> {

		private final RetryOperationsInterceptor interceptor = new RetryOperationsInterceptor();

		@Override
		public RetryOperationsInterceptor build() {
			if (this.recoverer != null) {
				this.interceptor.setRecoverer(this.recoverer);
			}
			if (this.retryOperations != null) {
				this.interceptor.setRetryOperations(this.retryOperations);
			}
			else {
				this.interceptor.setRetryOperations(this.retryTemplate);
			}
			if (this.label != null) {
				this.interceptor.setLabel(this.label);
			}
			return this.interceptor;
		}

		private StatelessRetryInterceptorBuilder() {
		}

	}

}
