/*
 * Copyright 2006-2022 the original author or authors.
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

import org.junit.jupiter.api.Test;

import org.springframework.classify.annotation.Classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dave Syer
 *
 */
public class ClassifierAdapterTests {

	private ClassifierAdapter<String, Integer> adapter = new ClassifierAdapter<>();

	@Test
	public void testClassifierAdapterObject() {
		adapter = new ClassifierAdapter<>(new Object() {
			@Classifier
			public Integer getValue(String key) {
				return Integer.parseInt(key);
			}

			@SuppressWarnings("unused")
			public Integer getAnother(String key) {
				throw new UnsupportedOperationException("Not allowed");
			}
		});
		assertThat(adapter.classify("23").intValue()).isEqualTo(23);
	}

	@Test
	public void testClassifierAdapterObjectWithNoAnnotation() {
		assertThatIllegalStateException().isThrownBy(() -> new ClassifierAdapter<>(new Object() {
			@SuppressWarnings("unused")
			public Integer getValue(String key) {
				return Integer.parseInt(key);
			}

			@SuppressWarnings("unused")
			public Integer getAnother(String key) {
				throw new UnsupportedOperationException("Not allowed");
			}
		}));
	}

	@Test
	public void testClassifierAdapterObjectSingleMethodWithNoAnnotation() {
		adapter = new ClassifierAdapter<>(new Object() {
			@SuppressWarnings("unused")
			public Integer getValue(String key) {
				return Integer.parseInt(key);
			}

			@SuppressWarnings("unused")
			public void doNothing(String key) {
			}

			@SuppressWarnings("unused")
			public String doNothing(String key, int value) {
				return "foo";
			}
		});
		assertEquals(23, adapter.classify("23").intValue());
	}

	@SuppressWarnings({ "serial" })
	@Test
	public void testClassifierAdapterClassifier() {
		adapter = new ClassifierAdapter<>(Integer::valueOf);
		assertEquals(23, adapter.classify("23").intValue());
	}

	@Test
	public void testClassifyWithSetter() {
		adapter.setDelegate(new Object() {
			@Classifier
			public Integer getValue(String key) {
				return Integer.parseInt(key);
			}
		});
		assertEquals(23, adapter.classify("23").intValue());
	}

	@Test
	public void testClassifyWithWrongType() {
		adapter.setDelegate(new Object() {
			@Classifier
			public String getValue(Integer key) {
				return key.toString();
			}
		});
		assertThatIllegalArgumentException().isThrownBy(() -> adapter.classify("23"));
	}

	@Test
	public void testClassifyWithClassifier() {
		adapter.setDelegate(Integer::valueOf);
		assertEquals(23, adapter.classify("23").intValue());
	}

}
