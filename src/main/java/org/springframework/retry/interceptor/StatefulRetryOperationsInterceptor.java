/*
 * Copyright 2006-2007 the original author or authors.
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

package org.springframework.retry.interceptor;

import java.util.Arrays;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.RetryState;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * A {@link MethodInterceptor} that can be used to automatically retry calls to a method
 * on a service if it fails. The argument to the service method is treated as an item to
 * be remembered in case the call fails. So the retry operation is stateful, and the item
 * that failed is tracked by its unique key (via {@link MethodArgumentsKeyGenerator})
 * until the retry is exhausted, at which point the {@link MethodInvocationRecoverer} is
 * called.<br/>
 * 
 * The main use case for this is where the service is transactional, via a transaction
 * interceptor on the interceptor chain. In this case the retry (and recovery on
 * exhausted) always happens in a new transaction.<br/>
 * 
 * The injected {@link RetryOperations} is used to control the number of retries. By
 * default it will retry a fixed number of times, according to the defaults in
 * {@link RetryTemplate}.<br/>
 * 
 * @author Dave Syer
 */
public class StatefulRetryOperationsInterceptor implements IntroductionInterceptor {

	private transient Log logger = LogFactory.getLog(getClass());

	private MethodArgumentsKeyGenerator keyGenerator;

	private MethodInvocationRecoverer<? extends Object> recoverer;

	private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

	private RetryOperations retryOperations;

	public void setRetryOperations(RetryOperations retryTemplate) {
		Assert.notNull(retryTemplate, "'retryOperations' cannot be null.");
		this.retryOperations = retryTemplate;
	}

	/**
	 * 
	 */
	public StatefulRetryOperationsInterceptor() {
		super();
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		retryOperations = retryTemplate;
	}

	/**
	 * Public setter for the {@link MethodInvocationRecoverer} to use if the retry is
	 * exhausted. The recoverer should be able to return an object of the same type as the
	 * target object because its return value will be used to return to the caller in the
	 * case of a recovery.<br/>
	 * 
	 * @param recoverer the {@link MethodInvocationRecoverer} to set
	 */
	public void setRecoverer(MethodInvocationRecoverer<? extends Object> recoverer) {
		this.recoverer = recoverer;
	}

	public void setKeyGenerator(MethodArgumentsKeyGenerator keyGenerator) {
		this.keyGenerator = keyGenerator;
	}

	/**
	 * Public setter for the {@link NewMethodArgumentsIdentifier}. Only set this if the
	 * arguments to the intercepted method can be inspected to find out if they have never
	 * been processed before.
	 * @param newMethodArgumentsIdentifier the {@link NewMethodArgumentsIdentifier} to set
	 */
	public void setNewItemIdentifier(
			NewMethodArgumentsIdentifier newMethodArgumentsIdentifier) {
		this.newMethodArgumentsIdentifier = newMethodArgumentsIdentifier;
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return Retryable.class.isAssignableFrom(intf);
	}

	/**
	 * Wrap the method invocation in a stateful retry with the policy and other helpers
	 * provided. If there is a failure the exception will generally be re-thrown. The only
	 * time it is not re-thrown is when retry is exhausted and the recovery path is taken
	 * (though the {@link MethodInvocationRecoverer} provided if there is one). In that
	 * case the value returned from the method invocation will be the value returned by
	 * the recoverer (so the return type for that should be the same as the intercepted
	 * method).
	 * 
	 * @see org.aopalliance.intercept.MethodInterceptor#invoke(org.aopalliance.intercept.MethodInvocation)
	 * @see MethodInvocationRecoverer#recover(Object[], Throwable)
	 * 
	 */
	public Object invoke(final MethodInvocation invocation) throws Throwable {

		logger.debug("Executing proxied method in stateful retry: "
				+ invocation.getStaticPart() + "("
				+ ObjectUtils.getIdentityHexString(invocation) + ")");

		Object[] args = invocation.getArguments();
		Assert.state(
				args.length > 0,
				"Stateful retry applied to method that takes no arguments: "
						+ invocation.getStaticPart());
		Object arg = args;
		if (args.length == 1) {
			arg = args[0];
		}
		final Object item = arg;

		RetryState retryState = new DefaultRetryState(
				keyGenerator != null ? keyGenerator.getKey(args) : item,
				newMethodArgumentsIdentifier != null ? newMethodArgumentsIdentifier
						.isNew(args) : false);

		Object result = retryOperations.execute(new MethodInvocationRetryCallback(
				invocation), recoverer != null ? new ItemRecovererCallback(args,
				recoverer) : null, retryState);

		logger.debug("Exiting proxied method in stateful retry with result: (" + result
				+ ")");

		return result;

	}

	/**
	 * @author Dave Syer
	 * 
	 */
	private static final class MethodInvocationRetryCallback implements
			RetryCallback<Object, Throwable> {
		/**
		 * 
		 */
		private final MethodInvocation invocation;

		/**
		 * @param invocation
		 */
		private MethodInvocationRetryCallback(MethodInvocation invocation) {
			this.invocation = invocation;
		}

		public Object doWithRetry(RetryContext context) throws Exception {
			try {
				return invocation.proceed();
			} catch (Exception e) {
				throw e;
			} catch (Error e) {
				throw e;
			} catch (Throwable e) {
				throw new IllegalStateException(e);
			}
		}
	}

	/**
	 * @author Dave Syer
	 * 
	 */
	private static final class ItemRecovererCallback implements RecoveryCallback<Object> {

		private final Object[] args;

		private final MethodInvocationRecoverer<? extends Object> recoverer;

		/**
		 * @param args the item that failed.
		 */
		private ItemRecovererCallback(Object[] args,
				MethodInvocationRecoverer<? extends Object> recoverer) {
			this.args = Arrays.asList(args).toArray();
			this.recoverer = recoverer;
		}

		public Object recover(RetryContext context) {
			return recoverer.recover(args, context.getLastThrowable());
		}

	}

}
