/*
 * Copyright 2014 the original author or authors.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;

import org.aopalliance.aop.Advice;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.aop.support.annotation.AnnotationClassFilter;
import org.springframework.aop.support.annotation.AnnotationMethodMatcher;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.MethodArgumentsKeyGenerator;
import org.springframework.retry.interceptor.NewMethodArgumentsIdentifier;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;

/**
 * Basic configuration for <code>@Retryable</code> processing. For stateful retry, if
 * there is a unique bean elsewhere in the context of type {@link RetryContextCache},
 * {@link MethodArgumentsKeyGenerator} or {@link NewMethodArgumentsIdentifier} it will be
 * used by the corresponding retry interceptor (otherwise sensible defaults are adopted).
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @since 1.1
 *
 */
@SuppressWarnings("serial")
@Configuration
public class RetryConfiguration extends AbstractPointcutAdvisor implements IntroductionAdvisor, BeanFactoryAware {

	private Advice advice;

	private Pointcut pointcut;

	@Autowired(required = false)
	private RetryContextCache retryContextCache;

	@Autowired(required = false)
	private List<RetryListener> retryListeners;

	@Autowired(required = false)
	private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

	@Autowired(required = false)
	private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

	@Autowired(required = false)
	private Sleeper sleeper;

	private BeanFactory beanFactory;

	@PostConstruct
	public void init() {
		Set<Class<? extends Annotation>> retryableAnnotationTypes = new LinkedHashSet<Class<? extends Annotation>>(1);
		retryableAnnotationTypes.add(Retryable.class);
		this.pointcut = buildPointcut(retryableAnnotationTypes);
		this.advice = buildAdvice();
		if (this.advice instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.advice).setBeanFactory(beanFactory);
		}
	}

	/**
	 * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public ClassFilter getClassFilter() {
		return pointcut.getClassFilter();
	}

	@Override
	public Class<?>[] getInterfaces() {
		return new Class[] { org.springframework.retry.interceptor.Retryable.class };
	}

	@Override
	public void validateInterfaces() throws IllegalArgumentException {
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	protected Advice buildAdvice() {
		AnnotationAwareRetryOperationsInterceptor interceptor = new AnnotationAwareRetryOperationsInterceptor();
		if (retryContextCache != null) {
			interceptor.setRetryContextCache(retryContextCache);
		}
		if (retryListeners != null) {
			interceptor.setListeners(retryListeners);
		}
		if (methodArgumentsKeyGenerator != null) {
			interceptor.setKeyGenerator(methodArgumentsKeyGenerator);
		}
		if (newMethodArgumentsIdentifier != null) {
			interceptor.setNewItemIdentifier(newMethodArgumentsIdentifier);
		}
		if (sleeper != null) {
			interceptor.setSleeper(sleeper);
		}
		return interceptor;
	}

	/**
	 * Calculate a pointcut for the given retry annotation types, if any.
	 * @param retryAnnotationTypes the retry annotation types to introspect
	 * @return the applicable Pointcut object, or {@code null} if none
	 */
	protected Pointcut buildPointcut(Set<Class<? extends Annotation>> retryAnnotationTypes) {
		ComposablePointcut result = null;
		for (Class<? extends Annotation> retryAnnotationType : retryAnnotationTypes) {
			Pointcut filter = new AnnotationClassOrMethodPointcut(retryAnnotationType);
			if (result == null) {
				result = new ComposablePointcut(filter);
			}
			else {
				result.union(filter);
			}
		}
		return result;
	}

	private final class AnnotationClassOrMethodPointcut extends StaticMethodMatcherPointcut {

		private final MethodMatcher methodResolver;

		AnnotationClassOrMethodPointcut(Class<? extends Annotation> annotationType) {
			this.methodResolver = new AnnotationMethodMatcher(annotationType);
			setClassFilter(new AnnotationClassOrMethodFilter(annotationType));
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return getClassFilter().matches(targetClass) || this.methodResolver.matches(method, targetClass);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AnnotationClassOrMethodPointcut)) {
				return false;
			}
			AnnotationClassOrMethodPointcut otherAdvisor = (AnnotationClassOrMethodPointcut) other;
			return ObjectUtils.nullSafeEquals(this.methodResolver, otherAdvisor.methodResolver);
		}

	}

	private final class AnnotationClassOrMethodFilter extends AnnotationClassFilter {

		private final AnnotationMethodsResolver methodResolver;

		AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
			super(annotationType, true);
			this.methodResolver = new AnnotationMethodsResolver(annotationType);
		}

		@Override
		public boolean matches(Class<?> clazz) {
			return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
		}

	}

	private static class AnnotationMethodsResolver {

		private Class<? extends Annotation> annotationType;

		public AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {
			this.annotationType = annotationType;
		}

		public boolean hasAnnotatedMethods(Class<?> clazz) {
			final AtomicBoolean found = new AtomicBoolean(false);
			ReflectionUtils.doWithMethods(clazz, new MethodCallback() {
				@Override
				public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
					if (found.get()) {
						return;
					}
					Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
					if (annotation != null) {
						found.set(true);
					}
				}
			});
			return found.get();
		}

	}

}
