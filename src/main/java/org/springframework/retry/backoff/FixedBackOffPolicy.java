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

import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.retry.RetryContext;
import org.springframework.util.Assert;

/**
 * Implementation of {@link BackOffPolicy} that pauses for a fixed period of time before
 * continuing. A pause is implemented using {@link Sleeper#sleep(long)}.
 *
 * {@link #setBackOffPeriod(long)} is thread-safe and it is safe to call
 * {@link #setBackOffPeriod} during execution from multiple threads, however this may
 * cause a single retry operation to have pauses of different intervals.
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 */
public class FixedBackOffPolicy extends StatelessBackOffPolicy implements SleepingBackOffPolicy<FixedBackOffPolicy> {

	/**
	 * Default back off period - 1000ms.
	 */
	private static final long DEFAULT_BACK_OFF_PERIOD = 1000L;

	/**
	 * The back off period in milliseconds. Defaults to 1000ms.
	 */
	private Function<RetryContext, Long> backOffPeriod = context -> DEFAULT_BACK_OFF_PERIOD;

	private Sleeper sleeper = new ThreadWaitSleeper();

	private RetryContext retryContext;

	@Override
	public FixedBackOffPolicy withSleeper(Sleeper sleeper) {
		FixedBackOffPolicy res = new FixedBackOffPolicy();
		res.backOffPeriodFunction(this.backOffPeriod);
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
	 * Set the back off period in milliseconds. Cannot be &lt; 1. Default value is 1000ms.
	 * @param backOffPeriod the back off period
	 */
	public void setBackOffPeriod(long backOffPeriod) {
		this.backOffPeriod = context -> (backOffPeriod > 0 ? backOffPeriod : 1);
	}

	/**
	 * Set a supplier for the back off period in milliseconds. Cannot be &lt; 1. Default
	 * supplier supplies 1000ms.
	 * @param backOffPeriodSupplier the back off period
	 * @since 2.0
	 * @deprecated in favor of {@link #backOffPeriodFunction(Function)}.
	 */
	@Deprecated
	public void backOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
		Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
		this.backOffPeriod = context -> backOffPeriodSupplier.get();
	}

	/**
	 * Set a supplier for the back off period in milliseconds. Cannot be &lt; 1. Default
	 * supplier supplies 1000ms.
	 * @param backOffPeriodFunction the back off period
	 * @since 2.0.3
	 */
	public void backOffPeriodFunction(Function<RetryContext, Long> backOffPeriodFunction) {
		Assert.notNull(backOffPeriodFunction, "'backOffPeriodFunction' cannot be null");
		this.backOffPeriod = backOffPeriodFunction;
	}

	/**
	 * The backoff period in milliseconds.
	 * @return the backoff period
	 */
	public long getBackOffPeriod() {
		return this.backOffPeriod.apply(this.retryContext);
	}

	@Override
	public BackOffContext start(RetryContext status) {
		this.retryContext = status;
		return super.start(status);
	}

	/**
	 * Pause for the {@link #setBackOffPeriod(long)}.
	 * @throws BackOffInterruptedException if interrupted during sleep.
	 */
	@Override
	protected void doBackOff() throws BackOffInterruptedException {
		try {
			this.sleeper.sleep(this.backOffPeriod.apply(this.retryContext));
		}
		catch (InterruptedException e) {
			throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
		}
	}

	@Override
	public String toString() {
		return "FixedBackOffPolicy[backOffPeriod=" + this.backOffPeriod.apply(this.retryContext) + "]";
	}

}
