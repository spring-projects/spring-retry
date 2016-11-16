/*
 * Copyright 2006-2007 the original author or authors.
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

package org.springframework.retry.support;

import java.util.ArrayList;
import java.util.List;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.backoff.SleepingBackOffPolicy;

/**
 * A {@link RetrySimulator} is a tool for exercising retry + backoff operations.
 *
 * When calibrating a set of retry + backoff pairs, it is useful to know the behaviour
 * of the retry for various scenarios.
 *
 * Things you may want to know:
 *     - Does a 'maxInterval' of 5000 ms in my backoff even matter?
 *       (This is often the case when retry counts are low -- so why set the max interval
 *        at something that cannot be achieved?)
 *     - What are the typical sleep durations for threads in a retry
 *     - What was the longest sleep duration for any retry sequence
 *
 * The simulator provides this information by executing a retry + backoff pair until failure
 * (that is all retries are exhausted).  The information about each retry is provided
 * as part of the {@link RetrySimulation}.
 *
 * Note that the impetus for this class was to expose the timings which are possible with
 * {@link org.springframework.retry.backoff.ExponentialRandomBackOffPolicy}, which provides
 * random values and must be looked at over a series of trials.
 *
 * @author Jon Travis
 */
public class RetrySimulator {

	private final SleepingBackOffPolicy<?> backOffPolicy;
    private final RetryPolicy retryPolicy;

    public RetrySimulator(SleepingBackOffPolicy<?> backOffPolicy, RetryPolicy retryPolicy) {
        this.backOffPolicy = backOffPolicy;
        this.retryPolicy   = retryPolicy;
    }

    /**
     * Execute the simulator for a give # of iterations.
     *
     * @param numSimulations  Number of simulations to run
     * @return the outcome of all simulations
     */
    public RetrySimulation executeSimulation(int numSimulations) {
        RetrySimulation simulation = new RetrySimulation();

        for (int i=0; i<numSimulations; i++) {
            simulation.addSequence(executeSingleSimulation());
        }
        return simulation;
    }

    /**
     * Execute a single simulation
     * @return The sleeps which occurred within the single simulation.
     */
    public List<Long> executeSingleSimulation() {
        StealingSleeper stealingSleeper = new StealingSleeper();
        SleepingBackOffPolicy<?> stealingBackoff = backOffPolicy.withSleeper(stealingSleeper);

        RetryTemplate template = new RetryTemplate();
        template.setBackOffPolicy(stealingBackoff);
        template.setRetryPolicy(retryPolicy);

        try {
            template.execute(new FailingRetryCallback());
        } catch(FailingRetryException e) {

        } catch(Throwable e) {
            throw new RuntimeException("Unexpected exception", e);
        }

        return stealingSleeper.getSleeps();
    }

    static class FailingRetryCallback implements RetryCallback<Object, Exception> {
        public Object doWithRetry(RetryContext context) throws Exception {
            throw new FailingRetryException();
        }
    }

    @SuppressWarnings("serial")
	static class FailingRetryException extends Exception {
    }

    @SuppressWarnings("serial")
	static class StealingSleeper implements Sleeper {
        private final List<Long> sleeps = new ArrayList<Long>();

        public void sleep(long backOffPeriod) throws InterruptedException {
            sleeps.add(backOffPeriod);
        }

        public List<Long> getSleeps() {
            return sleeps;
        }
    }
}
