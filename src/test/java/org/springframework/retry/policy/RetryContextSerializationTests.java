/*
 * Copyright 2012-2015 the original author or authors.
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Dave Syer
 *
 */
@RunWith(Parameterized.class)
public class RetryContextSerializationTests {

	private static Log logger = LogFactory.getLog(RetryContextSerializationTests.class);

	private RetryPolicy policy;

	@Parameters(name = "{index}: {0}")
	public static List<Object[]> policies() {
		List<Object[]> result = new ArrayList<Object[]>();
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
		extra.setExceptionClassifier(new SubclassClassifier<Throwable, RetryPolicy>(new AlwaysRetryPolicy()));
		result.add(new Object[] { extra });
		return result;
	}

	public RetryContextSerializationTests(RetryPolicy policy) {
		this.policy = policy;
	}

	@Test
	public void testSerializationCycleForContext() {
		RetryContext context = policy.open(null);
		assertEquals(0, context.getRetryCount());
		policy.registerThrowable(context, new RuntimeException());
		assertEquals(1, context.getRetryCount());
		assertEquals(1,
				((RetryContext) SerializationUtils.deserialize(SerializationUtils.serialize(context))).getRetryCount());
	}

	@Test
	public void testSerializationCycleForPolicy() {
		assertTrue(SerializationUtils.deserialize(SerializationUtils.serialize(policy)) instanceof RetryPolicy);
	}

}
