/*
 * Copyright 2006-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.retry.backoff.Sleeper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Artem Bilan
 *
 */
public class EnableRetryWithBackoffTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		service.service();
		assertThat(context.getBean(PeriodSleeper.class).getPeriods().toString()).isEqualTo("[1000, 1000]");
		assertThat(service.getCount()).isEqualTo(3);
		context.close();
	}

	@Test
	public void type() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		RandomService service = context.getBean(RandomService.class);
		service.service();
		List<Long> periods = context.getBean(PeriodSleeper.class).getPeriods();
		assertThat(periods.get(0)).describedAs("Wrong periods: %s" + periods).isGreaterThan(1000);
		assertThat(service.getCount()).isEqualTo(3);
		context.close();
	}

	@Test
	public void exponential() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExponentialService service = context.getBean(ExponentialService.class);
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		assertThat(context.getBean(PeriodSleeper.class).getPeriods().toString()).isEqualTo("[1000, 1100]");
		context.close();
	}

	@Test
	public void randomExponential() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExponentialRandomService service = context.getBean(ExponentialRandomService.class);
		service.service(1);
		assertThat(service.getCount()).isEqualTo(3);
		List<Long> periods = context.getBean(PeriodSleeper.class).getPeriods();
		assertThat(context.getBean(PeriodSleeper.class).getPeriods().toString()).isNotEqualTo("[1000, 1100]");
		assertThat(periods.get(0)).describedAs("Wrong periods: %s" + periods).isGreaterThanOrEqualTo(1000);
		assertThat(periods.get(1)).describedAs("Wrong periods: %s" + periods).isBetween(1100L, 1210L);
		context.close();
	}

	@Test
	public void randomExponentialExpression() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExponentialRandomExpressionService service = context.getBean(ExponentialRandomExpressionService.class);
		service.service(1);
		assertThat(service.getCount()).isEqualTo(3);
		List<Long> periods = context.getBean(PeriodSleeper.class).getPeriods();
		assertThat(context.getBean(PeriodSleeper.class).getPeriods().toString()).isNotEqualTo("[1000, 1100]");
		assertThat(periods.get(0)).describedAs("Wrong periods: %s" + periods).isGreaterThanOrEqualTo(1000);
		assertThat(periods.get(1)).describedAs("Wrong periods: %s" + periods).isBetween(1100L, 1210L);
		context.close();
	}

	@Configuration
	@EnableRetry
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	protected static class TestConfiguration {

		@Bean
		public PeriodSleeper sleeper() {
			return new PeriodSleeper();
		}

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RandomService retryable() {
			return new RandomService();
		}

		@Bean
		public ExponentialRandomService stateful() {
			return new ExponentialRandomService();
		}

		@Bean
		public ExponentialService excludes() {
			return new ExponentialService();
		}

		@Bean
		public ExponentialRandomExpressionService statefulExpression() {
			return new ExponentialRandomExpressionService();
		}

	}

	@SuppressWarnings("serial")
	protected static class PeriodSleeper implements Sleeper {

		private final List<Long> periods = new ArrayList<>();

		@Override
		public void sleep(long period) {
			periods.add(period);
		}

		private List<Long> getPeriods() {
			return periods;
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000))
		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	@Retryable(backoff = @Backoff(delay = 1000, maxDelay = 2000))
	protected static class RandomService {

		private int count = 0;

		public void service() {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class ExponentialService {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000, maxDelay = 2000, multiplier = 1.1))
		public void service() {
			if (count++ < 2) {
				throw new IllegalStateException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class ExponentialRandomService {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000, maxDelay = 2000, multiplier = 1.1, random = true))
		public void service(int value) {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

	protected static class ExponentialRandomExpressionService {

		private int count = 0;

		@Retryable(backoff = @Backoff(delay = 1000, maxDelay = 2000, multiplier = 1.1, randomExpression = "#{true}"))
		public void service(int value) {
			if (count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return count;
		}

	}

}
