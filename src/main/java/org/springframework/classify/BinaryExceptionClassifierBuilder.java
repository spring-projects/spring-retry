/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.classify;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.Assert;

/**
 * Fluent API for BinaryExceptionClassifier configuration.
 * <p>
 * Can be used in while list style: <pre>{@code
 * BinaryExceptionClassifier.newBuilder()
 * 			.retryOn(IOException.class)
 * 			.retryOn(IllegalArgumentException.class)
 * 			.build();
 * } </pre> or in black list style: <pre>{@code
 * BinaryExceptionClassifier.newBuilder()
 *            .notRetryOn(Error.class)
 *            .build();
 * } </pre>
 * <p>
 * Provides traverseCauses=false by default, and no default rules for exceptions.
 * <p>
 * Not thread safe. Building should be performed in a single thread, publishing of newly
 * created instance should be safe.
 *
 * @author Aleksandr Shamukov
 */
public class BinaryExceptionClassifierBuilder {

	/**
	 * Building notation type (white list or black list) - null: has not selected yet -
	 * true: white list - false: black list
	 */
	private Boolean isWhiteList = null;

	private boolean traverseCauses = false;

	private List<Class<? extends Throwable>> exceptionClasses = new ArrayList<Class<? extends Throwable>>();

	public BinaryExceptionClassifierBuilder retryOn(Class<? extends Throwable> throwable) {
		Assert.isTrue(isWhiteList == null || isWhiteList, "Please use only retryOn() or only notRetryOn()");
		Assert.notNull(throwable, "Exception class can not be null");
		isWhiteList = true;
		exceptionClasses.add(throwable);
		return this;

	}

	public BinaryExceptionClassifierBuilder notRetryOn(Class<? extends Throwable> throwable) {
		Assert.isTrue(isWhiteList == null || !isWhiteList, "Please use only retryOn() or only notRetryOn()");
		Assert.notNull(throwable, "Exception class can not be null");
		isWhiteList = false;
		exceptionClasses.add(throwable);
		return this;
	}

	public BinaryExceptionClassifierBuilder traversingCauses() {
		this.traverseCauses = true;
		return this;
	}

	public BinaryExceptionClassifier build() {
		Assert.isTrue(!exceptionClasses.isEmpty(),
				"Attempt to build classifier with empty rules. To build always true, or always false "
						+ "instance, please use explicit rule for Throwable");
		BinaryExceptionClassifier classifier = new BinaryExceptionClassifier(exceptionClasses, isWhiteList // using
																											// white
																											// list
																											// means
																											// classifying
																											// provided
																											// classes
																											// as
																											// "true"
																											// (is
																											// retryable)
		);
		classifier.setTraverseCauses(traverseCauses);
		return classifier;
	}

}
