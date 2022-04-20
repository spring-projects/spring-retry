/*
 * Copyright 2015-2022 the original author or authors.
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
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
		assertThat(AopUtils.isAopProxy(service)).isTrue();
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		assertThat((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isFalse();
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		assertThat((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isFalse();
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		assertThat((Boolean) service.getContext().getAttribute(CircuitBreakerRetryPolicy.CIRCUIT_OPEN)).isTrue();
		assertThat(service.getCount()).isEqualTo(3);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		// Not called again once circuit is open
		assertThat(service.getCount()).isEqualTo(3);
		service.expressionService();
		assertThat(service.getCount()).isEqualTo(4);
		service.expressionService2();
		assertThat(service.getCount()).isEqualTo(5);
		Advised advised = (Advised) service;
		Advisor advisor = advised.getAdvisors()[0];
		Map<?, ?> delegates = (Map<?, ?>) new DirectFieldAccessor(advisor).getPropertyValue("advice.delegates");
		assertThat(delegates).hasSize(1);
		Map<?, ?> methodMap = (Map<?, ?>) delegates.values().iterator().next();
		MethodInterceptor interceptor = (MethodInterceptor) methodMap
				.get(Service.class.getDeclaredMethod("expressionService"));
		DirectFieldAccessor accessor = new DirectFieldAccessor(interceptor);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.delegate.maxAttempts")).isEqualTo(8);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.openTimeout")).isEqualTo(19000L);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.resetTimeout")).isEqualTo(20000L);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.delegate.expression.expression"))
				.isEqualTo("#root instanceof RuntimeExpression");

		interceptor = (MethodInterceptor) methodMap.get(Service.class.getDeclaredMethod("expressionService2"));
		accessor = new DirectFieldAccessor(interceptor);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.delegate.maxAttempts")).isEqualTo(10);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.openTimeout")).isEqualTo(10000L);
		assertThat(accessor.getPropertyValue("retryOperations.retryPolicy.resetTimeout")).isEqualTo(20000L);
		context.close();
	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public Service service() {
			return new ServiceImpl();
		}

		@Bean
		Configs configs() {
			return new Configs();
		}

	}

	public static class Configs {

		public int maxAttempts = 10;

		public long openTimeout = 10000;

		public long resetTimeout = 20000;

	}

	interface Service {

		void service();

		void expressionService();

		void expressionService2();

		int getCount();

		RetryContext getContext();

	}

	protected static class ServiceImpl implements Service {

		private int count = 0;

		private RetryContext context;

		@Override
		@CircuitBreaker(RuntimeException.class)
		public void service() {
			this.context = RetrySynchronizationManager.getContext();
			if (this.count++ < 5) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		@CircuitBreaker(maxAttemptsExpression = "#{2 * ${foo:4}}", openTimeoutExpression = "#{${bar:19}000}",
				resetTimeoutExpression = "#{${baz:20}000}",
				exceptionExpression = "#{#root instanceof RuntimeExpression}")
		public void expressionService() {
			this.count++;
		}

		@Override
		@CircuitBreaker(maxAttemptsExpression = "@configs.maxAttempts", openTimeoutExpression = "@configs.openTimeout",
				resetTimeoutExpression = "@configs.resetTimeout")
		public void expressionService2() {
			this.count++;
		}

		@Override
		public RetryContext getContext() {
			return this.context;
		}

		@Override
		public int getCount() {
			return this.count;
		}

	}

}
