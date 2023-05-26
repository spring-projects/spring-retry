/*
 * Copyright 2006-2023 the original author or authors.
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

package org.springframework.retry.policy;

import org.junit.jupiter.api.Test;

import org.springframework.retry.context.RetryContextSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class SoftReferenceMapRetryContextCacheTests {

	SoftReferenceMapRetryContextCache cache = new SoftReferenceMapRetryContextCache();

	@Test
	public void testPut() {
		RetryContextSupport context = new RetryContextSupport(null);
		cache.put("foo", context);
		assertThat(cache.get("foo")).isEqualTo(context);
	}

	@Test
	public void testPutOverLimit() {
		RetryContextSupport context = new RetryContextSupport(null);
		cache.setCapacity(1);
		cache.put("foo", context);
		assertThatExceptionOfType(RetryCacheCapacityExceededException.class)
			.isThrownBy(() -> cache.put("foo", context));
	}

	@Test
	public void testRemove() {
		assertThat(cache.containsKey("foo")).isFalse();
		RetryContextSupport context = new RetryContextSupport(null);
		cache.put("foo", context);
		assertThat(cache.containsKey("foo")).isTrue();
		cache.remove("foo");
		assertThat(cache.containsKey("foo")).isFalse();
	}

}
