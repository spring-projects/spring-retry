/*
 * Copyright 2014 the original author or authors.
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
 * @since 1.1
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retryable {

	/**
	 * Retry interceptor bean name to be applied for retryable method.
	 * Is mutually exclusive with other attributes.
	 * @return the retry interceptor bean name
	 */
	String interceptor() default "";

	/**
	 * Exception types that are retryable. Synonym for includes(). Defaults to
	 * empty (and if excludes is also empty all exceptions are retried).
	 * @return exception types to retry
	 */
	Class<? extends Throwable>[] value() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and if excludes is
	 * also empty all exceptions are retried).
	 * @return exception types to retry
	 */
	Class<? extends Throwable>[] include() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and if
	 * includes is also empty all exceptions are retried).
	 * @return exception types to retry
	 */
	Class<? extends Throwable>[] exclude() default {};

	/**
	 * Flag to say that the retry is stateful: i.e. exceptions are re-thrown,
	 * but the retry policy is applied with the same policy to subsequent
	 * invocations with the same arguments. If false then retryable exceptions
	 * are not re-thrown.
	 * @return true if retry is stateful, default false
	 */
	boolean stateful() default false;

	/**
	 * @return the maximum number of attempts (including the first failure),
	 *         defaults to 3
	 */
	int maxAttempts() default 3;

	/**
	 * Specify the backoff properties for retrying this operation. The default is
	 * no backoff, but it can be a good idea to pause between attempts (even at
	 * the cost of blocking a thread).
	 * @return a backoff specification
	 */
	Backoff backoff() default @Backoff();

}
