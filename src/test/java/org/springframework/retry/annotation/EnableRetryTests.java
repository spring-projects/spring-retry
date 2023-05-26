/*
 * Copyright 2006-2023 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.Ordered;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.setMaxStackTraceElementsDisplayed;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @author Aldo Sinanaj
 * @author Henning Pöttker
 * @author Yanming Zhou
 * @since 1.1
 */
public class EnableRetryTests {

	@Test
	public void vanilla() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		Foo foo = context.getBean(Foo.class);
		assertThat(AopUtils.isAopProxy(foo)).isFalse();
		assertThat(AopUtils.isAopProxy(service)).isTrue();
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		TestConfiguration config = context.getBean(TestConfiguration.class);
		assertThat(config.listener1).isTrue();
		assertThat(config.listener2).isTrue();
		assertThat(config.twoFirst).isTrue();
		context.close();
	}

	@Test
	public void multipleMethods() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		MultiService service = context.getBean(MultiService.class);
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		service.other();
		assertThat(service.getCount()).isEqualTo(4);
		setMaxStackTraceElementsDisplayed(100);
		assertThatIllegalArgumentException().isThrownBy(() -> service.conditional("foo"));
		assertThat(service.getCount()).isEqualTo(7);
		assertThatIllegalArgumentException().isThrownBy(() -> service.conditional("bar"));
		assertThat(service.getCount()).isEqualTo(8);
		context.close();
	}

	@Test
	public void proxyTargetClass() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestProxyConfiguration.class);
		Service service = context.getBean(Service.class);
		assertThat(AopUtils.isCglibProxy(service)).isTrue();
		RecoverableService recoverable = context.getBean(RecoverableService.class);
		recoverable.service();
		assertThat(recoverable.isOtherAdviceCalled()).isTrue();
		context.close();
	}

	@Test
	public void order() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				TestOrderConfiguration.class);
		RetryConfiguration config = context.getBean(RetryConfiguration.class);
		assertThat(config.getOrder()).isEqualTo(1);
		context.close();
	}

	@Test
	public void marker() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		Service service = context.getBean(Service.class);
		assertThat(AopUtils.isCglibProxy(service)).isTrue();
		assertThat(service instanceof org.springframework.retry.interceptor.Retryable).isTrue();
		context.close();
	}

	@Test
	public void recovery() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		RecoverableService service = context.getBean(RecoverableService.class);
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		assertThat(service.getCause()).isExactlyInstanceOf(RuntimeException.class);
		assertThatIllegalArgumentException().isThrownBy(() -> service.service());
		assertThat(service.getCount()).isEqualTo(6);
		assertThat(service.getCause()).isExactlyInstanceOf(RuntimeException.class);
		assertThatIllegalStateException().isThrownBy(() -> service.service());
		assertThat(service.getCount()).isEqualTo(7);
		assertThat(service.getCause()).isExactlyInstanceOf(RuntimeException.class);
		context.close();
	}

	@Test
	public void type() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		RetryableService service = context.getBean(RetryableService.class);
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		context.close();
	}

	@Test
	public void excludes() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExcludesService service = context.getBean(ExcludesService.class);
		assertThatIllegalStateException().isThrownBy(() -> service.service());
		assertThat(service.getCount()).isEqualTo(1);
		context.close();
	}

	@Test
	public void excludesOnly() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExcludesOnlyService service = context.getBean(ExcludesOnlyService.class);
		service.setExceptionToThrow(new IllegalStateException());
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service());
		assertThat(service.getCount()).isEqualTo(1);

		service.setExceptionToThrow(new IllegalArgumentException());
		service.service();
		assertThat(service.getCount()).isEqualTo(3);
		context.close();
	}

	@Test
	public void stateful() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		StatefulService service = context.getBean(StatefulService.class);
		for (int i = 0; i < 3; i++) {
			try {
				service.service(1);
			}
			catch (Exception e) {
				assertThat(e.getMessage()).isEqualTo("Planned");
			}
		}
		assertThat(service.getCount()).isEqualTo(3);
		context.close();
	}

	@Test
	public void testExternalInterceptor() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		InterceptableService service = context.getBean(InterceptableService.class);
		service.service();
		assertThat(service.getCount()).isEqualTo(5);
		context.close();
	}

	@Test
	public void testInterface() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		TheInterface service = context.getBean(TheInterface.class);
		service.service1();
		service.service2();
		assertThat(service.getCount()).isEqualTo(4);
		service.service3();
		assertThat(service.isRecovered()).isTrue();
		context.close();
	}

	@Test
	public void testInterfaceWithNoRecover() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		NoRecoverInterface service = context.getBean(NoRecoverInterface.class);
		service.service();
		assertThat(service.isRecovered()).isTrue();
	}

	@Test
	public void testImplementation() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		NotAnnotatedInterface service = context.getBean(NotAnnotatedInterface.class);
		service.service1();
		service.service2();
		assertThat(service.getCount()).isEqualTo(5);
		context.close();
	}

	@Test
	public void testExpression() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExpressionService service = context.getBean(ExpressionService.class);
		service.service1();
		assertThat(service.getCount()).isEqualTo(3);
		assertThatExceptionOfType(Exception.class).isThrownBy(() -> service.service2());
		assertThat(service.getCount()).isEqualTo(4);
		service.service3();
		assertThat(service.getCount()).isEqualTo(9);
		RetryConfiguration config = context.getBean(RetryConfiguration.class);
		AnnotationAwareRetryOperationsInterceptor advice = (AnnotationAwareRetryOperationsInterceptor) new DirectFieldAccessor(
				config)
			.getPropertyValue("advice");
		@SuppressWarnings("unchecked")
		Map<Object, Map<Method, MethodInterceptor>> delegates = (Map<Object, Map<Method, MethodInterceptor>>) new DirectFieldAccessor(
				advice)
			.getPropertyValue("delegates");
		MethodInterceptor interceptor = delegates.get(target(service))
			.get(ExpressionService.class.getDeclaredMethod("service3"));
		RetryTemplate template = (RetryTemplate) new DirectFieldAccessor(interceptor)
			.getPropertyValue("retryOperations");
		DirectFieldAccessor templateAccessor = new DirectFieldAccessor(template);
		ExponentialBackOffPolicy backOff = (ExponentialBackOffPolicy) templateAccessor
			.getPropertyValue("backOffPolicy");
		assertThat(backOff.getInitialInterval()).isEqualTo(1);
		assertThat(backOff.getMaxInterval()).isEqualTo(5);
		assertThat(backOff.getMultiplier()).isEqualTo(1.1);
		SimpleRetryPolicy retryPolicy = (SimpleRetryPolicy) templateAccessor.getPropertyValue("retryPolicy");
		assertThat(retryPolicy.getMaxAttempts()).isEqualTo(5);
		service.service4();
		assertThat(service.getCount()).isEqualTo(11);
		interceptor = delegates.get(target(service)).get(ExpressionService.class.getDeclaredMethod("service4"));
		template = (RetryTemplate) new DirectFieldAccessor(interceptor).getPropertyValue("retryOperations");
		templateAccessor = new DirectFieldAccessor(template);
		FixedBackOffPolicy fbp = (FixedBackOffPolicy) templateAccessor.getPropertyValue("backOffPolicy");
		assertThat(fbp.getBackOffPeriod()).isEqualTo(5000L);
		service.service5();
		assertThat(service.getCount()).isEqualTo(12);
		context.close();
	}

	@Test
	void runtimeExpressions() throws Exception {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfiguration.class);
		ExpressionService service = context.getBean(ExpressionService.class);
		service.service6();
		RuntimeConfigs runtime = context.getBean(RuntimeConfigs.class);
		verify(runtime, times(5)).getMaxAttempts();
		verify(runtime, times(2)).getInitial();
		verify(runtime, times(2)).getMax();
		verify(runtime, times(2)).getMult();

		RetryConfiguration config = context.getBean(RetryConfiguration.class);
		AnnotationAwareRetryOperationsInterceptor advice = (AnnotationAwareRetryOperationsInterceptor) new DirectFieldAccessor(
				config)
			.getPropertyValue("advice");
		@SuppressWarnings("unchecked")
		Map<Object, Map<Method, MethodInterceptor>> delegates = (Map<Object, Map<Method, MethodInterceptor>>) new DirectFieldAccessor(
				advice)
			.getPropertyValue("delegates");
		MethodInterceptor interceptor = delegates.get(target(service))
			.get(ExpressionService.class.getDeclaredMethod("service6"));
		RetryTemplate template = (RetryTemplate) new DirectFieldAccessor(interceptor)
			.getPropertyValue("retryOperations");
		DirectFieldAccessor templateAccessor = new DirectFieldAccessor(template);
		ExponentialBackOffPolicy backOff = (ExponentialBackOffPolicy) templateAccessor
			.getPropertyValue("backOffPolicy");
		assertThat(backOff.getInitialInterval()).isEqualTo(1000);
		assertThat(backOff.getMaxInterval()).isEqualTo(2000);
		assertThat(backOff.getMultiplier()).isEqualTo(1.2);
		SimpleRetryPolicy retryPolicy = (SimpleRetryPolicy) templateAccessor.getPropertyValue("retryPolicy");
		assertThat(retryPolicy.getMaxAttempts()).isEqualTo(3);
		context.close();
	}

	private Object target(Object target) {
		if (!AopUtils.isAopProxy(target)) {
			return target;
		}
		try {
			return target(((Advised) target).getTargetSource().getTarget());
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class TestProxyConfiguration {

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RecoverableService recoverable() {
			return new RecoverableService();
		}

		@Bean
		public static AdviceBPP bpp() {
			return new AdviceBPP();
		}

		static class AdviceBPP implements BeanPostProcessor, Ordered {

			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

				return bean;
			}

			@Override
			public Object postProcessAfterInitialization(final Object bean, String beanName) throws BeansException {

				if (bean instanceof RecoverableService) {
					Advised advised = (Advised) bean;
					advised.addAdvice((MethodInterceptor) invocation -> {
						if (invocation.getMethod().getName().equals("recover")) {
							((RecoverableService) bean).setOtherAdviceCalled();
						}
						return invocation.proceed();
					});
					return bean;
				}
				return bean;
			}

			@Override
			public int getOrder() {
				return Integer.MAX_VALUE;
			}

		}

	}

	@Configuration
	@EnableRetry(order = 1)
	protected static class TestOrderConfiguration {

		@Bean
		public Service service() {
			return new Service();
		}

	}

	@Configuration
	@EnableRetry
	protected static class TestConfiguration {

		@Bean
		public static PropertySourcesPlaceholderConfigurer pspc() {
			PropertySourcesPlaceholderConfigurer pspc = new PropertySourcesPlaceholderConfigurer();
			Properties properties = new Properties();
			properties.setProperty("one", "1");
			properties.setProperty("five", "5");
			properties.setProperty("onePointOne", "1.1");
			properties.setProperty("retryMethod", "shouldRetry");
			pspc.setProperties(properties);
			return pspc;
		}

		boolean listener1;

		boolean listener2;

		protected boolean twoFirst;

		@SuppressWarnings("serial")
		@Bean
		public Sleeper sleeper() {
			return period -> {
			};
		}

		@Bean
		public Service service() {
			return new Service();
		}

		@Bean
		public RetryListener listener1() {
			return new OrderedListener() {

				@Override
				public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

					TestConfiguration.this.listener1 = true;
					TestConfiguration.this.twoFirst = true;
					return super.open(context, callback);
				}

				@Override
				public int getOrder() {
					return Integer.MAX_VALUE;
				}

			};
		}

		@Bean
		public RetryListener listener2() {
			return new OrderedListener() {

				private boolean listener1;

				@Override
				public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {

					TestConfiguration.this.listener2 = true;
					TestConfiguration.this.twoFirst = false;
					return super.open(context, callback);
				}

				@Override
				public int getOrder() {
					return Integer.MIN_VALUE;
				}

			};
		}

		@Bean
		public MultiService multiService() {
			return new MultiService();
		}

		@Bean
		public RecoverableService recoverable() {
			return new RecoverableService();
		}

		@Bean
		public RetryableService retryable() {
			return new RetryableService();
		}

		@Bean
		public StatefulService stateful() {
			return new StatefulService();
		}

		@Bean
		public ExcludesService excludes() {
			return new ExcludesService();
		}

		@Bean
		public ExcludesOnlyService excludesOnly() {
			return new ExcludesOnlyService();
		}

		@Bean
		public MethodInterceptor retryInterceptor() {
			return RetryInterceptorBuilder.stateless().maxAttempts(5).build();
		}

		@Bean
		public InterceptableService serviceWithExternalInterceptor() {
			return new InterceptableService();
		}

		@Bean
		public ExpressionService expressionService() {
			return new ExpressionService();
		}

		@Bean
		public ExceptionChecker exceptionChecker() {
			return new ExceptionChecker();
		}

		@Bean
		public Integer integerFiveBean() {
			return 5;
		}

		@Bean
		public Foo foo() {
			return new Foo();
		}

		@Bean
		public TheInterface anInterface() {
			return new TheClass();
		}

		@Bean
		public NoRecoverInterface anInterfaceWithNoRecover() {
			return new NoRecoverClass();
		}

		@Bean
		public NotAnnotatedInterface notAnnotatedInterface() {
			return new RetryableImplementation();
		}

		@Bean
		RuntimeConfigs runtimeConfigs() {
			return spy(new RuntimeConfigs());
		}

	}

	public static class RuntimeConfigs {

		int count = 0;

		public int getMaxAttempts() {
			count++;
			return 3;
		}

		public long getInitial() {
			count++;
			return 1000;
		}

		public long getMax() {
			count++;
			return 2000;
		}

		public double getMult() {
			count++;
			return 1.2;
		}

	}

	protected static class Service {

		private int count = 0;

		@Retryable(RuntimeException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class MultiService {

		private int count = 0;

		@Retryable(retryFor = RuntimeException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Retryable(retryFor = RuntimeException.class)
		public void other() {
			if (this.count++ < 3) {
				throw new RuntimeException("Other");
			}
		}

		@Retryable(maxAttemptsExpression = "args[0] == 'foo' ? 3 : 1")
		public void conditional(String string) {
			this.count++;
			throw new IllegalArgumentException("conditional");
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class RecoverableService {

		private int count = 0;

		private Throwable cause;

		boolean otherAdviceCalled;

		@Retryable(retryFor = RuntimeException.class, noRetryFor = IllegalStateException.class,
				notRecoverable = { IllegalArgumentException.class, IllegalStateException.class })
		public void service() {
			if (this.count++ >= 3 && count < 7) {
				throw new IllegalArgumentException("Planned");
			}
			else if (count > 6) {
				throw new IllegalStateException("Planned");
			}
			else {
				throw new RuntimeException("Planned");
			}
		}

		@Recover
		public void recover(Throwable cause) {
			this.cause = cause;
		}

		public Throwable getCause() {
			return this.cause;
		}

		public int getCount() {
			return this.count;
		}

		public void setOtherAdviceCalled() {
			this.otherAdviceCalled = true;
		}

		public boolean isOtherAdviceCalled() {
			return this.otherAdviceCalled;
		}

	}

	@Retryable(retryFor = RuntimeException.class)
	protected static class RetryableService {

		private int count = 0;

		public void service() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class ExcludesService {

		private int count = 0;

		@Retryable(retryFor = RuntimeException.class, noRetryFor = IllegalStateException.class)
		public void service() {
			if (this.count++ < 2) {
				throw new IllegalStateException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	protected static class ExcludesOnlyService {

		private int count = 0;

		private RuntimeException exceptionToThrow;

		@Retryable(noRetryFor = IllegalStateException.class)
		public void service() {
			if (this.count++ < 2) {
				throw this.exceptionToThrow;
			}
		}

		public int getCount() {
			return this.count;
		}

		public void setExceptionToThrow(RuntimeException exceptionToThrow) {
			this.exceptionToThrow = exceptionToThrow;
		}

	}

	protected static class StatefulService {

		private int count = 0;

		@Retryable(stateful = true)
		public void service(int value) {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	static class InterceptableService {

		private int count = 0;

		@Retryable(interceptor = "retryInterceptor")
		public void service() {
			if (this.count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	static class ExpressionService {

		private int count = 0;

		@Retryable(exceptionExpression = "message.contains('this can be retried')")
		public void service1() {
			if (this.count++ < 2) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(exceptionExpression = "message.contains('this can be retried')")
		public void service2() {
			this.count++;
			throw new RuntimeException("this cannot be retried");
		}

		@Retryable(exceptionExpression = "@exceptionChecker.${retryMethod}(#root)",
				maxAttemptsExpression = "@integerFiveBean", backoff = @Backoff(delayExpression = "${one}",
						maxDelayExpression = "@integerFiveBean", multiplierExpression = "${onePointOne}"))
		public void service3() {
			if (this.count++ < 8) {
				throw new RuntimeException();
			}
		}

		@Retryable(exceptionExpression = "message.contains('this can be retried')",
				backoff = @Backoff(delayExpression = "5000"))
		public void service4() {
			if (this.count++ < 10) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(exceptionExpression = "message.contains('this can be retried')", include = RuntimeException.class)
		public void service5() {
			if (this.count++ < 11) {
				throw new RuntimeException("this can be retried");
			}
		}

		@Retryable(maxAttemptsExpression = "@runtimeConfigs.maxAttempts",
				backoff = @Backoff(delayExpression = "@runtimeConfigs.initial",
						maxDelayExpression = "@runtimeConfigs.max", multiplierExpression = "@runtimeConfigs.mult"))
		public void service6() {
			if (this.count++ < 2) {
				throw new RuntimeException("retry");
			}
		}

		public int getCount() {
			return this.count;
		}

	}

	public static class ExceptionChecker {

		public boolean shouldRetry(Throwable t) {
			return true;
		}

	}

	private static class Foo {

	}

	public static interface TheInterface {

		void service1();

		@Retryable
		void service2();

		int getCount();

		void service3();

		void recover(Exception e);

		boolean isRecovered();

	}

	public static class TheClass implements TheInterface {

		private int count = 0;

		private boolean recovered;

		@Override
		@Retryable
		public void service1() {
			if (this.count++ < 1) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (this.count++ < 3) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return this.count;
		}

		@Override
		@Retryable
		public void service3() {
			throw new RuntimeException("planned");
		}

		@Override
		@Recover
		public void recover(Exception e) {
			this.recovered = true;
		}

		@Override
		public boolean isRecovered() {
			return this.recovered;
		}

	}

	public static interface NoRecoverInterface {

		void service();

		boolean isRecovered();

	}

	public static class NoRecoverClass implements NoRecoverInterface {

		private boolean recovered;

		@Override
		@Retryable
		public void service() {
			throw new RuntimeException("Planned");
		}

		@Recover
		public void recover(Exception e) {
			this.recovered = true;
		}

		@Override
		public boolean isRecovered() {
			return this.recovered;
		}

	}

	public static interface NotAnnotatedInterface {

		void service1();

		void service2();

		int getCount();

	}

	@Retryable
	public static class RetryableImplementation implements NotAnnotatedInterface {

		private int count = 0;

		@Override
		public void service1() {
			if (this.count++ < 2) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public void service2() {
			if (this.count++ < 4) {
				throw new RuntimeException("Planned");
			}
		}

		@Override
		public int getCount() {
			return this.count;
		}

	}

	public abstract static class OrderedListener implements RetryListener, Ordered {

	}

}
