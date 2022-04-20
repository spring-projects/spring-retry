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

package org.springframework.retry.support;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.springframework.retry.util.test.TestUtils.getPropertyValue;

/**
 * The goal of the builder is to build proper instance. So, this test inspects instance
 * structure instead behaviour. Writing more integrative test is also encouraged.
 * Accessing of private fields is performed via {@link TestUtils#getPropertyValue} to
 * follow project's style.
 *
 * @author Aleksandr Shamukov
 * @author Kim In Hoi
 * @author Gary Russell
 */
public class RetryTemplateBuilderTests {

	/* ---------------- Mixed tests -------------- */

	@Test
	public void testDefaultBehavior() {
		RetryTemplate template = RetryTemplate.builder().build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);
		assertThat(policyTuple.baseRetryPolicy).isInstanceOf(MaxAttemptsRetryPolicy.class);
		assertDefaultClassifier(policyTuple);

		assertThat(getPropertyValue(template, "throwLastExceptionOnExhausted", Boolean.class)).isFalse();
		assertThat(getPropertyValue(template, "retryContextCache")).isInstanceOf(MapRetryContextCache.class);
		assertThat(getPropertyValue(template, "listeners", RetryListener[].class).length).isEqualTo(0);

		assertThat(getPropertyValue(template, "backOffPolicy")).isInstanceOf(NoBackOffPolicy.class);
	}

	@Test
	public void testBasicCustomization() {
		RetryListener listener1 = mock(RetryListener.class);
		RetryListener listener2 = mock(RetryListener.class);

		RetryTemplate template = RetryTemplate.builder().maxAttempts(10).exponentialBackoff(99, 1.5, 1717)
				.retryOn(IOException.class)
				.retryOn(Collections.<Class<? extends Throwable>>singletonList(IllegalArgumentException.class))
				.traversingCauses().withListener(listener1).withListeners(Collections.singletonList(listener2)).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);

		BinaryExceptionClassifier classifier = policyTuple.exceptionClassifierRetryPolicy.getExceptionClassifier();
		assertThat(classifier.classify(new FileNotFoundException())).isTrue();
		assertThat(classifier.classify(new IllegalArgumentException())).isTrue();
		assertThat(classifier.classify(new RuntimeException())).isFalse();
		assertThat(classifier.classify(new OutOfMemoryError())).isFalse();

		assertThat(policyTuple.baseRetryPolicy instanceof MaxAttemptsRetryPolicy).isTrue();
		assertThat(((MaxAttemptsRetryPolicy) policyTuple.baseRetryPolicy).getMaxAttempts()).isEqualTo(10);

		List<RetryListener> listeners = Arrays.asList(getPropertyValue(template, "listeners", RetryListener[].class));
		assertThat(listeners).hasSize(2);
		assertThat(listeners.contains(listener1)).isTrue();
		assertThat(listeners.contains(listener2)).isTrue();

		assertThat(getPropertyValue(template, "backOffPolicy")).isInstanceOf(ExponentialBackOffPolicy.class);
	}

	/* ---------------- Retry policy -------------- */

	@Test
	public void testFailOnRetryPoliciesConflict() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RetryTemplate.builder().maxAttempts(3).withinMillis(1000).build());
	}

	@Test
	public void testTimeoutPolicy() {
		RetryTemplate template = RetryTemplate.builder().withinMillis(10000).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);

		assertThat(policyTuple.baseRetryPolicy).isInstanceOf(TimeoutRetryPolicy.class);
		assertThat(((TimeoutRetryPolicy) policyTuple.baseRetryPolicy).getTimeout()).isEqualTo(10000);
	}

	@Test
	public void testInfiniteRetry() {
		RetryTemplate template = RetryTemplate.builder().infiniteRetry().build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);
		assertDefaultClassifier(policyTuple);

		assertThat(policyTuple.baseRetryPolicy).isInstanceOf(AlwaysRetryPolicy.class);
	}

	@Test
	public void testCustomPolicy() {
		RetryPolicy customPolicy = mock(RetryPolicy.class);

		RetryTemplate template = RetryTemplate.builder().customPolicy(customPolicy).build();

		PolicyTuple policyTuple = PolicyTuple.extractWithAsserts(template);

		assertDefaultClassifier(policyTuple);
		assertThat(policyTuple.baseRetryPolicy).isEqualTo(customPolicy);
	}

	private void assertDefaultClassifier(PolicyTuple policyTuple) {
		BinaryExceptionClassifier classifier = policyTuple.exceptionClassifierRetryPolicy.getExceptionClassifier();
		assertThat(classifier.classify(new Exception())).isTrue();
		assertThat(classifier.classify(new Exception(new Error()))).isTrue();
		assertThat(classifier.classify(new Error())).isFalse();
		assertThat(classifier.classify(new Error(new Exception()))).isFalse();
	}

	/* ---------------- Exception classification -------------- */

	@Test
	public void testFailOnEmptyExceptionClassifierRules() {
		assertThatIllegalArgumentException().isThrownBy(() -> RetryTemplate.builder().traversingCauses().build());
	}

	@Test
	public void testFailOnNotationMix() {
		assertThatIllegalArgumentException().isThrownBy(
				() -> RetryTemplate.builder().retryOn(IOException.class).notRetryOn(OutOfMemoryError.class));
	}

	@Test
	public void testFailOnNotationsMix() {
		assertThatIllegalArgumentException().isThrownBy(() -> RetryTemplate.builder()
				.retryOn(Collections.<Class<? extends Throwable>>singletonList(IOException.class))
				.notRetryOn(Collections.<Class<? extends Throwable>>singletonList(OutOfMemoryError.class)));
	}

	/* ---------------- BackOff -------------- */

	@Test
	public void testFailOnBackOffPolicyNull() {
		assertThatIllegalArgumentException().isThrownBy(() -> RetryTemplate.builder().customBackoff(null).build());
	}

	@Test
	public void testFailOnBackOffPolicyConflict() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RetryTemplate.builder().noBackoff().fixedBackoff(1000).build());
	}

	@Test
	public void testUniformRandomBackOff() {
		RetryTemplate template = RetryTemplate.builder().uniformRandomBackoff(10, 100).build();
		assertThat(getPropertyValue(template, "backOffPolicy")).isInstanceOf(UniformRandomBackOffPolicy.class);
	}

	@Test
	public void testNoBackOff() {
		RetryTemplate template = RetryTemplate.builder().noBackoff().build();
		assertThat(getPropertyValue(template, "backOffPolicy")).isInstanceOf(NoBackOffPolicy.class);
	}

	@Test
	public void testExpBackOffWithRandom() {
		RetryTemplate template = RetryTemplate.builder().exponentialBackoff(10, 2, 500, true).build();
		assertThat(getPropertyValue(template, "backOffPolicy")).isInstanceOf(ExponentialRandomBackOffPolicy.class);
	}

	@Test
	public void testValidateInitAndMax() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RetryTemplate.builder().exponentialBackoff(100, 2, 100).build());
	}

	@Test
	public void testValidateMeaninglessMultipier() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RetryTemplate.builder().exponentialBackoff(100, 1, 200).build());
	}

	@Test
	public void testValidateZeroInitInterval() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RetryTemplate.builder().exponentialBackoff(0, 2, 200).build());
	}

	/* ---------------- Utils -------------- */

	private static class PolicyTuple {

		RetryPolicy baseRetryPolicy;

		BinaryExceptionClassifierRetryPolicy exceptionClassifierRetryPolicy;

		static PolicyTuple extractWithAsserts(RetryTemplate template) {
			CompositeRetryPolicy compositeRetryPolicy = getPropertyValue(template, "retryPolicy",
					CompositeRetryPolicy.class);
			PolicyTuple res = new PolicyTuple();

			assertThat(getPropertyValue(compositeRetryPolicy, "optimistic", Boolean.class)).isFalse();

			for (final RetryPolicy policy : getPropertyValue(compositeRetryPolicy, "policies", RetryPolicy[].class)) {
				if (policy instanceof BinaryExceptionClassifierRetryPolicy) {
					res.exceptionClassifierRetryPolicy = (BinaryExceptionClassifierRetryPolicy) policy;
				}
				else {
					res.baseRetryPolicy = policy;
				}
			}
			assertThat(res.exceptionClassifierRetryPolicy).isNotNull();
			assertThat(res.baseRetryPolicy).isNotNull();
			return res;
		}

	}

}
