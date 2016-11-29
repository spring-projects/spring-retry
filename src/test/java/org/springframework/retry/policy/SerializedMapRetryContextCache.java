/*
 * Copyright 2016 the original author or authors.
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
package org.springframework.retry.policy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.retry.RetryContext;
import org.springframework.util.SerializationUtils;

public class SerializedMapRetryContextCache implements RetryContextCache {
	private static final int DEFAULT_CAPACITY = 4096;

	private Map<Object, byte[]> map = Collections.synchronizedMap(new HashMap<Object, byte[]>());

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public RetryContext get(Object key) {
		byte[] bytes = map.get(key);
		return (RetryContext) SerializationUtils.deserialize(bytes);
	}

	@Override
	public void put(Object key, RetryContext context) {
		if (map.size() >= DEFAULT_CAPACITY) {
			throw new RetryCacheCapacityExceededException("Retry cache capacity "
					+ "limit breached. Do you need to re-consider the implementation of the key generator, "
					+ "or the equals and hashCode of the items that failed?");
		}
		byte[] serialized = SerializationUtils.serialize(context);
		map.put(key, serialized);
	}

	@Override
	public void remove(Object key) {
		map.remove(key);
	}
}
