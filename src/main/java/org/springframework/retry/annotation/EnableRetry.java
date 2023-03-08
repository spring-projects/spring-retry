/*
 * Copyright 2012-2023 the original author or authors.
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

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AliasFor;

/**
 * Global enabler for <code>@Retryable</code> annotations in Spring beans. If this is
 * declared on any <code>@Configuration</code> in the context then beans that have
 * retryable methods will be proxied and the retry handled according to the metadata in
 * the annotations.
 *
 * @author Dave Syer
 * @author Yanming Zhou
 * @author Ruslan Stelmachenko
 * @since 1.1
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableAspectJAutoProxy(proxyTargetClass = false)
@Import(RetryConfiguration.class)
@Documented
public @interface EnableRetry {

	/**
	 * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed to
	 * standard Java interface-based proxies. The default is {@code false}.
	 * @return whether to proxy or not to proxy the class
	 */
	@AliasFor(annotation = EnableAspectJAutoProxy.class)
	boolean proxyTargetClass() default false;

	/**
	 * Indicate the order in which the {@link RetryConfiguration} AOP <b>advice</b> should
	 * be applied.
	 * <p>
	 * The default is {@code Ordered.LOWEST_PRECEDENCE - 1} in order to make sure the
	 * advice is applied before other advices with {@link Ordered#LOWEST_PRECEDENCE} order
	 * (e.g. an advice responsible for {@code @Transactional} behavior).
	 */
	int order() default Ordered.LOWEST_PRECEDENCE - 1;

}
