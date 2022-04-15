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

package org.springframework.retry.annotation;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.junit.Assert.assertEquals;

public class ProxyApplicationTests {

	private final CountClassesClassLoader classLoader = new CountClassesClassLoader();

	@Test
	// See gh-53
	public void contextLoads() throws Exception {
		int count = count();
		runAndClose();
		runAndClose();
		// Let the JVM catch up
		Thread.sleep(500L);
		runAndClose();
		int base = count();
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
	}

	@SuppressWarnings("resource")
	private void runAndClose() {
		AnnotationConfigApplicationContext run = new AnnotationConfigApplicationContext();
		run.setClassLoader(this.classLoader);
		run.register(Empty.class);
		run.close();
		while (run.getParent() != null) {
			((ConfigurableApplicationContext) run.getParent()).close();
			run = (AnnotationConfigApplicationContext) run.getParent();
		}
	}

	private int count() {
		return this.classLoader.classes.size();
	}

	private static class CountClassesClassLoader extends URLClassLoader {

		private final Set<Class<?>> classes = new HashSet<Class<?>>();

		public CountClassesClassLoader() {
			super(new URL[0], ProxyApplicationTests.class.getClassLoader());
		}

		@Override
		public Class<?> loadClass(String name) throws ClassNotFoundException {
			Class<?> type = super.loadClass(name);
			classes.add(type);
			return type;
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			Class<?> type = super.loadClass(name, resolve);
			classes.add(type);
			return type;
		}

	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class Empty {

		@Bean
		public Service service() {
			return new Service();
		}

	}

	@Component
	static class Service {

		@Retryable
		public void handle() {
			System.err.println("Handling");
		}

	}

}
