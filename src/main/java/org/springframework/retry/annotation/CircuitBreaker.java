/*
 * Copyright 2016-2022 the original author or authors.
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
 * @since 1.2
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Retryable(stateful = true)
public @interface CircuitBreaker {

	/**
	 * Exception types that are retryable. Defaults to empty (and if excludes is also
	 * empty all exceptions are retried).
	 * @return exception types to retry
	 * @deprecated in favor of {@link #retryFor()}
	 */
	@AliasFor(annotation = Retryable.class)
	@Deprecated
	Class<? extends Throwable>[] value() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and if excludes is also
	 * empty all exceptions are retried).
	 * @return exception types to retry
	 * @deprecated in favor of {@link #retryFor()}.
	 */
	@AliasFor(annotation = Retryable.class)
	@Deprecated
	Class<? extends Throwable>[] include() default {};

	/**
	 * Exception types that are retryable. Defaults to empty (and, if noRetryFor is also
	 * empty, all exceptions are retried).
	 * @return exception types to retry
	 * @since 2.0
	 */
	@AliasFor(annotation = Retryable.class)
	Class<? extends Throwable>[] retryFor() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and if includes is also
	 * empty all exceptions are retried). If includes is empty but excludes is not, all
	 * not excluded exceptions are retried
	 * @return exception types not to retry
	 * @deprecated in favor of {@link #noRetryFor()}.
	 */
	@Deprecated
	@AliasFor(annotation = Retryable.class)
	Class<? extends Throwable>[] exclude() default {};

	/**
	 * Exception types that are not retryable. Defaults to empty (and, if retryFor is also
	 * empty, all exceptions are retried). If retryFor is empty but excludes is not, all
	 * other exceptions are retried
	 * @return exception types not to retry
	 * @since 2.0
	 */
	@AliasFor(annotation = Retryable.class)
	Class<? extends Throwable>[] noRetryFor() default {};

	/**
	 * Exception types that are not recoverable; these exceptions are thrown to the caller
	 * without calling any recoverer (immediately if also in {@link #noRetryFor()}).
	 * Defaults to empty.
	 * @return exception types not to retry
	 * @since 2.0
	 */
	@AliasFor(annotation = Retryable.class)
	Class<? extends Throwable>[] notRecoverable() default {};

	/**
	 * @return the maximum number of attempts (including the first failure), defaults to 3
	 */
	@AliasFor(annotation = Retryable.class)
	int maxAttempts() default 3;

	/**
	 * @return an expression evaluated to the maximum number of attempts (including the
	 * first failure), defaults to 3 Overrides {@link #maxAttempts()}. Use {@code #{...}}
	 * for one-time evaluation during initialization, omit the delimiters for evaluation
	 * at runtime.
	 * @since 1.2.3
	 */
	@AliasFor(annotation = Retryable.class)
	String maxAttemptsExpression() default "";

	/**
	 * A unique label for the circuit for reporting and state management. Defaults to the
	 * method signature where the annotation is declared.
	 * @return the label for the circuit
	 */
	@AliasFor(annotation = Retryable.class)
	String label() default "";

	/**
	 * If the circuit is open for longer than this timeout then it resets on the next call
	 * to give the downstream component a chance to respond again.
	 * @return the timeout before an open circuit is reset in milliseconds, defaults to
	 * 20000
	 */
	long resetTimeout() default 20000;

	/**
	 * If the circuit is open for longer than this timeout then it resets on the next call
	 * to give the downstream component a chance to respond again. Overrides
	 * {@link #resetTimeout()}. Use {@code #{...}} for one-time evaluation during
	 * initialization, omit the delimiters for evaluation at runtime.
	 * @return the timeout before an open circuit is reset in milliseconds, no default.
	 * @since 1.2.3
	 */
	String resetTimeoutExpression() default "";

	/**
	 * When {@link #maxAttempts()} failures are reached within this timeout, the circuit
	 * is opened automatically, preventing access to the downstream component.
	 * @return the timeout before a closed circuit is opened in milliseconds, defaults to
	 * 5000
	 */
	long openTimeout() default 5000;

	/**
	 * When {@link #maxAttempts()} failures are reached within this timeout, the circuit
	 * is opened automatically, preventing access to the downstream component. Overrides
	 * {@link #openTimeout()}. Use {@code #{...}} for one-time evaluation during
	 * initialization, omit the delimiters for evaluation at runtime.
	 * @return the timeout before a closed circuit is opened in milliseconds, no default.
	 * @since 1.2.3
	 */
	String openTimeoutExpression() default "";

	/**
	 * Specify an expression to be evaluated after the
	 * {@code SimpleRetryPolicy.canRetry()} returns true - can be used to conditionally
	 * suppress the retry. Only invoked after an exception is thrown. The root object for
	 * the evaluation is the last {@code Throwable}. Other beans in the context can be
	 * referenced. For example:
	 *
	 * <pre class=code>
	 *  {@code "message.contains('you can retry this')"}.
	 * </pre>
	 *
	 * and
	 *
	 * <pre class=code>
	 *  {@code "@someBean.shouldRetry(#root)"}.
	 * </pre>
	 * @return the expression.
	 * @since 1.2.3
	 */
	@AliasFor(annotation = Retryable.class)
	String exceptionExpression() default "";

}
