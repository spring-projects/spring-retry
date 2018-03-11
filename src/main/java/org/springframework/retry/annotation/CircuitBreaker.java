/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.retry.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for a method invocation that is retryable.
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @since 1.2
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Retryable(stateful = true)
public @interface CircuitBreaker {

	/**
	 * Exception types that are retryable. Synonym for includes(). Defaults to empty (and
	 * if excludes is also empty all exceptions are retried).
	 * @return exception types to retry
	 */
	Class<? extends Throwable>[] value() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and if excludes is also
	 * empty all exceptions are retried).
	 * @return exception types to retry
	 */
	Class<? extends Throwable>[] include() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and if includes is also
	 * empty all exceptions are retried).
	 * If includes is empty but excludes is not, all not excluded exceptions are retried
	 * @return exception types not to retry
	 */
	Class<? extends Throwable>[] exclude() default {};

	/**
	 * @return the maximum number of attempts (including the first failure), defaults to 3
	 */
	int maxAttempts() default 3;

	/**
	 * A unique label for the circuit for reporting and state management. Defaults to the
	 * method signature where the annotation is declared.
	 *
	 * @return the label for the circuit
	 */
	String label() default "";

	/**
	 * If the circuit is open for longer than this timeout then it resets on the next call
	 * to give the downstream component a chance to respond again.
	 *
	 * @return the timeout before an open circuit is reset in milliseconds, defaults to
	 * 20000
	 */
	long resetTimeout() default 20000;

	/**
	 * When {@link #maxAttempts()} failures are reached within this timeout, the circuit
	 * is opened automatically, preventing access to the downstream component.
	 *
	 * @return the timeout before an closed circuit is opened in milliseconds, defaults to
	 * 5000
	 */
	long openTimeout() default 5000;

}
