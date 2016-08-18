/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.retry.policy;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

/**
 * @author Dave Syer
 *
 */
public class CircuitBreakerRetryPolicy implements RetryPolicy {

	private static Log logger = LogFactory.getLog(CircuitBreakerRetryPolicy.class);

	private final RetryPolicy delegate;
	private long resetTimeout = 20000;
	private long openTimeout = 5000;

	public CircuitBreakerRetryPolicy(RetryPolicy delegate) {
		this.delegate = delegate;
	}

	/**
	 * Timeout for resetting circuit in milliseconds. After the circuit opens it will
	 * re-close after this time has elapsed and the context will be restarted.
	 *
	 * @param timeout the timeout to set in milliseconds
	 */
	public void setResetTimeout(long timeout) {
		this.resetTimeout = timeout;
	}

	/**
	 * Timeout for tripping the open circuit. If the delegate policy cannot retry and the
	 * time elapsed since the context was started is less than this window, then the
	 * circuit is opened.
	 *
	 * @param timeout the timeout to set in milliseconds
	 */
	public void setOpenTimeout(long timeout) {
		this.openTimeout = timeout;
	}

	@Override
	public boolean canRetry(RetryContext context) {
		CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
		if (circuit.isOpen()) {
			return false;
		}
		return this.delegate.canRetry(circuit.context);
	}

	@Override
	public RetryContext open(RetryContext parent) {
		return new CircuitBreakerRetryContext(parent, this.delegate, this.resetTimeout,
				this.openTimeout);
	}

	@Override
	public void close(RetryContext context) {
		CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
		this.delegate.close(circuit.context);
	}

	@Override
	public void registerThrowable(RetryContext context, Throwable throwable) {
		CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
		circuit.registerThrowable(throwable);
		this.delegate.registerThrowable(circuit.context, throwable);
	}

	@SuppressWarnings("serial")
	static class CircuitBreakerRetryContext extends RetryContextSupport {
		private volatile RetryContext context;
		private final RetryPolicy policy;
		private volatile long start = System.currentTimeMillis();
		private final long timeout;
		private final long openWindow;

		public CircuitBreakerRetryContext(RetryContext parent, RetryPolicy policy,
				long timeout, long openWindow) {
			super(parent);
			this.policy = policy;
			this.timeout = timeout;
			this.openWindow = openWindow;
			this.context = createDelegateContext(policy, parent);
			setAttribute("state.global", true);
		}

		private RetryContext createDelegateContext(RetryPolicy policy,
				RetryContext parent) {
			RetryContext context = policy.open(parent);
			return context;
		}

		public boolean isOpen() {
			long time = System.currentTimeMillis() - this.start;
			boolean retryable = this.policy.canRetry(this.context);
			if (!retryable) {
				if (time > this.timeout) {
					logger.trace("Closing");
					this.context = createDelegateContext(policy, getParent());
					this.start = System.currentTimeMillis();
					retryable = this.policy.canRetry(this.context);
				}
				else if (time < this.openWindow) {
					if ((Boolean) getAttribute("open") == false) {
						logger.trace("Opening circuit");
						setAttribute("open", true);
					}
					this.start = System.currentTimeMillis();
					return true;
				}
			}
			else {
				if (time > this.openWindow) {
					logger.trace("Resetting context");
					this.start = System.currentTimeMillis();
					this.context = createDelegateContext(policy, getParent());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Open: " + !retryable);
			}
			setAttribute("open", !retryable);
			return !retryable;
		}

		@Override
		public int getRetryCount() {
			return this.context.getRetryCount();
		}

		@Override
		public String toString() {
			return this.context.toString();
		}

	}

}
