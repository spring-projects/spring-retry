/*
 * Copyright 2012-2013 the original author or authors.
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

import org.springframework.context.annotation.Import;

/**
 * Annotation for a method invocation that is a recovery handler. A suitable recovery
 * handler has a first parameter of type Throwable (or a subtype of Throwable) and a
 * return value of the same type as the <code>@Retryable</code> method to recover from.
 * The Throwable first argument is optional (but a method without it will only be called
 * if no others match). Subsequent arguments are populated from the argument list of the
 * failed method in order.
 *
 * @author Dave Syer
 * @since 2.0
 *
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Import(RetryConfiguration.class)
@Documented
public @interface Recover {

}
