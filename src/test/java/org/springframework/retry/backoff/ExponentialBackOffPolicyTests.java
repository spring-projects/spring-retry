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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Rob Harrop
 * @author Dave Syer
 * @author Gary Russell
 * @author Marius Lichtblau
 */
public class ExponentialBackOffPolicyTests {

	private final DummySleeper sleeper = new DummySleeper();

	@Test
	public void testSetMaxInterval() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setMaxInterval(1000);
		assertThat(strategy.toString()).contains("maxInterval=1000");
		strategy.setMaxInterval(0);
		// The minimum value for the max interval is 1
		assertThat(strategy.toString()).contains("maxInterval=1");
	}

	@Test
	public void testSetInitialInterval() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setInitialInterval(10000);
		assertThat(strategy.toString()).contains("initialInterval=10000,");
		strategy.setInitialInterval(0);
		assertThat(strategy.toString()).contains("initialInterval=1,");
	}

	@Test
	public void testSetMultiplier() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setMultiplier(3.);
		assertThat(strategy.toString()).contains("multiplier=3.");
		strategy.setMultiplier(.5);
		assertThat(strategy.toString()).contains("multiplier=1.");
	}

	@Test
	public void testSingleBackOff() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setSleeper(sleeper);
		BackOffContext context = strategy.start(null);
		strategy.backOff(context);
		assertThat(sleeper.getLastBackOff()).isEqualTo(ExponentialBackOffPolicy.DEFAULT_INITIAL_INTERVAL);
	}

	@Test
	public void testMaximumBackOff() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setMaxInterval(50);
		strategy.setSleeper(sleeper);
		BackOffContext context = strategy.start(null);
		strategy.backOff(context);
		assertThat(sleeper.getLastBackOff()).isEqualTo(50);
	}

	@Test
	public void testMultiBackOff() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		long seed = 40;
		double multiplier = 1.2;
		strategy.setInitialInterval(seed);
		strategy.setMultiplier(multiplier);
		strategy.setSleeper(sleeper);
		BackOffContext context = strategy.start(null);
		for (int x = 0; x < 5; x++) {
			strategy.backOff(context);
			assertThat(sleeper.getLastBackOff()).isEqualTo(seed);
			seed *= multiplier;
		}
	}

	@Test
	public void testMultiBackOffWithInitialDelaySupplier() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		long seed = 40;
		double multiplier = 1.2;
		strategy.initialIntervalSupplier(() -> 40L);
		strategy.setMultiplier(multiplier);
		strategy.setSleeper(sleeper);
		BackOffContext context = strategy.start(null);
		for (int x = 0; x < 5; x++) {
			strategy.backOff(context);
			assertThat(sleeper.getLastBackOff()).isEqualTo(seed);
			seed *= multiplier;
		}
	}

	@Test
	public void testInterruptedStatusIsRestored() {
		ExponentialBackOffPolicy strategy = new ExponentialBackOffPolicy();
		strategy.setSleeper(new Sleeper() {
			@Override
			public void sleep(long backOffPeriod) throws InterruptedException {
				throw new InterruptedException("foo");
			}
		});
		BackOffContext context = strategy.start(null);
		assertThatExceptionOfType(BackOffInterruptedException.class).isThrownBy(() -> strategy.backOff(context));
		assertThat(Thread.interrupted()).isTrue();
	}

}
