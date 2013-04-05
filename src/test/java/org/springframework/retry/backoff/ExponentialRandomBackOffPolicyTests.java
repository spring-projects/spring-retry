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

package org.springframework.retry.backoff;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetrySimulation;
import org.springframework.retry.support.RetrySimulator;

/**
 * @author Dave Syer
 * @author Jon Travis
 *
 */
public class ExponentialRandomBackOffPolicyTests {
    static final int NUM_TRIALS = 10000;
    static final int MAX_RETRIES = 6;

    private ExponentialBackOffPolicy makeBackoffPolicy() {
        ExponentialBackOffPolicy policy = new ExponentialRandomBackOffPolicy();
        policy.setInitialInterval(50);
        policy.setMultiplier(2.0);
        policy.setMaxInterval(3000);
        return policy;
    }

    private SimpleRetryPolicy makeRetryPolicy() {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_RETRIES);
        return retryPolicy;
    }

	@Test
   public void testSingleBackoff() throws Exception {
        ExponentialBackOffPolicy backOffPolicy = makeBackoffPolicy();
        RetrySimulator simulator = new RetrySimulator(backOffPolicy, makeRetryPolicy());
        RetrySimulation simulation = simulator.executeSimulation(1);

        List<Long> sleeps = simulation.getLongestTotalSleepSequence().getSleeps();
        System.out.println("Single trial of " + backOffPolicy + ": sleeps=" + sleeps);
        assertEquals(MAX_RETRIES - 1, sleeps.size());
        long initialInterval = backOffPolicy.getInitialInterval();
        for (int i=0; i<sleeps.size(); i++) {
            long expectedMaxValue = 2*(long) (initialInterval + initialInterval * Math.max(1, Math.pow(backOffPolicy.getMultiplier(), i)));
            assertTrue("Found a sleep [" + sleeps.get(i) + "] which exceeds our max expected value of " + expectedMaxValue + " at interval " + i,
                       sleeps.get(i) < expectedMaxValue);
        }
    }

	@Test
	public void testMultiBackOff() throws Exception {
        ExponentialBackOffPolicy backOffPolicy = makeBackoffPolicy();
        RetrySimulator simulator = new RetrySimulator(backOffPolicy, makeRetryPolicy());
        RetrySimulation simulation = simulator.executeSimulation(NUM_TRIALS);

        System.out.println("Ran " + NUM_TRIALS + " backoff trials.  Each trial retried " + MAX_RETRIES + " times");
        System.out.println("Policy: " + backOffPolicy);
        System.out.println("All generated backoffs:");
        System.out.println("    " + simulation.getPercentiles());

        System.out.println("Backoff frequencies:");
        System.out.print("    " + simulation.getPercentiles());

	}
}
