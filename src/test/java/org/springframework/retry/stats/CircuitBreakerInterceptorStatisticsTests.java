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

package org.springframework.retry.stats;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryStatistics;
import org.springframework.retry.annotation.CircuitBreaker;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.support.RetrySynchronizationManager;

/**
 * @author Dave Syer
 *
 */
public class CircuitBreakerInterceptorStatisticsTests {

	private static final String RECOVERED = "RECOVERED";
	private static final String RESULT = "RESULT";
	private Service callback;
	private StatisticsRepository repository;
	private AnnotationConfigApplicationContext context;

	@Before
	public void init() {
		context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		this.callback = context.getBean(Service.class);
		this.repository = context.getBean(StatisticsRepository.class);
		this.callback.setAttemptsBeforeSuccess(1);
	}

	@After
	public void close() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void testCircuitOpenWhenNotRetryable() throws Throwable {
		Object result = callback.service("one");
		RetryStatistics stats = repository.findOne("test");
		// System.err.println(stats);
		assertEquals(1, stats.getStartedCount());
		assertEquals(RECOVERED, result);
		result = callback.service("two");
		assertEquals(RECOVERED, result);
		assertEquals("There should be two recoveries", 2, stats.getRecoveryCount());
		assertEquals("There should only be one error because the circuit is now open", 1,
				stats.getErrorCount());
	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public StatisticsRepository repository() {
			return new DefaultStatisticsRepository();
		}

		@Bean
		public StatisticsListener listener(StatisticsRepository repository) {
			return new StatisticsListener(repository);
		}

		@Bean
		public Service service() {
			return new Service();
		}
	}

	protected static class Service {

		private int attemptsBeforeSuccess;

		private Exception exceptionToThrow = new Exception();

		private RetryContext status;

		@CircuitBreaker(label = "test", maxAttempts = 1)
		public Object service(String input) throws Exception {
			this.status = RetrySynchronizationManager.getContext();
			Integer attempts = (Integer) status.getAttribute("attempts");
			if (attempts == null) {
				attempts = 0;
			}
			attempts++;
			this.status.setAttribute("attempts", attempts);
			if (attempts <= this.attemptsBeforeSuccess) {
				throw this.exceptionToThrow;
			}
			return RESULT;
		}

		@Recover
		public Object recover() {
			this.status.setAttribute(RECOVERED, true);
			return RECOVERED;
		}

		public boolean isOpen() {
			return this.status != null
					&& this.status.getAttribute("open") == Boolean.TRUE;
		}

		public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
			this.attemptsBeforeSuccess = attemptsBeforeSuccess;
		}

		public void setExceptionToThrow(Exception exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}
	}

}
