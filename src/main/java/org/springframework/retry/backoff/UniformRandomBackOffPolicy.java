/*
 * Copyright 2006-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.backoff;

import java.util.Random;


/**
 * Implementation of {@link BackOffPolicy} that pauses for a random period of
 * time before continuing. A pause is implemented using {@link Sleeper#sleep(long)}.
 *
 * {@link #setMinBackOffPeriod(long)} is thread-safe and it is safe to call
 * {@link #setMaxBackOffPeriod(long)} during execution from multiple threads, however
 * this may cause a single retry operation to have pauses of different
 * intervals.
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public class UniformRandomBackOffPolicy extends StatelessBackOffPolicy implements SleepingBackOffPolicy<UniformRandomBackOffPolicy> {

	/**
	 * Default min back off period - 500ms.
	 */
	private static final long DEFAULT_BACK_OFF_MIN_PERIOD = 500L;

	/**
	 * Default max back off period - 1500ms.
	 */
	private static final long DEFAULT_BACK_OFF_MAX_PERIOD = 1500L;

	private volatile long minBackOffPeriod = DEFAULT_BACK_OFF_MIN_PERIOD;

	private volatile long maxBackOffPeriod = DEFAULT_BACK_OFF_MAX_PERIOD;

	private Random random = new Random(System.currentTimeMillis());

	private Sleeper sleeper = new ThreadWaitSleeper();

    public UniformRandomBackOffPolicy withSleeper(Sleeper sleeper) {
        UniformRandomBackOffPolicy res = new UniformRandomBackOffPolicy();
        res.setMinBackOffPeriod(minBackOffPeriod);
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
	 * Set the minimum back off period in milliseconds. Cannot be &lt; 1. Default value
	 * is 500ms.
	 *
	 * @param backOffPeriod the backoff period
	 */
	public void setMinBackOffPeriod(long backOffPeriod) {
		this.minBackOffPeriod = (backOffPeriod > 0 ? backOffPeriod : 1);
	}

	/**
	 * The minimum backoff period in milliseconds.
	 * @return the backoff period
	 */
	public long getMinBackOffPeriod() {
		return minBackOffPeriod;
	}

	/**
	 * Set the maximum back off period in milliseconds. Cannot be &lt; 1. Default value
	 * is 1500ms.
	 * @param backOffPeriod the back off period
	 */
	public void setMaxBackOffPeriod(long backOffPeriod) {
		this.maxBackOffPeriod = (backOffPeriod > 0 ? backOffPeriod : 1);
	}

	/**
	 * The maximum backoff period in milliseconds.
	 * @return the backoff period
	 */
	public long getMaxBackOffPeriod() {
		return maxBackOffPeriod;
	}

	/**
	 * Pause for the {@link #setMinBackOffPeriod(long)}.
	 * @throws BackOffInterruptedException if interrupted during sleep.
	 */
	protected void doBackOff() throws BackOffInterruptedException {
		try {
			long delta = maxBackOffPeriod==minBackOffPeriod ? 0 : random.nextInt((int) (maxBackOffPeriod - minBackOffPeriod));
			sleeper.sleep(minBackOffPeriod + delta );
		}
		catch (InterruptedException e) {
			throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
		}
	}

    public String toString() {
        return "RandomBackOffPolicy[backOffPeriod=" + minBackOffPeriod + ", " + maxBackOffPeriod + "]";
    }
}
