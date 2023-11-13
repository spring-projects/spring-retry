package org.springframework.retry.backoff;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class BackOffByExceptionTypePolicyTests {

	@Test
	public void defaultNoBackOff() {
		BackOffByExceptionTypePolicy exceptionClassifierBackOffPolicy = new BackOffByExceptionTypePolicy();
		BackOffByExceptionTypePolicy.BackOffByExceptionTypeContext startedBackOffContext = (BackOffByExceptionTypePolicy.BackOffByExceptionTypeContext) exceptionClassifierBackOffPolicy
			.start(null);

		BackOffPolicy backOffPolicy = startedBackOffContext.exceptionClassifier.classify(new IOException());
		Assertions.assertTrue(backOffPolicy instanceof NoBackOffPolicy, "Expected NoBackOffPolicy ");
	}

	@Test
	public void matchTheExceptionType() {
		BackOffByExceptionTypePolicy exceptionClassifierBackOffPolicy = new BackOffByExceptionTypePolicy();
		Map<Class<? extends Throwable>, BackOffPolicy> backOffPolicyMap = new HashMap<>();
		backOffPolicyMap.put(IOException.class, new FixedBackOffPolicy());
		backOffPolicyMap.put(SecurityException.class, new ExponentialBackOffPolicy());
		exceptionClassifierBackOffPolicy.setPolicyMap(backOffPolicyMap);
		BackOffByExceptionTypePolicy.BackOffByExceptionTypeContext startedBackOffContext = (BackOffByExceptionTypePolicy.BackOffByExceptionTypeContext) exceptionClassifierBackOffPolicy
			.start(null);
		BackOffPolicy backOffPolicy = startedBackOffContext.exceptionClassifier.classify(new IOException());
		Assertions.assertTrue(backOffPolicy instanceof FixedBackOffPolicy, "Expected FixedBackOffPolicy ");
		backOffPolicy = startedBackOffContext.exceptionClassifier.classify(new SecurityException());
		Assertions.assertTrue(backOffPolicy instanceof ExponentialBackOffPolicy, "Expected FixedBackOffPolicy ");
	}

	@Test
	public void testStatefulBackOff() throws IOException {
		BackOffByExceptionTypePolicy exceptionClassifierBackOffPolicy = new BackOffByExceptionTypePolicy();
		Map<Class<? extends Throwable>, BackOffPolicy> backOffPolicyMap = new HashMap<>();
		ExponentialBackOffPolicy max222 = new ExponentialBackOffPolicy();
		DummySleeper max222Sleeper = new DummySleeper();
		max222.setSleeper(max222Sleeper);
		max222.setMaxInterval(222);
		ExponentialBackOffPolicy interval333 = new ExponentialBackOffPolicy();
		DummySleeper initial333Sleeper = new DummySleeper();
		interval333.setInitialInterval(333);
		interval333.setSleeper(initial333Sleeper);
		backOffPolicyMap.put(SecurityException.class, interval333);
		backOffPolicyMap.put(IOException.class, max222);
		exceptionClassifierBackOffPolicy.setPolicyMap(backOffPolicyMap);
		RetryTemplate retryTemplate = new RetryTemplate();
		retryTemplate.setRetryPolicy(new SimpleRetryPolicy(6));
		retryTemplate.setBackOffPolicy(exceptionClassifierBackOffPolicy);
		AtomicReference<Integer> count = new AtomicReference<>(0);
		Integer execute = retryTemplate.execute(context -> {
			count.getAndSet(count.get() + 1);
			if (count.get() == 1) {
				throw new SecurityException();
			}
			else if (count.get() < 5) {
				throw new IOException();
			}
			else {
				return count.get();
			}
		});

		Assertions.assertArrayEquals(new long[] { 100, 200, 222 }, max222Sleeper.getBackOffs());
		Assertions.assertArrayEquals(new long[] { 333 }, initial333Sleeper.getBackOffs());
	}

}
