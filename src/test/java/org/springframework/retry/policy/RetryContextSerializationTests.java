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

package org.springframework.retry.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.classify.SubclassClassifier;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.util.ClassUtils;
import org.springframework.util.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class RetryContextSerializationTests {

	private static final Log logger = LogFactory.getLog(RetryContextSerializationTests.class);

	@SuppressWarnings("deprecation")
	public static List<Object[]> policies() {
		List<Object[]> result = new ArrayList<>();
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
		scanner.addIncludeFilter(new AssignableTypeFilter(RetryPolicy.class));
		scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Test.*")));
		scanner.addExcludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Mock.*")));
		Set<BeanDefinition> candidates = scanner.findCandidateComponents("org.springframework.retry.policy");
		for (BeanDefinition beanDefinition : candidates) {
			try {
				result.add(new Object[] {
						BeanUtils.instantiate(ClassUtils.resolveClassName(beanDefinition.getBeanClassName(), null)) });
			}
			catch (Exception e) {
				logger.warn("Cannot create instance of " + beanDefinition.getBeanClassName(), e);
			}
		}
		ExceptionClassifierRetryPolicy extra = new ExceptionClassifierRetryPolicy();
		extra.setExceptionClassifier(new SubclassClassifier<>(new AlwaysRetryPolicy()));
		result.add(new Object[] { extra });
		return result;
	}

	@SuppressWarnings("deprecation")
	@ParameterizedTest
	@MethodSource("policies")
	public void testSerializationCycleForContext(RetryPolicy policy) {
		RetryContext context = policy.open(null);
		assertThat(context.getRetryCount()).isEqualTo(0);
		policy.registerThrowable(context, new RuntimeException());
		assertThat(context.getRetryCount()).isEqualTo(1);
		assertThat(
				((RetryContext) SerializationUtils.deserialize(SerializationUtils.serialize(context))).getRetryCount())
			.isEqualTo(1);
	}

	@ParameterizedTest
	@MethodSource("policies")
	@SuppressWarnings("deprecation")
	public void testSerializationCycleForPolicy(RetryPolicy policy) {
		assertThat(SerializationUtils.deserialize(SerializationUtils.serialize(policy)) instanceof RetryPolicy)
			.isTrue();
	}

}
