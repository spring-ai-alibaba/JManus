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
package com.alibaba.cloud.ai.lynxe.tool;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tool for reporting execution errors and storing error messages in the execution record.
 * This tool implements TerminableTool to stop execution when an error is reported.
 */
public class ErrorReportTool extends AbstractBaseTool<Map<String, Object>> implements TerminableTool {

	private static final Logger log = LoggerFactory.getLogger(ErrorReportTool.class);

	public static final String name = "error_report";

	private String lastErrorMessage = "";

	private boolean isErrorReported = false;

	private String errorReportTimestamp = "";

	private final ObjectMapper objectMapper;

	private static String getDescriptionText() {
		return "Report execution errors and store error messages in the execution record. "
				+ "This tool should be used when an error occurs during execution that needs to be displayed in the execution details view. "
				+ "The error message will be stored and displayed to the user.";
	}

	private static String generateParametersJson() {
		return """
				{
				  "type": "object",
				  "properties": {
				    "errorMessage": {
				      "type": "string",
				      "description": "Detailed error message describing what went wrong during execution. This message will be displayed in the execution details view."
				    }
				  },
				  "required": ["errorMessage"]
				}
				""";
	}

	@Override
	public String getCurrentToolStateString() {
		return String.format("""
				Error Report Tool Status:
				- Current State: %s
				- Last Error: %s
				- Error Message: %s
				- Timestamp: %s
				- Plan ID: %s
				""", isErrorReported ? "⚠️ Error Reported" : "✅ No Error",
				isErrorReported ? "Error was reported" : "No error recorded",
				lastErrorMessage.isEmpty() ? "N/A" : lastErrorMessage,
				errorReportTimestamp.isEmpty() ? "N/A" : errorReportTimestamp,
				currentPlanId != null ? currentPlanId : "N/A");
	}

	public ErrorReportTool(String planId) {
		this.currentPlanId = planId;
		this.objectMapper = new ObjectMapper();
		this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	public ErrorReportTool(String planId, ObjectMapper objectMapper) {
		this.currentPlanId = planId;
		if (objectMapper != null) {
			this.objectMapper = objectMapper;
		}
		else {
			this.objectMapper = new ObjectMapper();
			this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		}
	}

	@Override
	public ToolExecuteResult run(Map<String, Object> input) {
		log.info("ErrorReportTool called with input: {}", input);

		// Extract error message from input
		Object errorMessageObj = input.get("errorMessage");
		String errorMessage = errorMessageObj != null ? errorMessageObj.toString() : "";

		if (errorMessage.isEmpty()) {
			log.warn("ErrorReportTool called without errorMessage, using default message");
			errorMessage = "An error occurred during execution";
		}

		this.lastErrorMessage = errorMessage;
		this.isErrorReported = true;
		this.errorReportTimestamp = java.time.LocalDateTime.now().toString();

		// Format the error message as JSON for storage
		try {
			Map<String, Object> errorData = Map.of("errorMessage", errorMessage, "timestamp", errorReportTimestamp);
			String jsonString = objectMapper.writeValueAsString(errorData);
			log.info("Error reported successfully for planId: {}, errorMessage: {}", currentPlanId, errorMessage);
			return new ToolExecuteResult(jsonString);
		}
		catch (Exception e) {
			log.error("Failed to format error message as JSON", e);
			// Fallback to simple string representation
			return new ToolExecuteResult("{\"errorMessage\":\"" + errorMessage + "\"}");
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return ErrorReportTool.getDescriptionText();
	}

	@Override
	public String getParameters() {
		return generateParametersJson();
	}

	@Override
	@SuppressWarnings("unchecked")
	public Class<Map<String, Object>> getInputType() {
		return (Class<Map<String, Object>>) (Class<?>) Map.class;
	}

	@Override
	public boolean isReturnDirect() {
		return true;
	}

	@Override
	public void cleanup(String planId) {
		// Reset state when cleaning up
		this.lastErrorMessage = "";
		this.isErrorReported = false;
		this.errorReportTimestamp = "";
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	// ==================== TerminableTool interface implementation ====================

	@Override
	public boolean canTerminate() {
		// ErrorReportTool can always terminate as its purpose is to report errors and
		// stop execution
		return true;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
