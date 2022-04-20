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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PatternMatchingClassifierTests {

	private PatternMatchingClassifier<String> classifier = new PatternMatchingClassifier<>();

	private Map<String, String> map;

	@BeforeEach
	public void createMap() {
		map = new HashMap<>();
		map.put("foo", "bar");
		map.put("*", "spam");
	}

	@Test
	public void testSetPatternMap() {
		classifier.setPatternMap(map);
		assertThat(classifier.classify("foo")).isEqualTo("bar");
		assertThat(classifier.classify("bucket")).isEqualTo("spam");
	}

	@Test
	public void testCreateFromMap() {
		classifier = new PatternMatchingClassifier<>(map);
		assertThat(classifier.classify("foo")).isEqualTo("bar");
		assertThat(classifier.classify("bucket")).isEqualTo("spam");
	}

}
