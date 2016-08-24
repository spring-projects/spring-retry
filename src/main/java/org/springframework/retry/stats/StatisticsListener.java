/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.retry.stats;

import org.springframework.core.AttributeAccessor;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryStatistics;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.CircuitBreakerRetryPolicy;

/**
 * @author Dave Syer
 *
 */
public class StatisticsListener extends RetryListenerSupport {

	private final StatisticsRepository repository;

	public StatisticsListener(StatisticsRepository repository) {
		this.repository = repository;
	}

	@Override
	public <T, E extends Throwable> void close(RetryContext context,
			RetryCallback<T, E> callback, Throwable throwable) {
		String name = getName(context);
		if (name != null) {
			if (!isExhausted(context) || isGlobal(context)) {
				// If exhausted and stateful then the retry callback was not called. If
				// exhausted and stateless it was called, but the started counter was
				// already incremented.
				repository.addStarted(name);
			}
			if (isRecovered(context)) {
				repository.addRecovery(name);
			}
			else if (isExhausted(context)) {
				repository.addAbort(name);
			}
			else if (isClosed(context)) {
				repository.addComplete(name);
			}
			RetryStatistics stats = repository.findOne(name);
			if (stats instanceof AttributeAccessor) {
				AttributeAccessor accessor = (AttributeAccessor) stats;
				for (String key : new String[] { CircuitBreakerRetryPolicy.CIRCUIT_OPEN,
						CircuitBreakerRetryPolicy.CIRCUIT_SHORT_COUNT }) {
					if (context.hasAttribute(key)) {
						accessor.setAttribute(key, context.getAttribute(key));
					}
				}
			}
		}
	}

	@Override
	public <T, E extends Throwable> void onError(RetryContext context,
			RetryCallback<T, E> callback, Throwable throwable) {
		String name = getName(context);
		if (name != null) {
			if (!hasState(context)) {
				// Stateless retry involves starting the retry callback once per error
				// without closing the context, so we need to increment the started count
				repository.addStarted(name);
			}
			repository.addError(name);
		}
	}

	private boolean isGlobal(RetryContext context) {
		return context.hasAttribute("state.global");
	}

	private boolean isExhausted(RetryContext context) {
		return context.hasAttribute(RetryContext.EXHAUSTED);
	}

	private boolean isClosed(RetryContext context) {
		return context.hasAttribute(RetryContext.CLOSED);
	}

	private boolean isRecovered(RetryContext context) {
		return context.hasAttribute(RetryContext.RECOVERED);
	}

	private boolean hasState(RetryContext context) {
		return context.hasAttribute(RetryContext.STATE_KEY);
	}

	private String getName(RetryContext context) {
		return (String) context.getAttribute(RetryContext.NAME);
	}
}
