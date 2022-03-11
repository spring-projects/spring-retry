/*
 * Copyright 2022-2022 the original author or authors.
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

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * @author Tomaz Fernandes
 * @since 1.3.3
 */
public class BackOffPolicyBuilderTests {

	@Test
	public void shouldCreateDefaultBackOffPolicy() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().withSleeper(mockSleeper).build();
		assertTrue(FixedBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass()));
		FixedBackOffPolicy policy = (FixedBackOffPolicy) backOffPolicy;
		assertEquals(1000, policy.getBackOffPeriod());
		assertEquals(mockSleeper, new DirectFieldAccessor(policy).getPropertyValue("sleeper"));
	}

	@Test
	public void shouldCreateFixedBackOffPolicy() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().delay(3500).withSleeper(mockSleeper).build();
		assertTrue(FixedBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass()));
		FixedBackOffPolicy policy = (FixedBackOffPolicy) backOffPolicy;
		assertEquals(3500, policy.getBackOffPeriod());
		assertEquals(mockSleeper, new DirectFieldAccessor(policy).getPropertyValue("sleeper"));
	}

	@Test
	public void shouldCreateUniformRandomBackOffPolicy() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().delay(1).maxDelay(5000).withSleeper(mockSleeper)
				.build();
		assertTrue(UniformRandomBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass()));
		UniformRandomBackOffPolicy policy = (UniformRandomBackOffPolicy) backOffPolicy;
		assertEquals(1, policy.getMinBackOffPeriod());
		assertEquals(5000, policy.getMaxBackOffPeriod());
		assertEquals(mockSleeper, new DirectFieldAccessor(policy).getPropertyValue("sleeper"));
	}

	@Test
	public void shouldCreateExponentialBackOff() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().delay(100).maxDelay(1000).multiplier(2)
				.isRandom(false).withSleeper(mockSleeper).build();
		assertTrue(ExponentialBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass()));
		ExponentialBackOffPolicy policy = (ExponentialBackOffPolicy) backOffPolicy;
		assertEquals(100, policy.getInitialInterval());
		assertEquals(1000, policy.getMaxInterval());
		assertEquals(2, policy.getMultiplier(), 0);
		assertEquals(mockSleeper, new DirectFieldAccessor(policy).getPropertyValue("sleeper"));
	}

	@Test
	public void shouldCreateExponentialRandomBackOff() {
		Sleeper mockSleeper = mock(Sleeper.class);
		BackOffPolicy backOffPolicy = BackOffPolicyBuilder.newBuilder().delay(10000).maxDelay(100000).multiplier(10)
				.isRandom(true).withSleeper(mockSleeper).build();
		assertTrue(ExponentialRandomBackOffPolicy.class.isAssignableFrom(backOffPolicy.getClass()));
		ExponentialRandomBackOffPolicy policy = (ExponentialRandomBackOffPolicy) backOffPolicy;
		assertEquals(10000, policy.getInitialInterval());
		assertEquals(100000, policy.getMaxInterval());
		assertEquals(10, policy.getMultiplier(), 0);
		assertEquals(mockSleeper, new DirectFieldAccessor(policy).getPropertyValue("sleeper"));
	}

}