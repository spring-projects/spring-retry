/*
 * Copyright 2006-2019 the original author or authors.
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

package org.springframework.retry.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryOperations;
import org.springframework.util.StringUtils;

/**
 * Callback class for a Spring AOP reflective `MethodInvocation` that can be retried using
 * a {@link RetryOperations}.
 *
 * In a concrete {@link org.springframework.retry.RetryListener} implementation, the
 * `MethodInvocation` can be analysed for providing insights on the method called as well
 * as its parameter values which could then be used for monitoring purposes.
 *
 * @param <T> the type of object returned by the callback
 * @param <E> the type of exception it declares may be thrown
 * @see StatefulRetryOperationsInterceptor
 * @see RetryOperationsInterceptor
 * @see org.springframework.retry.listener.MethodInvocationRetryListenerSupport
 * @author Marius Grama
 * @since 1.3
 */
public abstract class MethodInvocationRetryCallback<T, E extends Throwable> implements RetryCallback<T, E> {

	protected final MethodInvocation invocation;

	protected final String label;

	/**
	 * Constructor for the class.
	 * @param invocation the method invocation
	 * @param label a unique label for statistics reporting.
	 */
	public MethodInvocationRetryCallback(MethodInvocation invocation, String label) {
		this.invocation = invocation;
		if (StringUtils.hasText(label)) {
			this.label = label;
		}
		else {
			this.label = invocation.getMethod().toGenericString();
		}
	}

	public MethodInvocation getInvocation() {
		return invocation;
	}

	public String getLabel() {
		return label;
	}

}
