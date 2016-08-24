/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.retry.stats;

/**
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class ExponentialAverageRetryStatistics extends DefaultRetryStatistics {

	private long window = 15000;

	private ExponentialAverage started;
	
	private ExponentialAverage error;
	
	private ExponentialAverage complete;
	
	private ExponentialAverage recovery;
	
	private ExponentialAverage abort;
	
	public ExponentialAverageRetryStatistics(String name) {
		super(name);
		init();
	}

	private void init() {
		started = new ExponentialAverage(window);
		error = new ExponentialAverage(window);
		complete = new ExponentialAverage(window);
		abort = new ExponentialAverage(window);
		recovery = new ExponentialAverage(window);
	}

	/**
	 * Window in milliseconds for exponential decay factor in rolling average.
	 * 
	 * @param window the window to set
	 */
	public void setWindow(long window) {
		this.window = window;
		init();
	}

	public int getRollingStartedCount() {
		return (int) Math.round(started.getValue());
	}

	public int getRollingErrorCount() {
		return (int) Math.round(error.getValue());
	}
	
	public int getRollingAbortCount() {
		return (int) Math.round(abort.getValue());
	}
	
	public int getRollingRecoveryCount() {
		return (int) Math.round(recovery.getValue());
	}
	
	public int getRollingCompleteCount() {
		return (int) Math.round(complete.getValue());
	}
	
	public double getRollingErrorRate() {
		if (Math.round(started.getValue())==0) {
			return 0.;
		}
		return (abort.getValue() + recovery.getValue()) / started.getValue();
	}
	
	@Override
	public void incrementStartedCount() {
		super.incrementStartedCount();
		started.increment();
	}
	
	@Override
	public void incrementCompleteCount() {
		super.incrementCompleteCount();
		complete.increment();
	}

	@Override
	public void incrementRecoveryCount() {
		super.incrementRecoveryCount();
		recovery.increment();
	}

	@Override
	public void incrementErrorCount() {
		super.incrementErrorCount();
		error.increment();
	}

	@Override
	public void incrementAbortCount() {
		super.incrementAbortCount();
		abort.increment();
	}

	private class ExponentialAverage {

		private final double alpha;
		
		private volatile long lastTime = System.currentTimeMillis();
		
		private volatile double value = 0;

		public ExponentialAverage(long window) {
			alpha = 1. / window;
		}

		public synchronized void increment() {
			long time = System.currentTimeMillis();
			value = value * Math.exp(-alpha*(time - lastTime)) + 1;
			lastTime = time;
		}
		
		public double getValue() {
			long time = System.currentTimeMillis();
			return value * Math.exp(-alpha*(time - lastTime));
		}

	}

}
