/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.annotation;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Gary Russell
 * @since 1.2.2
 *
 */
@RunWith(SpringRunner.class)
public class PrototypeBeanTests {

	@Autowired
	private Bar bar1;

	@Autowired
	private Bar bar2;

	@Autowired
	private Foo foo;

	@Test
	public void testProtoBean() {
		this.bar1.foo("one");
		this.bar2.foo("two");
		assertThat(this.foo.recovered, equalTo("two"));
	}

	@Configuration
	@EnableRetry
	public static class Config {

		@Bean
		public Foo foo() {
			return new Foo();
		}

		@Bean
		@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
		public Baz baz() {
			return new Baz();
		}

	}

	public static class Foo {

		private String recovered;

		void demoRun(Bar bar) {
			throw new RuntimeException();
		}

		void demoRecover(String instance) {
			this.recovered = instance;
		}

	}

	public interface Bar {

		@Retryable(backoff = @Backoff(0))
		void foo(String instance);

		@Recover
		void bar();

	}

	public static class Baz implements Bar {

		private String instance;

		@Autowired
		private Foo foo;

		@Override
		public void foo(String instance) {
			this.instance = instance;
			foo.demoRun(this);
		}

		@Override
		public void bar() {
			foo.demoRecover(this.instance);
		}

		@Override
		public String toString() {
			return "Baz [instance=" + this.instance + "]";
		}

	}

}
