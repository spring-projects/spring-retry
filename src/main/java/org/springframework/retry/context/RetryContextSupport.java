/*
 * Copyright 2006-2023 the original author or authors.
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

package org.springframework.retry.context;

import org.springframework.core.AttributeAccessorSupport;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;

/**
 * @author Dave Syer
 * @author Gary Russell
 */
@SuppressWarnings("serial")
public class RetryContextSupport extends AttributeAccessorSupport implements RetryContext {

	private final RetryContext parent;

	private volatile boolean terminate = false;

	private volatile int count;

	private volatile Throwable lastException;

	public RetryContextSupport(RetryContext parent) {
		super();
		this.parent = parent;
		if (parent != null) {
			setAttribute(RetryContext.KEY, parent.getAttribute(RetryContext.KEY));
		}
	}

	@Override
	public RetryContext getParent() {
		return this.parent;
	}

	@Override
	public boolean isExhaustedOnly() {
		return this.terminate;
	}

	@Override
	public void setExhaustedOnly() {
		this.terminate = true;
	}

	@Override
	public int getRetryCount() {
		return this.count;
	}

	@Override
	public Throwable getLastThrowable() {
		return this.lastException;
	}

	/**
	 * Set the exception for the public interface {@link RetryContext}, and also increment
	 * the retry count if the throwable is non-null.
	 *
	 * All {@link RetryPolicy} implementations should use this method when they register
	 * the throwable. It should only be called once per retry attempt because it
	 * increments a counter.
	 *
	 * Use of this method is not enforced by the framework - it is a service provider
	 * contract for authors of policies.
	 * @param throwable the exception that caused the current retry attempt to fail.
	 */
	public void registerThrowable(Throwable throwable) {
		this.lastException = throwable;
		if (throwable != null) {
			this.count++;
		}
	}

	@Override
	public String toString() {
		return String.format("[RetryContext: count=%d, lastException=%s, exhausted=%b]", this.count, this.lastException,
				this.terminate);
	}

}
