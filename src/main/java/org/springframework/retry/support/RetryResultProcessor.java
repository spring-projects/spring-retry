/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.retry.support;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author Dave Syer
 * @param <T> the type of result from the retryable operation
 */
public interface RetryResultProcessor<T> {

	Result<T> process(T input, Supplier<Result<T>> supplier, Consumer<Throwable> handler);

	public static class Result<T> {

		public Throwable exception;

		private T result;

		private boolean complete;

		public Result(Throwable exception) {
			this.exception = exception;
			this.complete = false;
		}

		public Result(T result) {
			this.result = result;
			this.complete = true;
		}

		boolean isComplete() {
			return this.complete;
		}

		public Throwable getException() {
			return exception;
		}

		public T getResult() {
			return result;
		}

	}

}
