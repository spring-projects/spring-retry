/*
 * Copyright 2006-2022 the original author or authors.
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

package org.springframework.retry.listener;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * @author Dave Syer
 * @author Gary Russell
 * @author Henning Pöttker
 */
@SuppressWarnings("deprecation")
public class RetryListenerSupportTests {

	@Test
	public void testClose() {
		RetryListenerSupport support = new RetryListenerSupport();
		assertThatNoException().isThrownBy(() -> support.close(null, null, null));
	}

	@Test
	public void testOnError() {
		RetryListenerSupport support = new RetryListenerSupport();
		assertThatNoException().isThrownBy(() -> support.onError(null, null, null));
	}

	@Test
	public void testOpen() {
		RetryListenerSupport support = new RetryListenerSupport();
		assertThat(support.open(null, null)).isTrue();
	}

}
