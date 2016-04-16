/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetrySynchronizationManager;

/**
 * @author Dave Syer
 *
 */
public class CircuitBreakerTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestConfiguration.class);
		Service service = context.getBean(Service.class);
		assertTrue(AopUtils.isAopProxy(service));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertFalse((Boolean)service.getContext().getAttribute("open"));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertFalse((Boolean)service.getContext().getAttribute("open"));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertTrue((Boolean)service.getContext().getAttribute("open"));
		assertEquals(3, service.getCount());
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		// Not called again once circuit is open
		assertEquals(3, service.getCount());
		context.close();
	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public Service service() {
			return new Service();
		}

	}

	protected static class Service {

		private int count = 0;

		private RetryContext context;

		@CircuitBreaker(RuntimeException.class)
		public void service() {
			this.context = RetrySynchronizationManager.getContext();
			if (this.count++ < 5) {
				throw new RuntimeException("Planned");
			}
		}

		public RetryContext getContext() {
			return this.context;
		}

		public int getCount() {
			return this.count;
		}

	}

}
