/*
 * Copyright 2006-2013 the original author or authors.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.beans.DirectFieldAccessor;

public class BinaryExceptionClassifierTests {

	BinaryExceptionClassifier classifier = new BinaryExceptionClassifier(false);

	@Test
	public void testClassifyNullIsDefault() {
		assertFalse(classifier.classify(null));
	}

	@Test
	public void testFalseIsDefault() {
		assertFalse(classifier.getDefault());
	}

	@Test
	public void testDefaultProvided() {
		classifier = new BinaryExceptionClassifier(true);
		assertTrue(classifier.getDefault());
	}

	@Test
	public void testClassifyRandomException() {
		assertFalse(classifier.classify(new IllegalStateException("foo")));
	}

	@Test
	public void testClassifyExactMatch() {
		Collection<Class<? extends Throwable>> set = Collections
				.<Class<? extends Throwable>>singleton(IllegalStateException.class);
		assertTrue(new BinaryExceptionClassifier(set).classify(new IllegalStateException("Foo")));
	}

	@Test
	public void testClassifyExactMatchInCause() {
		Collection<Class<? extends Throwable>> set = Collections
				.<Class<? extends Throwable>>singleton(IllegalStateException.class);
		BinaryExceptionClassifier binaryExceptionClassifier = new BinaryExceptionClassifier(set);
		binaryExceptionClassifier.setTraverseCauses(true);
		assertTrue(binaryExceptionClassifier.classify(new RuntimeException(new IllegalStateException("Foo"))));
	}

	@Test
	public void testClassifySubclassMatchInCause() {
		Collection<Class<? extends Throwable>> set = Collections
				.<Class<? extends Throwable>>singleton(IllegalStateException.class);
		BinaryExceptionClassifier binaryExceptionClassifier = new BinaryExceptionClassifier(set);
		binaryExceptionClassifier.setTraverseCauses(true);
		assertTrue(binaryExceptionClassifier.classify(new RuntimeException(new FooException("Foo"))));
	}

	@Test
	public void testClassifySubclassMatchInCauseFalse() {
		Map<Class<? extends Throwable>, Boolean> map = new HashMap<Class<? extends Throwable>, Boolean>();
		map.put(IllegalStateException.class, true);
		map.put(BarException.class, false);
		BinaryExceptionClassifier binaryExceptionClassifier = new BinaryExceptionClassifier(map, true);
		binaryExceptionClassifier.setTraverseCauses(true);
		assertTrue(
				binaryExceptionClassifier.classify(new RuntimeException(new FooException("Foo", new BarException()))));
		assertTrue(((Map<?, ?>) new DirectFieldAccessor(binaryExceptionClassifier).getPropertyValue("classified"))
				.containsKey(FooException.class));
	}

	@Test
	public void testTypesProvidedInConstructor() {
		classifier = new BinaryExceptionClassifier(
				Collections.<Class<? extends Throwable>>singleton(IllegalStateException.class));
		assertTrue(classifier.classify(new IllegalStateException("Foo")));
	}

	@Test
	public void testTypesProvidedInConstructorWithNonDefault() {
		classifier = new BinaryExceptionClassifier(
				Collections.<Class<? extends Throwable>>singleton(IllegalStateException.class), false);
		assertFalse(classifier.classify(new IllegalStateException("Foo")));
	}

	@Test
	public void testTypesProvidedInConstructorWithNonDefaultInCause() {
		classifier = new BinaryExceptionClassifier(
				Collections.<Class<? extends Throwable>>singleton(IllegalStateException.class), false);
		classifier.setTraverseCauses(true);
		assertFalse(classifier.classify(new RuntimeException(new RuntimeException(new IllegalStateException("Foo")))));
	}

	@SuppressWarnings("serial")
	private class FooException extends IllegalStateException {

		private FooException(String s) {
			super(s);
		}

		private FooException(String s, Throwable t) {
			super(s, t);
		}

	}

	@SuppressWarnings("serial")
	private class BarException extends RuntimeException {

		private BarException() {
			super();
		}

	}

}
