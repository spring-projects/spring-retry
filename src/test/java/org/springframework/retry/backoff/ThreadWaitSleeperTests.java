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

import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author Dave Syer
 * @author Artem Bilan
 */
public class ThreadWaitSleeperTests {

	@Test
	public void testSingleBackOff() throws Exception {
		long backOffPeriod = 50;
		ThreadWaitSleeper strategy = new ThreadWaitSleeper();
		long before = System.currentTimeMillis();
		strategy.sleep(backOffPeriod);
		long after = System.currentTimeMillis();
		assertEqualsApprox(backOffPeriod, after - before, 25);
	}

	private void assertEqualsApprox(long desired, long actual, long variance) {
		long lower = desired - variance;
		long upper = desired + 2 * variance;
		assertTrue("Expected value to be between '" + lower + "' and '" + upper + "' but was '" + actual + "'",
				lower <= actual);
	}

}
