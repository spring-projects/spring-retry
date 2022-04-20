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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class ExponentialAverageRetryStatisticsTests {

	private final ExponentialAverageRetryStatistics stats = new ExponentialAverageRetryStatistics("test");

	@Test
	public void pointless() {
		stats.setName("spam");
		assertThat(stats.getName()).isEqualTo("spam");
	}

	@Test
	public void attributes() {
		stats.setAttribute("foo", "bar");
		assertThat(stats.getAttribute("foo")).isEqualTo("bar");
		assertThat(Arrays.asList(stats.attributeNames()).contains("foo")).isTrue();
	}

	@Test
	public void abortCount() {
		stats.incrementAbortCount();
		assertThat(stats.getAbortCount()).isEqualTo(1);
		// rounds up to 1
		assertThat(stats.getRollingAbortCount()).isEqualTo(1);
	}

	@Test
	public void errorCount() {
		stats.incrementErrorCount();
		assertThat(stats.getErrorCount()).isEqualTo(1);
		// rounds up to 1
		assertThat(stats.getRollingErrorCount()).isEqualTo(1);
	}

	@Test
	public void startedCount() {
		stats.incrementStartedCount();
		assertThat(stats.getStartedCount()).isEqualTo(1);
		// rounds up to 1
		assertThat(stats.getRollingStartedCount()).isEqualTo(1);
	}

	@Test
	public void completeCount() {
		stats.incrementCompleteCount();
		assertThat(stats.getCompleteCount()).isEqualTo(1);
		// rounds up to 1
		assertThat(stats.getRollingCompleteCount()).isEqualTo(1);
	}

	@Test
	public void recoveryCount() {
		stats.incrementRecoveryCount();
		assertThat(stats.getRecoveryCount()).isEqualTo(1);
		// rounds up to 1
		assertThat(stats.getRollingRecoveryCount()).isEqualTo(1);
	}

	@Test
	public void oldValuesDecay() {
		stats.incrementAbortCount();
		assertThat(stats.getAbortCount()).isEqualTo(1);
		// Wind back time to epoch 0
		ReflectionTestUtils.setField(ReflectionTestUtils.getField(stats, "abort"), "lastTime", 0);
		// rounds down to 1
		assertThat(stats.getRollingAbortCount()).isEqualTo(0);
	}

}
