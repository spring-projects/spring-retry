/*
 * Copyright 2006-2016 the original author or authors.
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
package org.springframework.retry.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.classify.BackToBackPatternClassifier;
import org.springframework.classify.ClassifierSupport;
import org.springframework.classify.PatternMatchingClassifier;
import org.springframework.classify.SubclassClassifier;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

/**
 * @author Dave Syer
 * @author Gary Russell
 *
 */
public class StatefulRetryOperationsInterceptorTests {

	private StatefulRetryOperationsInterceptor interceptor;

	private final RetryTemplate retryTemplate = new RetryTemplate();

	private Service service;

	private Transformer transformer;

	private Transformer transformerThrowingSpecificEx;

	private RetryContext context;

	private static int count;

	@Before
	public void setUp() throws Exception {
		interceptor = new StatefulRetryOperationsInterceptor();
		retryTemplate.registerListener(new RetryListenerSupport() {
			@Override
			public <T, E extends Throwable> void close(RetryContext context,
					RetryCallback<T, E> callback, Throwable throwable) {
				StatefulRetryOperationsInterceptorTests.this.context = context;
			}
		});
		interceptor.setRetryOperations(retryTemplate);
		service = ProxyFactory.getProxy(Service.class,
				new SingletonTargetSource(new ServiceImpl()));
		transformer = ProxyFactory.getProxy(Transformer.class,
				new SingletonTargetSource(new TransformerImpl()));
		transformerThrowingSpecificEx = ProxyFactory.getProxy(Transformer.class,
				new SingletonTargetSource(new TransformerImplForTestOriginalException()));
		count = 0;
	}

	@Test
	public void testDefaultInterceptorSunnyDay() throws Exception {
		((Advised) service).addAdvice(interceptor);
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
	}

	@Test
	public void testDefaultInterceptorWithLabel() throws Exception {
		interceptor.setLabel("FOO");
		((Advised) service).addAdvice(interceptor);
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		assertEquals("FOO", context.getAttribute(RetryContext.NAME));
	}

	@Test
	public void testDefaultTransformerInterceptorSunnyDay() throws Exception {
		((Advised) transformer).addAdvice(interceptor);
		try {
			transformer.transform("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
	}

	@Test
	public void testDefaultInterceptorAlwaysRetry() throws Exception {
		retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
		interceptor.setRetryOperations(retryTemplate);
		((Advised) service).addAdvice(interceptor);
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
	}

	@Test
	public void testInterceptorChainWithRetry() throws Exception {
		((Advised) service).addAdvice(interceptor);
		final List<String> list = new ArrayList<String>();
		((Advised) service).addAdvice(new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation invocation) throws Throwable {
				list.add("chain");
				return invocation.proceed();
			}
		});
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		service.service("foo");
		assertEquals(2, count);
		assertEquals(2, list.size());
	}

	@Test
	public void testTransformerWithSuccessfulRetry() throws Exception {
		((Advised) transformer).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(2));
		try {
			transformer.transform("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		Collection<String> result = transformer.transform("foo");
		assertEquals(2, count);
		assertEquals(1, result.size());
	}

	@Test
	public void testRetryExceptionAfterTooManyAttemptsWithNoRecovery() throws Exception {
		((Advised) service).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		try {
			service.service("foo");
			fail("Expected ExhaustedRetryException");
		}
		catch (ExhaustedRetryException e) {
			// expected
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Retry exhausted"));
		}
		assertEquals(1, count);
	}

	@Test
	public void testRecoveryAfterTooManyAttempts() throws Exception {
		((Advised) service).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		try {
			service.service("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		interceptor.setRecoverer(new MethodInvocationRecoverer<Object>() {
			@Override
			public Object recover(Object[] data, Throwable cause) {
				count++;
				return null;
			}
		});
		service.service("foo");
		assertEquals(2, count);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testKeyGeneratorReturningNull() throws Throwable {
		this.interceptor.setKeyGenerator(mock(MethodArgumentsKeyGenerator.class));
		this.interceptor.setLabel("foo");
		RetryOperations template = mock(RetryOperations.class);
		this.interceptor.setRetryOperations(template);
		MethodInvocation invocation = mock(MethodInvocation.class);
		when(invocation.getArguments()).thenReturn(new Object[] { new Object() });
		this.interceptor.invoke(invocation);
		ArgumentCaptor<DefaultRetryState> captor = ArgumentCaptor.forClass(DefaultRetryState.class);
		verify(template).execute(any(RetryCallback.class), any(RecoveryCallback.class), captor.capture());
		assertNull(captor.getValue().getKey());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testKeyGeneratorAndRawKey() throws Throwable {
		this.interceptor.setKeyGenerator(new MethodArgumentsKeyGenerator() {

			@Override
			public Object getKey(Object[] item) {
				return "bar";
			}
		});
		this.interceptor.setLabel("foo");
		this.interceptor.setUseRawKey(true);
		RetryOperations template = mock(RetryOperations.class);
		this.interceptor.setRetryOperations(template);
		MethodInvocation invocation = mock(MethodInvocation.class);
		when(invocation.getArguments()).thenReturn(new Object[] { new Object() });
		this.interceptor.invoke(invocation);
		ArgumentCaptor<DefaultRetryState> captor = ArgumentCaptor.forClass(DefaultRetryState.class);
		verify(template).execute(any(RetryCallback.class), any(RecoveryCallback.class), captor.capture());
		assertEquals("bar", captor.getValue().getKey());
	}

	@Test
	public void testTransformerRecoveryAfterTooManyAttempts() throws Exception {
		((Advised) transformer).addAdvice(interceptor);
		interceptor.setRetryOperations(retryTemplate);
		retryTemplate.setRetryPolicy(new NeverRetryPolicy());
		try {
			transformer.transform("foo");
			fail("Expected Exception.");
		}
		catch (Exception e) {
			String message = e.getMessage();
			assertTrue("Wrong message: " + message,
					message.startsWith("Not enough calls"));
		}
		assertEquals(1, count);
		interceptor.setRecoverer(new MethodInvocationRecoverer<Collection<String>>() {
			@Override
			public Collection<String> recover(Object[] data, Throwable cause) {
				count++;
				return Collections.singleton((String) data[0]);
			}
		});
		Collection<String> result = transformer.transform("foo");
		assertEquals(2, count);
		assertEquals(1, result.size());
	}

	@Test
	public void testInterceptorWithExposeOriginalException() {
		((Advised) transformerThrowingSpecificEx).addAdvice(interceptor);
		interceptor.setExposeOriginalException(true);
		interceptor.setRetryOperations(retryTemplate);
		HashMap<Class<? extends Throwable>, Boolean> caredExceptionMap = new HashMap<Class<? extends Throwable>,
                Boolean>();
		caredExceptionMap.put(RuntimeException.class, Boolean.TRUE);
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3, caredExceptionMap));
        // every time the exception will not be rethrown
		interceptor.setRollbackClassifier(new ClassifierSupport(false));

		boolean exceptionCaught = false;
		// without recover, original exception should be thrown
		try {
			transformerThrowingSpecificEx.transform("foo");
		}
		catch (Exception e) {
		    exceptionCaught = true;
			assertTrue(e instanceof RuntimeException);
		}
		assertEquals(0, count);
		assertTrue(exceptionCaught);

		// with recover, original exception still should be thrown
		interceptor.setRecoverer(new MethodInvocationRecoverer<Collection<String>>() {
			@Override
			public Collection<String> recover(Object[] data, Throwable cause) {
				count++;
				// simulate that the closest recover not found
                throw new ExhaustedRetryException("ExhaustedException", cause);
			}
		});
		try {
		    exceptionCaught = false;
			transformerThrowingSpecificEx.transform("foo");
		}
		catch (Exception e) {
		    exceptionCaught = true;
			assertTrue(e instanceof RuntimeException);
		}
		// recover still will be executed
		assertEquals(1, count);
		assertTrue(exceptionCaught);

		// if it is not cared exception, it should be thrown as before
		count = 3;
		try {
		    exceptionCaught = false;
		    interceptor.setRecoverer(null);
		    transformerThrowingSpecificEx.transform("foo");
        }
        catch (Exception e) {
		    exceptionCaught = true;
		    assertTrue(e instanceof IOException);
        }

        assertTrue(exceptionCaught);

		// if not exposed, ExhaustedRetryException should be thrown
		interceptor.setExposeOriginalException(false);
		count = 0;
		interceptor.setRecoverer(null);
        try {
            exceptionCaught = false;
            transformerThrowingSpecificEx.transform("foo");
        }
        catch (Exception e) {
            exceptionCaught = true;
            assertTrue(e instanceof ExhaustedRetryException);
        }
        assertTrue(exceptionCaught);
	}

	public static interface Service {
		void service(String in) throws Exception;
	}

	public static class ServiceImpl implements Service {

		@Override
		public void service(String in) throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
		}

	}

	public static interface Transformer {
		Collection<String> transform(String in) throws Exception;
	}

	public static class TransformerImpl implements Transformer {

		@Override
		public Collection<String> transform(String in) throws Exception {
			count++;
			if (count < 2) {
				throw new Exception("Not enough calls: " + count);
			}
			return Collections.singleton(in + ":" + count);
		}

	}

	public static class TransformerImplForTestOriginalException implements Transformer {
		@Override
		public Collection<String> transform(String in) throws Exception {
		    if (count < 3) {
		        throw new RuntimeException();
            } else {
		        throw new IOException();
            }
		}
	}
}
