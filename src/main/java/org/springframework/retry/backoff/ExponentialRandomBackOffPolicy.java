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

package org.springframework.retry.backoff;

import java.util.Random;
import java.util.function.Supplier;

import org.springframework.retry.RetryContext;

/**
 * Implementation of {@link org.springframework.retry.backoff.ExponentialBackOffPolicy}
 * that chooses a random multiple of the interval that would come from a simple
 * deterministic exponential. The random multiple is uniformly distributed between 1 and
 * the deterministic multiplier (so in practice the interval is somewhere between the next
 * and next but one intervals in the deterministic case).
 * This is often referred to as Jitter.
 *
 * This has shown to at least be useful in testing scenarios where excessive contention is
 * generated by the test needing many retries. In test, usually threads are started at the
 * same time, and thus stomp together onto the next interval. Using this
 * {@link BackOffPolicy} can help avoid that scenario.
 *
 * Example: initialInterval = 50 multiplier = 2.0 maxInterval = 3000 numRetries = 5
 *
 * {@link ExponentialBackOffPolicy} yields: [50, 100, 200, 400, 800]
 *
 * {@link ExponentialRandomBackOffPolicy} may yield [76, 151, 304, 580, 901] or [53, 190,
 * 267, 451, 815] (random distributed values within the ranges of [50-100, 100-200,
 * 200-400, 400-800, 800-1600])
 *
 * @author Jon Travis
 * @author Dave Syer
 * @author Chase Diem
 */
@SuppressWarnings("serial")
public class ExponentialRandomBackOffPolicy extends ExponentialBackOffPolicy {

	/**
	 * Returns a new instance of {@link org.springframework.retry.backoff.BackOffContext},
	 * seeded with this policies settings.
	 */
	public BackOffContext start(RetryContext context) {
		return new ExponentialRandomBackOffContext(getInitialInterval(), getMultiplier(), getMaxInterval(),
				getInitialIntervalSupplier(), getMultiplierSupplier(), getMaxIntervalSupplier());
	}

	protected ExponentialBackOffPolicy newInstance() {
		return new ExponentialRandomBackOffPolicy();
	}

	static class ExponentialRandomBackOffContext extends ExponentialBackOffPolicy.ExponentialBackOffContext {

		private final Random r = new Random();

		public ExponentialRandomBackOffContext(long expSeed, double multiplier, long maxInterval,
				Supplier<Long> expSeedSupplier, Supplier<Double> multiplierSupplier,
				Supplier<Long> maxIntervalSupplier) {

			super(expSeed, multiplier, maxInterval, expSeedSupplier, multiplierSupplier, maxIntervalSupplier);
		}

		@Override
		public synchronized long getSleepAndIncrement() {
			long next = super.getSleepAndIncrement();
			next = (long) (next * (1 + r.nextFloat() * (getMultiplier() - 1)));
			if (next > super.getMaxInterval()) {
				next = super.getMaxInterval();
			}
			return next;
		}

	}

}
