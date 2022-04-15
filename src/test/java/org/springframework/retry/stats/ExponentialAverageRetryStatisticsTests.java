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

package org.springframework.retry.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author Dave Syer
 *
 */
public class ExponentialAverageRetryStatisticsTests {

	private final ExponentialAverageRetryStatistics stats = new ExponentialAverageRetryStatistics("test");

	@Test
	public void pointless() {
		stats.setName("spam");
		assertEquals("spam", stats.getName());
		assertNotNull(stats.toString());
	}

	@Test
	public void attributes() {
		stats.setAttribute("foo", "bar");
		;
		assertEquals("bar", stats.getAttribute("foo"));
		assertTrue(Arrays.asList(stats.attributeNames()).contains("foo"));
	}

	@Test
	public void abortCount() {
		stats.incrementAbortCount();
		assertEquals(1, stats.getAbortCount());
		// rounds up to 1
		assertEquals(1, stats.getRollingAbortCount());
	}

	@Test
	public void errorCount() {
		stats.incrementErrorCount();
		assertEquals(1, stats.getErrorCount());
		// rounds up to 1
		assertEquals(1, stats.getRollingErrorCount());
	}

	@Test
	public void startedCount() {
		stats.incrementStartedCount();
		assertEquals(1, stats.getStartedCount());
		// rounds up to 1
		assertEquals(1, stats.getRollingStartedCount());
	}

	@Test
	public void completeCount() {
		stats.incrementCompleteCount();
		assertEquals(1, stats.getCompleteCount());
		// rounds up to 1
		assertEquals(1, stats.getRollingCompleteCount());
	}

	@Test
	public void recoveryCount() {
		stats.incrementRecoveryCount();
		assertEquals(1, stats.getRecoveryCount());
		// rounds up to 1
		assertEquals(1, stats.getRollingRecoveryCount());
	}

	@Test
	public void oldValuesDecay() {
		stats.incrementAbortCount();
		assertEquals(1, stats.getAbortCount());
		// Wind back time to epoch 0
		ReflectionTestUtils.setField(ReflectionTestUtils.getField(stats, "abort"), "lastTime", 0);
		// rounds down to 1
		assertEquals(0, stats.getRollingAbortCount());
	}

}
