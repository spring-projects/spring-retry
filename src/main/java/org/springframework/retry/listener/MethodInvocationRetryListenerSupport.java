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

package org.springframework.retry.listener;

import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.interceptor.MethodInvocationRetryCallback;

/**
 * <p>
 * Empty method implementation of {@link RetryListener} with focus on the AOP reflective
 * method invocations providing convenience retry listener type-safe (with a
 * `MethodInvocationRetryCallback` callback parameter) specific methods.
 * </p>
 * NOTE that this listener performs an action only when dealing with callbacks that are
 * instances of {@link MethodInvocationRetryCallback}.
 *
 * @author Marius Grama
 * @since 1.3
 */
public class MethodInvocationRetryListenerSupport implements RetryListener {

	public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
			Throwable throwable) {
		if (callback instanceof MethodInvocationRetryCallback) {
			MethodInvocationRetryCallback<T, E> methodInvocationRetryCallback = (MethodInvocationRetryCallback<T, E>) callback;
			doClose(context, methodInvocationRetryCallback, throwable);
		}
	}

	public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
			Throwable throwable) {
		if (callback instanceof MethodInvocationRetryCallback) {
			MethodInvocationRetryCallback<T, E> methodInvocationRetryCallback = (MethodInvocationRetryCallback<T, E>) callback;
			doOnError(context, methodInvocationRetryCallback, throwable);
		}
	}

	public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
		if (callback instanceof MethodInvocationRetryCallback) {
			MethodInvocationRetryCallback<T, E> methodInvocationRetryCallback = (MethodInvocationRetryCallback<T, E>) callback;
			return doOpen(context, methodInvocationRetryCallback);
		}
		// in case that the callback is not for a reflective method invocation
		// just go forward with the execution
		return true;
	}

	protected <T, E extends Throwable> void doClose(RetryContext context, MethodInvocationRetryCallback<T, E> callback,
			Throwable throwable) {
	}

	protected <T, E extends Throwable> void doOnError(RetryContext context,
			MethodInvocationRetryCallback<T, E> callback, Throwable throwable) {
	}

	protected <T, E extends Throwable> boolean doOpen(RetryContext context,
			MethodInvocationRetryCallback<T, E> callback) {
		return true;
	}

}
