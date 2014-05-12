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

package org.springframework.retry.interceptor;

/**
 * Marker interface for proxies that are providing retryable behaviour. Can be added by
 * proxy creators that use the {@link RetryOperationsInterceptor} and
 * {@link StatefulRetryOperationsInterceptor}.
 *
 * @author Dave Syer
 * @since 1.1
 */
public interface Retryable {

}
