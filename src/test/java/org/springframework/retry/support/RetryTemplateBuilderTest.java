/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.retry.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.BinaryExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.util.test.TestUtils;

import static org.mockito.Mockito.mock;
import static org.springframework.retry.util.test.TestUtils.getPropertyValue;

/**
 * The goal of the builder is to build proper instance. So, this test inspects instance
 * structure instead behaviour. Writing more integrative test is also encouraged.
 * Accessing of private fields is performed via {@link TestUtils#getPropertyValue} to
 * follow project's style.
 *
 * @author Aleksandr Shamukov
 */
public class RetryTemplateBuilderTest {

	/* ---------------- Mixed tests -------------- */

	@Test
	public void testDefaultBehavior() {
		RetryTemplate template = RetryTemplate.builder().build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);
		Assert.assertTrue(policyTuple.baseRetryPolicy instanceof MaxAttemptsRetryPolicy);
		assertDefaultClassifier(policyTuple);

		Assert.assertFalse(getPropertyValue(template, "throwLastExceptionOnExhausted", Boolean.class));
		Assert.assertTrue(getPropertyValue(template, "retryContextCache") instanceof MapRetryContextCache);
		Assert.assertEquals(0, getPropertyValue(template, "listeners", RetryListener[].class).length);

		Assert.assertTrue(getPropertyValue(template, "backOffPolicy") instanceof NoBackOffPolicy);
	}

	@Test
	public void testBasicCustomization() {
		RetryListener listener1 = mock(RetryListener.class);
		RetryListener listener2 = mock(RetryListener.class);

		RetryTemplate template = RetryTemplate.builder().maxAttempts(10).exponentialBackoff(99, 1.5, 1717)
				.retryOn(IOException.class).traversingCauses().withListener(listener1)
				.withListeners(Collections.singletonList(listener2)).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);

		BinaryExceptionClassifier classifier = policyTuple.exceptionClassifierRetryPolicy.getExceptionClassifier();
		Assert.assertTrue(classifier.classify(new FileNotFoundException()));
		Assert.assertFalse(classifier.classify(new RuntimeException()));
		Assert.assertFalse(classifier.classify(new OutOfMemoryError()));

		Assert.assertTrue(policyTuple.baseRetryPolicy instanceof MaxAttemptsRetryPolicy);
		Assert.assertEquals(10, ((MaxAttemptsRetryPolicy) policyTuple.baseRetryPolicy).getMaxAttempts());

		List<RetryListener> listeners = Arrays.asList(getPropertyValue(template, "listeners", RetryListener[].class));
		Assert.assertEquals(2, listeners.size());
		Assert.assertTrue(listeners.contains(listener1));
		Assert.assertTrue(listeners.contains(listener2));

		Assert.assertTrue(getPropertyValue(template, "backOffPolicy") instanceof ExponentialBackOffPolicy);
	}

	/* ---------------- Retry policy -------------- */

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnRetryPoliciesConflict() {
		RetryTemplate.builder().maxAttempts(3).withinMillis(1000).build();
	}

	@Test
	public void testTimeoutPolicy() {
		RetryTemplate template = RetryTemplate.builder().withinMillis(10000).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);

		Assert.assertTrue(policyTuple.baseRetryPolicy instanceof TimeoutRetryPolicy);
		Assert.assertEquals(10000, ((TimeoutRetryPolicy) policyTuple.baseRetryPolicy).getTimeout());
	}

	@Test
	public void testInfiniteRetry() {
		RetryTemplate template = RetryTemplate.builder().infiniteRetry().build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);

		Assert.assertTrue(policyTuple.baseRetryPolicy instanceof AlwaysRetryPolicy);
	}

	@Test
	public void testCustomPolicy() {
		RetryPolicy customPolicy = mock(RetryPolicy.class);

		RetryTemplate template = RetryTemplate.builder().customPolicy(customPolicy).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);

		assertDefaultClassifier(policyTuple);
		Assert.assertEquals(customPolicy, policyTuple.baseRetryPolicy);
	}

	private void assertDefaultClassifier(PolicyTuple policyTuple) {
		BinaryExceptionClassifier classifier = policyTuple.exceptionClassifierRetryPolicy.getExceptionClassifier();
		Assert.assertTrue(classifier.classify(new Exception()));
		Assert.assertTrue(classifier.classify(new Exception(new Error())));
		Assert.assertFalse(classifier.classify(new Error()));
		Assert.assertFalse(classifier.classify(new Error(new Exception())));
	}

	/* ---------------- Exception classification -------------- */

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnEmptyExceptionClassifierRules() {
		RetryTemplate.builder().traversingCauses().build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnNotationMix() {
		RetryTemplate.builder().retryOn(IOException.class).notRetryOn(OutOfMemoryError.class);
	}

	/* ---------------- BackOff -------------- */

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnBackOffPolicyNull() {
		RetryTemplate.builder().customBackoff(null).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnBackOffPolicyConflict() {
		RetryTemplate.builder().noBackoff().fixedBackoff(1000).build();
	}

	@Test
	public void testUniformRandomBackOff() {
		RetryTemplate template = RetryTemplate.builder().uniformRandomBackoff(10, 100).build();
		Assert.assertTrue(getPropertyValue(template, "backOffPolicy") instanceof UniformRandomBackOffPolicy);
	}

	@Test
	public void testNoBackOff() {
		RetryTemplate template = RetryTemplate.builder().noBackoff().build();
		Assert.assertTrue(getPropertyValue(template, "backOffPolicy") instanceof NoBackOffPolicy);
	}

	@Test
	public void testExpBackOffWithRandom() {
		RetryTemplate template = RetryTemplate.builder().exponentialBackoff(10, 2, 500, true).build();
		Assert.assertTrue(getPropertyValue(template, "backOffPolicy") instanceof ExponentialRandomBackOffPolicy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateInitAndMax() {
		RetryTemplate.builder().exponentialBackoff(100, 2, 100).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateMeaninglessMultipier() {
		RetryTemplate.builder().exponentialBackoff(100, 1, 200).build();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testValidateZeroInitInterval() {
		RetryTemplate.builder().exponentialBackoff(0, 2, 200).build();
	}

	/* ---------------- Utils -------------- */

	private static class PolicyTuple {

		RetryPolicy baseRetryPolicy;

		BinaryExceptionClassifierRetryPolicy exceptionClassifierRetryPolicy;

		static PolicyTuple extractWithAsserts(RetryTemplate template) {
			CompositeRetryPolicy compositeRetryPolicy = getPropertyValue(template, "retryPolicy",
					CompositeRetryPolicy.class);
			PolicyTuple res = new PolicyTuple();

			Assert.assertFalse(getPropertyValue(compositeRetryPolicy, "optimistic", Boolean.class));

			for (final RetryPolicy policy : getPropertyValue(compositeRetryPolicy, "policies", RetryPolicy[].class)) {
				if (policy instanceof BinaryExceptionClassifierRetryPolicy) {
					res.exceptionClassifierRetryPolicy = (BinaryExceptionClassifierRetryPolicy) policy;
				}
				else {
					res.baseRetryPolicy = policy;
				}
			}
			Assert.assertNotNull(res.exceptionClassifierRetryPolicy);
			Assert.assertNotNull(res.baseRetryPolicy);
			return res;
		}

	}

}