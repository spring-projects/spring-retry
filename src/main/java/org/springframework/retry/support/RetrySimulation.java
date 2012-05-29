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


import java.util.*;

/**
 * The results of a simulation.
 */
public class RetrySimulation {
    private final List<SleepSequence> sleepSequences = new ArrayList<SleepSequence>();
    private final Map<Long, Long> sleepHistogram = new HashMap<Long, Long>();

    /**
     * Add a sequence of sleeps to the simulation.
     */
    public void addSequence(List<Long> sleeps) {
        for (Long sleep : sleeps) {
            Long existingHisto = sleepHistogram.get(sleep);
            if (existingHisto == null) {
                sleepHistogram.put(sleep, 1l);
            } else {
                sleepHistogram.put(sleep, existingHisto + 1);
            }
        }

        sleepSequences.add(new SleepSequence(sleeps));
    }

    /**
     * @return Returns a list of all the unique sleep values which were executed within
     *         all simulations.
     */
    public List<Long> getUniqueSleeps() {
        List<Long> res = new ArrayList<Long>(sleepHistogram.keySet());
        Collections.sort(res);
        return res;
    }

    /**
     * @return the count of each sleep which was seen throughout all sleeps.
     *         histogram[i] = sum(getUniqueSleeps()[i])
     */
    public List<Long> getUniqueSleepsHistogram() {
        List<Long> res = new ArrayList<Long>(sleepHistogram.size());
        for (Long sleep : getUniqueSleeps()) {
            res.add(sleepHistogram.get(sleep));
        }
        return res;
    }

    /**
     * @return the longest total time slept by a retry sequence.
     */
    public SleepSequence getLongestTotalSleepSequence() {
        SleepSequence longest = null;
        for (SleepSequence sequence : sleepSequences) {
            if (longest == null || sequence.getTotalSleep() > longest.getTotalSleep()) {
                longest = sequence;
            }
        }
        return longest;
    }

    public static class SleepSequence {
        private final List<Long> sleeps;
        private final long longestSleep;
        private final long totalSleep;

        public SleepSequence(List<Long> sleeps) {
            this.sleeps = sleeps;
            this.longestSleep = Collections.max(sleeps);
            long totalSleep = 0;
            for (Long sleep : sleeps) {
                totalSleep += sleep;
            }
            this.totalSleep = totalSleep;
        }

        public List<Long> getSleeps() {
            return sleeps;
        }

        /**
         * Returns the longest individual sleep within this sequence.
         */
        public long getLongestSleep() {
            return longestSleep;
        }

        public long getTotalSleep() {
            return totalSleep;
        }

        public String toString() {
            return "totalSleep=" + totalSleep + ": " + sleeps.toString();
        }
    }
}
