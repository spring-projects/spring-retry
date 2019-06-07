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

package org.springframework.retry.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

public class RetryListenerTests {

	RetryTemplate template = new RetryTemplate();

	int count = 0;

	List<String> list = new ArrayList<String>();

	@Test
	public void testOpenInterceptors() throws Throwable {
		template.setListeners(new RetryListener[] { new RetryListenerSupport() {
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
				count++;
				list.add("1:" + count);
				return true;
			}
		}, new RetryListenerSupport() {
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
				count++;
				list.add("2:" + count);
				return true;
			}
		} });
		template.execute(new RetryCallback<String, Exception>() {
			public String doWithRetry(RetryContext context) throws Exception {
				return null;
			}
		});
		assertEquals(2, count);
		assertEquals(2, list.size());
		assertEquals("1:1", list.get(0));
	}

	@Test
	public void testOpenCanVetoRetry() throws Throwable {
		template.registerListener(new RetryListenerSupport() {
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
				list.add("1");
				return false;
			}
		});
		try {
			template.execute(new RetryCallback<String, Exception>() {
				public String doWithRetry(RetryContext context) throws Exception {
					count++;
					return null;
				}
			});
			fail("Expected TerminatedRetryException");
		}
		catch (TerminatedRetryException e) {
			// expected
		}
		assertEquals(0, count);
		assertEquals(1, list.size());
		assertEquals("1", list.get(0));
	}

	@Test
	public void testCloseInterceptors() throws Throwable {
		template.setListeners(new RetryListener[] { new RetryListenerSupport() {
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable t) {
				count++;
				list.add("1:" + count);
			}
		}, new RetryListenerSupport() {
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable t) {
				count++;
				list.add("2:" + count);
			}
		} });
		template.execute(new RetryCallback<String, Exception>() {
			public String doWithRetry(RetryContext context) throws Exception {
				return null;
			}
		});
		assertEquals(2, count);
		assertEquals(2, list.size());
		// interceptors are called in reverse order on close...
		assertEquals("2:1", list.get(0));
	}

	@Test
	public void testOnError() throws Throwable {
		template.setRetryPolicy(new NeverRetryPolicy());
		template.setListeners(new RetryListener[] { new RetryListenerSupport() {
			public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				list.add("1");
			}
		}, new RetryListenerSupport() {
			public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				list.add("2");
			}
		} });
		try {
			template.execute(new RetryCallback<String, Exception>() {
				public String doWithRetry(RetryContext context) throws Exception {
					count++;
					throw new IllegalStateException("foo");
				}
			});
			fail("Expected IllegalStateException");
		}
		catch (IllegalStateException e) {
			assertEquals("foo", e.getMessage());
		}
		// never retry so callback is executed once
		assertEquals(1, count);
		assertEquals(2, list.size());
		// interceptors are called in reverse order on error...
		assertEquals("2", list.get(0));

	}

	@Test
	public void testCloseInterceptorsAfterRetry() throws Throwable {
		template.registerListener(new RetryListenerSupport() {
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable t) {
				list.add("" + count);
				// The last attempt should have been successful:
				assertNull(t);
			}
		});
		template.execute(new RetryCallback<String, Exception>() {
			public String doWithRetry(RetryContext context) throws Exception {
				if (count++ < 1)
					throw new RuntimeException("Retry!");
				return null;
			}
		});
		assertEquals(2, count);
		// The close interceptor was only called once:
		assertEquals(1, list.size());
		// We succeeded on the second try:
		assertEquals("2", list.get(0));
	}

}
