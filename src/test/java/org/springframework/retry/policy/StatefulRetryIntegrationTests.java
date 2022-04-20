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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryState;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class StatefulRetryIntegrationTests {

	@Test
	public void testExternalRetryWithFailAndNoRetry() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();

		RetryState retryState = new DefaultRetryState("foo");

		RetryTemplate retryTemplate = new RetryTemplate();
		MapRetryContextCache cache = new MapRetryContextCache();
		retryTemplate.setRetryContextCache(cache);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));

		assertThat(cache.containsKey("foo")).isFalse();

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> retryTemplate.execute(callback, retryState))
				.withMessage(null);

		assertThat(cache.containsKey("foo")).isTrue();

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> retryTemplate.execute(callback, retryState))
				.withMessageContaining("exhausted");

		assertThat(cache.containsKey("foo")).isFalse();

		// Callback is called once: the recovery path should be called in
		// handleRetryExhausted (so not in this test)...
		assertThat(callback.attempts).isEqualTo(1);
	}

	@Test
	public void testExternalRetryWithSuccessOnRetry() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();

		RetryState retryState = new DefaultRetryState("foo");

		RetryTemplate retryTemplate = new RetryTemplate();
		MapRetryContextCache cache = new MapRetryContextCache();
		retryTemplate.setRetryContextCache(cache);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));

		assertThat(cache.containsKey("foo")).isFalse();

		Object result = "start_foo";
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> retryTemplate.execute(callback, retryState))
				.withMessage(null);

		assertThat(cache.containsKey("foo")).isTrue();

		result = retryTemplate.execute(callback, retryState);

		assertThat(cache.containsKey("foo")).isFalse();

		assertThat(callback.attempts).isEqualTo(2);
		assertThat(callback.context.getRetryCount()).isEqualTo(1);
		assertThat(result).isEqualTo("bar");
	}

	@Test
	public void testExternalRetryWithSuccessOnRetryAndSerializedContext() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();

		RetryState retryState = new DefaultRetryState("foo");

		RetryTemplate retryTemplate = new RetryTemplate();
		RetryContextCache cache = new SerializedMapRetryContextCache();
		retryTemplate.setRetryContextCache(cache);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));

		assertThat(cache.containsKey("foo")).isFalse();

		Object result = "start_foo";
		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> retryTemplate.execute(callback, retryState))
				.withMessage(null);

		assertThat(cache.containsKey("foo")).isTrue();

		result = retryTemplate.execute(callback, retryState);

		assertThat(cache.containsKey("foo")).isFalse();

		assertThat(callback.attempts).isEqualTo(2);
		assertThat(callback.context.getRetryCount()).isEqualTo(1);
		assertThat(result).isEqualTo("bar");
	}

	@Test
	public void testExponentialBackOffIsExponential() {
		ExponentialBackOffPolicy policy = new ExponentialBackOffPolicy();
		policy.setInitialInterval(100);
		policy.setMultiplier(1.5);
		RetryTemplate template = new RetryTemplate();
		template.setBackOffPolicy(policy);
		final List<Long> times = new ArrayList<>();
		RetryState retryState = new DefaultRetryState("bar");
		for (int i = 0; i < 3; i++) {
			try {
				template.execute(context -> {
					times.add(System.currentTimeMillis());
					throw new Exception("Fail");
				}, context -> null, retryState);
			}
			catch (Exception e) {
				assertThat(e.getMessage().equals("Fail")).isTrue();
			}
		}
		assertThat(times).hasSize(3);
		assertThat(times.get(1) - times.get(0) >= 100).isTrue();
		assertThat(times.get(2) - times.get(1) >= 150).isTrue();
	}

	@Test
	public void testExternalRetryWithFailAndNoRetryWhenKeyIsNull() throws Throwable {
		MockRetryCallback callback = new MockRetryCallback();

		RetryState retryState = new DefaultRetryState(null);

		RetryTemplate retryTemplate = new RetryTemplate();
		MapRetryContextCache cache = new MapRetryContextCache();
		retryTemplate.setRetryContextCache(cache);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> retryTemplate.execute(callback, retryState))
				.withMessage(null);

		retryTemplate.execute(callback, retryState);
		// The second attempt is successful by design...

		// Callback is called twice because its state is null: the recovery path should
		// not be called...
		assertThat(callback.attempts).isEqualTo(2);
	}

	/**
	 * @author Dave Syer
	 *
	 */
	private static final class MockRetryCallback implements RetryCallback<String, Exception> {

		int attempts = 0;

		RetryContext context;

		public String doWithRetry(RetryContext context) {
			attempts++;
			this.context = context;
			if (attempts < 2) {
				throw new RuntimeException();
			}
			return "bar";
		}

	}

}
