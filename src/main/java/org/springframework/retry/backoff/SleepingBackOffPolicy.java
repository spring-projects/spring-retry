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

/**
 * A interface which can be mixed in by {@link BackOffPolicy}s indicating that they sleep
 * when backing off.
 */
public interface SleepingBackOffPolicy<T extends SleepingBackOffPolicy<T>> extends BackOffPolicy {
    /**
     * Clone the policy and return a new policy which uses the passed sleeper.
     *
     * @param sleeper  Target to be invoked any time the backoff policy sleeps
     * @return a clone of this policy which will have all of its backoff sleeps
     *         routed into the passed sleeper
     */
    T withSleeper(Sleeper sleeper);
}
