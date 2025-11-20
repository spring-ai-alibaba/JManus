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
package com.alibaba.cloud.ai.lynxe.tool.database;

/**
 * UUID generation tool request object
 *
 * <p>
 * This object contains optional parameters for UUID generation operations.
 * </p>
 *
 * @author Spring AI Alibaba Team
 * @since 1.0.0
 */
public class UuidGenerateRequest {

	/**
	 * Action type for UUID generation
	 */
	private String action;

	/**
	 * Get action type
	 * @return Action type string
	 */
	public String getAction() {
		return action;
	}

	/**
	 * Set action type
	 * @param action Action type string
	 */
	public void setAction(String action) {
		this.action = action;
	}

}
