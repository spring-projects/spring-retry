/*
 * Copyright 2006-2007 the original author or authors.
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

package org.springframework.retry.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

/**
 * A {@link RetryPolicy} that composes a list of other policies and delegates calls to
 * them in order.
 *
 * @author Dave Syer
 * @author Michael Minella
 *
 */
@SuppressWarnings("serial")
public class CompositeRetryPolicy implements RetryPolicy {

	RetryPolicy[] policies = new RetryPolicy[0];

	private boolean optimistic = false;

	/**
	 * Setter for optimistic.
	 * @param optimistic should this retry policy be optimistic
	 */
	public void setOptimistic(boolean optimistic) {
		this.optimistic = optimistic;
	}

	/**
	 * Setter for policies.
	 * @param policies the {@link RetryPolicy} policies
	 */
	public void setPolicies(RetryPolicy[] policies) {
		this.policies = Arrays.asList(policies).toArray(new RetryPolicy[policies.length]);
	}

	/**
	 * Delegate to the policies that were in operation when the context was created. If
	 * any of them cannot retry then return false, otherwise return true.
	 * @param context the {@link RetryContext}
	 * @see org.springframework.retry.RetryPolicy#canRetry(org.springframework.retry.RetryContext)
	 */
	@Override
	public boolean canRetry(RetryContext context) {
		RetryContext[] contexts = ((CompositeRetryContext) context).contexts;
		RetryPolicy[] policies = ((CompositeRetryContext) context).policies;

		boolean retryable = true;

		if (this.optimistic) {
			retryable = false;
			for (int i = 0; i < contexts.length; i++) {
				if (policies[i].canRetry(contexts[i])) {
					retryable = true;
				}
			}
		}
		else {
			for (int i = 0; i < contexts.length; i++) {
				if (!policies[i].canRetry(contexts[i])) {
					retryable = false;
				}
			}
		}

		return retryable;
	}

	/**
	 * Delegate to the policies that were in operation when the context was created. If
	 * any of them fails to close the exception is propagated (and those later in the
	 * chain are closed before re-throwing).
	 *
	 * @see org.springframework.retry.RetryPolicy#close(org.springframework.retry.RetryContext)
	 * @param context the {@link RetryContext}
	 */
	@Override
	public void close(RetryContext context) {
		RetryContext[] contexts = ((CompositeRetryContext) context).contexts;
		RetryPolicy[] policies = ((CompositeRetryContext) context).policies;
		RuntimeException exception = null;
		for (int i = 0; i < contexts.length; i++) {
			try {
				policies[i].close(contexts[i]);
			}
			catch (RuntimeException e) {
				if (exception == null) {
					exception = e;
				}
			}
		}
		if (exception != null) {
			throw exception;
		}
	}

	/**
	 * Creates a new context that copies the existing policies and keeps a list of the
	 * contexts from each one.
	 *
	 * @see org.springframework.retry.RetryPolicy#open(RetryContext)
	 */
	@Override
	public RetryContext open(RetryContext parent) {
		List<RetryContext> list = new ArrayList<RetryContext>();
		for (RetryPolicy policy : this.policies) {
			list.add(policy.open(parent));
		}
		return new CompositeRetryContext(parent, list, this.policies);
	}

	/**
	 * Delegate to the policies that were in operation when the context was created.
	 *
	 * @see org.springframework.retry.RetryPolicy#close(org.springframework.retry.RetryContext)
	 */
	@Override
	public void registerThrowable(RetryContext context, Throwable throwable) {
		RetryContext[] contexts = ((CompositeRetryContext) context).contexts;
		RetryPolicy[] policies = ((CompositeRetryContext) context).policies;
		for (int i = 0; i < contexts.length; i++) {
			policies[i].registerThrowable(contexts[i], throwable);
		}
		((RetryContextSupport) context).registerThrowable(throwable);
	}

	private static class CompositeRetryContext extends RetryContextSupport {

		RetryContext[] contexts;

		RetryPolicy[] policies;

		public CompositeRetryContext(RetryContext parent, List<RetryContext> contexts, RetryPolicy[] policies) {
			super(parent);
			this.contexts = contexts.toArray(new RetryContext[contexts.size()]);
			this.policies = policies;
		}

	}

}
