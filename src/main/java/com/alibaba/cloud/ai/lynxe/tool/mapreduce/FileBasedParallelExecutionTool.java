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
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
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

	private final UnifiedDirectoryManager directoryManager;

	private final ParallelExecutionService parallelExecutionService;

	private final ToolI18nService toolI18nService;

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
			UnifiedDirectoryManager directoryManager, ParallelExecutionService parallelExecutionService,
			ToolI18nService toolI18nService) {
		this.objectMapper = objectMapper;
		this.toolCallbackMap = toolCallbackMap;
		this.directoryManager = directoryManager;
		this.parallelExecutionService = parallelExecutionService;
		this.toolI18nService = toolI18nService;
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
		return toolI18nService.getDescription("file-based-parallel-execution-tool");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("file-based-parallel-execution-tool");
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
					// Count success and failure
					int successCount = 0;
					int failureCount = 0;
					for (Map<String, Object> result : results) {
						String status = (String) result.get("status");
						if ("SUCCESS".equals(status)) {
							successCount++;
						}
						else {
							failureCount++;
						}
					}

					// Build complete result JSON
					Map<String, Object> finalResult = new HashMap<>();
					finalResult.put("message", "Executed " + paramsList.size() + " parameter sets");
					finalResult.put("total", paramsList.size());
					finalResult.put("successCount", successCount);
					finalResult.put("failureCount", failureCount);
					finalResult.put("results", results);

					// Generate filename: toolName-timestamp.json
					String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
					String outputFileName = toolName + "-" + timestamp + ".json";

					// Save complete JSON to file
					String savedFilePath = saveResultToFile(finalResult, outputFileName);
					if (savedFilePath == null) {
						// If file save failed, return full result as fallback
						try {
							return new ToolExecuteResult(objectMapper.writeValueAsString(finalResult));
						}
						catch (JsonProcessingException e) {
							logger.error("Error serializing result: {}", e.getMessage(), e);
							return new ToolExecuteResult(
									String.format("Executed %d parameter sets. Success: %d, Failure: %d",
											paramsList.size(), successCount, failureCount));
						}
					}

					// Return simplified message
					String summaryMessage = String.format(
							"Executed %d parameter sets. Success: %d, Failure: %d. Details saved to file: %s",
							paramsList.size(), successCount, failureCount, outputFileName);
					return new ToolExecuteResult(summaryMessage);
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
	 * Read file and parse JSON array of parameters File is located in root plan shared
	 * directory (same as MarkdownConverterTool)
	 */
	private List<Map<String, Object>> readAndParseFile(String fileName) {
		try {
			if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
				logger.error("rootPlanId is required for file operations but is null or empty");
				return null;
			}

			// Get the root plan directory (same as MarkdownConverterTool)
			Path rootPlanDirectory = directoryManager.getRootPlanDirectory(rootPlanId);

			// Resolve file path within the root plan directory
			Path filePath = rootPlanDirectory.resolve(fileName).normalize();

			// Ensure the path stays within the root plan directory
			if (!filePath.startsWith(rootPlanDirectory)) {
				logger.warn("File path is outside root plan directory: {}", fileName);
				return null;
			}

			if (!Files.exists(filePath)) {
				logger.error("File not found in root plan directory: {} (full path: {})", fileName, filePath);
				return null;
			}

			// Read entire file content
			String fileContent = Files.readString(filePath).trim();
			if (fileContent.isEmpty()) {
				logger.warn("File is empty: {}", filePath);
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
		catch (Exception e) {
			logger.error("Error finding file {}: {}", fileName, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Save complete result JSON to file in root plan directory
	 * @param result Complete result map
	 * @param fileName Output file name
	 * @return Saved file path (relative) or null if failed
	 */
	private String saveResultToFile(Map<String, Object> result, String fileName) {
		try {
			if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
				logger.error("rootPlanId is required for file operations but is null or empty");
				return null;
			}

			// Get root plan directory (same as MarkdownConverterTool)
			Path rootPlanDirectory = directoryManager.getRootPlanDirectory(rootPlanId);

			// Ensure root plan directory exists
			Files.createDirectories(rootPlanDirectory);

			// Resolve file path within the root plan directory
			Path filePath = rootPlanDirectory.resolve(fileName).normalize();

			// Ensure the path stays within the root plan directory
			if (!filePath.startsWith(rootPlanDirectory)) {
				logger.warn("File path is outside root plan directory: {}", fileName);
				return null;
			}

			// Convert to JSON string with pretty printing
			String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);

			// Write to file
			Files.writeString(filePath, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);

			logger.info("Successfully saved execution results to file: {}", filePath);
			return fileName; // Return relative path
		}
		catch (IOException e) {
			logger.error("Error saving results to file: {}", fileName, e);
			return null;
		}
		catch (Exception e) {
			logger.error("Error converting results to JSON for file: {}", fileName, e);
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
