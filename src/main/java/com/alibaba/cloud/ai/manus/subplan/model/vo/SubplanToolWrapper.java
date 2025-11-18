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
package com.alibaba.cloud.ai.manus.subplan.model.vo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.manus.planning.model.po.FuncAgentToolEntity;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.PlanExecutionResult;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.PlanInterface;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.RequestSource;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.runtime.service.PlanningCoordinator;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wrapper class that extends AbstractBaseTool for FuncAgentToolEntity
 *
 * This allows integration with the existing tool registry system
 */
public class SubplanToolWrapper extends AbstractBaseTool<Map<String, Object>> {

	public static final String PARENT_PLAN_ID_ARG_NAME = "PLAN_PARENT_ID_ARG_NAME";

	private static final Logger logger = LoggerFactory.getLogger(SubplanToolWrapper.class);

	private final FuncAgentToolEntity funcAgentToolEntity;

	private final PlanTemplate planTemplate;

	private final String currentPlanId;

	private final String rootPlanId;

	private final PlanTemplateService planTemplateService;

	private final PlanningCoordinator planningCoordinator;

	private final PlanIdDispatcher planIdDispatcher;

	private final ObjectMapper objectMapper;

	private final IPlanParameterMappingService parameterMappingService;

	public SubplanToolWrapper(FuncAgentToolEntity funcAgentToolEntity, PlanTemplate planTemplate, String currentPlanId,
			String rootPlanId, PlanTemplateService planTemplateService, PlanningCoordinator planningCoordinator,
			PlanIdDispatcher planIdDispatcher, ObjectMapper objectMapper,
			IPlanParameterMappingService parameterMappingService) {
		this.funcAgentToolEntity = funcAgentToolEntity;
		this.planTemplate = planTemplate;
		this.currentPlanId = currentPlanId;
		this.rootPlanId = rootPlanId;
		this.planTemplateService = planTemplateService;
		this.planningCoordinator = planningCoordinator;
		this.planIdDispatcher = planIdDispatcher;
		this.objectMapper = objectMapper;
		this.parameterMappingService = parameterMappingService;
	}

	@Override
	public String getServiceGroup() {
		// Get serviceGroup from PlanTemplate
		if (planTemplate != null && planTemplate.getServiceGroup() != null
				&& !planTemplate.getServiceGroup().trim().isEmpty()) {
			return planTemplate.getServiceGroup();
		}
		// Fallback to default value if serviceGroup is null/empty
		return "coordinator-tools";
	}

	@Override
	public String getName() {
		// Use PlanTemplate title as tool name
		if (planTemplate != null && planTemplate.getTitle() != null && !planTemplate.getTitle().trim().isEmpty()) {
			return planTemplate.getTitle();
		}
		// Fallback to planTemplateId if title is not available
		return funcAgentToolEntity.getPlanTemplateId();
	}

	@Override
	public String getDescription() {
		return funcAgentToolEntity.getToolDescription();
	}

	@Override
	public String getParameters() {
		// Convert FuncAgentToolEntity inputSchema (JSON array) to JSON schema format
		try {
			String inputSchemaJson = funcAgentToolEntity.getInputSchema();
			if (inputSchemaJson == null || inputSchemaJson.trim().isEmpty()) {
				return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
			}

			// Parse the inputSchema JSON string
			JsonNode inputSchemaNode = objectMapper.readTree(inputSchemaJson);
			if (!inputSchemaNode.isArray()) {
				logger.warn("InputSchema for tool {} is not an array, using empty schema", getName());
				return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
			}

			// Convert array of parameters to JSON schema format
			Map<String, Object> schema = new HashMap<>();
			schema.put("type", "object");

			Map<String, Object> properties = new HashMap<>();
			List<String> required = new ArrayList<>();

			for (JsonNode paramNode : inputSchemaNode) {
				if (!paramNode.isObject()) {
					continue;
				}

				String paramName = paramNode.has("name") ? paramNode.get("name").asText() : null;
				if (paramName == null || paramName.trim().isEmpty()) {
					continue;
				}

				Map<String, Object> paramSchema = new HashMap<>();
				String paramType = paramNode.has("type") ? paramNode.get("type").asText().toLowerCase() : "string";
				paramSchema.put("type", paramType);

				if (paramNode.has("description")) {
					paramSchema.put("description", paramNode.get("description").asText());
				}

				properties.put(paramName, paramSchema);

				if (paramNode.has("required") && paramNode.get("required").asBoolean()) {
					required.add(paramName);
				}
			}

			schema.put("properties", properties);
			if (!required.isEmpty()) {
				schema.put("required", required);
			}

			return objectMapper.writeValueAsString(schema);

		}
		catch (Exception e) {
			logger.error("Error converting inputSchema to parameters for tool: {}", getName(), e);
			return "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
		}
	}

	@Override
	public Class<Map<String, Object>> getInputType() {
		@SuppressWarnings("unchecked")
		Class<Map<String, Object>> mapClass = (Class<Map<String, Object>>) (Class<?>) Map.class;
		return mapClass;
	}

	@Override
	public ToolExecuteResult apply(Map<String, Object> input, ToolContext toolContext) {
		// Extract toolCallId from ToolContext
		String toolCallId = extractToolCallIdFromContext(toolContext);
		if (toolCallId != null) {
			logger.info("Using provided toolCallId from context: {} for tool: {}", toolCallId, getName());

			// Extract planDepth from ToolContext and increment by 1 for subplan
			int parentPlanDepth = extractPlanDepthFromContext(toolContext);
			int subplanDepth = parentPlanDepth + 1;
			logger.info("Parent plan depth: {}, subplan will have depth: {}", parentPlanDepth, subplanDepth);

			return executeSubplanWithToolCallId(input, toolCallId, subplanDepth);
		}
		else {
			throw new IllegalArgumentException("ToolCallId is required for coordinator tool: " + getName());
		}
	}

	@Override
	public ToolExecuteResult run(Map<String, Object> input) {
		throw new IllegalArgumentException("ToolCallId is required for coordinator tool: " + getName());
	}

	@Override
	public void cleanup(String planId) {
		// Cleanup logic for the coordinator tool
		logger.debug("Cleaning up coordinator tool: {} for planId: {}", getName(), planId);
	}

	@Override
	public String getCurrentToolStateString() {
		return "Ready";
	}

	// Getter for the wrapped coordinator tool
	public FuncAgentToolEntity getFuncAgentToolEntity() {
		return funcAgentToolEntity;
	}

	/**
	 * Extract toolCallId from ToolContext. This method looks for toolCallId in the tool
	 * context that was set by DynamicAgent.
	 * @param toolContext The tool context containing toolCallId information
	 * @return toolCallId if found, null otherwise
	 */
	private String extractToolCallIdFromContext(ToolContext toolContext) {
		try {
			return String.valueOf(toolContext.getContext().get("toolcallId"));

		}
		catch (Exception e) {
			logger.warn("Error extracting toolCallId from context: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Extract planDepth from ToolContext. This method looks for planDepth in the tool
	 * context that was set by DynamicAgent.
	 * @param toolContext The tool context containing planDepth information
	 * @return planDepth if found, 0 otherwise
	 */
	private int extractPlanDepthFromContext(ToolContext toolContext) {
		try {
			Object depthObj = toolContext.getContext().get("planDepth");
			if (depthObj instanceof Number) {
				return ((Number) depthObj).intValue();
			}
			else if (depthObj instanceof String) {
				return Integer.parseInt((String) depthObj);
			}
			return 0;
		}
		catch (Exception e) {
			logger.warn("Error extracting planDepth from context: {}, defaulting to 0", e.getMessage());
			return 0;
		}
	}

	/**
	 * Execute subplan with the provided toolCallId. This method contains the main subplan
	 * execution logic using the provided toolCallId.
	 * @param input The input parameters for the subplan
	 * @param toolCallId The toolCallId to use for this execution
	 * @param planDepth The depth of the subplan in the execution hierarchy
	 * @return ToolExecuteResult containing the execution result
	 */
	private ToolExecuteResult executeSubplanWithToolCallId(Map<String, Object> input, String toolCallId,
			int planDepth) {
		try {
			logger.info("Executing coordinator tool: {} with template: {} and toolCallId: {}", getName(),
					funcAgentToolEntity.getPlanTemplateId(), toolCallId);

			// Get the plan template from PlanTemplateService
			String planJson = planTemplateService.getLatestPlanVersion(funcAgentToolEntity.getPlanTemplateId());
			if (planJson == null) {
				String errorMsg = "Plan template not found: " + funcAgentToolEntity.getPlanTemplateId();
				logger.error(errorMsg);
				return new ToolExecuteResult(errorMsg);
			}

			// Execute the plan using PlanningCoordinator
			// Generate a new plan ID for this subplan execution using PlanIdDispatcher
			String newPlanId = planIdDispatcher.generateSubPlanId(rootPlanId);

			// Prepare parameters for replacement - add planId to input parameters
			Map<String, Object> parametersForReplacement = new HashMap<>();
			if (input != null) {
				parametersForReplacement.putAll(input);
			}
			// Add the generated planId to parameters
			parametersForReplacement.put("planId", newPlanId);

			// Replace parameter placeholders (<< >>) with actual input parameters
			if (!parametersForReplacement.isEmpty()) {
				try {
					logger.info("Replacing parameter placeholders in plan template with input parameters: {}",
							parametersForReplacement.keySet());
					planJson = parameterMappingService.replaceParametersInJson(planJson, parametersForReplacement);
					logger.debug("Parameter replacement completed successfully");
				}
				catch (Exception e) {
					String errorMsg = "Failed to replace parameters in plan template: " + e.getMessage();
					logger.error(errorMsg, e);
					return new ToolExecuteResult(errorMsg);
				}
			}
			else {
				logger.debug("No parameter replacement needed - input: {}", input != null ? input.size() : 0);
			}

			// Parse the JSON to create a PlanInterface
			PlanInterface plan = objectMapper.readValue(planJson, PlanInterface.class);

			// Use the provided toolCallId instead of generating a new one
			logger.info("Using provided toolCallId: {} for subplan execution: {} at depth: {}", toolCallId, newPlanId,
					planDepth);

			// Sub-plans should use the same conversationId as parent, but it's not
			// available in this context
			// Use HTTP_REQUEST as subplans are internal calls
			CompletableFuture<PlanExecutionResult> future = planningCoordinator.executeByPlan(plan, rootPlanId,
					currentPlanId, newPlanId, toolCallId, RequestSource.HTTP_REQUEST, null, planDepth, null);

			PlanExecutionResult result = future.get();

			if (result.isSuccess()) {
				String output = result.getFinalResult();
				if (output == null || output.trim().isEmpty()) {
					output = "Subplan executed successfully but no output was generated";
				}
				logger.info("Subplan execution completed successfully: {}", output);
				return new ToolExecuteResult(output);
			}
			else {
				String errorMsg = result.getErrorMessage() != null ? result.getErrorMessage()
						: "Subplan execution failed";
				logger.error("Subplan execution failed: {}", errorMsg);
				return new ToolExecuteResult("Subplan execution failed: " + errorMsg);
			}

		}
		catch (InterruptedException e) {
			String errorMsg = "Coordinator tool execution was interrupted";
			logger.error("{} for tool: {}", errorMsg, getName(), e);
			Thread.currentThread().interrupt(); // Restore interrupt status
			return new ToolExecuteResult(errorMsg);
		}
		catch (ExecutionException e) {
			String errorMsg = "Coordinator tool execution failed with exception: " + e.getCause().getMessage();
			logger.error("{} for tool: {}", errorMsg, getName(), e);
			return new ToolExecuteResult(errorMsg);
		}
		catch (Exception e) {
			String errorMsg = "Unexpected error during coordinator tool execution: " + e.getMessage();
			logger.error("{} for tool: {}", errorMsg, getName(), e);
			return new ToolExecuteResult(errorMsg);
		}
	}

	@Override
	public boolean isSelectable() {
		// Only selectable if enableInternalToolcall is true
		return funcAgentToolEntity.getEnableInternalToolcall() != null
				&& funcAgentToolEntity.getEnableInternalToolcall();
	}

}
