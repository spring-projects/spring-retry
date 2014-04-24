/*
 * Copyright 2013-2014 the original author or authors.
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

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.RecoverAnnotationRecoveryHandler;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class RecoverAnnotationRecoveryHandlerTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void defaultRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new DefaultRecover(), ReflectionUtils.findMethod(DefaultRecover.class,
						"foo", String.class));
		assertEquals(1,
				handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	@Test
	public void fewerArgs() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new FewerArgs(), ReflectionUtils.findMethod(FewerArgs.class, "foo",
						String.class, int.class));
		assertEquals(1,
				handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	@Test
	public void noArgs() {
		NoArgs target = new NoArgs();
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				target, ReflectionUtils.findMethod(NoArgs.class, "foo"));
		handler.recover(new Object[0], new RuntimeException("Planned"));
		assertEquals("Planned", target.getCause().getMessage());
	}

	@Test
	public void noMatch() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificException(), ReflectionUtils.findMethod(
						SpecificException.class, "foo", String.class));
		expected.expect(ExhaustedRetryException.class);
		handler.recover(new Object[] { "Dave" }, new Error("Planned"));
	}

	@Test
	public void specificRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificRecover(), ReflectionUtils.findMethod(SpecificRecover.class,
						"foo", String.class));
		assertEquals(2,
				handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	protected static class DefaultRecover {
		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int bar(String name) {
			return 1;
		}
	}

	protected static class NoArgs {
		private Throwable cause;

		@Retryable
		public void foo() {
		}

		@Recover
		public void bar(Throwable cause) {
			this.cause = cause;
		}

		public Throwable getCause() {
			return cause;
		}
	}

	protected static class SpecificRecover {
		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int bar(String name) {
			return 1;
		}

		@Recover
		public int bar(RuntimeException e, String name) {
			return 2;
		}

	}

	protected static class FewerArgs {
		@Retryable
		public int foo(String name, int value) {
			return 0;
		}

		@Recover
		public int bar(RuntimeException e, String name) {
			return 1;
		}

	}

	protected static class SpecificException {
		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int bar(RuntimeException e, String name) {
			return 1;
		}

	}

}
