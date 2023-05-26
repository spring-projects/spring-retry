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

package org.springframework.retry.support;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;

public class RetrySimulationTests {

	@Test
	public void testSimulatorExercisesFixedBackoff() {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(5);

		FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
		backOffPolicy.setBackOffPeriod(400);

		RetrySimulator simulator = new RetrySimulator(backOffPolicy, retryPolicy);
		RetrySimulation simulation = simulator.executeSimulation(1000);
		System.out.println(backOffPolicy);
		System.out.println("Longest sequence  " + simulation.getLongestTotalSleepSequence());
		System.out.println("Percentiles:       " + simulation.getPercentiles());

		assertThat(simulation.getLongestTotalSleepSequence().getSleeps())
			.isEqualTo(Arrays.asList(400l, 400l, 400l, 400l));
		assertThat(simulation.getPercentiles())
			.isEqualTo(Arrays.asList(400d, 400d, 400d, 400d, 400d, 400d, 400d, 400d, 400d));
		assertThat(simulation.getPercentile(0.5)).isEqualTo(400d);
	}

	@Test
	public void testSimulatorExercisesExponentialBackoff() {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(5);

		ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
		backOffPolicy.setMultiplier(2);
		backOffPolicy.setMaxInterval(30000);
		backOffPolicy.setInitialInterval(100);

		RetrySimulator simulator = new RetrySimulator(backOffPolicy, retryPolicy);
		RetrySimulation simulation = simulator.executeSimulation(1000);
		System.out.println(backOffPolicy);
		System.out.println("Longest sequence  " + simulation.getLongestTotalSleepSequence());
		System.out.println("Percentiles:       " + simulation.getPercentiles());

		assertThat(simulation.getLongestTotalSleepSequence().getSleeps())
			.isEqualTo(Arrays.asList(100l, 200l, 400l, 800l));
		assertThat(simulation.getPercentiles())
			.isEqualTo(Arrays.asList(100d, 100d, 200d, 200d, 300d, 400d, 400d, 800d, 800d));
		assertThat(simulation.getPercentile(0.5f)).isEqualTo(300d);
	}

	@Test
	public void testSimulatorExercisesRandomExponentialBackoff() {
		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
		retryPolicy.setMaxAttempts(5);

		ExponentialBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
		backOffPolicy.setMultiplier(2);
		backOffPolicy.setMaxInterval(30000);
		backOffPolicy.setInitialInterval(100);

		RetrySimulator simulator = new RetrySimulator(backOffPolicy, retryPolicy);
		RetrySimulation simulation = simulator.executeSimulation(10000);
		System.out.println(backOffPolicy);
		System.out.println("Longest sequence  " + simulation.getLongestTotalSleepSequence());
		System.out.println("Percentiles:       " + simulation.getPercentiles());

		assertThat(simulation.getPercentiles().size()).isGreaterThan(4);
	}

}
