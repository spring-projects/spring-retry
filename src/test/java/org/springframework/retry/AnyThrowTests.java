/*
 * Copyright 2012-2013 the original author or authors.
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

package org.springframework.retry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 * @author Dave Syer
 *
 */
public class AnyThrowTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void testRuntimeException() throws Throwable {
		expected.expect(RuntimeException.class);
		AnyThrow.throwAny(new RuntimeException("planned"));
	}

	@Test
	public void testUncheckedRuntimeException() throws Throwable {
		expected.expect(RuntimeException.class);
		AnyThrow.throwUnchecked(new RuntimeException("planned"));
	}

	@Test
	public void testCheckedException() throws Throwable {
		expected.expect(Exception.class);
		AnyThrow.throwAny(new Exception("planned"));
	}

	private static class AnyThrow {

		private static void throwUnchecked(Throwable e) {
			AnyThrow.<RuntimeException>throwAny(e);
		}

		@SuppressWarnings("unchecked")
		private static <E extends Throwable> void throwAny(Throwable e) throws E {
			throw (E) e;
		}

	}

}
