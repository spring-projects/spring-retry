/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.retry.support;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import org.springframework.lang.Nullable;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.util.Assert;

/**
 * The {@link RetryListener} implementation for Micrometer {@link Timer}s around retry
 * operations.
 * <p>
 * The {@link Timer#start} is called from the {@link #open(RetryContext, RetryCallback)}
 * and stopped in the {@link #close(RetryContext, RetryCallback, Throwable)}. This
 * {@link Timer.Sample} is associated with the provided {@link RetryContext} to make this
 * {@link MetricsRetryListener} instance reusable for many retry operation.
 * <p>
 * The registered {@value #TIMER_NAME} {@link Timer} has these tags by default:
 * <ul>
 * <li>{@code name} - {@link RetryCallback#getLabel()}</li>
 * <li>{@code retry.count} - the number of attempts - 1; essentially the successful first
 * call means no counts</li>
 * <li>{@code exception} - the thrown back to the caller (after all the retry attempts)
 * exception class name</li>
 * </ul>
 * <p>
 * The {@link #setCustomTags(Iterable)} and {@link #setCustomTagsProvider(Function)} can
 * be used to further customize tags on the timers.
 *
 * @author Artem Bilan
 * @author Huijin Hong
 * @since 2.0.8
 */
public class MetricsRetryListener implements RetryListener {

	public static final String TIMER_NAME = "spring.retry";

	private final MeterRegistry meterRegistry;

	private final Map<RetryContext, Timer.Sample> retryContextToSample = Collections
		.synchronizedMap(new IdentityHashMap<>());

	private Tags customTags = Tags.empty();

	private Function<RetryContext, Iterable<Tag>> customTagsProvider = retryContext -> Tags.empty();

	/**
	 * Construct an instance based on the provided {@link MeterRegistry}.
	 * @param meterRegistry the {@link MeterRegistry} to use for timers.
	 */
	public MetricsRetryListener(MeterRegistry meterRegistry) {
		Assert.notNull(meterRegistry, "'meterRegistry' must not be null");
		this.meterRegistry = meterRegistry;
	}

	/**
	 * Supply tags which are going to be used for all the timers managed by this listener.
	 * @param customTags the list of additional tags for all the timers.
	 */
	public void setCustomTags(@Nullable Iterable<Tag> customTags) {
		this.customTags = this.customTags.and(customTags);
	}

	/**
	 * Supply a {@link Function} to build additional tags for all the timers based on the
	 * {@link RetryContext}.
	 * @param customTagsProvider the {@link Function} for additional tags with a
	 * {@link RetryContext} scope.
	 */
	public void setCustomTagsProvider(Function<RetryContext, Iterable<Tag>> customTagsProvider) {
		Assert.notNull(customTagsProvider, "'customTagsProvider' must not be null");
		this.customTagsProvider = customTagsProvider;
	}

	@Override
	public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
		this.retryContextToSample.put(context, Timer.start(this.meterRegistry));
		return true;
	}

	@Override
	public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback,
			@Nullable Throwable throwable) {

		Timer.Sample sample = this.retryContextToSample.remove(context);

		Assert.state(sample != null,
				() -> String.format("No 'Timer.Sample' registered for '%s'. Was the 'open()' called?", context));

		Tags retryTags = Tags.of("name", callback.getLabel())
			.and("retry.count", "" + context.getRetryCount())
			.and(this.customTags)
			.and(this.customTagsProvider.apply(context))
			.and("exception", throwable != null ? throwable.getClass().getSimpleName() : "none");

		Timer.Builder timeBuilder = Timer.builder(TIMER_NAME)
			.description("Metrics for Spring RetryTemplate")
			.tags(retryTags);

		sample.stop(timeBuilder.register(this.meterRegistry));
	}

}
