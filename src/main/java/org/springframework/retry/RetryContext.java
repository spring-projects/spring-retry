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

package org.springframework.retry;

import org.springframework.core.AttributeAccessor;

/**
 * Low-level access to ongoing retry operation. Normally not needed by clients, but can be
 * used to alter the course of the retry, e.g. force an early termination.
 *
 * @author Dave Syer
 *
 */
public interface RetryContext extends AttributeAccessor {

	/**
	 * Retry context attribute name for reporting key. Can be used for reporting purposes,
	 * for instance in a retry listener, to accumulate data about the performance of a
	 * retry.
	 */
	String NAME = "context.name";

	/**
	 * Retry context attribute name for state key. Can be used to identify a stateful
	 * retry from its context.
	 */
	String STATE_KEY = "context.state";

	/**
	 * Retry context attribute that is non-null (and true) if the context has been closed.
	 */
	String CLOSED = "context.closed";

	/**
	 * Retry context attribute that is non-null (and true) if the recovery path was taken.
	 */
	String RECOVERED = "context.recovered";

	/**
	 * Retry context attribute that is non-null (and true) if the retry was exhausted.
	 */
	String EXHAUSTED = "context.exhausted";

	/**
	 * Retry context attribute that is non-null (and true) if the exception is not
	 * recoverable.
	 */
	String NO_RECOVERY = "context.no-recovery";

	/**
	 * Signal to the framework that no more attempts should be made to try or retry the
	 * current {@link RetryCallback}.
	 */
	void setExhaustedOnly();

	/**
	 * Public accessor for the exhausted flag {@link #setExhaustedOnly()}.
	 * @return true if the flag has been set.
	 */
	boolean isExhaustedOnly();

	/**
	 * Accessor for the parent context if retry blocks are nested.
	 * @return the parent or null if there is none.
	 */
	RetryContext getParent();

	/**
	 * Counts the number of retry attempts. Before the first attempt this counter is zero,
	 * and before the first and subsequent attempts it should increment accordingly.
	 * @return the number of retries.
	 */
	int getRetryCount();

	/**
	 * Accessor for the exception object that caused the current retry.
	 * @return the last exception that caused a retry, or possibly null. It will be null
	 * if this is the first attempt, but also if the enclosing policy decides not to
	 * provide it (e.g. because of concerns about memory usage).
	 */
	Throwable getLastThrowable();

}
