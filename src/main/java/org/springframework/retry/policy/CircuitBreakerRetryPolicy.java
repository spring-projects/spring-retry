/*
 * Copyright 2006-2022 the original author or authors.
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.context.RetryContextSupport;

/**
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class CircuitBreakerRetryPolicy implements RetryPolicy {

	public static final String CIRCUIT_OPEN = "circuit.open";

	public static final String CIRCUIT_SHORT_COUNT = "circuit.shortCount";

	private static final Log logger = LogFactory.getLog(CircuitBreakerRetryPolicy.class);

	private final RetryPolicy delegate;

	private long resetTimeout = 20000;

	private long openTimeout = 5000;

	private Function<RetryContext, Long> resetTimeoutFunction;

	private Function<RetryContext, Long> openTimeoutFunction;

	private CircuitBreakerRetryContext context;

	public CircuitBreakerRetryPolicy() {
		this(new SimpleRetryPolicy());
	}

	public CircuitBreakerRetryPolicy(RetryPolicy delegate) {
		this.delegate = delegate;
	}

	/**
	 * Timeout for resetting circuit in milliseconds. After the circuit opens it will
	 * re-close after this time has elapsed and the context will be restarted.
	 * @param timeout the timeout to set in milliseconds
	 */
	public void setResetTimeout(long timeout) {
		this.resetTimeout = timeout;
	}

	/**
	 * A supplier for the timeout for resetting circuit in milliseconds. After the circuit
	 * opens it will re-close after this time has elapsed and the context will be
	 * restarted.
	 * @param timeoutSupplier a supplier for the timeout to set in milliseconds
	 * @since 2.0
	 * @deprecated in favor of {@link #resetTimeoutFunction(Function)}
	 */
	@Deprecated
	public void resetTimeoutSupplier(Supplier<Long> timeoutSupplier) {
		this.resetTimeoutFunction = context -> timeoutSupplier.get();
	}

	/**
	 * A supplier for the timeout for resetting circuit in milliseconds. After the circuit
	 * opens it will re-close after this time has elapsed and the context will be
	 * restarted.
	 * @param timeoutSupplier a supplier for the timeout to set in milliseconds
	 * @since 2.0.3
	 */
	public void resetTimeoutFunction(Function<RetryContext, Long> timeoutSupplier) {
		this.resetTimeoutFunction = timeoutSupplier;
	}

	/**
	 * Timeout for tripping the open circuit. If the delegate policy cannot retry and the
	 * time elapsed since the context was started is less than this window, then the
	 * circuit is opened.
	 * @param timeout the timeout to set in milliseconds
	 */
	public void setOpenTimeout(long timeout) {
		this.openTimeout = timeout;
	}

	/**
	 * A supplier for the Timeout for tripping the open circuit. If the delegate policy
	 * cannot retry and the time elapsed since the context was started is less than this
	 * window, then the circuit is opened.
	 * @param timeoutSupplier a supplier for the timeout to set in milliseconds
	 * @since 2.0
	 * @deprecated in favor of {@link #openTimeoutFunction(Function)}.
	 */
	@Deprecated
	public void openTimeoutSupplier(Supplier<Long> timeoutSupplier) {
		this.openTimeoutFunction = context -> timeoutSupplier.get();
	}

	/**
	 * A supplier for the Timeout for tripping the open circuit. If the delegate policy
	 * cannot retry and the time elapsed since the context was started is less than this
	 * window, then the circuit is opened.
	 * @param openTimeoutFunction a supplier for the timeout to set in milliseconds
	 * @since 2.0.3
	 */
	public void openTimeoutFunction(Function<RetryContext, Long> openTimeoutFunction) {
		this.openTimeoutFunction = openTimeoutFunction;
	}

	@Override
	public boolean canRetry(RetryContext context) {
		CircuitBreakerRetryContext circuit = (CircuitBreakerRetryContext) context;
		if (circuit.isOpen()) {
			circuit.incrementShortCircuitCount();
			return false;
		}
		else {
			circuit.reset();
		}
		return this.delegate.canRetry(circuit.context);
	}

	@Override
	public RetryContext open(RetryContext parent) {
		long resetTimeout = this.resetTimeout;
		if (this.resetTimeoutFunction != null) {
			resetTimeout = this.resetTimeoutFunction.apply(this.context);
		}
		long openTimeout = this.openTimeout;
		if (this.resetTimeoutFunction != null) {
			openTimeout = this.openTimeoutFunction.apply(this.context);
		}
		CircuitBreakerRetryContext context = new CircuitBreakerRetryContext(parent, this.delegate, resetTimeout,
				openTimeout);
		this.context = context;
		return context;
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

	static class CircuitBreakerRetryContext extends RetryContextSupport {

		private volatile RetryContext context;

		private final RetryPolicy policy;

		private volatile long start = System.currentTimeMillis();

		private final long timeout;

		private final long openWindow;

		private final AtomicInteger shortCircuitCount = new AtomicInteger();

		public CircuitBreakerRetryContext(RetryContext parent, RetryPolicy policy, long timeout, long openWindow) {
			super(parent);
			this.policy = policy;
			this.timeout = timeout;
			this.openWindow = openWindow;
			this.context = createDelegateContext(policy, parent);
			setAttribute("state.global", true);
		}

		public void reset() {
			this.shortCircuitCount.set(0);
			setAttribute(CIRCUIT_SHORT_COUNT, this.shortCircuitCount.get());
		}

		public void incrementShortCircuitCount() {
			this.shortCircuitCount.incrementAndGet();
			setAttribute(CIRCUIT_SHORT_COUNT, this.shortCircuitCount.get());
		}

		private RetryContext createDelegateContext(RetryPolicy policy, RetryContext parent) {
			RetryContext context = policy.open(parent);
			reset();
			return context;
		}

		public boolean isOpen() {
			long time = System.currentTimeMillis() - this.start;
			boolean retryable = this.policy.canRetry(this.context);
			if (!retryable) {
				if (time > this.timeout) {
					logger.trace("Closing");
					this.context = createDelegateContext(this.policy, getParent());
					this.start = System.currentTimeMillis();
					retryable = this.policy.canRetry(this.context);
				}
				else if (time < this.openWindow) {
					if (!hasAttribute(CIRCUIT_OPEN) || (Boolean) getAttribute(CIRCUIT_OPEN) == false) {
						logger.trace("Opening circuit");
						setAttribute(CIRCUIT_OPEN, true);
						this.start = System.currentTimeMillis();
					}

					return true;
				}
			}
			else {
				if (time > this.openWindow) {
					logger.trace("Resetting context");
					this.start = System.currentTimeMillis();
					this.context = createDelegateContext(this.policy, getParent());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Open: " + !retryable);
			}
			setAttribute(CIRCUIT_OPEN, !retryable);
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
