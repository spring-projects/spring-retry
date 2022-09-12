/*
 * Copyright 2014-2022 the original author or authors.
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

/**
 * Annotation for a method invocation that is retryable.
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Gary Russell
 * @author Maksim Kita
 * @since 1.1
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retryable {

	/**
	 * Name of method in this class to use for recover. Method had to be marked with
	 * {@link Recover} annotation.
	 * @return the name of recover method
	 */
	String recover() default "";

	/**
	 * Retry interceptor bean name to be applied for retryable method. Is mutually
	 * exclusive with other attributes.
	 * @return the retry interceptor bean name
	 */
	String interceptor() default "";

	/**
	 * Exception types that are retryable. Synonym for include(). Defaults to empty (and
	 * if excludes is also empty all exceptions are retried).
	 * @return exception types to retry
	 * @deprecated in favor of {@link #retryFor()}
	 */
	@Deprecated
	Class<? extends Throwable>[] value() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and, if exclude is also
	 * empty, all exceptions are retried).
	 * @return exception types to retry
	 * @deprecated in favor of {@link #retryFor()}.
	 */
	@AliasFor("retryFor")
	@Deprecated
	Class<? extends Throwable>[] include() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and, if noRetryFor is also
	 * empty, all exceptions are retried).
	 * @return exception types to retry
	 * @since 2.0
	 */
	@AliasFor("include")
	Class<? extends Throwable>[] retryFor() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and if include is also
	 * empty all exceptions are retried). If includes is empty but excludes is not, all
	 * not excluded exceptions are retried
	 * @return exception types not to retry
	 * @deprecated in favor of {@link #noRetryFor()}.
	 */
	@Deprecated
	@AliasFor("noRetryFor")
	Class<? extends Throwable>[] exclude() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and, if retryFor is also
	 * empty, all exceptions are retried). If retryFor is empty but excludes is not, all
	 * other exceptions are retried
	 * @return exception types not to retry
	 * @since 2.0
	 */
	@AliasFor("exclude")
	Class<? extends Throwable>[] noRetryFor() default {};

	/**
	 * Exception types that are not recoverable; these exceptions are thrown to the caller
	 * without calling any recoverer (immediately if also in {@link #noRetryFor()}).
	 * Defaults to empty.
	 * @return exception types not to retry
	 * @since 2.0
	 */
	Class<? extends Throwable>[] notRecoverable() default {};

	/**
	 * A unique label for statistics reporting. If not provided the caller may choose to
	 * ignore it, or provide a default.
	 * @return the label for the statistics
	 */
	String label() default "";

	/**
	 * Flag to say that the retry is stateful: i.e. exceptions are re-thrown, but the
	 * retry policy is applied with the same policy to subsequent invocations with the
	 * same arguments. If false then retryable exceptions are not re-thrown.
	 * @return true if retry is stateful, default false
	 */
	boolean stateful() default false;

	/**
	 * @return the maximum number of attempts (including the first failure), defaults to 3
	 */
	int maxAttempts() default 3;

	/**
	 * @return an expression evaluated to the maximum number of attempts (including the
	 * first failure), defaults to 3 Overrides {@link #maxAttempts()}. Use {@code #{...}}
	 * for one-time evaluation during initialization, omit the delimiters for evaluation
	 * at runtime.
	 * @since 1.2
	 */
	String maxAttemptsExpression() default "";

	/**
	 * Specify the backoff properties for retrying this operation. The default is a simple
	 * {@link Backoff} specification with no properties - see its documentation for
	 * defaults.
	 * @return a backoff specification
	 */
	Backoff backoff() default @Backoff();

	/**
	 * Specify an expression to be evaluated after the
	 * {@code SimpleRetryPolicy.canRetry()} returns true - can be used to conditionally
	 * suppress the retry. Only invoked after an exception is thrown. The root object for
	 * the evaluation is the last {@code Throwable}. Other beans in the context can be
	 * referenced. For example: <pre class=code>
	 *  {@code "message.contains('you can retry this')"}.
	 * </pre> and <pre class=code>
	 *  {@code "@someBean.shouldRetry(#root)"}.
	 * </pre>
	 * @return the expression.
	 * @since 1.2
	 */
	String exceptionExpression() default "";

	/**
	 * Bean names of retry listeners to use instead of default ones defined in Spring
	 * context
	 * @return retry listeners bean names
	 */
	String[] listeners() default {};

}
