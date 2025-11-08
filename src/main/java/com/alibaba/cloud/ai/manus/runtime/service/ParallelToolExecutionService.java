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
package com.alibaba.cloud.ai.manus.runtime.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.ToolCallBiFunctionDef;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;

/**
 * Service for executing multiple tools in parallel Extracted from ParallelExecutionTool
 * to provide reusable parallel execution functionality
 */
@Service
public class ParallelToolExecutionService {

	private static final Logger log = LoggerFactory.getLogger(ParallelToolExecutionService.class);

	/**
	 * Result of a single tool execution
	 */
	public static class ToolExecutionResult {

		private final String toolName;

		private final ToolExecuteResult result;

		private final boolean success;

		public ToolExecutionResult(String toolName, ToolExecuteResult result, boolean success) {
			this.toolName = toolName;
			this.result = result;
			this.success = success;
		}

		public String getToolName() {
			return toolName;
		}

		public ToolExecuteResult getResult() {
			return result;
		}

		public boolean isSuccess() {
			return success;
		}

	}

	/**
	 * Execute multiple tools in parallel
	 * @param toolCalls List of tool calls to execute
	 * @param toolCallbackMap Map of tool names to their callback contexts
	 * @param planIdDispatcher Plan ID dispatcher for generating tool call IDs
	 * @param parentToolContext Parent tool context (for propagating toolCallId and
	 * planDepth)
	 * @return List of execution results for each tool
	 */
	public List<ToolExecutionResult> executeToolsInParallel(List<ToolCall> toolCalls,
			Map<String, ToolCallBackContext> toolCallbackMap, PlanIdDispatcher planIdDispatcher,
			ToolContext parentToolContext) {
		if (toolCalls == null || toolCalls.isEmpty()) {
			log.warn("No tool calls provided for parallel execution");
			return new ArrayList<>();
		}

		log.info("Executing {} tools in parallel", toolCalls.size());

		// Extract parent toolCallId and planDepth from context
		String parentToolCallId = extractToolCallIdFromContext(parentToolContext);
		Integer parentPlanDepth = extractPlanDepthFromContext(parentToolContext);

		List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();

		// Create parallel execution tasks
		for (ToolCall toolCall : toolCalls) {
			String toolName = toolCall.name();
			ToolCallBackContext toolContext = toolCallbackMap.get(toolName);

			if (toolContext == null) {
				log.warn("Tool not found in callback map: {}", toolName);
				futures.add(CompletableFuture.completedFuture(new ToolExecutionResult(toolName,
						new ToolExecuteResult("Tool not found: " + toolName), false)));
				continue;
			}

			// Create future for this tool execution
			CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(() -> {
				try {
					log.debug("Executing tool: {} in parallel", toolName);

					ToolCallBiFunctionDef<?> functionInstance = toolContext.getFunctionInstance();
					Map<String, Object> input = parseToolArguments(toolCall.arguments());

					// Generate or use parent toolCallId
					String toolCallId = (parentToolCallId != null) ? parentToolCallId
							: planIdDispatcher.generateToolCallId();

					// Build tool context with toolCallId and planDepth
					Map<String, Object> contextMap = new HashMap<>();
					contextMap.put("toolcallId", toolCallId);
					if (parentPlanDepth != null) {
						contextMap.put("planDepth", parentPlanDepth);
					}
					ToolContext toolContextForExecution = new ToolContext(contextMap);

					// Execute the tool using apply method
					@SuppressWarnings("unchecked")
					ToolExecuteResult result = ((AbstractBaseTool<Map<String, Object>>) functionInstance).apply(input,
							toolContextForExecution);

					log.debug("Completed execution for tool: {}", toolName);
					return new ToolExecutionResult(toolName, result, true);
				}
				catch (Exception e) {
					log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
					return new ToolExecutionResult(toolName, new ToolExecuteResult("Error: " + e.getMessage()), false);
				}
			});

			futures.add(future);
		}

		// Wait for all tasks to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Collect results
		List<ToolExecutionResult> results = new ArrayList<>();
		for (CompletableFuture<ToolExecutionResult> future : futures) {
			try {
				results.add(future.get());
			}
			catch (Exception e) {
				log.error("Error getting tool execution result: {}", e.getMessage(), e);
			}
		}

		log.info("Completed parallel execution of {} tools", results.size());
		return results;
	}

	/**
	 * Extract toolCallId from parent ToolContext
	 */
	private String extractToolCallIdFromContext(ToolContext parentToolContext) {
		try {
			if (parentToolContext != null && parentToolContext.getContext() != null) {
				Object v = parentToolContext.getContext().get("toolcallId");
				if (v != null) {
					return String.valueOf(v);
				}
			}
		}
		catch (Exception e) {
			log.debug("Error extracting toolCallId from context: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Extract planDepth from parent ToolContext
	 */
	private Integer extractPlanDepthFromContext(ToolContext parentToolContext) {
		try {
			if (parentToolContext != null && parentToolContext.getContext() != null) {
				Object d = parentToolContext.getContext().get("planDepth");
				if (d instanceof Number) {
					return ((Number) d).intValue();
				}
				else if (d instanceof String) {
					return Integer.parseInt((String) d);
				}
			}
		}
		catch (Exception e) {
			log.debug("Error extracting planDepth from context: {}", e.getMessage());
		}
		return null;
	}

	/**
	 * Parse tool arguments from JSON string to Map
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> parseToolArguments(String arguments) {
		if (arguments == null || arguments.trim().isEmpty()) {
			return new HashMap<>();
		}

		try {
			// Try to parse as JSON
			com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
			Object parsed = objectMapper.readValue(arguments, Object.class);
			if (parsed instanceof Map) {
				return (Map<String, Object>) parsed;
			}
			else {
				// If it's not a Map, wrap it
				Map<String, Object> result = new HashMap<>();
				result.put("value", parsed);
				return result;
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse tool arguments as JSON: {}. Using empty map.", arguments);
			return new HashMap<>();
		}
	}

}
