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

import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryContext;

import static org.assertj.core.api.Assertions.assertThat;

public class NeverRetryPolicyTests {

	@Test
	public void testSimpleOperations() {
		NeverRetryPolicy policy = new NeverRetryPolicy();
		RetryContext context = policy.open(null);
		assertThat(context).isNotNull();
		// We can retry until the first exception is registered...
		assertThat(policy.canRetry(context)).isTrue();
		assertThat(policy.canRetry(context)).isTrue();
		policy.registerThrowable(context, null);
		assertThat(policy.canRetry(context)).isFalse();
		policy.close(context);
		assertThat(policy.canRetry(context)).isFalse();
	}

	@Test
	public void testRetryCount() {
		NeverRetryPolicy policy = new NeverRetryPolicy();
		RetryContext context = policy.open(null);
		assertThat(context).isNotNull();
		policy.registerThrowable(context, null);
		assertThat(context.getRetryCount()).isEqualTo(0);
		policy.registerThrowable(context, new RuntimeException("foo"));
		assertThat(context.getRetryCount()).isEqualTo(1);
		assertThat(context.getLastThrowable().getMessage()).isEqualTo("foo");
	}

	@Test
	public void testParent() {
		NeverRetryPolicy policy = new NeverRetryPolicy();
		RetryContext context = policy.open(null);
		RetryContext child = policy.open(context);
		assertThat(context).isNotSameAs(child);
		assertThat(child.getParent()).isSameAs(context);
	}

}
