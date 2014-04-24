/*
 * Copyright 2012-2013 the original author or authors.
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

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.aopalliance.aop.Advice;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.interceptor.MethodArgumentsKeyGenerator;
import org.springframework.retry.interceptor.NewMethodArgumentsIdentifier;
import org.springframework.retry.policy.RetryContextCache;

/**
 * Basic configuration for <code>@Retryable</code> processing. For stateful retry, if
 * there is a unique bean elsewhere in the context of type {@link RetryContextCache},
 * {@link MethodArgumentsKeyGenerator} or {@link NewMethodArgumentsIdentifier} it will be
 * used by the corresponding retry interceptor (otherwise sensible defaults are adopted).
 * 
 * @author Dave Syer
 * @since 2.0
 *
 */
@SuppressWarnings("serial")
@Configuration
public class RetryConfiguration extends AbstractPointcutAdvisor implements
		IntroductionAdvisor, BeanFactoryAware {

	private Advice advice;

	private Pointcut pointcut;

	@Autowired(required = false)
	private RetryContextCache retryContextCache;

	@Autowired(required = false)
	private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

	@Autowired(required = false)
	private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

	@Autowired(required = false)
	private Sleeper sleeper;

	@PostConstruct
	public void init() {
		Set<Class<? extends Annotation>> retryableAnnotationTypes = new LinkedHashSet<Class<? extends Annotation>>(
				1);
		retryableAnnotationTypes.add(Retryable.class);
		this.pointcut = buildPointcut(retryableAnnotationTypes);
		this.advice = buildAdvice();
	}

	/**
	 * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (this.advice instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.advice).setBeanFactory(beanFactory);
		}
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
			Pointcut cpc = new AnnotationMatchingPointcut(retryAnnotationType, true);
			Pointcut mpc = AnnotationMatchingPointcut
					.forMethodAnnotation(retryAnnotationType);
			if (result == null) {
				result = new ComposablePointcut(cpc).union(mpc);
			} else {
				result.union(cpc).union(mpc);
			}
		}
		return result;
	}

}
