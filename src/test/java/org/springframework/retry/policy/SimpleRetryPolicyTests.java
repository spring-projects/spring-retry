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

package org.springframework.retry.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryContext;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleRetryPolicyTests {

	@Test
	public void testCanRetryIfNoException() {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertThat(policy.canRetry(context)).isTrue();
	}

	@Test
	public void testEmptyExceptionsNeverRetry() {

		// We can't retry any exceptions...
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3,
				Collections.<Class<? extends Throwable>, Boolean>emptyMap());
		RetryContext context = policy.open(null);

		// ...so we can't retry this one...
		policy.registerThrowable(context, new IllegalStateException());
		assertThat(policy.canRetry(context)).isFalse();
	}

	@Test
	public void testWithExceptionDefaultAlwaysRetry() {

		// We retry any exceptions except...
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3,
				Collections.<Class<? extends Throwable>, Boolean>singletonMap(IllegalStateException.class, false), true,
				true);
		RetryContext context = policy.open(null);

		// ...so we can't retry this one...
		policy.registerThrowable(context, new IllegalStateException());
		assertThat(policy.canRetry(context)).isFalse();

		// ...and we can retry this one...
		policy.registerThrowable(context, new IllegalArgumentException());
		assertThat(policy.canRetry(context)).isTrue();
	}

	@Test
	public void testRetryLimitInitialState() {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertThat(policy.canRetry(context)).isTrue();
		policy.setMaxAttempts(0);
		context = policy.open(null);
		assertThat(policy.canRetry(context)).isFalse();
	}

	@Test
	public void testRetryLimitSubsequentState() {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		policy.setMaxAttempts(2);
		assertThat(policy.canRetry(context)).isTrue();
		policy.registerThrowable(context, new Exception());
		assertThat(policy.canRetry(context)).isTrue();
		policy.registerThrowable(context, new Exception());
		assertThat(policy.canRetry(context)).isFalse();
	}

	@Test
	public void testRetryCount() {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertThat(context).isNotNull();
		policy.registerThrowable(context, null);
		assertThat(context.getRetryCount()).isEqualTo(0);
		policy.registerThrowable(context, new RuntimeException("foo"));
		assertThat(context.getRetryCount()).isEqualTo(1);
		assertThat(context.getLastThrowable().getMessage()).isEqualTo("foo");
	}

	@Test
	public void testFatalOverridesRetryable() {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
		map.put(Exception.class, false);
		map.put(RuntimeException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map);
		RetryContext context = policy.open(null);
		assertThat(context).isNotNull();
		policy.registerThrowable(context, new RuntimeException("foo"));
		assertThat(policy.canRetry(context)).isTrue();
	}

	@Test
	public void testRetryableWithCause() {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
		map.put(RuntimeException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map, true);
		RetryContext context = policy.open(null);
		assertThat(context).isNotNull();
		policy.registerThrowable(context, new Exception(new RuntimeException("foo")));
		assertThat(policy.canRetry(context)).isTrue();
	}

	@Test
	public void testParent() {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		RetryContext child = policy.open(context);
		assertThat(context).isNotSameAs(child);
		assertThat(child.getParent()).isSameAs(context);
	}

}
