/*
 * Copyright 2014-2022 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.naming.OperationNotSupportedException;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.IntroductionInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.BackOffPolicyBuilder;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.FixedKeyGenerator;
import org.springframework.retry.interceptor.MethodArgumentsKeyGenerator;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.interceptor.NewMethodArgumentsIdentifier;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;
import org.springframework.retry.policy.ExpressionRetryPolicy;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * Interceptor that parses the retry metadata on the method it is invoking and delegates
 * to an appropriate RetryOperationsInterceptor.
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @since 1.1
 */
public class AnnotationAwareRetryOperationsInterceptor implements IntroductionInterceptor, BeanFactoryAware {

	private static final TemplateParserContext PARSER_CONTEXT = new TemplateParserContext();

	private static final SpelExpressionParser PARSER = new SpelExpressionParser();

	private static final MethodInterceptor NULL_INTERCEPTOR = new MethodInterceptor() {
		@Override
		public Object invoke(MethodInvocation methodInvocation) throws Throwable {
			throw new OperationNotSupportedException("Not supported");
		}
	};

	private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

	private final ConcurrentReferenceHashMap<Object, ConcurrentMap<Method, MethodInterceptor>> delegates = new ConcurrentReferenceHashMap<Object, ConcurrentMap<Method, MethodInterceptor>>();

	private RetryContextCache retryContextCache = new MapRetryContextCache();

	private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

	private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

	private Sleeper sleeper;

	private BeanFactory beanFactory;

	private RetryListener[] globalListeners;

	/**
	 * @param sleeper the sleeper to set
	 */
	public void setSleeper(Sleeper sleeper) {
		this.sleeper = sleeper;
	}

	/**
	 * Public setter for the {@link RetryContextCache}.
	 * @param retryContextCache the {@link RetryContextCache} to set.
	 */
	public void setRetryContextCache(RetryContextCache retryContextCache) {
		this.retryContextCache = retryContextCache;
	}

	/**
	 * @param methodArgumentsKeyGenerator the {@link MethodArgumentsKeyGenerator}
	 */
	public void setKeyGenerator(MethodArgumentsKeyGenerator methodArgumentsKeyGenerator) {
		this.methodArgumentsKeyGenerator = methodArgumentsKeyGenerator;
	}

	/**
	 * @param newMethodArgumentsIdentifier the {@link NewMethodArgumentsIdentifier}
	 */
	public void setNewItemIdentifier(NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
		this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
	}

	/**
	 * Default retry listeners to apply to all operations.
	 * @param globalListeners the default listeners
	 */
	public void setListeners(Collection<RetryListener> globalListeners) {
		ArrayList<RetryListener> retryListeners = new ArrayList<RetryListener>(globalListeners);
		AnnotationAwareOrderComparator.sort(retryListeners);
		this.globalListeners = retryListeners.toArray(new RetryListener[0]);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		this.evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return org.springframework.retry.interceptor.Retryable.class.isAssignableFrom(intf);
	}

	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		MethodInterceptor delegate = getDelegate(invocation.getThis(), invocation.getMethod());
		if (delegate != null) {
			return delegate.invoke(invocation);
		}
		else {
			return invocation.proceed();
		}
	}

	private MethodInterceptor getDelegate(Object target, Method method) {
		ConcurrentMap<Method, MethodInterceptor> cachedMethods = this.delegates.get(target);
		if (cachedMethods == null) {
			cachedMethods = new ConcurrentHashMap<Method, MethodInterceptor>();
		}
		MethodInterceptor delegate = cachedMethods.get(method);
		if (delegate == null) {
			MethodInterceptor interceptor = NULL_INTERCEPTOR;
			Retryable retryable = AnnotatedElementUtils.findMergedAnnotation(method, Retryable.class);
			if (retryable == null) {
				retryable = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Retryable.class);
			}
			if (retryable == null) {
				retryable = findAnnotationOnTarget(target, method, Retryable.class);
			}
			if (retryable != null) {
				if (StringUtils.hasText(retryable.interceptor())) {
					interceptor = this.beanFactory.getBean(retryable.interceptor(), MethodInterceptor.class);
				}
				else if (retryable.stateful()) {
					interceptor = getStatefulInterceptor(target, method, retryable);
				}
				else {
					interceptor = getStatelessInterceptor(target, method, retryable);
				}
			}
			cachedMethods.putIfAbsent(method, interceptor);
			delegate = cachedMethods.get(method);
		}
		this.delegates.putIfAbsent(target, cachedMethods);
		return delegate == NULL_INTERCEPTOR ? null : delegate;
	}

	private <A extends Annotation> A findAnnotationOnTarget(Object target, Method method, Class<A> annotation) {

		try {
			Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
			A retryable = AnnotatedElementUtils.findMergedAnnotation(targetMethod, annotation);
			if (retryable == null) {
				retryable = AnnotatedElementUtils.findMergedAnnotation(targetMethod.getDeclaringClass(), annotation);
			}

			return retryable;
		}
		catch (Exception e) {
			return null;
		}
	}

	private MethodInterceptor getStatelessInterceptor(Object target, Method method, Retryable retryable) {
		RetryTemplate template = createTemplate(retryable.listeners());
		template.setRetryPolicy(getRetryPolicy(retryable));
		template.setBackOffPolicy(getBackoffPolicy(retryable.backoff()));
		return RetryInterceptorBuilder.stateless().retryOperations(template).label(retryable.label())
				.recoverer(getRecoverer(target, method)).build();
	}

	private MethodInterceptor getStatefulInterceptor(Object target, Method method, Retryable retryable) {
		RetryTemplate template = createTemplate(retryable.listeners());
		template.setRetryContextCache(this.retryContextCache);

		CircuitBreaker circuit = AnnotatedElementUtils.findMergedAnnotation(method, CircuitBreaker.class);
		if (circuit == null) {
			circuit = findAnnotationOnTarget(target, method, CircuitBreaker.class);
		}
		if (circuit != null) {
			RetryPolicy policy = getRetryPolicy(circuit);
			CircuitBreakerRetryPolicy breaker = new CircuitBreakerRetryPolicy(policy);
			breaker.setOpenTimeout(getOpenTimeout(circuit));
			breaker.setResetTimeout(getResetTimeout(circuit));
			template.setRetryPolicy(breaker);
			template.setBackOffPolicy(new NoBackOffPolicy());
			String label = circuit.label();
			if (!StringUtils.hasText(label)) {
				label = method.toGenericString();
			}
			return RetryInterceptorBuilder.circuitBreaker().keyGenerator(new FixedKeyGenerator("circuit"))
					.retryOperations(template).recoverer(getRecoverer(target, method)).label(label).build();
		}
		RetryPolicy policy = getRetryPolicy(retryable);
		template.setRetryPolicy(policy);
		template.setBackOffPolicy(getBackoffPolicy(retryable.backoff()));
		String label = retryable.label();
		return RetryInterceptorBuilder.stateful().keyGenerator(this.methodArgumentsKeyGenerator)
				.newMethodArgumentsIdentifier(this.newMethodArgumentsIdentifier).retryOperations(template).label(label)
				.recoverer(getRecoverer(target, method)).build();
	}

	private long getOpenTimeout(CircuitBreaker circuit) {
		if (StringUtils.hasText(circuit.openTimeoutExpression())) {
			Long value = PARSER.parseExpression(resolve(circuit.openTimeoutExpression()), PARSER_CONTEXT)
					.getValue(Long.class);
			if (value != null) {
				return value;
			}
		}
		return circuit.openTimeout();
	}

	private long getResetTimeout(CircuitBreaker circuit) {
		if (StringUtils.hasText(circuit.resetTimeoutExpression())) {
			Long value = PARSER.parseExpression(resolve(circuit.resetTimeoutExpression()), PARSER_CONTEXT)
					.getValue(Long.class);
			if (value != null) {
				return value;
			}
		}
		return circuit.resetTimeout();
	}

	private RetryTemplate createTemplate(String[] listenersBeanNames) {
		RetryTemplate template = new RetryTemplate();
		if (listenersBeanNames.length > 0) {
			template.setListeners(getListenersBeans(listenersBeanNames));
		}
		else if (this.globalListeners != null) {
			template.setListeners(this.globalListeners);
		}
		return template;
	}

	private RetryListener[] getListenersBeans(String[] listenersBeanNames) {
		RetryListener[] listeners = new RetryListener[listenersBeanNames.length];
		for (int i = 0; i < listeners.length; i++) {
			listeners[i] = this.beanFactory.getBean(listenersBeanNames[i], RetryListener.class);
		}
		return listeners;
	}

	private MethodInvocationRecoverer<?> getRecoverer(Object target, Method method) {
		if (target instanceof MethodInvocationRecoverer) {
			return (MethodInvocationRecoverer<?>) target;
		}
		final AtomicBoolean foundRecoverable = new AtomicBoolean(false);
		ReflectionUtils.doWithMethods(target.getClass(), new MethodCallback() {
			@Override
			public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
				if (AnnotatedElementUtils.findMergedAnnotation(method, Recover.class) != null) {
					foundRecoverable.set(true);
				}
			}
		});

		if (!foundRecoverable.get()) {
			return null;
		}
		return new RecoverAnnotationRecoveryHandler<Object>(target, method);
	}

	private RetryPolicy getRetryPolicy(Annotation retryable) {
		Map<String, Object> attrs = AnnotationUtils.getAnnotationAttributes(retryable);
		@SuppressWarnings("unchecked")
		Class<? extends Throwable>[] includes = (Class<? extends Throwable>[]) attrs.get("value");
		String exceptionExpression = (String) attrs.get("exceptionExpression");
		boolean hasExpression = StringUtils.hasText(exceptionExpression);
		if (includes.length == 0) {
			@SuppressWarnings("unchecked")
			Class<? extends Throwable>[] value = (Class<? extends Throwable>[]) attrs.get("include");
			includes = value;
		}
		@SuppressWarnings("unchecked")
		Class<? extends Throwable>[] excludes = (Class<? extends Throwable>[]) attrs.get("exclude");
		Integer maxAttempts = (Integer) attrs.get("maxAttempts");
		String maxAttemptsExpression = (String) attrs.get("maxAttemptsExpression");
		if (StringUtils.hasText(maxAttemptsExpression)) {
			if (ExpressionRetryPolicy.isTemplate(maxAttemptsExpression)) {
				maxAttempts = PARSER.parseExpression(resolve(maxAttemptsExpression), PARSER_CONTEXT)
						.getValue(this.evaluationContext, Integer.class);
			}
			else {
				maxAttempts = PARSER.parseExpression(resolve(maxAttemptsExpression)).getValue(this.evaluationContext,
						Integer.class);
			}
		}
		if (includes.length == 0 && excludes.length == 0) {
			SimpleRetryPolicy simple = hasExpression
					? new ExpressionRetryPolicy(resolve(exceptionExpression)).withBeanFactory(this.beanFactory)
					: new SimpleRetryPolicy();
			simple.setMaxAttempts(maxAttempts);
			return simple;
		}
		Map<Class<? extends Throwable>, Boolean> policyMap = new HashMap<Class<? extends Throwable>, Boolean>();
		for (Class<? extends Throwable> type : includes) {
			policyMap.put(type, true);
		}
		for (Class<? extends Throwable> type : excludes) {
			policyMap.put(type, false);
		}
		boolean retryNotExcluded = includes.length == 0;
		if (hasExpression) {
			return new ExpressionRetryPolicy(maxAttempts, policyMap, true, exceptionExpression, retryNotExcluded)
					.withBeanFactory(this.beanFactory);
		}
		else {
			return new SimpleRetryPolicy(maxAttempts, policyMap, true, retryNotExcluded);
		}
	}

	private BackOffPolicy getBackoffPolicy(Backoff backoff) {
		Map<String, Object> attrs = AnnotationUtils.getAnnotationAttributes(backoff);
		long min = backoff.delay() == 0 ? backoff.value() : backoff.delay();
		String delayExpression = (String) attrs.get("delayExpression");
		if (StringUtils.hasText(delayExpression)) {
			if (ExpressionRetryPolicy.isTemplate(delayExpression)) {
				min = PARSER.parseExpression(resolve(delayExpression), PARSER_CONTEXT).getValue(this.evaluationContext,
						Long.class);
			}
			else {
				min = PARSER.parseExpression(resolve(delayExpression)).getValue(this.evaluationContext, Long.class);
			}
		}
		long max = backoff.maxDelay();
		String maxDelayExpression = (String) attrs.get("maxDelayExpression");
		if (StringUtils.hasText(maxDelayExpression)) {
			if (ExpressionRetryPolicy.isTemplate(delayExpression)) {
				max = PARSER.parseExpression(resolve(maxDelayExpression), PARSER_CONTEXT)
						.getValue(this.evaluationContext, Long.class);
			}
			else {
				max = PARSER.parseExpression(resolve(maxDelayExpression)).getValue(this.evaluationContext, Long.class);
			}
		}
		double multiplier = backoff.multiplier();
		String multiplierExpression = (String) attrs.get("multiplierExpression");
		if (StringUtils.hasText(multiplierExpression)) {
			if (ExpressionRetryPolicy.isTemplate(delayExpression)) {
				multiplier = PARSER.parseExpression(resolve(multiplierExpression), PARSER_CONTEXT)
						.getValue(this.evaluationContext, Double.class);
			}
			else {
				multiplier = PARSER.parseExpression(resolve(multiplierExpression)).getValue(this.evaluationContext,
						Double.class);
			}
		}
		boolean isRandom = false;
		if (multiplier > 0) {
			isRandom = backoff.random();
			String randomExpression = (String) attrs.get("randomExpression");
			if (StringUtils.hasText(randomExpression)) {
				if (ExpressionRetryPolicy.isTemplate(randomExpression)) {
					isRandom = PARSER.parseExpression(resolve(randomExpression), PARSER_CONTEXT)
							.getValue(this.evaluationContext, Boolean.class);
				}
				else {
					isRandom = PARSER.parseExpression(resolve(randomExpression)).getValue(this.evaluationContext,
							Boolean.class);
				}
			}
		}
		return BackOffPolicyBuilder.newBuilder().delay(min).maxDelay(max).multiplier(multiplier).isRandom(isRandom)
				.withSleeper(this.sleeper).build();
	}

	/**
	 * Resolve the specified value if possible.
	 *
	 * @see ConfigurableBeanFactory#resolveEmbeddedValue
	 */
	private String resolve(String value) {
		if (this.beanFactory != null && this.beanFactory instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) this.beanFactory).resolveEmbeddedValue(value);
		}
		return value;
	}

}
