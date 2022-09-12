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

package org.springframework.retry.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryException;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.RetryState;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class StatefulRecoveryRetryTests {

	private final RetryTemplate retryTemplate = new RetryTemplate();

	private int count = 0;

	private final List<String> list = new ArrayList<>();

	@Test
	public void testOpenSunnyDay() {
		RetryContext context = this.retryTemplate.open(new NeverRetryPolicy(), new DefaultRetryState("foo"));
		assertThat(context).isNotNull();
		// we haven't called the processor yet...
		assertThat(this.count).isEqualTo(0);
	}

	@Test
	public void testRegisterThrowable() {
		NeverRetryPolicy retryPolicy = new NeverRetryPolicy();
		RetryState state = new DefaultRetryState("foo");
		RetryContext context = this.retryTemplate.open(retryPolicy, state);
		assertThat(context).isNotNull();
		this.retryTemplate.registerThrowable(retryPolicy, state, context, new Exception());
		assertThat(retryPolicy.canRetry(context)).isFalse();
	}

	@Test
	public void testClose() {
		NeverRetryPolicy retryPolicy = new NeverRetryPolicy();
		RetryState state = new DefaultRetryState("foo");
		RetryContext context = this.retryTemplate.open(retryPolicy, state);
		assertThat(context).isNotNull();
		this.retryTemplate.registerThrowable(retryPolicy, state, context, new Exception());
		assertThat(retryPolicy.canRetry(context)).isFalse();
		this.retryTemplate.close(retryPolicy, context, state, true);
		// still can't retry, even if policy is closed
		// (not that this would happen in practice)...
		assertThat(retryPolicy.canRetry(context)).isFalse();
	}

	@Test
	public void testRecover() throws Throwable {
		this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
		final String input = "foo";
		RetryState state = new DefaultRetryState(input);
		RetryCallback<String, Exception> callback = context -> {
			throw new RuntimeException("Barf!");
		};
		RecoveryCallback<String> recoveryCallback = context -> {
			StatefulRecoveryRetryTests.this.count++;
			StatefulRecoveryRetryTests.this.list.add(input);
			return input;
		};
		Object result = null;
		assertThatExceptionOfType(Exception.class)
				.isThrownBy(() -> this.retryTemplate.execute(callback, recoveryCallback, state));
		// On the second retry, the recovery path is taken...
		result = this.retryTemplate.execute(callback, recoveryCallback, state);
		assertThat(result).isEqualTo(input); // default result is the item
		assertThat(this.count).isEqualTo(1);
		assertThat(this.list.get(0)).isEqualTo(input);
	}

	@Test
	public void testSwitchToStatelessForNoRollback() throws Throwable {
		this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
		// Roll back for these:
		BinaryExceptionClassifier classifier = new BinaryExceptionClassifier(
				Collections.<Class<? extends Throwable>>singleton(DataAccessException.class));
		// ...but not these:
		assertThat(classifier.classify(new RuntimeException())).isFalse();
		final String input = "foo";
		RetryState state = new DefaultRetryState(input, classifier);
		RetryCallback<String, Exception> callback = context -> {
			throw new RuntimeException("Barf!");
		};
		RecoveryCallback<String> recoveryCallback = context -> {
			StatefulRecoveryRetryTests.this.count++;
			StatefulRecoveryRetryTests.this.list.add(input);
			return input;
		};
		Object result = null;
		// On the second retry, the recovery path is taken...
		result = this.retryTemplate.execute(callback, recoveryCallback, state);
		assertThat(result).isEqualTo(input); // default result is the item
		assertThat(this.count).isEqualTo(1);
		assertThat(this.list.get(0)).isEqualTo(input);
	}

	@Test
	public void testExhaustedClearsHistoryAfterLastAttempt() throws Throwable {
		RetryPolicy retryPolicy = new SimpleRetryPolicy(1);
		this.retryTemplate.setRetryPolicy(retryPolicy);

		final String input = "foo";
		RetryState state = new DefaultRetryState(input);
		RetryCallback<String, Exception> callback = context -> {
			throw new RuntimeException("Barf!");
		};

		assertThatExceptionOfType(Exception.class).isThrownBy(() -> this.retryTemplate.execute(callback, state))
				.withMessage("Barf!");
		assertThatExceptionOfType(ExhaustedRetryException.class)
				.isThrownBy(() -> this.retryTemplate.execute(callback, state));

		RetryContext context = this.retryTemplate.open(retryPolicy, state);
		// True after exhausted - the history is reset...
		assertThat(retryPolicy.canRetry(context)).isTrue();
	}

	@Test
	public void testKeyGeneratorNotConsistentAfterFailure() throws Throwable {

		RetryPolicy retryPolicy = new SimpleRetryPolicy(3);
		this.retryTemplate.setRetryPolicy(retryPolicy);
		final StringHolder item = new StringHolder("bar");
		RetryState state = new DefaultRetryState(item);

		RetryCallback<StringHolder, Exception> callback = context -> {
			// This simulates what happens if someone uses a primary key
			// for hashCode and equals and then relies on default key
			// generator
			item.string = item.string + (StatefulRecoveryRetryTests.this.count++);
			throw new RuntimeException("Barf!");
		};

		assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> this.retryTemplate.execute(callback, state))
				.withMessage("Barf!");
		// Only fails second attempt because the algorithm to detect
		// inconsistent has codes relies on the cache having been used for this
		// item already...
		assertThatExceptionOfType(RetryException.class).isThrownBy(() -> this.retryTemplate.execute(callback, state))
				.withStackTraceContaining("inconsistent");

		RetryContext context = this.retryTemplate.open(retryPolicy, state);
		// True after exhausted - the history is reset...
		assertThat(context.getRetryCount()).isEqualTo(0);

	}

	@Test
	public void testCacheCapacity() throws Throwable {

		this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1));
		this.retryTemplate.setRetryContextCache(new MapRetryContextCache(1));

		RetryCallback<Object, Exception> callback = context -> {
			StatefulRecoveryRetryTests.this.count++;
			throw new RuntimeException("Barf!");
		};

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> this.retryTemplate.execute(callback, new DefaultRetryState("foo")))
				.withMessage("Barf!");

		assertThatExceptionOfType(RetryException.class)
				.isThrownBy(() -> this.retryTemplate.execute(callback, new DefaultRetryState("bar")))
				.withStackTraceContaining("capacity");
	}

	@Test
	public void testCacheCapacityNotReachedIfRecovered() throws Throwable {

		SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(1);
		this.retryTemplate.setRetryPolicy(retryPolicy);
		this.retryTemplate.setRetryContextCache(new MapRetryContextCache(2));
		final StringHolder item = new StringHolder("foo");
		RetryState state = new DefaultRetryState(item);

		RetryCallback<Object, Exception> callback = context -> {
			StatefulRecoveryRetryTests.this.count++;
			throw new RuntimeException("Barf!");
		};
		RecoveryCallback<Object> recoveryCallback = context -> null;

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> this.retryTemplate.execute(callback, recoveryCallback, state)).withMessage("Barf!");
		this.retryTemplate.execute(callback, recoveryCallback, state);

		RetryContext context = this.retryTemplate.open(retryPolicy, state);
		// True after exhausted - the history is reset...
		assertThat(context.getRetryCount()).isEqualTo(0);

	}

	private static class StringHolder {

		private String string;

		public StringHolder(String string) {
			this.string = string;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof StringHolder)) {
				return false;
			}
			return this.string.equals(((StringHolder) obj).string);
		}

		@Override
		public int hashCode() {
			return this.string.hashCode();
		}

		@Override
		public String toString() {
			return "String: " + this.string + " (hash = " + hashCode() + ")";
		}

	}

}
