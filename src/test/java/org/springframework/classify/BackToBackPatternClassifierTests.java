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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.classify.annotation.Classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Dave Syer
 *
 */
public class BackToBackPatternClassifierTests {

	private BackToBackPatternClassifier<String, String> classifier = new BackToBackPatternClassifier<>();

	private Map<String, String> map;

	@BeforeEach
	public void createMap() {
		map = new HashMap<>();
		map.put("foo", "bar");
		map.put("*", "spam");
	}

	@Test
	public void testNoClassifiers() {
		assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> classifier.classify("foo"));
	}

	@Test
	public void testCreateFromConstructor() {
		classifier = new BackToBackPatternClassifier<>(
				new PatternMatchingClassifier<>(Collections.singletonMap("oof", "bucket")),
				new PatternMatchingClassifier<>(map));
		assertThat(classifier.classify("oof")).isEqualTo("spam");
	}

	@Test
	public void testSetRouterDelegate() {
		classifier.setRouterDelegate(new Object() {
			@Classifier
			public String convert(String value) {
				return "bucket";
			}
		});
		classifier.setMatcherMap(map);
		assertThat(classifier.classify("oof")).isEqualTo("spam");
	}

	@Test
	public void testSingleMethodWithNoAnnotation() {
		classifier = new BackToBackPatternClassifier<>();
		classifier.setRouterDelegate(new RouterDelegate());
		classifier.setMatcherMap(map);
		assertThat(classifier.classify("oof")).isEqualTo("spam");
	}

	@SuppressWarnings("serial")
	private class RouterDelegate implements org.springframework.classify.Classifier<Object, String> {

		@Override
		public String classify(Object classifiable) {
			return "bucket";
		}

	}

}
