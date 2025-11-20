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
package com.alibaba.cloud.ai.lynxe.exception.handler;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;

import com.alibaba.cloud.ai.lynxe.exception.PlanException;
import com.alibaba.cloud.ai.lynxe.planning.exception.PlanTemplateConfigException;

/**
 * @author dahua
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * Handle plan exceptions
	 */
	@SuppressWarnings("rawtypes")
	@ExceptionHandler(PlanException.class)
	public ResponseEntity handlePlanException(PlanException ex) {
		Map<String, Object> response = new HashMap<>();
		response.put("error", ex.getMessage());
		return ResponseEntity.internalServerError().body(response);
	}

	/**
	 * Handle PlanTemplateConfigException - return JSON format with errorCode
	 */
	@ExceptionHandler(PlanTemplateConfigException.class)
	public ResponseEntity<Map<String, Object>> handlePlanTemplateConfigException(PlanTemplateConfigException ex) {
		Map<String, Object> response = new HashMap<>();
		response.put("error", ex.getMessage());
		response.put("errorCode", ex.getErrorCode());
		return ResponseEntity.badRequest().body(response);
	}

	/**
	 * Handle all uncaught exceptions
	 */
	@SuppressWarnings("rawtypes")
	@ExceptionHandler(Exception.class)
	public ResponseEntity handleGlobalException(Exception ex) {
		Map<String, Object> response = new HashMap<>();
		response.put("error", ex.getMessage());
		return ResponseEntity.internalServerError().body(response);
	}

}
