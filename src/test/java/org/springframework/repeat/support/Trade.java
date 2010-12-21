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

import java.math.BigDecimal;

/**
 * @author Rob Harrop
 */
public class Trade {

	private String isin;

	private long quantity;

	private BigDecimal price;

	Trade(String isin, long quantity, String price) {
		this.isin = isin;
		this.quantity = quantity;
		this.price = new BigDecimal(price);
	}

	public String getIsin() {
		return isin;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public long getQuantity() {
		return quantity;
	}

	public String toString() {
		return "Trade: [isin=" + isin + ",quantity=" + quantity + ",price=" + price + "]";
	}
}
