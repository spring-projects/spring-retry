/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.retry.support;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.Before;
import org.junit.Test;

import org.springframework.classify.SubclassClassifier;
import org.springframework.retry.policy.SimpleRetryPolicy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.retry.util.test.TestUtils.getPropertyValue;

/**
 * @author Dave Syer
 */
public class AsyncRetryTemplateTests extends AbstractAsyncRetryTest {

	private RetryTemplate retryTemplate;
	
	@Before
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void init() {
//		org.apache.log4j.BasicConfigurator.configure();
		
		Logger root = Logger.getRootLogger();
		root.removeAllAppenders();
		root.addAppender(new ConsoleAppender(new PatternLayout("%r [%t] %p %c{1} %x - %m%n")));
		Logger.getRootLogger().setLevel(Level.TRACE);
		
		this.retryTemplate = new RetryTemplate();
		Map<Class<?>, RetryResultProcessor<?>> map = new HashMap<>();
		map.put(Future.class, new FutureRetryResultProcessor());
		map.put(CompletableFuture.class, new CompletableFutureRetryResultProcessor());
		SubclassClassifier processors = new SubclassClassifier(map,
				(RetryResultProcessor<?>) null);
		this.retryTemplate.setRetryResultProcessors(processors);
	}

	@Test
	public void testSuccessfulRetryCompletable() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
			callback.setAttemptsBeforeSchedulingSuccess(1);
			callback.setAttemptsBeforeJobSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(x);
			this.retryTemplate.setRetryPolicy(policy);
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			assertEquals(callback.defaultResult,
					result.get(10000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.jobAttempts.get());
		}
	}

	// todo: remove of fix after discussion
	/*@Test
	public void testSuccessfulRetryFuture() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			FutureRetryCallback callback = new FutureRetryCallback();
			callback.setAttemptsBeforeSchedulingSuccess(1);
			callback.setAttemptsBeforeJobSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(x + 1);
			this.retryTemplate.setRetryPolicy(policy);
			Future<Object> result = this.retryTemplate.execute(callback);
			assertEquals(callback.defaultResult,
					result.get(10000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.jobAttempts.get());
		}
	}*/

	@Test
	public void testBackOffInvoked() throws Throwable {
		for (int x = 1; x <= 10; x++) {
			CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
			MockBackOffStrategy backOff = new MockBackOffStrategy();
			callback.setAttemptsBeforeSchedulingSuccess(1);
			callback.setAttemptsBeforeJobSuccess(x);
			SimpleRetryPolicy policy = new SimpleRetryPolicy(10);
			this.retryTemplate.setRetryPolicy(policy);
			this.retryTemplate.setBackOffPolicy(backOff);
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			assertEquals(callback.defaultResult,
					result.get(10000L, TimeUnit.MILLISECONDS));
			assertEquals(x, callback.jobAttempts.get());
			assertEquals(1, backOff.startCalls);
			assertEquals(x - 1, backOff.backOffCalls);
		}
	}

	@Test
	public void testNoSuccessRetry() throws Throwable {
		CompletableFutureRetryCallback callback = new CompletableFutureRetryCallback();
		// Something that won't be thrown by JUnit...
		callback.setExceptionToThrow(new IllegalArgumentException());
		callback.setAttemptsBeforeJobSuccess(Integer.MAX_VALUE);
		int retryAttempts = 2;
		this.retryTemplate.setRetryPolicy(new SimpleRetryPolicy(retryAttempts));
		try {
			CompletableFuture<Object> result = this.retryTemplate.execute(callback);
			result.get(1000L, TimeUnit.MILLISECONDS);
			fail("Expected IllegalArgumentException");
		}
		catch (ExecutionException e) {
			assertTrue("Expected IllegalArgumentException",
					e.getCause() instanceof IllegalArgumentException);
			assertEquals(retryAttempts, callback.jobAttempts.get());
			return;
		}
		fail("Expected IllegalArgumentException");
	}
}
