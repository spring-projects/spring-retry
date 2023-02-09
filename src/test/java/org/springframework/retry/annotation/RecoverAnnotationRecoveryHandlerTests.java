/*
 * Copyright 2013-2023 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.core.annotation.AliasFor;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * @author Dave Syer
 * @author Aldo Sinanaj
 * @author Randell Callahan
 * @author Nathanaël Roberts
 * @author Maksim Kita
 * @author Gianluca Medici
 * @author Lijinliang
 */
public class RecoverAnnotationRecoveryHandlerTests {

	@Test
	public void genericReturnTypesMatch() throws InvocationTargetException, IllegalAccessException {
		Method isParameterizedTypeAssignable = ReflectionUtils.findMethod(RecoverAnnotationRecoveryHandler.class,
				"isParameterizedTypeAssignable", ParameterizedType.class, ParameterizedType.class);
		isParameterizedTypeAssignable.setAccessible(true);

		assertThat(isParameterizedTypeAssignable.invoke(null, getGenericReturnTypeByName("m1"),
				getGenericReturnTypeByName("m2"))).isEqualTo(Boolean.TRUE);
		assertThat(isParameterizedTypeAssignable.invoke(null, getGenericReturnTypeByName("m2"),
				getGenericReturnTypeByName("m2_1"))).isEqualTo(Boolean.FALSE);
		assertThat(isParameterizedTypeAssignable.invoke(null, getGenericReturnTypeByName("m3"),
				getGenericReturnTypeByName("m4"))).isEqualTo(Boolean.FALSE);
		assertThat(isParameterizedTypeAssignable.invoke(null, getGenericReturnTypeByName("m5"),
				getGenericReturnTypeByName("m6"))).isEqualTo(Boolean.TRUE);
	}

	private static ParameterizedType getGenericReturnTypeByName(String name) {
		return (ParameterizedType) ReflectionUtils.findMethod(ParameterTest.class, name).getGenericReturnType();
	}

	@Test
	public void defaultRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new DefaultRecover(), ReflectionUtils.findMethod(DefaultRecover.class, "foo", String.class));
		assertThat(handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned"))).isEqualTo(1);
	}

	@Test
	public void fewerArgs() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(new FewerArgs(),
				ReflectionUtils.findMethod(FewerArgs.class, "foo", String.class, int.class));
		assertThat(handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned"))).isEqualTo(1);
	}

	@Test
	public void noArgs() {
		NoArgs target = new NoArgs();
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(target,
				ReflectionUtils.findMethod(NoArgs.class, "foo"));
		handler.recover(new Object[0], new RuntimeException("Planned"));
		assertThat(target.getCause().getMessage()).isEqualTo("Planned");
	}

	@Test
	public void noMatch() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificException(), ReflectionUtils.findMethod(SpecificException.class, "foo", String.class));
		assertThatExceptionOfType(ExhaustedRetryException.class)
				.isThrownBy(() -> handler.recover(new Object[] { "Dave" }, new Error("Planned")));
	}

	@Test
	public void specificRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new SpecificRecover(), ReflectionUtils.findMethod(SpecificRecover.class, "foo", String.class));
		assertThat(handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned"))).isEqualTo(2);
	}

	@Test
	public void inAccessibleRecoverMethods() {
		Method foo = ReflectionUtils.findMethod(InAccessibleRecover.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InAccessibleRecover(), foo);
		assertThat(handler.recover(new Object[] { "Dave" }, new RuntimeException("Planned"))).isEqualTo(1);

	}

	@Test
	public void specificReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(InheritanceReturnTypeRecover.class, "foo", String.class));
		assertThat(fooHandler.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"))).isEqualTo(1);
		assertThat(fooHandler.recover(new Object[] { "Aldo" }, new IllegalStateException("Planned"))).isEqualTo(2);

	}

	@Test
	public void parentReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> barHandler = new RecoverAnnotationRecoveryHandler<Double>(
				new InheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(InheritanceReturnTypeRecover.class, "bar", String.class));
		assertThat(barHandler.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"))).isEqualTo(3);

	}

	@Test
	public void genericReturnStringValueTypeParentThrowableRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "foo", String.class));

		@SuppressWarnings("unchecked")
		Map<String, String> recoverResponseMap = (Map<String, String>) handler.recover(new Object[] { "Aldo" },
				new RuntimeException("Planned"));
		assertThat(CollectionUtils.isEmpty(recoverResponseMap)).isFalse();
		assertThat(recoverResponseMap.get("foo")).isEqualTo("fooRecoverValue1");
	}

	@Test
	public void genericReturnStringValueTypeChildThrowableRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "foo", String.class));

		@SuppressWarnings("unchecked")
		Map<String, String> recoverResponseMap = (Map<String, String>) handler.recover(new Object[] { "Aldo" },
				new IllegalStateException("Planned"));
		assertThat(CollectionUtils.isEmpty(recoverResponseMap)).isFalse();
		assertThat(recoverResponseMap.get("foo")).isEqualTo("fooRecoverValue2");
	}

	@Test
	public void genericReturnOneValueTypeRecoverMethod() {

		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<List<String>>(
				new GenericReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericReturnTypeRecover.class, "bar", String.class));

		@SuppressWarnings("unchecked")
		Map<String, GenericReturnTypeRecover.One> recoverResponseMap = (Map<String, GenericReturnTypeRecover.One>) handler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertThat(CollectionUtils.isEmpty(recoverResponseMap)).isFalse();
		assertThat(recoverResponseMap.get("bar")).isNotNull();
		assertThat(recoverResponseMap.get("bar").name).isEqualTo("barRecoverValue");
	}

	@Test
	public void genericSpecifiedReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new GenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(GenericInheritanceReturnTypeRecover.class, "foo", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Integer> recoverResponseMapRe = (Map<String, Integer>) fooHandler.recover(new Object[] { "Aldo" },
				new RuntimeException("Planned"));
		assertThat(recoverResponseMapRe.get("foo").intValue()).isEqualTo(1);
		@SuppressWarnings("unchecked")
		Map<String, Integer> recoverResponseMapIse = (Map<String, Integer>) fooHandler.recover(new Object[] { "Aldo" },
				new IllegalStateException("Planned"));
		assertThat(recoverResponseMapIse.get("foo").intValue()).isEqualTo(2);
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
		assertThat(recoverResponseMapRe.get("bar")).isEqualTo(0.2);
	}

	@Test
	public void genericNestedMapIntegerStringReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> fooHandler = new RecoverAnnotationRecoveryHandler<Integer>(
				new NestedGenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(NestedGenericInheritanceReturnTypeRecover.class, "foo", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Integer, String>>> recoverResponseMapRe = (Map<String, Map<String, Map<Integer, String>>>) fooHandler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertThat(recoverResponseMapRe.get("foo").get("foo").get(0)).isEqualTo("fooRecoverReValue");
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Integer, String>>> recoverResponseMapIe = (Map<String, Map<String, Map<Integer, String>>>) fooHandler
				.recover(new Object[] { "Aldo" }, new IllegalStateException("Planned"));
		assertThat(recoverResponseMapIe.get("foo").get("foo").get(0)).isEqualTo("fooRecoverIeValue");
	}

	@Test
	public void genericNestedMapNumberStringReturnTypeRecoverMethod() {
		RecoverAnnotationRecoveryHandler<?> barHandler = new RecoverAnnotationRecoveryHandler<Double>(
				new NestedGenericInheritanceReturnTypeRecover(),
				ReflectionUtils.findMethod(NestedGenericInheritanceReturnTypeRecover.class, "bar", String.class));
		@SuppressWarnings("unchecked")
		Map<String, Map<String, Map<Number, String>>> recoverResponseMapRe = (Map<String, Map<String, Map<Number, String>>>) barHandler
				.recover(new Object[] { "Aldo" }, new RuntimeException("Planned"));
		assertThat(recoverResponseMapRe.get("bar").get("bar").get(0.0)).isEqualTo("barRecoverNumberValue");

	}

	@Test
	public void multipleQualifyingRecoverMethods() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecovers.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecovers(), foo);
		assertThat(handler.recover(new Object[] { "Randell" }, new RuntimeException("Planned"))).isEqualTo(1);

	}

	@Test
	public void multipleQualifyingRecoverMethodsWithNull() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecovers.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecovers(), foo);
		assertThat(handler.recover(new Object[] { null }, new RuntimeException("Planned"))).isEqualTo(1);

	}

	@Test
	public void multipleQualifyingRecoverMethodsWithNoThrowable() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversNoThrowable.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversNoThrowable(), foo);
		assertThat(handler.recover(new Object[] { null }, new RuntimeException("Planned"))).isEqualTo(1);

	}

	@Test
	public void multipleQualifyingRecoverMethodsReOrdered() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversReOrdered.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversReOrdered(), foo);
		assertThat(handler.recover(new Object[] { "Randell" }, new RuntimeException("Planned"))).isEqualTo(3);

	}

	@Test
	public void multipleQualifyingRecoverMethodsExtendsThrowable() {
		Method foo = ReflectionUtils.findMethod(MultipleQualifyingRecoversExtendsThrowable.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new MultipleQualifyingRecoversExtendsThrowable(), foo);
		assertThat(handler.recover(new Object[] { "Kevin" }, new IllegalArgumentException("Planned"))).isEqualTo(2);
		assertThat(handler.recover(new Object[] { "Kevin" }, new UnsupportedOperationException("Planned")))
				.isEqualTo(3);

	}

	@Test
	public void inheritanceOnArgumentClass() {
		Method foo = ReflectionUtils.findMethod(InheritanceOnArgumentClass.class, "foo", List.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new InheritanceOnArgumentClass(), foo);
		assertThat(handler.recover(new Object[] { new ArrayList<String>() }, new IllegalArgumentException("Planned")))
				.isEqualTo(1);
	}

	@Test
	public void recoverByRetryableName() {
		Method foo = ReflectionUtils.findMethod(RecoverByRetryableName.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new RecoverByRetryableName(), foo);
		assertThat(handler.recover(new Object[] { "Kevin" }, new RuntimeException("Planned"))).isEqualTo(2);
	}

	@Test
	public void recoverByRetryableNameWithPrimitiveArgs() {
		Method foo = ReflectionUtils.findMethod(RecoverByRetryableNameWithPrimitiveArgs.class, "foo", int.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new RecoverByRetryableNameWithPrimitiveArgs(), foo);
		assertThat(handler.recover(new Object[] { 2 }, new RuntimeException("Planned"))).isEqualTo(2);
	}

	@Test
	public void recoverByComposedRetryableAnnotationName() {
		Method foo = ReflectionUtils.findMethod(RecoverByComposedRetryableAnnotationName.class, "foo", String.class);
		RecoverAnnotationRecoveryHandler<?> handler = new RecoverAnnotationRecoveryHandler<Integer>(
				new RecoverByComposedRetryableAnnotationName(), foo);
		assertThat(handler.recover(new Object[] { "Kevin" }, new RuntimeException("Planned"))).isEqualTo(4);
	}

	private static class ParameterTest<T, M> {

		List<T> m1() {
			return null;
		}

		List<T> m2() {
			return null;
		}

		List<M> m2_1() {
			return null;
		}

		Map<List<String>, Byte> m3() {
			return null;
		}

		Map<List<String>, Integer> m4() {
			return null;
		}

		Map<List<Integer>, Byte> m5() {
			return null;
		}

		Map<List<Integer>, Byte> m6() {
			return null;
		}

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

	protected static class RecoverByRetryableName implements RecoverByRetryableNameInterface {

		public int foo(String name) {
			return 0;
		}

		public int fooRecover(Throwable throwable, String name) {
			return 1;
		}

		public int barRecover(Throwable throwable, String name) {
			return 2;
		}

	}

	protected static class RecoverByComposedRetryableAnnotationName
			implements RecoverByComposedRetryableAnnotationNameInterface {

		public int foo(String name) {
			return 0;
		}

		public int fooRecover(Throwable throwable, String name) {
			return 1;
		}

		public int barRecover(Throwable throwable, String name) {
			return 4;
		}

	}

	protected interface RecoverByRetryableNameInterface {

		@Retryable(recover = "barRecover")
		public int foo(String name);

		@Recover
		public int fooRecover(Throwable throwable, String name);

		@Recover
		public int barRecover(Throwable throwable, String name);

	}

	protected static class RecoverByRetryableNameWithPrimitiveArgs
			implements RecoverByRetryableNameWithPrimitiveArgsInterface {

		public int foo(int number) {
			return 0;
		}

		public int fooRecover(Throwable throwable, int number) {
			return 0;
		}

		public int barRecover(Throwable throwable, int number) {
			return number;
		}

	}

	protected interface RecoverByRetryableNameWithPrimitiveArgsInterface {

		@Retryable(recover = "barRecover")
		public int foo(int number);

		@Recover
		public int fooRecover(Throwable throwable, int number);

		@Recover
		public int barRecover(Throwable throwable, int number);

	}

	protected interface RecoverByComposedRetryableAnnotationNameInterface {

		@ComposedRetryable(recover = "barRecover")
		public int foo(String name);

		@Recover
		public int fooRecover(Throwable throwable, String name);

		@Recover
		public int barRecover(Throwable throwable, String name);

	}

	@Target({ ElementType.METHOD, ElementType.TYPE })
	@Retention(RetentionPolicy.RUNTIME)
	@Documented
	@Retryable(maxAttempts = 4)
	public @interface ComposedRetryable {

		@AliasFor(annotation = Retryable.class, attribute = "recover")
		String recover() default "";

	}

}
