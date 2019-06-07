/*
 * Copyright 2015-2019 the original author or authors.
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

import java.util.Map;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.Test;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.support.RetrySynchronizationManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class CircuitBreakerTests {

	@Test
	public void vanilla() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		assertTrue(AopUtils.isAopProxy(service));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertFalse((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertFalse((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		assertTrue((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN));
		assertEquals(3, service.getCount());
		try {
			service.service();
			fail("Expected exception");
		}
		catch (Exception e) {
		}
		// Not called again once circuit is open
		assertEquals(3, service.getCount());
		service.expressionService();
		assertEquals(4, service.getCount());
		Advised advised = (Advised) service;
		Advisor advisor = advised.getAdvisors()[0];
		Map<?, ?> delegates = (Map<?, ?>) new DirectFieldAccessor(advisor).getPropertyValue("advice.delegates");
		assertTrue(delegates.size() == 1);
		Map<?, ?> methodMap = (Map<?, ?>) delegates.values().iterator().next();
		MethodInterceptor interceptor = (MethodInterceptor) methodMap
				.get(Service.class.getDeclaredMethod("expressionService"));
		DirectFieldAccessor accessor = new DirectFieldAccessor(interceptor);
		assertEquals(8, accessor.getPropertyValue("retryOperations.retryPolicy.delegate.maxAttempts"));
		assertEquals(19000L, accessor.getPropertyValue("retryOperations.retryPolicy.openTimeout"));
		assertEquals(20000L, accessor.getPropertyValue("retryOperations.retryPolicy.resetTimeout"));
		assertEquals("#root instanceof RuntimeExpression",
				accessor.getPropertyValue("retryOperations.retryPolicy.delegate.expression.expression"));
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

		@CircuitBreaker(maxAttemptsExpression = "#{2 * ${foo:4}}", openTimeoutExpression = "#{${bar:19}000}",
				resetTimeoutExpression = "#{${baz:20}000}",
				exceptionExpression = "#{#root instanceof RuntimeExpression}")
		public void expressionService() {
			this.count++;
		}

		public RetryContext getContext() {
			return this.context;
		}

		public int getCount() {
			return this.count;
		}

	}

}
