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

package org.springframework.retry.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.retry.RetryContext;

public class SimpleRetryPolicyTests {

	@Test
	public void testCanRetryIfNoException() throws Exception {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertTrue(policy.canRetry(context));
	}

	@Test
	public void testEmptyExceptionsNeverRetry() throws Exception {

		// We can't retry any exceptions...
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3,
				Collections.<Class<? extends Throwable>, Boolean>emptyMap());
		RetryContext context = policy.open(null);

		// ...so we can't retry this one...
		policy.registerThrowable(context, new IllegalStateException());
		assertFalse(policy.canRetry(context));
	}

	@Test
	public void testWithExceptionDefaultAlwaysRetry() throws Exception {

		// We retry any exceptions except...
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3,
				Collections.<Class<? extends Throwable>, Boolean>singletonMap(IllegalStateException.class, false), true,
				true);
		RetryContext context = policy.open(null);

		// ...so we can't retry this one...
		policy.registerThrowable(context, new IllegalStateException());
		assertFalse(policy.canRetry(context));

		// ...and we can retry this one...
		policy.registerThrowable(context, new IllegalArgumentException());
		assertTrue(policy.canRetry(context));
	}

	@Test
	public void testRetryLimitInitialState() throws Exception {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertTrue(policy.canRetry(context));
		policy.setMaxAttempts(0);
		context = policy.open(null);
		assertFalse(policy.canRetry(context));
	}

	@Test
	public void testRetryLimitSubsequentState() throws Exception {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		policy.setMaxAttempts(2);
		assertTrue(policy.canRetry(context));
		policy.registerThrowable(context, new Exception());
		assertTrue(policy.canRetry(context));
		policy.registerThrowable(context, new Exception());
		assertFalse(policy.canRetry(context));
	}

	@Test
	public void testRetryCount() throws Exception {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		assertNotNull(context);
		policy.registerThrowable(context, null);
		assertEquals(0, context.getRetryCount());
		policy.registerThrowable(context, new RuntimeException("foo"));
		assertEquals(1, context.getRetryCount());
		assertEquals("foo", context.getLastThrowable().getMessage());
	}

	@Test
	public void testFatalOverridesRetryable() throws Exception {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<Class<? extends Throwable>, Boolean>();
		map.put(Exception.class, false);
		map.put(RuntimeException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map);
		RetryContext context = policy.open(null);
		assertNotNull(context);
		policy.registerThrowable(context, new RuntimeException("foo"));
		assertTrue(policy.canRetry(context));
	}

	@Test
	public void testRetryableWithCause() throws Exception {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<Class<? extends Throwable>, Boolean>();
		map.put(RuntimeException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map, true);
		RetryContext context = policy.open(null);
		assertNotNull(context);
		policy.registerThrowable(context, new Exception(new RuntimeException("foo")));
		assertTrue(policy.canRetry(context));
	}

	@Test
	public void testParent() throws Exception {
		SimpleRetryPolicy policy = new SimpleRetryPolicy();
		RetryContext context = policy.open(null);
		RetryContext child = policy.open(context);
		assertNotSame(child, context);
		assertSame(context, child.getParent());
	}

	@Test
	public void testRetryForException() {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<Class<? extends Throwable>, Boolean>();
		map.put(RuntimeException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map, true);
		RetryContext context = policy.open(null);
		assertNotNull(context);
		assertTrue(policy.retryForException(new RuntimeException()));
	}

	@Test
	public void testNoRetryForException() {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<Class<? extends Throwable>, Boolean>();
		map.put(IllegalArgumentException.class, true);
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map, true);
		RetryContext context = policy.open(null);
		assertNotNull(context);
		assertFalse(policy.retryForException(new RuntimeException()));
	}

}
