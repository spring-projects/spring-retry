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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.concurrent.TimeoutException;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.retry.support.RetryTemplate;

/**
 * @author Aleksandr Shamukov
 */
public class BinaryExceptionClassifierBuilderTest {

	@Test
	public void testWhiteList() {
		RetryTemplate.builder().infiniteRetry().retryOn(IOException.class).uniformRandomBackoff(1000, 3000).build();

		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder().retryOn(IOException.class)
				.retryOn(TimeoutException.class).build();

		Assert.assertTrue(classifier.classify(new IOException()));
		// should not retry due to traverseCauses=fasle
		Assert.assertFalse(classifier.classify(new RuntimeException(new IOException())));
		Assert.assertTrue(classifier.classify(new StreamCorruptedException()));
		Assert.assertFalse(classifier.classify(new OutOfMemoryError()));
	}

	@Test
	public void testWhiteListWithTraverseCauses() {
		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder().retryOn(IOException.class)
				.retryOn(TimeoutException.class).traversingCauses().build();

		Assert.assertTrue(classifier.classify(new IOException()));
		// should retry due to traverseCauses=true
		Assert.assertTrue(classifier.classify(new RuntimeException(new IOException())));
		Assert.assertTrue(classifier.classify(new StreamCorruptedException()));
		// should retry due to FileNotFoundException is a subclass of TimeoutException
		Assert.assertTrue(classifier.classify(new FileNotFoundException()));
		Assert.assertFalse(classifier.classify(new RuntimeException()));
	}

	@Test
	public void testBlackList() {
		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder().notRetryOn(Error.class)
				.notRetryOn(InterruptedException.class).traversingCauses().build();

		// should not retry due to OutOfMemoryError is a subclass of Error
		Assert.assertFalse(classifier.classify(new OutOfMemoryError()));
		Assert.assertFalse(classifier.classify(new InterruptedException()));
		Assert.assertTrue(classifier.classify(new Throwable()));
		// should retry due to traverseCauses=true
		Assert.assertFalse(classifier.classify(new RuntimeException(new InterruptedException())));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testFailOnNotationMix() {
		BinaryExceptionClassifier.builder().retryOn(IOException.class).notRetryOn(OutOfMemoryError.class);
	}

}
