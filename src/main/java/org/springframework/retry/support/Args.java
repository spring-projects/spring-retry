/*
 * Copyright 2022-2025 the original author or authors.
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

/**
 * A root object containing the method arguments to use in expression evaluation.
 * IMPORTANT; the arguments are not available (will contain nulls) until after the first
 * call to the retryable method; this is generally only an issue for the
 * {@code maxAttempts}, meaning the arguments cannot be used to indicate
 * {@code maxAttempts = 0}.
 *
 * @author Gary Russell
 * @since 2.0
 */
public class Args {

	/**
	 * An empty {@link Args} with 100 null arguments.
	 */
	public static final Args NO_ARGS = new Args(new Object[100]);

	private final Object[] args;

	public Args(Object[] args) {
		this.args = args;
	}

	public Object[] getArgs() {
		return args;
	}

}
