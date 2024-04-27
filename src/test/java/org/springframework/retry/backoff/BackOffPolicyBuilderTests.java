/*
 * Copyright 2006-2023 the original author or authors.
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

package org.springframework.retry.backoff;

import org.junit.jupiter.api.Test;

import org.springframework.beans.DirectFieldAccessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Tomaz Fernandes
 * @author Gary Russell
 * @author Aftab Shaikh
 * @since 1.3.3
 */
public class BackOffPolicyBuilderTests {

	@Test
	public void shouldCreateDefaultBackOffPolicy() {
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newDefaultPolicy();
		assertThat(FixedBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		FixedBackOffPolicy policy = (FixedBackOffPolicy) backOffPolicy;
		assertThat(policy.getBackOffPeriod()).isEqualTo(1000);
	}

	@Test
	public void shouldCreateDefaultBackOffPolicyViaNewBuilder() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().sleeper(mockSleeper).build();
		assertThat(FixedBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		FixedBackOffPolicy policy = (FixedBackOffPolicy) backOffPolicy;
		assertThat(policy.getBackOffPeriod()).isEqualTo(1000);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateFixedBackOffPolicy() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().delay(3500).sleeper(mockSleeper).build();
		assertThat(FixedBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		FixedBackOffPolicy policy = (FixedBackOffPolicy) backOffPolicy;
		assertThat(policy.getBackOffPeriod()).isEqualTo(3500);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateUniformRandomBackOffPolicy() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder()
			.delay(1)
			.maxDelay(5000)
			.sleeper(mockSleeper)
			.build();
		assertThat(UniformRandomBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		UniformRandomBackOffPolicy policy = (UniformRandomBackOffPolicy) backOffPolicy;
		assertThat(policy.getMinBackOffPeriod()).isEqualTo(1);
		assertThat(policy.getMaxBackOffPeriod()).isEqualTo(5000);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateExponentialBackOff() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder()
			.delay(100)
			.maxDelay(1000)
			.multiplier(2)
			.random(false)
			.sleeper(mockSleeper)
			.build();
		assertThat(ExponentialBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		ExponentialBackOffPolicy policy = (ExponentialBackOffPolicy) backOffPolicy;
		assertThat(policy.getInitialInterval()).isEqualTo(100);
		assertThat(policy.getMaxInterval()).isEqualTo(1000);
		assertThat(policy.getMultiplier()).isEqualTo(2);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateExponentialRandomBackOff() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder()
			.delay(10000)
			.maxDelay(100000)
			.multiplier(10)
			.random(true)
			.sleeper(mockSleeper)
			.build();
		assertThat(ExponentialRandomBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		ExponentialRandomBackOffPolicy policy = (ExponentialRandomBackOffPolicy) backOffPolicy;
		assertThat(policy.getInitialInterval()).isEqualTo(10000);
		assertThat(policy.getMaxInterval()).isEqualTo(100000);
		assertThat(policy.getMultiplier()).isEqualTo(10);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateExponentialRandomBackOffWhenProvidedRandomSupplier() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder()
			.delay(10000)
			.maxDelay(100000)
			.multiplier(10)
			.randomSupplier(() -> true)
			.sleeper(mockSleeper)
			.build();
		assertThat(ExponentialRandomBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		ExponentialRandomBackOffPolicy policy = (ExponentialRandomBackOffPolicy) backOffPolicy;
		assertThat(policy.getInitialInterval()).isEqualTo(10000);
		assertThat(policy.getMaxInterval()).isEqualTo(100000);
		assertThat(policy.getMultiplier()).isEqualTo(10);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

	@Test
	public void shouldCreateExponentialBackOffWhenProvidedRandomSupplier() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder()
			.delay(100)
			.maxDelay(1000)
			.multiplier(2)
			.randomSupplier(() -> true)
			.sleeper(mockSleeper)
			.build();
		assertThat(ExponentialBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass())).isTrue();
		ExponentialBackOffPolicy policy = (ExponentialBackOffPolicy) backOffPolicy;
		assertThat(policy.getInitialInterval()).isEqualTo(100);
		assertThat(policy.getMaxInterval()).isEqualTo(1000);
		assertThat(policy.getMultiplier()).isEqualTo(2);
		assertThat(new DirectFieldAccessor(policy).getPropertyValue("sleeper")).isEqualTo(mockSleeper);
	}

}
