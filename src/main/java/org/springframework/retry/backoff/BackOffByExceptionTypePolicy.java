package org.springframework.retry.backoff;

import org.springframework.classify.Classifier;
import org.springframework.classify.ClassifierSupport;
import org.springframework.classify.SubclassClassifier;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link BackOffPolicy} that dynamically adapts to one of a set of injected policies
 * according to the value of the latest exception. Modelled after
 * {@link ExceptionClassifierRetryPolicy}
 *
 */

public class BackOffByExceptionTypePolicy implements BackOffPolicy {

	private Classifier<Throwable, BackOffPolicy> throwableToBackOffPolicyClassifier = new ClassifierSupport<>(
			new NoBackOffPolicy()); // defaults to NoBackOffPolicy

	/**
	 * Setter for policy map used to create a classifier.
	 * @param policyMap a map of Throwable class to {@link BackOffPolicy} that will be
	 * used to create a {@link Classifier} to locate a policy.
	 */
	public void setPolicyMap(Map<Class<? extends Throwable>, BackOffPolicy> policyMap) {
		throwableToBackOffPolicyClassifier = new SubclassClassifier<>(policyMap, new NoBackOffPolicy());
	}

	@Override
	public BackOffContext start(RetryContext retryContext) {
		// our backoff needs access to the last exception thrown so our backoff
		// retryContext
		// includes the retryContext (which has access to the last thrown exception)
		return new BackOffByExceptionTypeContext(throwableToBackOffPolicyClassifier, retryContext);
	}

	@Override
	public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
		BackOffPolicy backOffPolicy = (BackOffPolicy) backOffContext;
		backOffPolicy.backOff(backOffContext); // delegate to the context
	}

	static class BackOffByExceptionTypeContext implements BackOffContext, BackOffPolicy {

		final protected Classifier<Throwable, BackOffPolicy> exceptionClassifier;

		private RetryContext retryContext; // will have access to the last throwable

		// we need a way to map from the exception to a backoff policy to a prior backoff
		// context
		private Map<BackOffPolicy, BackOffContext> backOffPolicyToBackOffContentMap = new HashMap<>();

		BackOffByExceptionTypeContext(Classifier<Throwable, BackOffPolicy> exceptionClassifier,
				RetryContext retryContext) {
			this.exceptionClassifier = exceptionClassifier;
			this.retryContext = retryContext;
		}

		@Override
		public BackOffContext start(RetryContext context) {
			// will never be called because ExceptionClassifierBackOffPolicy creates the
			// context itself
			return null;
		}

		@Override
		public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
			BackOffPolicy backOffPolicy = exceptionClassifier.classify(retryContext.getLastThrowable());
			BackOffContext mappedBackoffPolicy = backOffPolicyToBackOffContentMap.get(backOffPolicy);
			if (mappedBackoffPolicy == null) {
				// we needed to postpone starting the backoff policy to here as the start
				// api doesn't have access yet to the last throwable
				mappedBackoffPolicy = backOffPolicy.start(retryContext);
				backOffPolicyToBackOffContentMap.put(backOffPolicy, mappedBackoffPolicy);
			}
			backOffPolicy.backOff(mappedBackoffPolicy);
		}

	}

}
