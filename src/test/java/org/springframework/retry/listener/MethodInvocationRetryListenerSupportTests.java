/*
 * Copyright 2006-2019 the original author or authors.
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

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.interceptor.MethodInvocationRetryCallback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class MethodInvocationRetryListenerSupportTests {

	@Test
	public void testClose() {
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport();
		try {
			support.close(null, null, null);
		}
		catch (Exception e) {
			fail("Unexpected exception");
		}
	}

	@Test
	public void testCloseWithMethodInvocationRetryCallbackShouldCallDoCloseMethod() {
		final AtomicInteger callsOnDoCloseMethod = new AtomicInteger(0);
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport() {
			@Override
			protected <T, E extends Throwable> void doClose(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
				callsOnDoCloseMethod.incrementAndGet();
			}
		};
		RetryContext context = mock(RetryContext.class);
		MethodInvocationRetryCallback callback = mock(MethodInvocationRetryCallback.class);
		support.close(context, callback, null);

		assertEquals(1, callsOnDoCloseMethod.get());
	}

	@Test
	public void testCloseWithRetryCallbackShouldntCallDoCloseMethod() {
		final AtomicInteger callsOnDoCloseMethod = new AtomicInteger(0);
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport() {
			@Override
			protected <T, E extends Throwable> void doClose(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
				callsOnDoCloseMethod.incrementAndGet();
			}
		};
		RetryContext context = mock(RetryContext.class);
		RetryCallback callback = mock(RetryCallback.class);
		support.close(context, callback, null);

		assertEquals(0, callsOnDoCloseMethod.get());
	}

	@Test
	public void testOnError() {
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport();
		try {
			support.onError(null, null, null);
		}
		catch (Exception e) {
			fail("Unexpected exception");
		}
	}

	@Test
	public void testOnErrorWithMethodInvocationRetryCallbackShouldCallDoOnErrorMethod() {
		final AtomicInteger callsOnDoOnErrorMethod = new AtomicInteger(0);
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport() {
			@Override
			protected <T, E extends Throwable> void doOnError(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
				callsOnDoOnErrorMethod.incrementAndGet();
			}
		};
		RetryContext context = mock(RetryContext.class);
		MethodInvocationRetryCallback callback = mock(MethodInvocationRetryCallback.class);
		support.onError(context, callback, null);

		assertEquals(1, callsOnDoOnErrorMethod.get());
	}

	@Test
	public void testOpen() {
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport();
		assertTrue(support.open(null, null));
	}

	@Test
	public void testOpenWithMethodInvocationRetryCallbackShouldCallDoCloseMethod() {
		final AtomicInteger callsOnDoOpenMethod = new AtomicInteger(0);
		MethodInvocationRetryListenerSupport support = new MethodInvocationRetryListenerSupport() {
			@Override
			protected <T, E extends Throwable> boolean doOpen(RetryContext context,
					MethodInvocationRetryCallback<T, E> callback) {
				callsOnDoOpenMethod.incrementAndGet();
				return true;
			}
		};
		RetryContext context = mock(RetryContext.class);
		MethodInvocationRetryCallback callback = mock(MethodInvocationRetryCallback.class);
		assertTrue(support.open(context, callback));

		assertEquals(1, callsOnDoOpenMethod.get());
	}

}
