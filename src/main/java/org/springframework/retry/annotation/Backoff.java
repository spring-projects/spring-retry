/*
 * Copyright 2012-2022 the original author or authors.
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

package org.springframework.retry.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.retry.backoff.BackOffPolicy;

/**
 * Collects metadata for a {@link BackOffPolicy}. Features:
 *
 * <ul>
 * <li>With no explicit settings the default is a fixed delay of 1000ms</li>
 * <li>Only the {@link #delay()} set: the backoff is a fixed delay with that value</li>
 * <li>When {@link #delay()} and {@link #maxDelay()} are set the backoff is uniformly
 * distributed between the two values</li>
 * <li>With {@link #delay()}, {@link #maxDelay()} and {@link #multiplier()} the backoff is
 * exponentially growing up to the maximum value</li>
 * <li>If, in addition, the {@link #random()} flag is set then the multiplier is chosen
 * for each delay from a uniform distribution in [1, multiplier-1]</li>
 * </ul>
 *
 * @author Dave Syer
 * @author Gary Russell
 * @since 1.1
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Backoff {

	/**
	 * Synonym for {@link #delay()}. When {@link #delay()} is non-zero, value of this
	 * element is ignored, otherwise value of this element is taken.
	 * @return the delay in milliseconds (default 1000)
	 */
	@AliasFor("delay")
	long value() default 1000;

	/**
	 * A canonical backoff period. Used as an initial value in the exponential case, and
	 * as a minimum value in the uniform case. When the value of this element is 0, value
	 * of element {@link #value()} is taken, otherwise value of this element is taken and
	 * {@link #value()} is ignored.
	 * @return the initial or canonical backoff period in milliseconds (default 1000)
	 */
	@AliasFor("value")
	long delay() default 1000;

	/**
	 * The maximum wait (in milliseconds) between retries. If less than the
	 * {@link #delay()} then the default of
	 * {@value org.springframework.retry.backoff.ExponentialBackOffPolicy#DEFAULT_MAX_INTERVAL}
	 * is applied.
	 * @return the maximum delay between retries (default 0 = ignored)
	 */
	long maxDelay() default 0;

	/**
	 * If greater than 1.0, used as a multiplier for generating the next delay for
	 * backoff; any value less than or equal to 1.0 is treated as 1.0, meaning a fixed
	 * delay.
	 * @return a multiplier to use to calculate the next backoff delay (default 0 =
	 * ignored)
	 */
	double multiplier() default 0;

	/**
	 * An expression evaluating to the canonical backoff period. Used as an initial value
	 * in the exponential case, and as a minimum value in the uniform case. Overrides
	 * {@link #delay()}. Use {@code #{...}} for one-time evaluation during initialization,
	 * omit the delimiters for evaluation at runtime.
	 * @return the initial or canonical backoff period in milliseconds.
	 * @since 1.2
	 */
	String delayExpression() default "";

	/**
	 * An expression evaluating to the maximum wait (in milliseconds) between retries. If
	 * less than the {@link #delay()} then the default of
	 * {@value org.springframework.retry.backoff.ExponentialBackOffPolicy#DEFAULT_MAX_INTERVAL}
	 * is applied. Overrides {@link #maxDelay()}. Use {@code #{...}} for one-time
	 * evaluation during initialization, omit the delimiters for evaluation at runtime.
	 * @return the maximum delay between retries (default 0 = ignored)
	 * @since 1.2
	 */
	String maxDelayExpression() default "";

	/**
	 * Evaluates to a value used as a multiplier for generating the next delay for
	 * backoff; ; any value less than or equal to 1.0 is treated as 1.0, meaning a fixed
	 * delay. Overrides {@link #multiplier()}. Use {@code #{...}} for one-time evaluation
	 * during initialization, omit the delimiters for evaluation at runtime.
	 * @return a multiplier expression to use to calculate the next backoff delay (default
	 * 0 = ignored)
	 * @since 1.2
	 */
	String multiplierExpression() default "";

	/**
	 * In the exponential case ({@link #multiplier()} &gt; 1.0) set this to true to have
	 * the backoff delays randomized with jitter, so that the maximum delay is multiplier
	 * times the previous delay and the distribution is uniform between the two values.
	 * @return the flag to signal randomization is required (default false)
	 */
	boolean random() default false;

	/**
	 * Evaluates to a value. In the exponential case ({@link #multiplier()} &gt; 1.0) set
	 * this to true to have the backoff delays randomized, so that the maximum delay is
	 * multiplier times the previous delay and the distribution is uniform between the two
	 * values. Use {@code #{...}} for one-time evaluation during initialization, omit the
	 * delimiters for evaluation at runtime.
	 * @return the flag to signal randomization is required (default false)
	 */
	String randomExpression() default "";

}
