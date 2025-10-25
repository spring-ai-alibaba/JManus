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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.runtime.service.PlanningCoordinator;
import com.alibaba.cloud.ai.manus.subplan.model.po.SubplanToolDef;
import com.alibaba.cloud.ai.manus.subplan.model.vo.SubplanToolWrapper;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parallel execution manager for batch registration and execution of functions
 * 
 * This class provides functionality to:
 * 1. Batch register executable functions (SubplanToolWrapper instances)
 * 2. Execute all registered functions in parallel using a 'start' function
 * 3. Track function execution status and get pending functions
 */
public class ParallelExecutionManager extends AbstractBaseTool<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(ParallelExecutionManager.class);

    // Function execution status enum
    public enum FunctionStatus {
        REGISTERED, // Function is registered but not started
        RUNNING, // Function is currently executing
        COMPLETED, // Function execution completed successfully
        FAILED, // Function execution failed
        CANCELLED // Function execution was cancelled
    }

    // Internal class to track function execution state
    private static class FunctionExecutionState {
        private final String functionId;
        private final AbstractBaseTool<Map<String, Object>> function;
        private final Map<String, Object> input;
        private final String toolCallId;
        private volatile FunctionStatus status;
        private CompletableFuture<ToolExecuteResult> future;

        public FunctionExecutionState(String functionId, AbstractBaseTool<Map<String, Object>> function,
                Map<String, Object> input, String toolCallId) {
            this.functionId = functionId;
            this.function = function;
            this.input = input;
            this.toolCallId = toolCallId;
            this.status = FunctionStatus.REGISTERED;
        }

        // Getters and setters
        public String getFunctionId() {
            return functionId;
        }

        public AbstractBaseTool<Map<String, Object>> getFunction() {
            return function;
        }

        public Map<String, Object> getInput() {
            return input;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public FunctionStatus getStatus() {
            return status;
        }

        public void setStatus(FunctionStatus status) {
            this.status = status;
        }

        public CompletableFuture<ToolExecuteResult> getFuture() {
            return future;
        }

        public void setFuture(CompletableFuture<ToolExecuteResult> future) {
            this.future = future;
        }
    }

    // Thread pool for parallel execution
    private final ExecutorService executorService;

    // Map to store registered functions
    private final Map<String, FunctionExecutionState> registeredFunctions;

    // Atomic counter for generating unique function IDs
    private final AtomicInteger functionIdCounter;

    // Required services for subplan execution
    private final PlanTemplateService planTemplateService;
    private final PlanningCoordinator planningCoordinator;
    private final PlanIdDispatcher planIdDispatcher;
    private final ObjectMapper objectMapper;
    private final IPlanParameterMappingService parameterMappingService;

    public ParallelExecutionManager(PlanTemplateService planTemplateService,
            PlanningCoordinator planningCoordinator,
            PlanIdDispatcher planIdDispatcher,
            ObjectMapper objectMapper,
            IPlanParameterMappingService parameterMappingService) {
        this.planTemplateService = planTemplateService;
        this.planningCoordinator = planningCoordinator;
        this.planIdDispatcher = planIdDispatcher;
        this.objectMapper = objectMapper;
        this.parameterMappingService = parameterMappingService;

        // Initialize thread pool with reasonable size
        this.executorService = Executors.newFixedThreadPool(10);
        this.registeredFunctions = new ConcurrentHashMap<>();
        this.functionIdCounter = new AtomicInteger(0);
    }

    @Override
    public String getServiceGroup() {
        return "parallel-execution";
    }

    @Override
    public String getName() {
        return "ParallelExecutionManager";
    }

    @Override
    public String getDescription() {
        return "Manages parallel execution of multiple functions with batch registration and status tracking";
    }

    @Override
    public String getParameters() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "action": {
                            "type": "string",
                            "enum": ["registerBatch", "start", "getPending"],
                            "description": "Action to perform"
                        },
                        "functions": {
                            "type": "array",
                            "description": "Array of functions for batch operations",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "toolName": {"type": "string"},
                                    "toolDescription": {"type": "string"},
                                    "planTemplateId": {"type": "string"},
                                    "serviceGroup": {"type": "string"},
                                    "input": {"type": "object"},
                                    "toolCallId": {"type": "string"}
                                }
                            }
                        },
                        "functionIds": {
                            "type": "array",
                            "description": "Array of function IDs for specific execution",
                            "items": {"type": "string"}
                        }
                    },
                    "required": ["action"]
                }
                """;
    }

    @Override
    public Class<Map<String, Object>> getInputType() {
        @SuppressWarnings("unchecked")
        Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
        return mapClass;
    }

    @Override
    public ToolExecuteResult run(Map<String, Object> input) {
        try {
            String action = (String) input.get("action");
            if (action == null) {
                return new ToolExecuteResult("Action is required");
            }

            switch (action) {
                case "registerBatch":
                    return registerFunctionsBatch(input);
                case "start":
                    return startExecution(input);
                case "getPending":
                    return getPendingFunctions(input);
                default:
                    return new ToolExecuteResult("Unknown action: " + action);
            }
        } catch (Exception e) {
            logger.error("Error in ParallelExecutionManager: {}", e.getMessage(), e);
            return new ToolExecuteResult("Error: " + e.getMessage());
        }
    }

    /**
     * Register multiple functions in batch
     */
    private ToolExecuteResult registerFunctionsBatch(Map<String, Object> input) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> functions = (List<Map<String, Object>>) input.get("functions");
            if (functions == null || functions.isEmpty()) {
                return new ToolExecuteResult("functions array is required and cannot be empty");
            }

            List<String> registeredIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (Map<String, Object> functionData : functions) {
                try {
                    // Add toolCallId to function data if not present
                    if (!functionData.containsKey("toolCallId")) {
                        functionData.put("toolCallId", "batch_" + System.currentTimeMillis());
                    }

                    String toolCallId = (String) functionData.get("toolCallId");
                    if (toolCallId == null) {
                        errors.add("toolCallId is required for registration");
                        continue;
                    }

                    // Extract function details from input
                    String toolName = (String) functionData.get("toolName");
                    String toolDescription = (String) functionData.get("toolDescription");
                    String planTemplateId = (String) functionData.get("planTemplateId");
                    String serviceGroup = (String) functionData.get("serviceGroup");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> functionInput = (Map<String, Object>) functionData.get("input");

                    if (toolName == null || planTemplateId == null) {
                        errors.add("toolName and planTemplateId are required");
                        continue;
                    }

                    // Create SubplanToolDef
                    SubplanToolDef toolDef = new SubplanToolDef();
                    toolDef.setToolName(toolName);
                    toolDef.setToolDescription(toolDescription != null ? toolDescription : "");
                    toolDef.setPlanTemplateId(planTemplateId);
                    toolDef.setServiceGroup(serviceGroup != null ? serviceGroup : "default");

                    // Create SubplanToolWrapper
                    SubplanToolWrapper wrapper = new SubplanToolWrapper(
                            toolDef, currentPlanId, rootPlanId,
                            planTemplateService, planningCoordinator,
                            planIdDispatcher, objectMapper, parameterMappingService);

                    // Generate unique function ID
                    String functionId = "func_" + functionIdCounter.incrementAndGet();

                    // Create execution state - cast to AbstractBaseTool
                    FunctionExecutionState state = new FunctionExecutionState(
                            functionId, (AbstractBaseTool<Map<String, Object>>) wrapper, functionInput, toolCallId);

                    registeredFunctions.put(functionId, state);
                    registeredIds.add(functionId);

                    logger.info("Registered function: {} with ID: {}", toolName, functionId);

                } catch (Exception e) {
                    errors.add("Error registering function: " + e.getMessage());
                }
            }

            StringBuilder response = new StringBuilder();
            response.append("Batch registration completed. Registered: ").append(registeredIds.size())
                    .append(" functions. IDs: ").append(String.join(", ", registeredIds));

            if (!errors.isEmpty()) {
                response.append("\nErrors: ").append(String.join("; ", errors));
            }

            logger.info("Batch registered {} functions", registeredIds.size());
            return new ToolExecuteResult(response.toString());

        } catch (Exception e) {
            logger.error("Error in batch registration: {}", e.getMessage(), e);
            return new ToolExecuteResult("Error in batch registration: " + e.getMessage());
        }
    }

    /**
     * Start execution of all registered functions in parallel
     */
    private ToolExecuteResult startExecution(Map<String, Object> input) {
        try {
            List<String> functionIds = new ArrayList<>();

            // Check if specific function IDs are provided
            @SuppressWarnings("unchecked")
            List<String> specifiedIds = (List<String>) input.get("functionIds");
            if (specifiedIds != null && !specifiedIds.isEmpty()) {
                functionIds.addAll(specifiedIds);
            } else {
                // Start all registered functions
                functionIds.addAll(registeredFunctions.keySet());
            }

            if (functionIds.isEmpty()) {
                return new ToolExecuteResult("No functions to execute");
            }

            List<String> startedIds = new ArrayList<>();
            List<String> errors = new ArrayList<>();

            for (String functionId : functionIds) {
                FunctionExecutionState state = registeredFunctions.get(functionId);
                if (state == null) {
                    errors.add("Function not found: " + functionId);
                    continue;
                }

                if (state.getStatus() != FunctionStatus.REGISTERED) {
                    errors.add("Function " + functionId + " is not in REGISTERED status: " + state.getStatus());
                    continue;
                }

                try {
                    // Execute function asynchronously - use run method instead of apply to avoid
                    // ToolContext
                    CompletableFuture<ToolExecuteResult> future = CompletableFuture.supplyAsync(() -> {
                        try {
                            state.setStatus(FunctionStatus.RUNNING);
                            // Use run method instead of apply to avoid ToolContext dependency
                            ToolExecuteResult result = state.getFunction().run(state.getInput());
                            // Check if result indicates success (no error in output)
                            boolean isSuccess = result.getOutput() != null && !result.getOutput().contains("Error")
                                    && !result.getOutput().contains("Failed");
                            state.setStatus(isSuccess ? FunctionStatus.COMPLETED : FunctionStatus.FAILED);
                            return result;
                        } catch (Exception e) {
                            state.setStatus(FunctionStatus.FAILED);
                            logger.error("Function {} execution failed: {}", functionId, e.getMessage(), e);
                            return new ToolExecuteResult("Execution failed: " + e.getMessage());
                        }
                    }, executorService);

                    state.setFuture(future);
                    startedIds.add(functionId);

                } catch (Exception e) {
                    errors.add("Failed to start function " + functionId + ": " + e.getMessage());
                }
            }

            StringBuilder response = new StringBuilder();
            response.append("Started execution of ").append(startedIds.size()).append(" functions. IDs: ")
                    .append(String.join(", ", startedIds));

            if (!errors.isEmpty()) {
                response.append("\nErrors: ").append(String.join("; ", errors));
            }

            logger.info("Started parallel execution of {} functions", startedIds.size());
            return new ToolExecuteResult(response.toString());

        } catch (Exception e) {
            logger.error("Error starting execution: {}", e.getMessage(), e);
            return new ToolExecuteResult("Error starting execution: " + e.getMessage());
        }
    }

    /**
     * Get all functions that are registered but not yet started
     */
    private ToolExecuteResult getPendingFunctions(Map<String, Object> input) {
        try {
            List<Map<String, Object>> pendingFunctions = new ArrayList<>();

            for (FunctionExecutionState state : registeredFunctions.values()) {
                if (state.getStatus() == FunctionStatus.REGISTERED) {
                    Map<String, Object> functionInfo = new HashMap<>();
                    functionInfo.put("functionId", state.getFunctionId());
                    functionInfo.put("toolName", state.getFunction().getName());
                    functionInfo.put("toolDescription", state.getFunction().getDescription());
                    functionInfo.put("status", state.getStatus().toString());
                    functionInfo.put("toolCallId", state.getToolCallId());
                    pendingFunctions.add(functionInfo);
                }
            }

            logger.info("Found {} pending functions", pendingFunctions.size());
            return new ToolExecuteResult("Pending functions: " + pendingFunctions.size() +
                    "\n" + pendingFunctions.toString());

        } catch (Exception e) {
            logger.error("Error getting pending functions: {}", e.getMessage(), e);
            return new ToolExecuteResult("Error getting pending functions: " + e.getMessage());
        }
    }

    @Override
    public void cleanup(String planId) {
        logger.debug("Cleaning up ParallelExecutionManager for planId: {}", planId);

        // Cancel all running functions
        for (FunctionExecutionState state : registeredFunctions.values()) {
            if (state.getStatus() == FunctionStatus.RUNNING && state.getFuture() != null) {
                state.getFuture().cancel(true);
                state.setStatus(FunctionStatus.CANCELLED);
            }
        }

        // Clear registered functions
        registeredFunctions.clear();
    }

    @Override
    public String getCurrentToolStateString() {
        long registeredCount = registeredFunctions.values().stream()
                .filter(state -> state.getStatus() == FunctionStatus.REGISTERED)
                .count();
        long runningCount = registeredFunctions.values().stream()
                .filter(state -> state.getStatus() == FunctionStatus.RUNNING)
                .count();
        long completedCount = registeredFunctions.values().stream()
                .filter(state -> state.getStatus() == FunctionStatus.COMPLETED)
                .count();

        return String.format("Registered: %d, Running: %d, Completed: %d",
                registeredCount, runningCount, completedCount);
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        logger.info("Shutting down ParallelExecutionManager");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
