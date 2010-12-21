/*
 * Copyright 2006-2007 the original author or authors.
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

package org.springframework.repeat.support;

import java.util.List;

import org.junit.Before;

/**
 * Base class for simple tests with small trade data set.
 * 
 * @author Dave Syer
 * 
 */
public abstract class AbstractTradeBatchTests {

	public static final int NUMBER_OF_ITEMS = 5;

	protected TradeWriter processor = new TradeWriter();

	protected TradeReader provider;

	@Before
	public void setUp() throws Exception {
		provider = new TradeReader();
	}

	protected static class TradeReader {

		private Trade[] trades = new Trade[] {
				new Trade("UK21341EAH45", 978, "98.34"),
				new Trade("UK21341EAH46", 112, "18.12"),
				new Trade("UK21341EAH47", 245, "12.78"),
				new Trade("UK21341EAH48", 108, "109.25"),
				new Trade("UK21341EAH49", 854, "123.39") };

		private int count = 0;

		public Trade read() {
			if (count < trades.length) {
				return trades[count++];
			}
			return null;
		}

	}

	protected static class TradeWriter {
		int count = 0;

		// This has to be synchronized because we are going to test the state
		// (count) at the end of a concurrent batch run.
		public synchronized void write(List<? extends Trade> data) {
			count++;
			System.out.println("Executing trade '" + data + "'");
		}
	}

}
