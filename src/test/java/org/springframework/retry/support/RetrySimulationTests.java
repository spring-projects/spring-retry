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

import org.junit.Test;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        System.out.println("All Sleeps:       " + simulation.getUniqueSleeps());
        System.out.println("Sleep Occurences: " + simulation.getUniqueSleepsHistogram());

        assertEquals(asList(400l, 400l, 400l, 400l), simulation.getLongestTotalSleepSequence().getSleeps());
        assertEquals(asList(400l), simulation.getUniqueSleeps());
        assertEquals(asList(4000l), simulation.getUniqueSleepsHistogram());
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
        System.out.println("All Sleeps:       " + simulation.getUniqueSleeps());
        System.out.println("Sleep Occurences: " + simulation.getUniqueSleepsHistogram());

        assertEquals(asList(100l, 200l, 400l, 800l), simulation.getLongestTotalSleepSequence().getSleeps());
        assertEquals(asList(100l, 200l, 400l, 800l), simulation.getUniqueSleeps());
        assertEquals(asList(1000l, 1000l, 1000l, 1000l), simulation.getUniqueSleepsHistogram());
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
        System.out.println("All Sleeps:       " + simulation.getUniqueSleeps());
        System.out.println("Sleep Occurences: " + simulation.getUniqueSleepsHistogram());

        assertEquals(asList(100l, 200l, 400l, 800l), simulation.getLongestTotalSleepSequence().getSleeps());
        assertEquals(asList(100l, 200l, 300l, 400l, 500l, 600l, 700l, 800l), simulation.getUniqueSleeps());
        for (long histo : simulation.getUniqueSleepsHistogram()) {
            assertTrue("Should have experienced a sleep more than " + histo + " times",
                       histo > 1000);
        }
    }
}
