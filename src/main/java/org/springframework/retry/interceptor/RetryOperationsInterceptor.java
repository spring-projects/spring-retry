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

package org.springframework.retry.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.lang.Nullable;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.support.Args;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * A {@link MethodInterceptor} that can be used to automatically retry calls to a method
 * on a service if it fails. The injected {@link RetryOperations} is used to control the
 * number of retries. By default, it will retry a fixed number of times, according to the
 * defaults in {@link RetryTemplate}.
 * <p>
 * Hint about transaction boundaries. If you want to retry a failed transaction you need
 * to make sure that the transaction boundary is inside the retry, otherwise the
 * successful attempt will roll back with the whole transaction. If the method being
 * intercepted is also transactional, then use the ordering hints in the advice
 * declarations to ensure that this one is before the transaction interceptor in the
 * advice chain.
 * <p>
 * An internal {@link MethodInvocationRetryCallback} implementation exposes a
 * {@value RetryOperationsInterceptor#METHOD} attribute into the provided
 * {@link RetryContext} with a value from {@link MethodInvocation#getMethod()}. In
 * addition, the arguments of this method are exposed into a
 * {@value RetryOperationsInterceptor#METHOD_ARGS} attribute as an {@link Args} instance
 * wrapper.
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Artem Bilan
 */
public class RetryOperationsInterceptor implements MethodInterceptor {

	/**
	 * The {@link RetryContext} attribute name for the
	 * {@link MethodInvocation#getMethod()}.
	 */
	public static final String METHOD = "method";

	/**
	 * The {@link RetryContext} attribute name for the
	 * {@code new Args(invocation.getArguments())}.
	 */
	public static final String METHOD_ARGS = "methodArgs";

	private RetryOperations retryOperations = new RetryTemplate();

	@Nullable
	private MethodInvocationRecoverer<?> recoverer;

	private String label;

	public void setLabel(String label) {
		this.label = label;
	}

	public void setRetryOperations(RetryOperations retryTemplate) {
		Assert.notNull(retryTemplate, "'retryOperations' cannot be null.");
		this.retryOperations = retryTemplate;
	}

	public void setRecoverer(MethodInvocationRecoverer<?> recoverer) {
		this.recoverer = recoverer;
	}

	@Override
	public Object invoke(final MethodInvocation invocation) throws Throwable {
		RetryCallback<Object, Throwable> retryCallback = new MethodInvocationRetryCallback<>(invocation, this.label) {

			@Override
			public Object doWithRetry(RetryContext context) throws Exception {

				context.setAttribute(RetryContext.NAME, this.label);
				Args args = new Args(invocation.getArguments());
				context.setAttribute(METHOD, invocation.getMethod());
				context.setAttribute(METHOD_ARGS, args);
				// TODO remove this attribute in the next major/minor version
				context.setAttribute("ARGS", args);

				/*
				 * If we don't copy the invocation carefully it won't keep a reference to
				 * the other interceptors in the chain. We don't have a choice here but to
				 * specialise to ReflectiveMethodInvocation (but how often would another
				 * implementation come along?).
				 */
				if (this.invocation instanceof ProxyMethodInvocation) {
					context.setAttribute("___proxy___", ((ProxyMethodInvocation) this.invocation).getProxy());
					try {
						return ((ProxyMethodInvocation) this.invocation).invocableClone().proceed();
					}
					catch (Exception | Error e) {
						throw e;
					}
					catch (Throwable e) {
						throw new IllegalStateException(e);
					}
				}
				else {
					throw new IllegalStateException(
							"MethodInvocation of the wrong type detected - this should not happen with Spring AOP, "
									+ "so please raise an issue if you see this exception");
				}
			}

		};

		RecoveryCallback<Object> recoveryCallback = (this.recoverer != null)
				? new ItemRecovererCallback(invocation.getArguments(), this.recoverer) : null;
		try {
			return this.retryOperations.execute(retryCallback, recoveryCallback);
		}
		finally {
			RetryContext context = RetrySynchronizationManager.getContext();
			if (context != null) {
				context.removeAttribute("___proxy___");
			}
		}
	}

	private record ItemRecovererCallback(Object[] args,
			MethodInvocationRecoverer<?> recoverer) implements RecoveryCallback<Object> {

		@Override
		public Object recover(RetryContext context) {
			return this.recoverer.recover(this.args, context.getLastThrowable());
		}

	}

}
