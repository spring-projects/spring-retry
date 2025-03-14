/*
 * Copyright 2006-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

/**
 * Simple {@link Sleeper} implementation that just waits on a local Object.
 *
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class DummySleeper implements Sleeper {

	private final List<Long> backOffs = new ArrayList<>();

	/**
	 * Public getter for the long.
	 * @return the lastBackOff
	 */
	public long getLastBackOff() {
		return backOffs.get(backOffs.size() - 1);
	}

	public long[] getBackOffs() {
		long[] result = new long[backOffs.size()];
		int i = 0;
		for (Long value : backOffs) {
			result[i++] = value;
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.springframework.batch.retry.backoff.Sleeper#sleep(long)
	 */
	public void sleep(long backOffPeriod) throws InterruptedException {
		this.backOffs.add(backOffPeriod);
	}

}
