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
package com.alibaba.cloud.ai.manus.tool.mapreduce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.ToolCallBiFunctionDef;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.mapreduce.ParallelExecutionTool.RegisterBatchInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parallel execution manager that follows DefaultToolCallingManager execution pattern
 *
 * This class provides functionality to: 1. Batch register executable functions 2. Execute
 * all registered functions in parallel using a 'start' function 3. Track function
 * execution status and get pending functions
 */
public class ParallelExecutionTool extends AbstractBaseTool<RegisterBatchInput> {

	private static final Logger logger = LoggerFactory.getLogger(ParallelExecutionTool.class);

	private final ObjectMapper objectMapper;

	private final Map<String, ToolCallBackContext> toolCallbackMap;

	private final PlanIdDispatcher planIdDispatcher;

	/**
	 * Registry entry for a function
	 */
	static class FunctionRegistry {

		private final String id;

		private final String toolName;

		private final Map<String, Object> input;

		private ToolExecuteResult result;

		public FunctionRegistry(String id, String toolName, Map<String, Object> input) {
			this.id = id;
			this.toolName = toolName;
			this.input = input;
		}

		public String getId() {
			return id;
		}

		public String getToolName() {
			return toolName;
		}

		public Map<String, Object> getInput() {
			return input;
		}

		public ToolExecuteResult getResult() {
			return result;
		}

		public void setResult(ToolExecuteResult result) {
			this.result = result;
		}

	}

	/**
	 * Input class for batch function registration
	 */
	static class RegisterBatchInput {

		private String action;

		@com.fasterxml.jackson.annotation.JsonRawValue
		private Object functions;

		public RegisterBatchInput() {
		}

		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public Object getFunctions() {
			return functions;
		}

		public void setFunctions(Object functions) {
			this.functions = functions;
		}

	}

	/**
	 * Function input for batch registration
	 */
	static class FunctionInput {

		private String toolName;

		private Map<String, Object> input;

		public FunctionInput() {
		}

		public String getToolName() {
			return toolName;
		}

		public void setToolName(String toolName) {
			this.toolName = toolName;
		}

		public Map<String, Object> getInput() {
			return input;
		}

		public void setInput(Map<String, Object> input) {
			this.input = input;
		}

	}

	// Store all function registries in a list (allows duplicates)
	private final List<FunctionRegistry> functionRegistries = new ArrayList<>();

	public ParallelExecutionTool(ObjectMapper objectMapper, Map<String, ToolCallBackContext> toolCallbackMap,
			PlanIdDispatcher planIdDispatcher) {
		this.objectMapper = objectMapper;
		this.toolCallbackMap = toolCallbackMap;
		this.planIdDispatcher = planIdDispatcher;
	}

	/**
	 * Set the tool callback map (used to look up actual tool implementations)
	 */
	public void setToolCallbackMap(Map<String, ToolCallBackContext> toolCallbackMap) {
		this.toolCallbackMap.putAll(toolCallbackMap);
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public String getName() {
		return "parallel_execution_tool";
	}

	@Override
	public String getDescription() {
		return "Manages parallel execution of multiple functions with batch registration and status tracking";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "oneOf": [
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "registerBatch" },
				                "functions": {
				                    "type": "array",
				                    "description": "Array of functions to register",
				                    "items": {
				                        "type": "object",
				                        "properties": {
				                            "toolName": {"type": "string", "description": "Tool name"},
				                            "input": {"type": "object", "description": "Input parameters for the tool"}
				                        },
				                        "required": ["toolName"]
				                    }
				                }
				            },
				            "required": ["action", "functions"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "start" },
				                "functionIds": {
				                    "type": "array",
				                    "description": "Array of function IDs to execute (optional, executes all if not provided)",
				                    "items": {"type": "string"}
				                }
				            },
				            "required": ["action"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "clearPending" }
				            },
				            "required": ["action"],
				            "additionalProperties": false
				        }
				    ]
				}
				""";
	}

	@Override
	public Class<RegisterBatchInput> getInputType() {
		return RegisterBatchInput.class;
	}

	/**
	 * Use apply method for execution (following DefaultToolCallingManager pattern)
	 * @param input Tool input parameters
	 * @param toolContext Tool execution context
	 * @return Tool execution result
	 */
	@Override
	public ToolExecuteResult apply(RegisterBatchInput input, ToolContext toolContext) {
		try {
			String action = input.getAction();
			if (action == null) {
				return new ToolExecuteResult("Action is required");
			}

			logger.debug("Executing action: {} with context: {}", action, toolContext);

			return switch (action) {
				case "registerBatch" -> registerFunctionsBatch(input);
				case "start" -> startExecution(toolContext);
				case "getPending" -> getPendingFunctions();
				case "clearPending" -> clearPendingFunctions();
				default -> new ToolExecuteResult("Unknown action: " + action);
			};
		}
		catch (Exception e) {
			logger.error("Error in ParallelExecutionManager: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(RegisterBatchInput input) {
		throw new UnsupportedOperationException(
				"ParallelExecutionManager must be called using apply() method with ToolContext, not run()");
	}

	/**
	 * Register multiple functions in batch
	 */
	@SuppressWarnings("unchecked")
	private ToolExecuteResult registerFunctionsBatch(RegisterBatchInput input) {
		try {
			Object functionsRaw = input.getFunctions();
			if (functionsRaw == null) {
				return new ToolExecuteResult("No functions provided");
			}

			List<FunctionInput> functions;

			// Try to parse as List first (Spring AI default behavior)
			if (functionsRaw instanceof List<?> rawList) {
				// Convert List<Map> to List<FunctionInput>
				functions = new ArrayList<>();
				for (Object item : rawList) {
					if (item instanceof Map) {
						// Convert Map to FunctionInput
						Map<String, Object> map = (Map<String, Object>) item;
						FunctionInput funcInput = objectMapper.convertValue(map, FunctionInput.class);
						functions.add(funcInput);
					}
					else if (item instanceof FunctionInput) {
						functions.add((FunctionInput) item);
					}
				}
			}
			// Try to parse as JSON string
			else if (functionsRaw instanceof String functionsString) {
				functions = objectMapper.readValue(functionsString, new TypeReference<List<FunctionInput>>() {
				});
			}
			// Try to parse as JSON array string (escaped)
			else {
				// Try to serialize and re-parse
				String jsonString = objectMapper.writeValueAsString(functionsRaw);
				functions = objectMapper.readValue(jsonString, new TypeReference<List<FunctionInput>>() {
				});
			}

			if (functions == null || functions.isEmpty()) {
				return new ToolExecuteResult("No valid functions provided");
			}

			List<Map<String, Object>> registeredFunctions = new ArrayList<>();
			for (FunctionInput functionInput : functions) {
				String toolName = functionInput.getToolName();
				if (toolName == null) {
					logger.warn("Missing toolName in function input");
					continue;
				}

				Map<String, Object> functionParams = functionInput.getInput();
				if (functionParams == null) {
					functionParams = new HashMap<>();
				}

				String funcId = planIdDispatcher.generateParallelExecutionId();
				FunctionRegistry function = new FunctionRegistry(funcId, toolName, functionParams);
				functionRegistries.add(function);

				registeredFunctions
					.add(Map.of("id", funcId, "input", functionParams, "toolName", toolName, "status", "REGISTERED"));
			}

			Map<String, Object> result = new HashMap<>();
			result.put("message", "Successfully registered " + registeredFunctions.size() + " functions");
			result.put("functions", registeredFunctions);
			try {
				return new ToolExecuteResult(objectMapper.writeValueAsString(result));
			}
			catch (JsonProcessingException e) {
				logger.error("Error serializing result: {}", e.getMessage(), e);
				return new ToolExecuteResult("Successfully registered " + registeredFunctions.size() + " functions");
			}
		}
		catch (Exception e) {
			logger.error("Error registering functions batch: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error registering functions: " + e.getMessage());
		}
	}

	/**
	 * Execute registered functions in parallel (following DefaultToolCallingManager
	 * pattern)
	 */

	private ToolExecuteResult startExecution(ToolContext parentToolContext) {
		try {
			if (functionRegistries.isEmpty()) {
				return new ToolExecuteResult("No functions registered");
			}

			List<CompletableFuture<Void>> futures = new ArrayList<>();
			int executedCount = 0;

			// Extract parent toolCallId from the context if available (propagate to
			// sub-calls)
			String parentToolCallId = null;
			try {
				if (parentToolContext != null && parentToolContext.getContext() != null) {
					Object v = parentToolContext.getContext().get("toolcallId");
					if (v != null) {
						parentToolCallId = String.valueOf(v);
						logger.debug("Using parent toolCallId from context: {}", parentToolCallId);
					}
				}
			}
			catch (Exception ignore) {
				// ignore extraction errors, we'll fallback to generated IDs per call
			}

			// Execute all pending functions in parallel
			for (FunctionRegistry function : functionRegistries) {
				// A function is pending if it has no result yet
				if (function.getResult() != null) {
					continue; // Skip already executed functions
				}

				String toolName = function.getToolName();
				ToolCallBackContext toolContext = toolCallbackMap.get(toolName);

				if (toolContext == null) {
					logger.warn("Tool not found in callback map: {}", toolName);
					function.setResult(new ToolExecuteResult("Tool not found: " + toolName));
					executedCount++;
					continue;
				}

				ToolCallBiFunctionDef<?> functionInstance = toolContext.getFunctionInstance();
				Map<String, Object> input = function.getInput();

				// Use parent toolCallId if present so sub-plans can link via hierarchy;
				// otherwise generate a new one for this call
				String toolCallId = (parentToolCallId != null) ? parentToolCallId
						: planIdDispatcher.generateToolCallId();
				// Propagate planDepth if present in parent ToolContext
				Integer tmpDepth = null;
				try {
					if (parentToolContext != null && parentToolContext.getContext() != null) {
						Object d = parentToolContext.getContext().get("planDepth");
						if (d instanceof Number) {
							tmpDepth = ((Number) d).intValue();
						}
						else if (d instanceof String) {
							tmpDepth = Integer.parseInt((String) d);
						}
					}
				}
				catch (Exception ignore) {
				}
				final Integer propagatedPlanDepth = tmpDepth;
				executedCount++;

				// Execute the function asynchronously
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					try {
						logger.debug("Executing function: {}", toolName);

						// Call the function using apply method with toolCallId in
						// ToolContext
						@SuppressWarnings("unchecked")
						ToolExecuteResult result = ((AbstractBaseTool<Map<String, Object>>) functionInstance)
							.apply(input, new ToolContext(propagatedPlanDepth == null ? Map.of("toolcallId", toolCallId)
									: Map.of("toolcallId", toolCallId, "planDepth", propagatedPlanDepth)));

						function.setResult(result);
						logger.debug("Completed execution for function: {}", toolName);
					}
					catch (Exception e) {
						logger.error("Error executing function {}: {}", toolName, e.getMessage(), e);
						function.setResult(new ToolExecuteResult("Error: " + e.getMessage()));
					}
				});

				futures.add(future);
			}

			if (futures.isEmpty()) {
				return new ToolExecuteResult("No pending functions to execute");
			}

			// Wait for all tasks to complete
			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

			// Collect results with richer details, using unique id instead of
			// toolName/input
			List<Map<String, Object>> results = new ArrayList<>();
			for (FunctionRegistry function : functionRegistries) {
				if (function.getResult() != null) {
					Map<String, Object> item = new HashMap<>();
					item.put("id", function.getId());
					item.put("status", "COMPLETED");
					String output = null;
					try {
						output = function.getResult().getOutput();
						// Remove excessive escaping from JSON strings
						if (output != null) {
							output = output.replace("\\\"", "\"").replace("\\\\", "\\");
						}
					}
					catch (Exception ignore) {
					}
					if (output == null) {
						output = String.valueOf(function.getResult());
					}
					item.put("output", output);
					results.add(item);
				}
			}

			Map<String, Object> result = new HashMap<>();
			result.put("results", results);
			result.put("message", "Successfully executed " + executedCount + " functions");
			try {
				return new ToolExecuteResult(objectMapper.writeValueAsString(result));
			}
			catch (JsonProcessingException e) {
				logger.error("Error serializing result: {}", e.getMessage(), e);
				return new ToolExecuteResult("Successfully executed " + executedCount + " functions");
			}
		}
		catch (Exception e) {
			logger.error("Error starting execution: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error starting execution: " + e.getMessage());
		}
	}

	/**
	 * Get all functions that are registered but not yet started
	 */
	private ToolExecuteResult getPendingFunctions() {
		try {
			List<Map<String, Object>> pendingFunctions = new ArrayList<>();
			for (FunctionRegistry function : functionRegistries) {
				// A function is pending if it has no result yet
				if (function.getResult() == null) {
					Map<String, Object> pendingFunc = new HashMap<>();
					pendingFunc.put("input", function.getInput());
					pendingFunc.put("toolName", function.getToolName());
					pendingFunc.put("status", "PENDING");
					pendingFunctions.add(pendingFunc);
				}
			}

			try {
				// Return the array directly as JSON string
				return new ToolExecuteResult(objectMapper.writeValueAsString(pendingFunctions));
			}
			catch (JsonProcessingException e) {
				logger.error("Error serializing result: {}", e.getMessage(), e);
				return new ToolExecuteResult("Found " + pendingFunctions.size() + " pending functions");
			}
		}
		catch (Exception e) {
			logger.error("Error getting pending functions: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error getting pending functions: " + e.getMessage());
		}
	}

	/**
	 * Clear all pending functions (functions with no result yet)
	 */
	private ToolExecuteResult clearPendingFunctions() {
		try {
			int clearedCount = 0;
			for (FunctionRegistry function : functionRegistries) {
				// A function is pending if it has no result yet
				if (function.getResult() == null) {
					function.setResult(new ToolExecuteResult("Cleared"));
					clearedCount++;
				}
			}

			Map<String, Object> result = new HashMap<>();
			result.put("message", "Cleared " + clearedCount + " pending functions");
			result.put("count", clearedCount);
			try {
				return new ToolExecuteResult(objectMapper.writeValueAsString(result));
			}
			catch (JsonProcessingException e) {
				logger.error("Error serializing result: {}", e.getMessage(), e);
				return new ToolExecuteResult("Cleared " + clearedCount + " pending functions");
			}
		}
		catch (Exception e) {
			logger.error("Error clearing pending functions: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error clearing pending functions: " + e.getMessage());
		}
	}

	@Override
	public void cleanup(String planId) {
		functionRegistries.clear();
		logger.debug("Cleaned up function registries");
	}

	@Override
	public String getCurrentToolStateString() {
		if (functionRegistries.isEmpty()) {
			return "No functions registered";
		}

		List<Map<String, Object>> plannedFunctions = new ArrayList<>();

		for (FunctionRegistry function : functionRegistries) {
			// Only include functions that are pending (not yet executed)
			if (function.getResult() == null) {
				Map<String, Object> funcInfo = new HashMap<>();
				funcInfo.put("input", function.getInput());
				funcInfo.put("toolName", function.getToolName());
				funcInfo.put("status", "PENDING");
				plannedFunctions.add(funcInfo);
			}
		}

		try {
			return objectMapper.writeValueAsString(plannedFunctions);
		}
		catch (Exception e) {
			return "[]";
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
