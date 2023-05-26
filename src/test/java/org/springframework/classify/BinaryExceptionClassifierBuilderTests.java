/*
 * Copyright 2006-2023 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * @author Aleksandr Shamukov
 */
public class BinaryExceptionClassifierBuilderTests {

	@Test
	public void testWhiteList() {
		RetryTemplate.builder().infiniteRetry().retryOn(IOException.class).uniformRandomBackoff(1000, 3000).build();

		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder()
			.retryOn(IOException.class)
			.retryOn(TimeoutException.class)
			.build();

		assertThat(classifier.classify(new IOException())).isTrue();
		// should not retry due to traverseCauses=fasle
		assertThat(classifier.classify(new RuntimeException(new IOException()))).isFalse();
		assertThat(classifier.classify(new StreamCorruptedException())).isTrue();
		assertThat(classifier.classify(new OutOfMemoryError())).isFalse();
	}

	@Test
	public void testWhiteListWithTraverseCauses() {
		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder()
			.retryOn(IOException.class)
			.retryOn(TimeoutException.class)
			.traversingCauses()
			.build();

		assertThat(classifier.classify(new IOException())).isTrue();
		// should retry due to traverseCauses=true
		assertThat(classifier.classify(new RuntimeException(new IOException()))).isTrue();
		assertThat(classifier.classify(new StreamCorruptedException())).isTrue();
		// should retry due to FileNotFoundException is a subclass of TimeoutException
		assertThat(classifier.classify(new FileNotFoundException())).isTrue();
		assertThat(classifier.classify(new RuntimeException())).isFalse();
	}

	@Test
	public void testBlackList() {
		BinaryExceptionClassifier classifier = BinaryExceptionClassifier.builder()
			.notRetryOn(Error.class)
			.notRetryOn(InterruptedException.class)
			.traversingCauses()
			.build();

		// should not retry due to OutOfMemoryError is a subclass of Error
		assertThat(classifier.classify(new OutOfMemoryError())).isFalse();
		assertThat(classifier.classify(new InterruptedException())).isFalse();
		assertThat(classifier.classify(new Throwable())).isTrue();
		// should retry due to traverseCauses=true
		assertThat(classifier.classify(new RuntimeException(new InterruptedException()))).isFalse();
	}

	@Test
	public void testFailOnNotationMix() {
		assertThatIllegalArgumentException().isThrownBy(() -> BinaryExceptionClassifier.builder()
			.retryOn(IOException.class)
			.notRetryOn(OutOfMemoryError.class));
	}

}
