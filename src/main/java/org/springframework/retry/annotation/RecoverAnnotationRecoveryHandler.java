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

package org.springframework.retry.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.classify.SubclassClassifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.interceptor.MethodInvocationRecoverer;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * A recoverer for method invocations based on the <code>@Recover</code> annotation. A
 * suitable recovery method is one with a Throwable type as the first parameter and the
 * same return type and arguments as the method that failed. The Throwable first argument
 * is optional and if omitted the method is treated as a default (called when there are no
 * other matches). Generally the best matching method is chosen based on the type of the
 * first parameter and the type of the exception being handled. The closest match in the
 * class hierarchy is chosen, so for instance if an IllegalArgumentException is being
 * handled and there is a method whose first argument is RuntimeException, then it will be
 * preferred over a method whose first argument is Throwable.
 *
 * @param <T> the type of the return value from the recovery
 * @author Dave Syer
 * @author Josh Long
 * @author Aldo Sinanaj
 * @author Randell Callahan
 * @author Nathanaël Roberts
 * @author Maksim Kita
 * @author Gary Russell
 * @author Artem Bilan
 * @author Gianluca Medici
 * @author Lijinliang
 * @author Yanming Zhou
 * @author Chih-Yu Huang
 */
public class RecoverAnnotationRecoveryHandler<T> implements MethodInvocationRecoverer<T> {

	private final SubclassClassifier<Throwable, Method> classifier = new SubclassClassifier<>();

	private final Map<Method, SimpleMetadata> methods = new LinkedHashMap<>();

	private final Object target;

	private String recoverMethodName;

	public RecoverAnnotationRecoveryHandler(Object target, Method method) {
		this.target = target;
		init(target, method);
	}

	@Override
	public T recover(Object[] args, Throwable cause) {
		Class<? extends Throwable> causeType = (cause == null) ? null : cause.getClass();
		Method method = findClosestMatch(args, causeType);
		if (method == null) {
			throw new ExhaustedRetryException("Cannot locate recovery method", cause);
		}
		SimpleMetadata meta = this.methods.get(method);
		Object[] argsToUse = meta.getArgs(cause, args);
		ReflectionUtils.makeAccessible(method);
		RetryContext context = RetrySynchronizationManager.getContext();
		Object proxy = null;
		if (context != null) {
			proxy = context.getAttribute("___proxy___");
			if (proxy != null) {
				Method proxyMethod = findMethodOnProxy(method, proxy);
				if (proxyMethod == null) {
					proxy = null;
				}
				else {
					method = proxyMethod;
				}
			}
		}
		if (proxy == null) {
			proxy = this.target;
		}
		@SuppressWarnings("unchecked")
		T result = (T) ReflectionUtils.invokeMethod(method, proxy, argsToUse);
		return result;
	}

	private Method findMethodOnProxy(Method method, Object proxy) {
		try {
			return proxy.getClass().getMethod(method.getName(), method.getParameterTypes());
		}
		catch (NoSuchMethodException | SecurityException e) {
			return null;
		}
	}

	private Method findClosestMatch(Object[] args, Class<? extends Throwable> cause) {
		if (StringUtils.hasText(this.recoverMethodName)) {
			return findMethodByName(args, cause);
		}

		List<Method> withThrowable = new ArrayList<>();
		List<Method> withoutThrowable = new ArrayList<>();
		for (Method method : this.methods.keySet()) {
			SimpleMetadata meta = this.methods.get(method);
			if (meta.getType() != null) {
				withThrowable.add(method);
			}
			else {
				withoutThrowable.add(method);
			}
		}

		Method result = findMethodWithThrowable(args, cause, withThrowable);
		if (result == null) {
			result = findMethodWithNoThrowable(args, withoutThrowable);
		}
		return result;
	}

	private static Method findMethodWithNoThrowable(Object[] args, List<Method> methods) {
		Method result = null;
		for (Method method : methods) {
			if (compareParameters(args, method.getParameterTypes(), false)) {
				if (result == null || result.getParameterCount() < method.getParameterCount()) {
					result = method;
				}
			}
		}
		return result;
	}

	private Method findMethodWithThrowable(Object[] args, Class<? extends Throwable> cause, List<Method> methods) {
		Method result = null;
		int minDistance = Integer.MAX_VALUE;
		List<Method> candidates = new ArrayList<>();

		if (cause != null) {
			for (Method method : methods) {
				SimpleMetadata meta = this.methods.get(method);
				Class<? extends Throwable> exceptionType = meta.getType();
				if (exceptionType.isAssignableFrom(cause)) {
					int distance = calculateDistance(cause, exceptionType);
					if (distance < minDistance) {
						minDistance = distance;
						candidates.clear();
						candidates.add(method);
					}
					else if (distance == minDistance) {
						candidates.add(method);
					}
				}
			}
		}

		for (Method method : candidates) {
			if (compareParameters(args, method.getParameterTypes(), true)) {
				if (result == null || result.getParameterCount() < method.getParameterCount()) {
					result = method;
				}
			}
		}
		return result;
	}

	private Method findMethodByName(Object[] args, Class<? extends Throwable> cause) {
		for (Map.Entry<Method, SimpleMetadata> entry : this.methods.entrySet()) {
			Method method = entry.getKey();
			if (method.getName().equals(this.recoverMethodName)) {
				SimpleMetadata meta = entry.getValue();
				Class<? extends Throwable> exceptionType = meta.getType();
				if (exceptionType == null || (cause != null && exceptionType.isAssignableFrom(cause))) {
					if (compareParameters(args, method.getParameterTypes(), exceptionType != null)) {
						return method;
					}
				}
			}
		}
		return null;
	}

	private static int calculateDistance(Class<?> cause, Class<?> type) {
		int result = 0;
		Class<?> current = cause;
		while (current != type && current != Throwable.class) {
			result++;
			current = current.getSuperclass();
		}
		return result;
	}

	private static boolean compareParameters(Object[] args, Class<?>[] parameterTypes, boolean hasThrowable) {
		int argCount = args.length;
		int paramCount = parameterTypes.length;
		int argIndex = 0;
		int paramIndex = hasThrowable ? 1 : 0;

		while (paramIndex < paramCount) {
			Class<?> parameterType = parameterTypes[paramIndex];
			Object argument = (argIndex < argCount) ? args[argIndex] : null;

			if (argument == null && parameterType.isPrimitive()) {
				return false;
			}
			if (argument != null && !ClassUtils.isAssignable(parameterType, argument.getClass())) {
				return false;
			}
			paramIndex++;
			argIndex++;
		}
		return true;
	}

	private void init(final Object target, Method method) {
		final Map<Class<? extends Throwable>, Method> types = new LinkedHashMap<>();
		final Method failingMethod = method;
		Retryable retryable = AnnotatedElementUtils.findMergedAnnotation(method, Retryable.class);
		if (retryable != null) {
			this.recoverMethodName = retryable.recover();
		}
		Method[] declared = target.getClass().getDeclaredMethods();
		Arrays.sort(declared, Comparator.comparing(Method::getName)
			.thenComparingInt(Method::getParameterCount)
			.thenComparing(
					m -> Arrays.stream(m.getParameterTypes()).map(Class::getName).collect(Collectors.joining(","))));

		for (Method candidate : declared) {
			Recover recover = AnnotatedElementUtils.findMergedAnnotation(candidate, Recover.class);
			if (recover == null) {
				recover = findAnnotationOnTarget(target, candidate);
			}
			if (recover != null) {
				if (failingMethod.getGenericReturnType() instanceof ParameterizedType
						&& candidate.getGenericReturnType() instanceof ParameterizedType) {
					if (isParameterizedTypeAssignable((ParameterizedType) candidate.getGenericReturnType(),
							(ParameterizedType) failingMethod.getGenericReturnType())) {
						putToMethodsMap(candidate, types);
					}
				}
				else if (candidate.getReturnType().isAssignableFrom(failingMethod.getReturnType())) {
					putToMethodsMap(candidate, types);
				}
			}
		}
		optionallyFilterMethodsBy(failingMethod.getReturnType());
	}

	/**
	 * Returns {@code true} if the input methodReturnType is a direct match of the
	 * failingMethodReturnType. Takes nested generics into consideration as well, while
	 * deciding a match.
	 * @param methodReturnType the method return type
	 * @param failingMethodReturnType the failing method return type
	 * @return true if the parameterized return types match.
	 * @since 1.3.2
	 */
	private static boolean isParameterizedTypeAssignable(ParameterizedType methodReturnType,
			ParameterizedType failingMethodReturnType) {

		Type[] methodActualArgs = methodReturnType.getActualTypeArguments();
		Type[] failingMethodActualArgs = failingMethodReturnType.getActualTypeArguments();
		if (methodActualArgs.length != failingMethodActualArgs.length) {
			return false;
		}
		int startingIndex = 0;
		for (int i = startingIndex; i < methodActualArgs.length; i++) {
			Type methodArgType = methodActualArgs[i];
			Type failingMethodArgType = failingMethodActualArgs[i];
			if (methodArgType instanceof ParameterizedType && failingMethodArgType instanceof ParameterizedType) {
				if (!isParameterizedTypeAssignable((ParameterizedType) methodArgType,
						(ParameterizedType) failingMethodArgType)) {

					return false;
				}
			}
			else if (methodArgType instanceof Class && failingMethodArgType instanceof Class) {
				if (!failingMethodArgType.equals(methodArgType)) {
					return false;
				}
			}
			else if (!methodArgType.equals(failingMethodArgType)) {
				return false;
			}
		}
		return true;
	}

	private void putToMethodsMap(Method method, Map<Class<? extends Throwable>, Method> types) {
		Class<?>[] parameterTypes = method.getParameterTypes();
		if (parameterTypes.length > 0 && Throwable.class.isAssignableFrom(parameterTypes[0])) {
			@SuppressWarnings("unchecked")
			Class<? extends Throwable> type = (Class<? extends Throwable>) parameterTypes[0];
			types.put(type, method);
			this.methods.put(method, new SimpleMetadata(parameterTypes.length, type));
		}
		else {
			this.methods.put(method, new SimpleMetadata(parameterTypes.length, null));
		}
	}

	private Recover findAnnotationOnTarget(Object target, Method method) {
		try {
			Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
			return AnnotatedElementUtils.findMergedAnnotation(targetMethod, Recover.class);
		}
		catch (Exception e) {
			return null;
		}
	}

	private void optionallyFilterMethodsBy(Class<?> returnClass) {
		Map<Method, SimpleMetadata> filteredMethods = new LinkedHashMap<>();
		for (Method method : this.methods.keySet()) {
			if (method.getReturnType() == returnClass) {
				filteredMethods.put(method, this.methods.get(method));
			}
		}
		if (filteredMethods.size() > 0) {
			this.methods.clear();
			;
			this.methods.putAll(filteredMethods);
		}
	}

	private static class SimpleMetadata {

		private final int argCount;

		private final Class<? extends Throwable> type;

		public SimpleMetadata(int argCount, Class<? extends Throwable> type) {
			super();
			this.argCount = argCount;
			this.type = type;
		}

		public int getArgCount() {
			return this.argCount;
		}

		public Class<? extends Throwable> getType() {
			return this.type;
		}

		public Object[] getArgs(Throwable t, Object[] args) {
			Object[] result = new Object[getArgCount()];
			int startArgs = 0;
			if (this.type != null) {
				result[0] = t;
				startArgs = 1;
			}
			int length = Math.min(result.length - startArgs, args.length);
			if (length == 0) {
				return result;
			}
			System.arraycopy(args, 0, result, startArgs, length);
			return result;
		}

	}

}
