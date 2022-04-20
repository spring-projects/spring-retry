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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class FatalExceptionRetryPolicyTests {

	@Test
	public void testFatalExceptionWithoutState() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		callback.setExceptionToThrow(new IllegalArgumentException());

		RetryTemplate retryTemplate = new RetryTemplate();

		// Make sure certain exceptions are fatal...
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
		map.put(IllegalArgumentException.class, false);
		map.put(IllegalStateException.class, false);

		// ... and allow multiple attempts
		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map);
		retryTemplate.setRetryPolicy(policy);
		RecoveryCallback<String> recoveryCallback = context -> "bar";

		AtomicReference<Object> result = new AtomicReference<>();
		assertThatNoException().isThrownBy(() -> result.set(retryTemplate.execute(callback, recoveryCallback)));
		// Callback is called once: the recovery path should also be called
		assertThat(callback.attempts).isEqualTo(1);
		assertThat(result.get()).isEqualTo("bar");
	}

	@Test
	public void testFatalExceptionWithState() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();
		callback.setExceptionToThrow(new IllegalArgumentException());

		RetryTemplate retryTemplate = new RetryTemplate();

		Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
		map.put(IllegalArgumentException.class, false);
		map.put(IllegalStateException.class, false);

		SimpleRetryPolicy policy = new SimpleRetryPolicy(3, map);
		retryTemplate.setRetryPolicy(policy);

		RecoveryCallback<String> recoveryCallback = context -> "bar";

		Object result = null;
		assertThatIllegalArgumentException()
				.isThrownBy(() -> retryTemplate.execute(callback, recoveryCallback, new DefaultRetryState("foo")));
		result = retryTemplate.execute(callback, recoveryCallback, new DefaultRetryState("foo"));
		// Callback is called once: the recovery path should also be called
		assertThat(callback.attempts).isEqualTo(1);
		assertThat(result).isEqualTo("bar");
	}

	private static class MockRetryCallback implements RetryCallback<String, Exception> {

		private int attempts;

		private Exception exceptionToThrow = new Exception();

		public String doWithRetry(RetryContext context) throws Exception {
			this.attempts++;
			// Just barf...
			throw this.exceptionToThrow;
		}

		public void setExceptionToThrow(Exception exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

}
