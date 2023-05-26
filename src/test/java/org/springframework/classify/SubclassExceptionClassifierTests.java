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
import java.net.ConnectException;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SubclassExceptionClassifierTests {

	SubclassClassifier<Throwable, String> classifier = new SubclassClassifier<>();

	@Test
	public void testClassifyNullIsDefault() {
		assertThat(this.classifier.getDefault()).isEqualTo(this.classifier.classify(null));
	}

	@Test
	public void testClassifyNull() {
		assertThat(this.classifier.classify(null)).isNull();
	}

	@Test
	public void testClassifyNullNonDefault() {
		this.classifier = new SubclassClassifier<>("foo");
		assertThat(this.classifier.classify(null)).isEqualTo("foo");
	}

	@Test
	public void testClassifyRandomException() {
		assertThat(this.classifier.classify(new IllegalStateException("Foo"))).isNull();
	}

	@Test
	public void testClassifyExactMatch() {
		this.classifier.setTypeMap(
				Collections.<Class<? extends Throwable>, String>singletonMap(IllegalStateException.class, "foo"));
		assertThat(this.classifier.classify(new IllegalStateException("Foo"))).isEqualTo("foo");
	}

	@Test
	public void testClassifySubclassMatch() {
		this.classifier
			.setTypeMap(Collections.<Class<? extends Throwable>, String>singletonMap(RuntimeException.class, "foo"));
		assertThat(this.classifier.classify(new IllegalStateException("Foo"))).isEqualTo("foo");
	}

	@Test
	public void testClassifySuperclassDoesNotMatch() {
		this.classifier.setTypeMap(
				Collections.<Class<? extends Throwable>, String>singletonMap(IllegalStateException.class, "foo"));
		assertThat(this.classifier.classify(new RuntimeException("Foo"))).isEqualTo(this.classifier.getDefault());
	}

	@SuppressWarnings("serial")
	@Test
	public void testClassifyAncestorMatch() {
		this.classifier.setTypeMap(new HashMap<Class<? extends Throwable>, String>() {
			{
				put(Exception.class, "foo");
				put(IllegalArgumentException.class, "bar");
				put(RuntimeException.class, "spam");
			}
		});
		assertThat(this.classifier.classify(new IllegalStateException("Foo"))).isEqualTo("spam");
	}

	@SuppressWarnings("serial")
	@Test
	public void testClassifyAncestorMatch2() {
		this.classifier = new SubclassClassifier<>();
		this.classifier.setTypeMap(new HashMap<Class<? extends Throwable>, String>() {
			{
				put(SocketException.class, "1");
				put(FileNotFoundException.class, "buz");
				put(NoSuchElementException.class, "buz");
				put(ArrayIndexOutOfBoundsException.class, "buz");
				put(IllegalArgumentException.class, "bar");
				put(RuntimeException.class, "spam");
				put(ConnectException.class, "2");
			}
		});
		assertThat(this.classifier.classify(new SubConnectException())).isEqualTo("2");
	}

	public static class SubConnectException extends ConnectException {

	}

}
