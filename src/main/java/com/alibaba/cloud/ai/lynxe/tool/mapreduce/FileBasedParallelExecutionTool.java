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
package com.alibaba.cloud.ai.lynxe.tool.mapreduce;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.AsyncToolCallBiFunctionDef;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.TextFileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File-based parallel execution tool that reads JSON parameters from a file (JSON array)
 * and executes a specified tool for each parameter set.
 *
 * The file format: The entire file contains a single JSON array, where each element is a
 * JSON object representing one parameter set.
 */
public class FileBasedParallelExecutionTool extends AbstractBaseTool<FileBasedParallelExecutionTool.BatchExecutionInput>
		implements AsyncToolCallBiFunctionDef<FileBasedParallelExecutionTool.BatchExecutionInput> {

	private static final Logger logger = LoggerFactory.getLogger(FileBasedParallelExecutionTool.class);

	private final ObjectMapper objectMapper;

	private final Map<String, ToolCallBackContext> toolCallbackMap;

	private final TextFileService textFileService;

	private final ParallelExecutionService parallelExecutionService;

	/**
	 * Input class for batch execution
	 */
	static class BatchExecutionInput {

		@com.fasterxml.jackson.annotation.JsonProperty("file_name")
		private String fileName;

		@com.fasterxml.jackson.annotation.JsonProperty("tool_name")
		private String toolName;

		public BatchExecutionInput() {
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public String getToolName() {
			return toolName;
		}

		public void setToolName(String toolName) {
			this.toolName = toolName;
		}

	}

	public FileBasedParallelExecutionTool(ObjectMapper objectMapper, Map<String, ToolCallBackContext> toolCallbackMap,
			TextFileService textFileService, ParallelExecutionService parallelExecutionService) {
		this.objectMapper = objectMapper;
		this.toolCallbackMap = toolCallbackMap;
		this.textFileService = textFileService;
		this.parallelExecutionService = parallelExecutionService;
	}

	/**
	 * Set the tool callback map (used to look up actual tool implementations)
	 */
	public void setToolCallbackMap(Map<String, ToolCallBackContext> toolCallbackMap) {
		this.toolCallbackMap.putAll(toolCallbackMap);
	}

	@Override
	public String getServiceGroup() {
		return "parallel-execution";
	}

	@Override
	public String getName() {
		return "file_based_parallel_execution_tool";
	}

	@Override
	public String getDescription() {
		return "Reads JSON parameters from a file (JSON array) and executes a specified tool for each parameter set. "
				+ "The file must contain a single JSON array, where each element is a JSON object representing one parameter set. "
				+ "If the tool requires parameters that are not present in the JSON, they will be set to empty string.";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "properties": {
				        "file_name": {
				            "type": "string",
				            "description": "Relative path to the file containing JSON array of parameters (each element is a parameter object)"
				        },
				        "tool_name": {
				            "type": "string",
				            "description": "Name of the tool to execute for each parameter set"
				        }
				    },
				    "required": ["file_name", "tool_name"],
				    "additionalProperties": false
				}
				""";
	}

	@Override
	public Class<BatchExecutionInput> getInputType() {
		return BatchExecutionInput.class;
	}

	/**
	 * Synchronous version - delegates to async version
	 */
	@Override
	public ToolExecuteResult apply(BatchExecutionInput input, ToolContext toolContext) {
		return applyAsync(input, toolContext).join();
	}

	/**
	 * Asynchronous version - returns CompletableFuture for non-blocking execution
	 */
	@Override
	public java.util.concurrent.CompletableFuture<ToolExecuteResult> applyAsync(BatchExecutionInput input,
			ToolContext toolContext) {
		try {
			String fileName = input.getFileName();
			String toolName = input.getToolName();

			if (fileName == null || fileName.trim().isEmpty()) {
				return java.util.concurrent.CompletableFuture
					.completedFuture(new ToolExecuteResult("Error: file_name parameter is required"));
			}

			if (toolName == null || toolName.trim().isEmpty()) {
				return java.util.concurrent.CompletableFuture
					.completedFuture(new ToolExecuteResult("Error: tool_name parameter is required"));
			}

			logger.debug("Executing batch execution: file={}, tool={}", fileName, toolName);

			// Read file and parse JSON parameters
			List<Map<String, Object>> paramsList = readAndParseFile(fileName);
			if (paramsList == null || paramsList.isEmpty()) {
				return java.util.concurrent.CompletableFuture
					.completedFuture(new ToolExecuteResult("Error: No valid parameters found in file"));
			}

			// Use common service to execute tools in parallel
			List<ParallelExecutionService.ParallelExecutionRequest> executions = new ArrayList<>();
			for (Map<String, Object> params : paramsList) {
				executions.add(new ParallelExecutionService.ParallelExecutionRequest(toolName, params));
			}

			return parallelExecutionService.executeToolsInParallel(executions, toolCallbackMap, toolContext)
				.thenApply(results -> {
					Map<String, Object> finalResult = new HashMap<>();
					finalResult.put("message", "Executed " + paramsList.size() + " parameter sets");
					finalResult.put("total", paramsList.size());
					finalResult.put("results", results);
					try {
						return new ToolExecuteResult(objectMapper.writeValueAsString(finalResult));
					}
					catch (JsonProcessingException e) {
						logger.error("Error serializing result: {}", e.getMessage(), e);
						return new ToolExecuteResult("Executed " + paramsList.size() + " parameter sets");
					}
				})
				.exceptionally(ex -> {
					logger.error("Error in batch execution: {}", ex.getMessage(), ex);
					return new ToolExecuteResult("Error in batch execution: " + ex.getMessage());
				});
		}
		catch (Exception e) {
			logger.error("Error in FileBasedParallelExecutionTool: {}", e.getMessage(), e);
			return java.util.concurrent.CompletableFuture
				.completedFuture(new ToolExecuteResult("Error: " + e.getMessage()));
		}
	}

	@Override
	public ToolExecuteResult run(BatchExecutionInput input) {
		throw new UnsupportedOperationException(
				"FileBasedParallelExecutionTool must be called using apply() method with ToolContext, not run()");
	}

	/**
	 * Read file and parse JSON array of parameters
	 */
	private List<Map<String, Object>> readAndParseFile(String fileName) {
		try {
			// Get absolute path using TextFileService
			Path absolutePath = textFileService.getAbsolutePath(rootPlanId, fileName, currentPlanId);

			if (!Files.exists(absolutePath)) {
				logger.error("File not found: {}", absolutePath);
				return null;
			}

			// Read entire file content
			String fileContent = Files.readString(absolutePath).trim();
			if (fileContent.isEmpty()) {
				logger.warn("File is empty: {}", absolutePath);
				return new ArrayList<>();
			}

			// Parse entire file as JSON array
			try {
				List<Map<String, Object>> paramsList = objectMapper.readValue(fileContent,
						new TypeReference<List<Map<String, Object>>>() {
						});
				if (paramsList == null) {
					logger.warn("Parsed JSON array is null, returning empty list");
					return new ArrayList<>();
				}
				logger.debug("Successfully parsed {} parameter sets from file: {}", paramsList.size(), fileName);
				return paramsList;
			}
			catch (Exception e) {
				logger.error("Error parsing JSON array from file {}: {}", fileName, e.getMessage(), e);
				return null;
			}
		}
		catch (IOException e) {
			logger.error("Error reading file {}: {}", fileName, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public void cleanup(String planId) {
		// No cleanup needed for this tool
		logger.debug("Cleaned up FileBasedParallelExecutionTool");
	}

	@Override
	public String getCurrentToolStateString() {
		return "FileBasedParallelExecutionTool is ready";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
