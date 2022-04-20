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

package org.springframework.retry.backoff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.retry.context.RetryContextSupport;
import org.springframework.util.ClassUtils;
import org.springframework.util.SerializationUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class BackOffPolicySerializationTests {

	private static final Log logger = LogFactory.getLog(BackOffPolicySerializationTests.class);

	@SuppressWarnings("deprecation")
	public static Stream<Object[]> policies() {
		List<Object[]> result = new ArrayList<>();
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
		scanner.addIncludeFilter(new AssignableTypeFilter(BackOffPolicy.class));
		scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Test.*")));
		scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Mock.*")));
		scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Configuration.*")));
		Set<BeanDefinition> candidates = scanner.findCandidateComponents("org.springframework.retry");
		for (BeanDefinition beanDefinition : candidates) {
			try {
				result.add(new Object[] {
						BeanUtils.instantiate(ClassUtils.resolveClassName(beanDefinition.getBeanClassName(), null)) });
			}
			catch (Exception e) {
				logger.warn("Cannot create instance of " + beanDefinition.getBeanClassName());
			}
		}
		return result.stream();
	}

	@ParameterizedTest
	@MethodSource("policies")
	@SuppressWarnings("deprecation")
	public void testSerializationCycleForContext(BackOffPolicy policy) {
		BackOffContext context = policy.start(new RetryContextSupport(null));
		if (context != null) {
			assertTrue(SerializationUtils.deserialize(SerializationUtils.serialize(context)) instanceof BackOffContext);
		}
	}

}
