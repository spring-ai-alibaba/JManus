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
package com.alibaba.cloud.ai.manus.planning.exception;

/**
 * Exception thrown when plan template configuration operations fail
 * This exception provides error codes and detailed messages for plan template configuration errors
 */
public class PlanTemplateConfigException extends RuntimeException {

	private final String errorCode;

	/**
	 * Constructs a new PlanTemplateConfigException with the specified error code and detail message
	 * @param errorCode the error code (e.g., "VALIDATION_ERROR", "NOT_FOUND", "INTERNAL_ERROR")
	 * @param message the detail message explaining the plan template configuration error
	 */
	public PlanTemplateConfigException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	/**
	 * Constructs a new PlanTemplateConfigException with the specified error code, detail message, and cause
	 * @param errorCode the error code (e.g., "VALIDATION_ERROR", "NOT_FOUND", "INTERNAL_ERROR")
	 * @param message the detail message explaining the plan template configuration error
	 * @param cause the cause of the plan template configuration error
	 */
	public PlanTemplateConfigException(String errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}

	/**
	 * Get the error code
	 * @return the error code
	 */
	public String getErrorCode() {
		return errorCode;
	}

}

