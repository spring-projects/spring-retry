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

package org.springframework.retry.listener;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.TerminatedRetryException;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class RetryListenerTests {

	RetryTemplate template = new RetryTemplate();

	int count = 0;

	List<String> list = new ArrayList<>();

	@Test
	public void testOpenInterceptors() {
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
		template.execute(context -> null);
		assertThat(count).isEqualTo(2);
		assertThat(list).hasSize(2);
		assertThat(list.get(0)).isEqualTo("1:1");
	}

	@Test
	public void testOpenCanVetoRetry() {
		template.registerListener(new RetryListenerSupport() {
			public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
				list.add("1");
				return false;
			}
		});
		try {
			template.execute(context -> {
				count++;
				return null;
			});
			fail("Expected TerminatedRetryException");
		}
		catch (TerminatedRetryException e) {
			// expected
		}
		assertThat(count).isEqualTo(0);
		assertThat(list).hasSize(1);
		assertThat(list.get(0)).isEqualTo("1");
	}

	@Test
	public void testCloseInterceptors() {
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
		template.execute(context -> null);
		assertThat(count).isEqualTo(2);
		assertThat(list).hasSize(2);
		// interceptors are called in reverse order on close...
		assertThat(list.get(0)).isEqualTo("2:1");
	}

	@Test
	public void testOnError() {
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
		assertThatIllegalStateException().isThrownBy(() -> template.execute(context -> {
			count++;
			throw new IllegalStateException("foo");
		})).withMessage("foo");
		// never retry so callback is executed once
		assertThat(count).isEqualTo(1);
		assertThat(list).hasSize(2);
		// interceptors are called in reverse order on error...
		assertThat(list.get(0)).isEqualTo("2");

	}

	@Test
	public void testCloseInterceptorsAfterRetry() {
		template.registerListener(new RetryListenerSupport() {
			public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
					Throwable t) {
				list.add("" + count);
				// The last attempt should have been successful:
				assertNull(t);
			}
		});
		template.execute(context -> {
			if (count++ < 1)
				throw new RuntimeException("Retry!");
			return null;
		});
		assertThat(count).isEqualTo(2);
		// The close interceptor was only called once:
		assertThat(list).hasSize(1);
		// We succeeded on the second try:
		assertThat(list.get(0)).isEqualTo("2");
	}

}
