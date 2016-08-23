/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.retry.interceptor;

import java.util.Arrays;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link MethodInterceptor} that can be used to automatically retry calls to a method
 * on a service if it fails. The injected {@link RetryOperations} is used to control the
 * number of retries. By default it will retry a fixed number of times, according to the
 * defaults in {@link RetryTemplate}.
 *
 * Hint about transaction boundaries. If you want to retry a failed transaction you need
 * to make sure that the transaction boundary is inside the retry, otherwise the
 * successful attempt will roll back with the whole transaction. If the method being
 * intercepted is also transactional, then use the ordering hints in the advice
 * declarations to ensure that this one is before the transaction interceptor in the
 * advice chain.
 *
 * @author Rob Harrop
 * @author Dave Syer
 */
public class RetryOperationsInterceptor implements MethodInterceptor {

	private RetryOperations retryOperations = new RetryTemplate();

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

	public Object invoke(final MethodInvocation invocation) throws Throwable {

		String name;
		if (StringUtils.hasText(label)) {
			name = label;
		} else {
			name = invocation.getMethod().toGenericString();
		}
		final String label = name;

		RetryCallback<Object, Throwable> retryCallback = new RetryCallback<Object, Throwable>() {

			public Object doWithRetry(RetryContext context) throws Exception {
				
				context.setAttribute(RetryContext.NAME, label);

				/*
				 * If we don't copy the invocation carefully it won't keep a reference to
				 * the other interceptors in the chain. We don't have a choice here but to
				 * specialise to ReflectiveMethodInvocation (but how often would another
				 * implementation come along?).
				 */
				if (invocation instanceof ProxyMethodInvocation) {
					try {
						return ((ProxyMethodInvocation) invocation).invocableClone().proceed();
					}
					catch (Exception e) {
						throw e;
					}
					catch (Error e) {
						throw e;
					}
					catch (Throwable e) {
						throw new IllegalStateException(e);
					}
				}
				else {
					throw new IllegalStateException(
							"MethodInvocation of the wrong type detected - this should not happen with Spring AOP, " +
									"so please raise an issue if you see this exception");
				}
			}

		};

		if (recoverer != null) {
			ItemRecovererCallback recoveryCallback = new ItemRecovererCallback(
					invocation.getArguments(), recoverer);
			return this.retryOperations.execute(retryCallback, recoveryCallback);
		}

		return this.retryOperations.execute(retryCallback);

	}

	/**
	 * @author Dave Syer
	 *
	 */
	private static final class ItemRecovererCallback implements RecoveryCallback<Object> {

		private final Object[] args;

		private final MethodInvocationRecoverer<?> recoverer;

		/**
		 * @param args the item that failed.
		 */
		private ItemRecovererCallback(Object[] args, MethodInvocationRecoverer<?> recoverer) {
			this.args = Arrays.asList(args).toArray();
			this.recoverer = recoverer;
		}

		public Object recover(RetryContext context) {
			return recoverer.recover(args, context.getLastThrowable());
		}

	}

}
