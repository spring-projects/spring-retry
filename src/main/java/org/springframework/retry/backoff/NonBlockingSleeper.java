/*
 * Copyright 2014 the original author or authors.
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

package org.springframework.retry.backoff;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Non-blocking {@link Sleeper} implementation that just sleep with specified
 * backOffPeriod.
 *
 * @author Alireza Hakimrabet
 */
@SuppressWarnings("serial")
public class NonBlockingSleeper implements Sleeper {

	@Override
	public void sleep(long backOffPeriod) throws InterruptedException {
		CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
			try {
				TimeUnit.MILLISECONDS.sleep(backOffPeriod);
			}
			catch (Exception e) {
				Thread.currentThread().interrupt();
				throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
			}
		});
		future.join();
	}

}
