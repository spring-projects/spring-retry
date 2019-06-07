/*
 * Copyright 2006-2007 the original author or authors.
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

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.classify.annotation.Classifier;

/**
 * @author Dave Syer
 *
 */
public class BackToBackPatternClassifierTests {

	private BackToBackPatternClassifier<String, String> classifier = new BackToBackPatternClassifier<String, String>();

	private Map<String, String> map;

	@Before
	public void createMap() {
		map = new HashMap<String, String>();
		map.put("foo", "bar");
		map.put("*", "spam");
	}

	@Test(expected = NullPointerException.class)
	public void testNoClassifiers() {
		classifier.classify("foo");
	}

	@Test
	public void testCreateFromConstructor() {
		classifier = new BackToBackPatternClassifier<String, String>(
				new PatternMatchingClassifier<String>(Collections.singletonMap("oof", "bucket")),
				new PatternMatchingClassifier<String>(map));
		assertEquals("spam", classifier.classify("oof"));
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
		assertEquals("spam", classifier.classify("oof"));
	}

	@Test
	public void testSingleMethodWithNoAnnotation() {
		classifier = new BackToBackPatternClassifier<String, String>();
		classifier.setRouterDelegate(new RouterDelegate());
		classifier.setMatcherMap(map);
		assertEquals("spam", classifier.classify("oof"));
	}

	@SuppressWarnings("serial")
	private class RouterDelegate implements org.springframework.classify.Classifier<Object, String> {

		@Override
		public String classify(Object classifiable) {
			return "bucket";
		}

	}

}
