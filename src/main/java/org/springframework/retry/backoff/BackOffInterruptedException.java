/*
 * Copyright 2006-2025 the original author or authors.
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

package org.springframework.retry.backoff;

import org.springframework.retry.RetryException;

/**
 * Exception class signifying that an attempt to back off using a {@link BackOffPolicy}
 * was interrupted, most likely by an {@link InterruptedException} during a call to
 * {@link Thread#sleep(long)}.
 *
 * @author Rob Harrop
 * @since 2.1
 */
@SuppressWarnings("serial")
public class BackOffInterruptedException extends RetryException {

	public BackOffInterruptedException(String msg) {
		super(msg);
	}

	public BackOffInterruptedException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
