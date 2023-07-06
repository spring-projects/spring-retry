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
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.retry.RetryContext;
import org.springframework.util.Assert;

/**
 * Implementation of {@link BackOffPolicy} that pauses for a random period of time before
 * continuing. A pause is implemented using {@link Sleeper#sleep(long)}.
 *
 * {@link #setMinBackOffPeriod(long)} is thread-safe and it is safe to call
 * {@link #setMaxBackOffPeriod(long)} during execution from multiple threads, however this
 * may cause a single retry operation to have pauses of different intervals.
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Tomaz Fernandes
 * @author Gary Russell
 */
public class UniformRandomBackOffPolicy extends StatelessBackOffPolicy
		implements SleepingBackOffPolicy<UniformRandomBackOffPolicy> {

	/**
	 * Default min back off period - 500ms.
	 */
	private static final long DEFAULT_BACK_OFF_MIN_PERIOD = 500L;

	/**
	 * Default max back off period - 1500ms.
	 */
	private static final long DEFAULT_BACK_OFF_MAX_PERIOD = 1500L;

	private Function<RetryContext, Long> minBackOffPeriod = context -> DEFAULT_BACK_OFF_MIN_PERIOD;

	private Function<RetryContext, Long> maxBackOffPeriod = context -> DEFAULT_BACK_OFF_MAX_PERIOD;

	private final Random random = new Random(System.currentTimeMillis());

	private Sleeper sleeper = new ThreadWaitSleeper();

	private RetryContext retryContext;

	@Override
	public UniformRandomBackOffPolicy withSleeper(Sleeper sleeper) {
		UniformRandomBackOffPolicy res = new UniformRandomBackOffPolicy();
		res.minBackOffPeriodFunction(this.minBackOffPeriod);
		res.maxBackOffPeriodFunction(this.maxBackOffPeriod);
		res.setSleeper(sleeper);
		return res;
	}

	/**
	 * Public setter for the {@link Sleeper} strategy.
	 * @param sleeper the sleeper to set defaults to {@link ThreadWaitSleeper}.
	 */
	public void setSleeper(Sleeper sleeper) {
		this.sleeper = sleeper;
	}

	/**
	 * Set the minimum back off period in milliseconds. Cannot be &lt; 1. Default value is
	 * 500ms.
	 * @param backOffPeriod the backoff period
	 */
	public void setMinBackOffPeriod(long backOffPeriod) {
		this.minBackOffPeriod = context -> (backOffPeriod > 0 ? backOffPeriod : 1);
	}

	/**
	 * Set a supplier for the minimum back off period in milliseconds. Cannot be &lt; 1.
	 * Default supplier supplies 500ms.
	 * @param backOffPeriodSupplier the backoff period
	 * @since 2.0
	 * @deprecated in favor of {@link #minBackOffPeriodFunction(Function)}.
	 */
	@Deprecated
	public void minBackOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
		Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
		this.minBackOffPeriod = context -> backOffPeriodSupplier.get();
	}

	/**
	 * Set a supplier for the minimum back off period in milliseconds. Cannot be &lt; 1.
	 * Default supplier supplies 500ms.
	 * @param backOffPeriodFunction the backoff period
	 * @since 2.0
	 */
	public void minBackOffPeriodFunction(Function<RetryContext, Long> backOffPeriodFunction) {
		Assert.notNull(backOffPeriodFunction, "'backOffPeriodFunction' cannot be null");
		this.minBackOffPeriod = context -> backOffPeriodFunction.apply(this.retryContext);
	}

	/**
	 * The minimum backoff period in milliseconds.
	 * @return the backoff period
	 */
	public long getMinBackOffPeriod() {
		return this.minBackOffPeriod.apply(this.retryContext);
	}

	/**
	 * Set the maximum back off period in milliseconds. Cannot be &lt; 1. Default value is
	 * 1500ms.
	 * @param backOffPeriod the back off period
	 */
	public void setMaxBackOffPeriod(long backOffPeriod) {
		this.maxBackOffPeriod = context -> (backOffPeriod > 0 ? backOffPeriod : 1);
	}

	/**
	 * Set a supplier for the maximum back off period in milliseconds. Cannot be &lt; 1.
	 * Default supplier supplies 1500ms.
	 * @param backOffPeriodSupplier the back off period
	 * @since 2.0
	 * @deprecated in favor of {@link #maxBackOffPeriodFunction(Function)}
	 */
	@Deprecated
	public void maxBackOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
		Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
		this.maxBackOffPeriod = context -> backOffPeriodSupplier.get();
	}

	/**
	 * Set a supplier for the maximum back off period in milliseconds. Cannot be &lt; 1.
	 * Default supplier supplies 1500ms.
	 * @param backOffPeriodFunction the back off period
	 * @since 2.0.3
	 */
	public void maxBackOffPeriodFunction(Function<RetryContext, Long> backOffPeriodFunction) {
		Assert.notNull(backOffPeriodFunction, "'backOffPeriodFunction' cannot be null");
		this.maxBackOffPeriod = backOffPeriodFunction;
	}

	/**
	 * The maximum backoff period in milliseconds.
	 * @return the backoff period
	 */
	public long getMaxBackOffPeriod() {
		return this.maxBackOffPeriod.apply(this.retryContext);
	}

	@Override
	public BackOffContext start(RetryContext status) {
		this.retryContext = status;
		return super.start(status);
	}

	/**
	 * Pause for the {@link #setMinBackOffPeriod(long)}.
	 * @throws BackOffInterruptedException if interrupted during sleep.
	 */
	@Override
	protected void doBackOff() throws BackOffInterruptedException {
		try {
			Long min = this.minBackOffPeriod.apply(this.retryContext);
			Long max = this.maxBackOffPeriod.apply(this.retryContext);
			long delta = max == this.minBackOffPeriod.apply(this.retryContext) ? 0
					: this.random.nextInt((int) (max - min));
			this.sleeper.sleep(min + delta);
		}
		catch (InterruptedException e) {
			throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
		}
	}

	@Override
	public String toString() {
		return "RandomBackOffPolicy[backOffPeriod=" + this.minBackOffPeriod + ", " + this.maxBackOffPeriod + "]";
	}

}
