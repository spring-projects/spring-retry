/*
 * Copyright 2006-2007 the original author or authors.
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

import org.springframework.classify.util.MethodInvoker;
import org.springframework.classify.util.MethodInvokerUtils;
import org.springframework.util.Assert;

/**
 * Wrapper for an object to adapt it to the {@link Classifier} interface.
 *
 * @author Dave Syer
 * @param <C> the type of the thing to classify
 * @param <T> the output of the classifier
 */
@SuppressWarnings("serial")
public class ClassifierAdapter<C, T> implements Classifier<C, T> {

	private MethodInvoker invoker;

	private Classifier<C, T> classifier;

	/**
	 * Default constructor for use with setter injection.
	 */
	public ClassifierAdapter() {
		super();
	}

	/**
	 * Create a new {@link Classifier} from the delegate provided. Use the constructor as
	 * an alternative to the {@link #setDelegate(Object)} method.
	 * @param delegate the delegate
	 */
	public ClassifierAdapter(Object delegate) {
		setDelegate(delegate);
	}

	/**
	 * Create a new {@link Classifier} from the delegate provided. Use the constructor as
	 * an alternative to the {@link #setDelegate(Classifier)} method.
	 * @param delegate the classifier to delegate to
	 */
	public ClassifierAdapter(Classifier<C, T> delegate) {
		this.classifier = delegate;
	}

	public void setDelegate(Classifier<C, T> delegate) {
		this.classifier = delegate;
		this.invoker = null;
	}

	/**
	 * Search for the {@link org.springframework.classify.annotation.Classifier
	 * Classifier} annotation on a method in the supplied delegate and use that to create
	 * a {@link Classifier} from the parameter type to the return type. If the annotation
	 * is not found a unique non-void method with a single parameter will be used, if it
	 * exists. The signature of the method cannot be checked here, so might be a runtime
	 * exception when the method is invoked if the signature doesn't match the classifier
	 * types.
	 * @param delegate an object with an annotated method
	 */
	public final void setDelegate(Object delegate) {
		this.classifier = null;
		this.invoker = MethodInvokerUtils
				.getMethodInvokerByAnnotation(org.springframework.classify.annotation.Classifier.class, delegate);
		if (this.invoker == null) {
			this.invoker = MethodInvokerUtils.<C, T>getMethodInvokerForSingleArgument(delegate);
		}
		Assert.state(this.invoker != null, "No single argument public method with or without "
				+ "@Classifier was found in delegate of type " + delegate.getClass());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public T classify(C classifiable) {
		if (this.classifier != null) {
			return this.classifier.classify(classifiable);
		}
		return (T) this.invoker.invokeMethod(classifiable);
	}

}
