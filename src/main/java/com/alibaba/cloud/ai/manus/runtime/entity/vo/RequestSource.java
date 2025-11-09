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
package com.alibaba.cloud.ai.manus.runtime.entity.vo;

/**
 * Request source enumeration
 * Indicates where the request originates from
 */
public enum RequestSource {

	/**
	 * Request from HTTP client (default)
	 */
	HTTP_REQUEST,

	/**
	 * Request from Vue sidebar
	 */
	VUE_SIDEBAR,

	/**
	 * Request from Vue dialog
	 */
	VUE_DIALOG;

	/**
	 * Parse request source from string, default to HTTP_REQUEST if not provided or invalid
	 * @param source Request source string (can be null)
	 * @return RequestSource enum, defaults to HTTP_REQUEST
	 */
	public static RequestSource fromString(String source) {
		if (source == null || source.trim().isEmpty()) {
			return HTTP_REQUEST;
		}
		try {
			return valueOf(source.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			// If invalid value, default to HTTP_REQUEST
			return HTTP_REQUEST;
		}
	}

	/**
	 * Check if this is a Vue request (either from sidebar or dialog)
	 * @return true if request is from Vue, false otherwise
	 */
	public boolean isVueRequest() {
		return this == VUE_SIDEBAR || this == VUE_DIALOG;
	}

}

