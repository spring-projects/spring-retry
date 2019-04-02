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

package org.springframework.retry.support;

import org.junit.Before;
import org.junit.Test;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.context.RetryContextSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Dave Syer
 */
public class RetrySynchronizationManagerTests {

	RetryTemplate template = new RetryTemplate();

	@Before
	public void setUp() throws Exception {
		RetrySynchronizationManagerTests.clearAll();
		RetryContext status = RetrySynchronizationManager.getContext();
		assertNull(status);
	}

	@Test
	public void testStatusIsStoredByTemplate() throws Throwable {

		RetryContext status = RetrySynchronizationManager.getContext();
		assertNull(status);

		this.template.execute(new RetryCallback<Object, Exception>() {
			@Override
			public Object doWithRetry(RetryContext status) throws Exception {
				RetryContext global = RetrySynchronizationManager.getContext();
				assertNotNull(status);
				assertEquals(global, status);
				return null;
			}
		});

		status = RetrySynchronizationManager.getContext();
		assertNull(status);
	}

	@Test
	public void testStatusRegistration() throws Exception {
		RetryContext status = new RetryContextSupport(null);
		RetryContext value = RetrySynchronizationManager.register(status);
		assertNull(value);
		value = RetrySynchronizationManager.register(status);
		assertEquals(status, value);
	}

	@Test
	public void testClear() throws Exception {
		RetryContext status = new RetryContextSupport(null);
		RetryContext value = RetrySynchronizationManager.register(status);
		assertNull(value);
		RetrySynchronizationManager.clear();
		value = RetrySynchronizationManager.register(status);
		assertNull(value);
	}

	@Test
	public void testParent() throws Exception {
		RetryContext parent = new RetryContextSupport(null);
		RetryContext child = new RetryContextSupport(parent);
		assertSame(parent, child.getParent());
	}

	/**
	 * Clear all contexts starting with the current one and continuing until
	 * {@link RetrySynchronizationManager#clear()} returns null.
	 * @return a retry context
	 */
	public static RetryContext clearAll() {
		RetryContext result = null;
		RetryContext context = RetrySynchronizationManager.clear();
		while (context != null) {
			result = context;
			context = RetrySynchronizationManager.clear();
		}
		return result;
	}

}
