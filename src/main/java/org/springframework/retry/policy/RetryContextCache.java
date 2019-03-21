/*
 * Copyright 2006-2007 the original author or authors.
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

import org.springframework.retry.RetryContext;

/**
 * Simple map-like abstraction for stateful retry policies to use when storing and
 * retrieving {@link RetryContext} instances. A null key should never be passed in by the
 * caller, but if it is then implementations are free to discard the context instead of
 * saving it (null key means "no information").
 *
 * @author Dave Syer
 * @see MapRetryContextCache
 *
 */
public interface RetryContextCache {

	RetryContext get(Object key);

	void put(Object key, RetryContext context) throws RetryCacheCapacityExceededException;

	void remove(Object key);

	boolean containsKey(Object key);

}
