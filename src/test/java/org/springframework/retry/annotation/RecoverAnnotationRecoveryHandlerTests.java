/*
 * Copyright 2013-2019 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.retry.ExhaustedRetryException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.junit.Assert.*;

/**
 * @author Dave Syer
 * @author Aldo Sinanaj
 * @author Randell Callahan
 * @author Nathanaël Roberts
 * @author Maksim Kita
 */
public class RecoverAnnotationRecoveryHandlerTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void defaultRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new DefaultRecover(), ReflectionUtils.findMethod(DefaultRecover.class, "foo", String.class));
		assertEquals(1, handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	@Test
	public void fewerArgs() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(new FewerArgs(),
				ReflectionUtils.findMethod(FewerArgs.class, "foo", String.class, int.class));
		assertEquals(1, handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	@Test
	public void noArgs() {
		NoArgs target = new NoArgs();
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(target,
				ReflectionUtils.findMethod(NoArgs.class, "foo"));
		handler.recover(new Object[0], new RuntimeException("Planned"));
		assertEquals("Planned", target.getCause().getMessage());
	}

	@Test
	public void noMatch() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificException(), ReflectionUtils.findMethod(SpecificException.class, "foo", String.class));
		this.expected.expect(ExhaustedRetryException.class);
		handler.recover(new Object[] { "Dave" }, new Error("Planned"));
	}

	@Test
	public void specificRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificRecover(), ReflectionUtils.findMethod(SpecificRecover.class, "foo", String.class));
		assertEquals(2, handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));
	}

	@Test
	public void inAccessibleRecoverMethods() {
		Method foo = ReflectionUtils.findMethod(InAccessibleRecover.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InAccessibleRecover(), foo);
		assertEquals(1, handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned")));

	}

	@Test
	public void specificReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(InheritanceReturnTypeRecover.class, "foo", String.class));
		assertEquals(1, fooHandler.recover(new Object[] { "Aldo" }, new RuntimeException("Planned")));
		assertEquals(2, fooHandler.recover(new Object[] { "Aldo" }, new IllegalStateException("Planned")));

	}

	@Test
	public void parentReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> barHandler = new RecoverAnnotationRecoveryHandler<Double>(
				new InheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(InheritanceReturnTypeRecover.class, "bar", String.class));
		assertEquals(3, barHandler.recover(new Object[] { "Aldo" }, new RuntimeException("Planned")));

	}

	@Test
	public void genericReturnStringValueTypeParentThrowableRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "foo", String.class));

		@SuppressWarnings("unchecked")
		Map<String, String> recoverResponseMap = (Map<String, String>) handler.recover(new Object[] { "Aldo" },
				new RuntimeException("Planned"));
		assertFalse(CollectionUtils.isEmpty(recoverResponseMap));
		assertEquals("fooRecoverValue1", recoverResponseMap.get("foo"));
	}

	@Test
	public void genericReturnStringValueTypeChildThrowableRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "foo", String.class));

		@SuppressWarnings("unchecked")
		Map<String, String> recoverResponseMap = (Map<String, String>) handler.recover(new Object[] { "Aldo" },
				new IllegalStateException("Planned"));
		assertFalse(CollectionUtils.isEmpty(recoverResponseMap));
		assertEquals("fooRecoverValue2", recoverResponseMap.get("foo"));
	}

	@Test
	public void genericReturnOneValueTypeRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "bar", String.class));

		@SuppressWarnings("unchecked")
		Map<String, GenericReturnTypeRecover.One> recoverResponseMap = (Map<String, GenericReturnTypeRecover.One>) handler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertFalse(CollectionUtils.isEmpty(recoverResponseMap));
		assertNotNull(recoverResponseMap.get("bar"));
		assertEquals("barRecoverValue", recoverResponseMap.get("bar").name);
	}

	@Test
	public void genericSpecifiedReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new GenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericInheritanceReturnTypeRecover.class, "foo", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Integer> recoverResponseMapRe = (Map<String, Integer>) fooHandler.recover(new Object[] { "Aldo" },
				new RuntimeException("Planned"));
		assertEquals(1, recoverResponseMapRe.get("foo").intValue());
		@SuppressWarnings("unchecked")
		Map<String, Integer> recoverResponseMapIse = (Map<String, Integer>) fooHandler.recover(new Object[] { "Aldo" },
				new IllegalStateException("Planned"));
		assertEquals(2, recoverResponseMapIse.get("foo").intValue());
	}

	/**
	 * Even if there are @Recover methods with narrower generic return types, the one with
	 * direct match should get called
	 */
	@Test
	public void genericDirectMatchReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> barHandler = new RecoverAnnotationRecoveryHandler<Double>(
				new GenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericInheritanceReturnTypeRecover.class, "bar", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Number> recoverResponseMapRe = (Map<String, Number>) barHandler.recover(new Object[] { "Aldo" },
				new RuntimeException("Planned"));
		assertEquals(0.2, recoverResponseMapRe.get("bar"));
	}

	@Test
	public void genericNestedMapIntegerStringReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new NestedGenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(NestedGenericInheritanceReturnTypeRecover.class, "foo", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Integer, String>>> recoverResponseMapRe = (Map<String, Map<String, Map<Integer, String>>>) fooHandler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertEquals("fooRecoverReValue", recoverResponseMapRe.get("foo").get("foo").get(0));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Integer, String>>> recoverResponseMapIe = (Map<String, Map<String, Map<Integer, String>>>) fooHandler
				.recover(new Object[] { "Aldo" }, new IllegalStateException("Planned"));
		assertEquals("fooRecoverIeValue", recoverResponseMapIe.get("foo").get("foo").get(0));
	}

	@Test
	public void genericNestedMapNumberStringReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> barHandler = new RecoverAnnotationRecoveryHandler<Double>(
				new NestedGenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(NestedGenericInheritanceReturnTypeRecover.class, "bar", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Number, String>>> recoverResponseMapRe = (Map<String, Map<String, Map<Number, String>>>) barHandler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertEquals("barRecoverNumberValue", recoverResponseMapRe.get("bar").get("bar").get(0.0));

	}

	@Test
	public void multipleQualifyingRecoverMethods() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecovers.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecovers(), foo);
		assertEquals(1, handler.recover(new Object[] { "Randell" }, new RuntimeException("Planned")));

	}

	@Test
	public void multipleQualifyingRecoverMethodsWithNull() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecovers.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecovers(), foo);
		assertEquals(1, handler.recover(new Object[] { null }, new RuntimeException("Planned")));

	}

	@Test
	public void multipleQualifyingRecoverMethodsWithNoThrowable() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversNoThrowable.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversNoThrowable(), foo);
		assertEquals(1, handler.recover(new Object[] { null }, new RuntimeException("Planned")));

	}

	@Test
	public void multipleQualifyingRecoverMethodsReOrdered() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversReOrdered.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversReOrdered(), foo);
		assertEquals(3, handler.recover(new Object[] { "Randell" }, new RuntimeException("Planned")));

	}

	@Test
	public void multipleQualifyingRecoverMethodsExtendsThrowable() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversExtendsThrowable.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversExtendsThrowable(), foo);
		assertEquals(2, handler.recover(new Object[] { "Kevin" }, new IllegalArgumentException("Planned")));
		assertEquals(3, handler.recover(new Object[] { "Kevin" }, new UnsupportedOperationException("Planned")));

	}

	@Test
	public void inheritanceOnArgumentClass() {
		Method foo = ReflectionUtils.findMethod(InheritanceOnArgumentClass.class, "foo", List.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InheritanceOnArgumentClass(), foo);
		assertEquals(1,
				handler.recover(new Object[] { new ArrayList<String>() }, new IllegalArgumentException("Planned")));
	}

	@Test
	public void recoverByRetryableName() {
		Method foo = ReflectionUtils.findMethod(RecoverByRetryableName.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new RecoverByRetryableName(), foo);
		assertEquals(2, handler.recover(new Object[] { "Kevin" }, new RuntimeException("Planned")));
	}

	private static class InAccessibleRecover {

		@Retryable
		private int foo(String n) {
			throw new RuntimeException("error trying to foo('" + n + "')");
		}

		@Recover
		private int bar(String n) {
			return 1;
		}

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
			return this.cause;
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

	protected static class InheritanceReturnTypeRecover {

		@Retryable
		public Integer foo(String name) {
			return 0;
		}

		@Retryable
		public Double bar(String name) {
			return 0.0;
		}

		@Recover
		public Integer baz(RuntimeException re, String name) {
			return 1;
		}

		@Recover
		public Integer qux(IllegalStateException re, String name) {
			return 2;
		}

		@Recover
		public Number quux(RuntimeException re, String name) {
			return 3;
		}

	}

	protected static class GenericReturnTypeRecover {

		private static class One {

			String name;

			public One(String name) {
				this.name = name;
			}

		}

		@Retryable
		public Map<String, String> foo(String name) {
			return Collections.singletonMap("foo", "fooValue");
		}

		@Retryable
		public Map<String, One> bar(String name) {
			return Collections.singletonMap("bar", new One("barValue"));
		}

		@Recover
		public Map<String, String> fooRecoverRe(RuntimeException re, String name) {
			return Collections.singletonMap("foo", "fooRecoverValue1");
		}

		@Recover
		public Map<String, String> fooRecoverIe(IllegalStateException re, String name) {
			return Collections.singletonMap("foo", "fooRecoverValue2");
		}

		@Recover
		public Map<String, One> barRecover(RuntimeException re, String name) {
			return Collections.singletonMap("bar", new One("barRecoverValue"));
		}

	}

	protected static class GenericInheritanceReturnTypeRecover {

		@Retryable
		public Map<String, Integer> foo(String name) {
			return Collections.singletonMap("foo", 0);
		}

		@Retryable
		public Map<String, Number> bar(String name) {
			return Collections.singletonMap("bar", (Number) 0.0);
		}

		@Recover
		public Map<String, Integer> fooRecoverRe(RuntimeException re, String name) {
			return Collections.singletonMap("foo", 1);
		}

		@Recover
		public Map<String, Integer> fooRecoverIe(IllegalStateException re, String name) {
			return Collections.singletonMap("foo", 2);
		}

		@Recover
		public Map<String, Double> barRecoverDouble(RuntimeException re, String name) {
			return Collections.singletonMap("bar", 0.1);
		}

		@Recover
		public Map<String, Number> barRecoverNumber(RuntimeException re, String name) {
			return Collections.singletonMap("bar", (Number) 0.2);
		}

	}

	protected static class NestedGenericInheritanceReturnTypeRecover {

		@Retryable
		public Map<String, Map<String, Map<Integer, String>>> foo(String name) {
			return Collections.singletonMap("foo",
					Collections.singletonMap("foo", Collections.singletonMap(0, "fooValue")));
		}

		@Retryable
		public Map<String, Map<String, Map<Number, String>>> bar(String name) {
			return Collections.singletonMap("bar",
					Collections.singletonMap("bar", Collections.singletonMap((Number) 0.0, "barValue")));
		}

		@Recover
		public Map<String, Map<String, Map<Integer, String>>> fooRecoverRe(RuntimeException re, String name) {
			return Collections.singletonMap("foo",
					Collections.singletonMap("foo", Collections.singletonMap(0, "fooRecoverReValue")));
		}

		@Recover
		public Map<String, Map<String, Map<Integer, String>>> fooRecoverIe(IllegalStateException re, String name) {
			return Collections.singletonMap("foo",
					Collections.singletonMap("foo", Collections.singletonMap(0, "fooRecoverIeValue")));
		}

		@Recover
		public Map<String, Map<String, Map<Number, String>>> barRecoverNumber(RuntimeException re, String name) {
			return Collections.singletonMap("bar",
					Collections.singletonMap("bar", Collections.singletonMap((Number) 0.0, "barRecoverNumberValue")));
		}

		@Recover
		public Map<String, Map<String, Map<Double, String>>> barRecoverDouble(RuntimeException re, String name) {
			return Collections.singletonMap("bar",
					Collections.singletonMap("bar", Collections.singletonMap(0.0, "barRecoverDoubleValue")));
		}

	}

	protected static class MultipleQualifyingRecoversNoThrowable {

		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int fooRecover(String name, String nullable) {
			return 1;
		}

		@Recover
		public int fooRecover(int other, String nullable) {
			return 2;
		}

	}

	protected static class MultipleQualifyingRecovers {

		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int fooRecover(Throwable e, String name) {
			return 1;
		}

		@Recover
		public int fooRecover(Throwable e) {
			return 2;
		}

		@Recover
		public int barRecover(Throwable e, int number) {
			return 3;
		}

	}

	protected static class MultipleQualifyingRecoversReOrdered {

		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int fooRecover(Throwable e) {
			return 1;
		}

		@Recover
		public int barRecover(Throwable e, int number) {
			return 2;
		}

		@Recover
		public int fooRecover(Throwable e, String name) {
			return 3;
		}

	}

	protected static class MultipleQualifyingRecoversExtendsThrowable {

		@Retryable
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int fooRecover(IllegalArgumentException e, String name) {
			return 1;
		}

		@Recover
		public int barRecover(IllegalArgumentException e, String name) {
			return 2;
		}

		@Recover
		public int bazRecover(UnsupportedOperationException e, String name) {
			return 3;
		}

	}

	protected static class InheritanceOnArgumentClass {

		@Retryable
		public int foo(List<String> list) {
			return 0;
		}

		@Recover
		public int fooRecover(Throwable t, List<String> list) {
			return 1;
		}

		@Recover
		public int barRecover(Throwable t, String name) {
			return 2;
		}

	}

	protected static class RecoverByRetryableName {

		@Retryable(recover = "barRecover")
		public int foo(String name) {
			return 0;
		}

		@Recover
		public int fooRecover(Throwable throwable, String name) {
			return 1;
		}

		@Recover
		public int barRecover(Throwable throwable, String name) {
			return 2;
		}

	}

}
