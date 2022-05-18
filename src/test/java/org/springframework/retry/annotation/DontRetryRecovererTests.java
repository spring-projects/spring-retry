/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.retry.annotation;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 1.3.4
 */
@RunWith(SpringRunner.class)
public class DontRetryRecovererTests {

	@Autowired
	Service service;

	@Test
	public void dontRetry() {
		try {
			this.service.foo("x");
			fail("Exception expected");
		}
		catch (Exception e) {
			assertEquals("test", e.getMessage());
		}

		assertEquals(3, this.service.getCallCount());
		assertEquals(1, this.service.getRecoverCount());
	}

	@Configuration
	@EnableRetry
	public static class Config {

		@Bean
		Service service() {
			return new Service();
		}

	}

	@Retryable
	public static class Service {

		int callCount;

		int recoverCount;

		public void foo(String in) {
			callCount++;
			throw new RuntimeException();
		}

		@Recover
		public void recover(Exception ex, String in) {
			this.recoverCount++;
			throw new RuntimeException("test");
		}

		public int getCallCount() {
			return callCount;
		}

		public int getRecoverCount() {
			return recoverCount;
		}

	}

}
