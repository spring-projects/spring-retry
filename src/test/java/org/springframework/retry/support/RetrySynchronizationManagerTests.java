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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.retry.RetryContext;
import org.springframework.retry.context.RetryContextSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Gary Russell
 */
public class RetrySynchronizationManagerTests {

	RetryTemplate template = new RetryTemplate();

	@BeforeEach
	public void setUp() {
		RetrySynchronizationManagerTests.clearAll();
		RetryContext status = RetrySynchronizationManager.getContext();
		assertThat(status).isNull();
	}

	@Test
	public void testStatusIsStoredByTemplate() {

		RetryContext status = RetrySynchronizationManager.getContext();
		assertThat(status).isNull();

		this.template.execute(retryContext -> {
			RetryContext global = RetrySynchronizationManager.getContext();
			assertThat(retryContext).isNotNull();
			assertThat(retryContext).isEqualTo(global);
			return null;
		});

		status = RetrySynchronizationManager.getContext();
		assertThat(status).isNull();
	}

	@Test
	public void testStatusRegistration() {
		RetryContext status = new RetryContextSupport(null);
		RetryContext value = RetrySynchronizationManager.register(status);
		assertThat(value).isNull();
		value = RetrySynchronizationManager.register(status);
		assertThat(value).isEqualTo(status);
	}

	@Test
	public void testClear() {
		RetryContext status = new RetryContextSupport(null);
		RetryContext value = RetrySynchronizationManager.register(status);
		assertThat(value).isNull();
		RetrySynchronizationManager.clear();
		value = RetrySynchronizationManager.register(status);
		assertThat(value).isNull();
	}

	@Test
	public void testParent() {
		RetryContext parent = new RetryContextSupport(null);
		RetryContext child = new RetryContextSupport(parent);
		assertThat(child.getParent()).isSameAs(parent);
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
