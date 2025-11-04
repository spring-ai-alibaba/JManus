/*
 * Copyright 2025 the original author or authors.
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
package com.alibaba.cloud.ai.manus.tool.browser;

/**
 * Options for generating ARIA snapshot of a page
 */
public class AriaSnapshotOptions {

	/**
	 * CSS selector for the element to snapshot. If not specified, defaults to "body"
	 */
	private String selector;

	/**
	 * Timeout in milliseconds for the snapshot operation
	 */
	private Integer timeout;

	public AriaSnapshotOptions() {
		this.selector = "body";
		this.timeout = 30000; // Default 30 seconds
	}

	/**
	 * Get CSS selector
	 * @return CSS selector
	 */
	public String getSelector() {
		return selector;
	}

	/**
	 * Set CSS selector
	 * @param selector CSS selector
	 * @return This instance for method chaining
	 */
	public AriaSnapshotOptions setSelector(String selector) {
		this.selector = selector;
		return this;
	}

	/**
	 * Get timeout in milliseconds
	 * @return Timeout value
	 */
	public Integer getTimeout() {
		return timeout;
	}

	/**
	 * Set timeout in milliseconds
	 * @param timeout Timeout value
	 * @return This instance for method chaining
	 */
	public AriaSnapshotOptions setTimeout(Integer timeout) {
		this.timeout = timeout;
		return this;
	}

}

