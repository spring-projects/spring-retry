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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.retry.RetryStatistics;

/**
 * @author Dave Syer
 *
 */
public class DefaultStatisticsRepository implements StatisticsRepository {

	private final ConcurrentMap<String, MutableRetryStatistics> map = new ConcurrentHashMap<>();

	private RetryStatisticsFactory factory = new DefaultRetryStatisticsFactory();

	public void setRetryStatisticsFactory(RetryStatisticsFactory factory) {
		this.factory = factory;
	}

	@Override
	public RetryStatistics findOne(String name) {
		return map.get(name);
	}

	@Override
	public Iterable<RetryStatistics> findAll() {
		return new ArrayList<>(map.values());
	}

	@Override
	public void addStarted(String name) {
		getStatistics(name).incrementStartedCount();
	}

	@Override
	public void addError(String name) {
		getStatistics(name).incrementErrorCount();
	}

	@Override
	public void addRecovery(String name) {
		getStatistics(name).incrementRecoveryCount();
	}

	@Override
	public void addComplete(String name) {
		getStatistics(name).incrementCompleteCount();
	}

	@Override
	public void addAbort(String name) {
		getStatistics(name).incrementAbortCount();
	}

	private MutableRetryStatistics getStatistics(String name) {
		MutableRetryStatistics stats;
		if (!map.containsKey(name)) {
			map.putIfAbsent(name, factory.create(name));
		}
		stats = map.get(name);
		return stats;
	}

}
