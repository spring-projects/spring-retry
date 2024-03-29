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
package org.springframework.classify;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link Classifier} for a parameterised object type based on a map. Classifies objects
 * according to their inheritance relation with the supplied type map. If the object to be
 * classified is one of the keys of the provided map, or is a subclass of one of the keys,
 * then the map entry value for that key is returned. Otherwise, returns the default value
 * which is null by default.
 *
 * @author Dave Syer
 * @author Gary Russell
 * @author Artem Bilan
 * @param <T> the type of the thing to classify
 * @param <C> the output of the classifier
 */
@SuppressWarnings("serial")
public class SubclassClassifier<T, C> implements Classifier<T, C> {

	private ConcurrentMap<Class<? extends T>, C> classified;

	private C defaultValue;

	/**
	 * Create a {@link SubclassClassifier} with null default value.
	 */
	public SubclassClassifier() {
		this(null);
	}

	/**
	 * Create a {@link SubclassClassifier} with supplied default value.
	 * @param defaultValue the default value
	 */
	public SubclassClassifier(C defaultValue) {
		this(new HashMap<>(), defaultValue);
	}

	/**
	 * Create a {@link SubclassClassifier} with supplied default value.
	 * @param defaultValue the default value
	 * @param typeMap the map of types
	 */
	public SubclassClassifier(Map<Class<? extends T>, C> typeMap, C defaultValue) {
		super();
		this.classified = new ConcurrentHashMap<>(typeMap);
		this.defaultValue = defaultValue;
	}

	/**
	 * Public setter for the default value for mapping keys that are not found in the map
	 * (or their subclasses). Defaults to false.
	 * @param defaultValue the default value to set
	 */
	public void setDefaultValue(C defaultValue) {
		this.defaultValue = defaultValue;
	}

	/**
	 * Set the classifications up as a map. The keys are types and these will be mapped
	 * along with all their subclasses to the corresponding value. The most specific types
	 * will match first.
	 * @param map a map from type to class
	 */
	public void setTypeMap(Map<Class<? extends T>, C> map) {
		this.classified = new ConcurrentHashMap<>(map);
	}

	/**
	 * The key is the type and this will be mapped along with all subclasses to the
	 * corresponding value. The most specific types will match first.
	 * @param type the type of the input object
	 * @param target the target value for all such types
	 */
	public void add(Class<? extends T> type, C target) {
		this.classified.put(type, target);
	}

	/**
	 * Return the value from the type map whose key is the class of the given Throwable,
	 * or its nearest ancestor if a subclass.
	 * @return C the classified value
	 * @param classifiable the classifiable thing
	 */
	@Override
	public C classify(T classifiable) {
		if (classifiable == null) {
			return this.defaultValue;
		}

		@SuppressWarnings("unchecked")
		Class<? extends T> exceptionClass = (Class<? extends T>) classifiable.getClass();
		if (this.classified.containsKey(exceptionClass)) {
			return this.classified.get(exceptionClass);
		}

		// check for subclasses
		C value = null;
		for (Class<?> cls = exceptionClass.getSuperclass(); !cls.equals(Object.class)
				&& value == null; cls = cls.getSuperclass()) {

			value = this.classified.get(cls);
		}

		// check for interfaces subclasses
		if (value == null) {
			for (Class<?> cls = exceptionClass; !cls.equals(Object.class) && value == null; cls = cls.getSuperclass()) {
				for (Class<?> ifc : cls.getInterfaces()) {
					value = this.classified.get(ifc);
					if (value != null) {
						break;
					}
				}
			}
		}

		// ConcurrentHashMap doesn't allow nulls
		if (value != null) {
			this.classified.put(exceptionClass, value);
		}

		if (value == null) {
			value = this.defaultValue;
		}

		return value;
	}

	/**
	 * Return the default value supplied in the constructor (default false).
	 * @return C the default value
	 */
	final public C getDefault() {
		return this.defaultValue;
	}

	protected Map<Class<? extends T>, C> getClassified() {
		return this.classified;
	}

}
