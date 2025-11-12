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
package com.alibaba.cloud.ai.manus.coordinator.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.manus.coordinator.entity.po.CoordinatorToolEntity;
import com.alibaba.cloud.ai.manus.coordinator.entity.vo.CoordinatorToolVO;
import com.alibaba.cloud.ai.manus.coordinator.entity.vo.PlanTemplateConfigVO;
import com.alibaba.cloud.ai.manus.coordinator.exception.CoordinatorToolException;
import com.alibaba.cloud.ai.manus.coordinator.repository.CoordinatorToolRepository;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;
import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.manus.subplan.model.po.SubplanParamDef;
import com.alibaba.cloud.ai.manus.subplan.model.po.SubplanToolDef;
import com.alibaba.cloud.ai.manus.subplan.service.SubplanToolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service implementation for managing Coordinator Tools Handles both
 * CoordinatorToolEntity and SubplanToolDef operations with transaction support
 */
@Service
public class CoordinatorToolServiceImpl {

	private static final Logger log = LoggerFactory.getLogger(CoordinatorToolServiceImpl.class);

	@Autowired
	private CoordinatorToolRepository coordinatorToolRepository;

	@Autowired
	private SubplanToolService subplanToolService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	@Autowired
	private PlanTemplateRepository planTemplateRepository;

	@Transactional
	public CoordinatorToolVO createCoordinatorTool(CoordinatorToolVO toolVO) throws CoordinatorToolException {
		int maxRetries = 3;
		int retryCount = 0;

		while (retryCount < maxRetries) {
			try {
				log.info("Creating coordinator tool: {} (attempt {})", toolVO, retryCount + 1);

				// Validate required fields
				validateCoordinatorToolVO(toolVO);

				// Set default values
				setDefaultValues(toolVO);

				// Create and save CoordinatorToolEntity
				CoordinatorToolEntity entity = toolVO.toEntity();
				CoordinatorToolEntity savedEntity = coordinatorToolRepository.save(entity);
				log.info("Successfully saved CoordinatorToolEntity: {} with ID: {}", savedEntity.getToolName(),
						savedEntity.getId());

				// Create SubplanToolDef for tool call registration
				SubplanToolDef subplanToolDef = createSubplanToolDefFromVO(toolVO);
				// Don't set ID manually - let the database generate it
				// The relationship will be maintained through the tool name

				// Register the tool in subplan tool service
				SubplanToolDef registeredTool = subplanToolService.registerSubplanTool(subplanToolDef);
				log.info("Successfully registered subplan tool: {} with ID: {}", registeredTool.getToolName(),
						registeredTool.getId());

				// Convert CoordinatorToolEntity back to CoordinatorToolVO and return (get PlanTemplate for serviceGroup)
				PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(savedEntity.getPlanTemplateId())
					.orElse(null);
				return CoordinatorToolVO.fromEntity(savedEntity, planTemplate);

			}
			catch (CoordinatorToolException e) {
				// Re-throw custom exceptions
				throw e;
			}
			catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
				retryCount++;
				if (retryCount >= maxRetries) {
					log.error("Max retries reached for optimistic locking failure: {}", e.getMessage(), e);
					throw new CoordinatorToolException("CONCURRENT_ACCESS",
							"Tool creation failed due to concurrent access. Please try again.");
				}
				log.warn("Optimistic locking failure, retrying... (attempt {})", retryCount);
				try {
					Thread.sleep(100 * retryCount); // Exponential backoff
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new CoordinatorToolException("INTERRUPTED", "Operation was interrupted");
				}
			}
			catch (Exception e) {
				log.error("Unexpected error creating coordinator tool: {}", e.getMessage(), e);
				throw new CoordinatorToolException("INTERNAL_ERROR",
						"An unexpected error occurred while creating coordinator tool");
			}
		}

		throw new CoordinatorToolException("MAX_RETRIES_EXCEEDED", "Maximum retry attempts exceeded");
	}

	@Transactional
	public CoordinatorToolVO updateCoordinatorTool(Long id, CoordinatorToolVO toolVO) throws CoordinatorToolException {
		try {
			log.info("Updating coordinator tool with ID: {}", id);

			// Validate required fields
			validateCoordinatorToolVO(toolVO);

			// Check if CoordinatorToolEntity exists
			CoordinatorToolEntity existingEntity = coordinatorToolRepository.findById(id)
				.orElseThrow(
						() -> new CoordinatorToolException("NOT_FOUND", "Coordinator tool not found with ID: " + id));

			// Set default values
			setDefaultValues(toolVO);

			// Update CoordinatorToolEntity
			updateCoordinatorToolEntityFromVO(existingEntity, toolVO);
			CoordinatorToolEntity savedEntity = coordinatorToolRepository.save(existingEntity);
			log.info("Successfully updated CoordinatorToolEntity: {} with ID: {}", savedEntity.getToolName(),
					savedEntity.getId());

			// Also update SubplanToolDef for backward compatibility
			// Mapping Strategy: coordinator_tools.planTemplateId <->
			// subplan_tool_def.planTemplateId (unique)
			// This ensures data consistency between the two tables when updating
			// coordinator tools
			Optional<SubplanToolDef> existingToolOpt = subplanToolService
				.getSubplanToolByTemplate(toolVO.getPlanTemplateId());
			if (existingToolOpt.isPresent()) {
				// Update existing SubplanToolDef with all fields from CoordinatorToolVO
				SubplanToolDef existingTool = existingToolOpt.get();
				SubplanToolDef updatedToolDef = createSubplanToolDefFromVO(toolVO);
				updatedToolDef.setId(existingTool.getId()); // Preserve the existing
															// SubplanToolDef ID

				// Update the SubplanToolDef
				subplanToolService.updateSubplanTool(updatedToolDef);
				log.info("Successfully updated subplan tool: {} with ID: {} for planTemplateId: {}",
						updatedToolDef.getToolName(), updatedToolDef.getId(), toolVO.getPlanTemplateId());
			}
			else {
				log.warn("No existing SubplanToolDef found for planTemplateId: {}, creating new one",
						toolVO.getPlanTemplateId());
				// If no existing SubplanToolDef found, create a new one
				SubplanToolDef newToolDef = createSubplanToolDefFromVO(toolVO);
				subplanToolService.registerSubplanTool(newToolDef);
				log.info("Successfully created new subplan tool: {} with ID: {} for planTemplateId: {}",
						newToolDef.getToolName(), newToolDef.getId(), toolVO.getPlanTemplateId());
			}

			// Convert back to VO and return (get PlanTemplate for serviceGroup)
			PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(savedEntity.getPlanTemplateId())
				.orElse(null);
			return CoordinatorToolVO.fromEntity(savedEntity, planTemplate);

		}
		catch (CoordinatorToolException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Error updating coordinator tool: {}", e.getMessage(), e);
			throw new CoordinatorToolException("INTERNAL_ERROR",
					"An unexpected error occurred while updating coordinator tool");
		}
	}

	@Transactional
	public void deleteCoordinatorTool(Long id) throws CoordinatorToolException {
		try {
			log.info("Deleting coordinator tool with ID: {}", id);

			// Check if CoordinatorToolEntity exists and get its planTemplateId
			CoordinatorToolEntity existingEntity = coordinatorToolRepository.findById(id)
				.orElseThrow(
						() -> new CoordinatorToolException("NOT_FOUND", "Coordinator tool not found with ID: " + id));

			// Delete from SubplanToolDef first (for backward compatibility)
			// Mapping Strategy: coordinator_tools.planTemplateId <->
			// subplan_tool_def.planTemplateId (unique)
			// This ensures data consistency between the two tables when deleting
			// coordinator tools
			try {
				Optional<SubplanToolDef> existingToolOpt = subplanToolService
					.getSubplanToolByTemplate(existingEntity.getPlanTemplateId());
				if (existingToolOpt.isPresent()) {
					// Delete the matching SubplanToolDef entry for this planTemplateId
					SubplanToolDef tool = existingToolOpt.get();
					subplanToolService.deleteSubplanTool(tool.getId());
					log.info("Successfully deleted subplan tool: {} with ID: {} for planTemplateId: {}",
							tool.getToolName(), tool.getId(), existingEntity.getPlanTemplateId());
				}
				else {
					log.warn("No SubplanToolDef found for planTemplateId: {}", existingEntity.getPlanTemplateId());
				}
			}
			catch (Exception e) {
				log.warn("Failed to delete subplan tool for planTemplateId: {}, continuing with entity deletion: {}",
						existingEntity.getPlanTemplateId(), e.getMessage());
			}

			// Delete CoordinatorToolEntity
			coordinatorToolRepository.deleteById(id);
			log.info("Successfully deleted CoordinatorToolEntity with ID: {}", id);

		}
		catch (CoordinatorToolException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Error deleting coordinator tool: {}", e.getMessage(), e);
			throw new CoordinatorToolException("INTERNAL_ERROR",
					"An unexpected error occurred while deleting coordinator tool");
		}
	}

	public Optional<CoordinatorToolVO> getCoordinatorToolById(Long id) {
		try {
			return coordinatorToolRepository.findById(id).map(entity -> {
				PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(entity.getPlanTemplateId())
					.orElse(null);
				return CoordinatorToolVO.fromEntity(entity, planTemplate);
			});
		}
		catch (Exception e) {
			log.error("Error getting coordinator tool by ID: {}", e.getMessage(), e);
			return Optional.empty();
		}
	}

	public Optional<CoordinatorToolVO> getCoordinatorToolByPlanTemplateId(String planTemplateId) {
		try {
			List<CoordinatorToolEntity> entities = coordinatorToolRepository.findByPlanTemplateId(planTemplateId);
			if (!entities.isEmpty()) {
				PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(planTemplateId).orElse(null);
				return Optional.of(CoordinatorToolVO.fromEntity(entities.get(0), planTemplate));
			}
			return Optional.empty();
		}
		catch (Exception e) {
			log.error("Error getting coordinator tool by plan template ID: {}", e.getMessage(), e);
			return Optional.empty();
		}
	}

	public List<CoordinatorToolVO> getAllCoordinatorTools() {
		try {
			return coordinatorToolRepository.findAll()
				.stream()
				.map(entity -> {
					PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(entity.getPlanTemplateId())
						.orElse(null);
					return CoordinatorToolVO.fromEntity(entity, planTemplate);
				})
				.collect(Collectors.toList());
		}
		catch (Exception e) {
			log.error("Error getting all coordinator tools: {}", e.getMessage(), e);
			return new ArrayList<>();
		}
	}

	public List<String> getAllUniqueMcpEndpoints() {
		try {
			return coordinatorToolRepository.findAllUniqueMcpEndpoints();
		}
		catch (Exception e) {
			log.error("Error getting unique MCP endpoints: {}", e.getMessage(), e);
			return new ArrayList<>();
		}
	}

	/**
	 * Validate CoordinatorToolVO
	 */
	private void validateCoordinatorToolVO(CoordinatorToolVO toolVO) throws CoordinatorToolException {
		if (toolVO.getToolName() == null || toolVO.getToolName().trim().isEmpty()) {
			throw new CoordinatorToolException("VALIDATION_ERROR", "Tool name is required");
		}
		if (toolVO.getToolDescription() == null || toolVO.getToolDescription().trim().isEmpty()) {
			throw new CoordinatorToolException("VALIDATION_ERROR", "Tool description is required");
		}
		if (toolVO.getPlanTemplateId() == null || toolVO.getPlanTemplateId().trim().isEmpty()) {
			throw new CoordinatorToolException("VALIDATION_ERROR", "Plan template ID is required");
		}

		// Validate service enablement and endpoints
		if (toolVO.getEnableMcpService() != null && toolVO.getEnableMcpService()
				&& (toolVO.getMcpEndpoint() == null || toolVO.getMcpEndpoint().trim().isEmpty())) {
			throw new CoordinatorToolException("VALIDATION_ERROR",
					"MCP endpoint is required when MCP service is enabled");
		}

		// At least one service must be enabled
		boolean hasEnabledService = (toolVO.getEnableInternalToolcall() != null && toolVO.getEnableInternalToolcall())
				|| (toolVO.getEnableHttpService() != null && toolVO.getEnableHttpService())
				|| (toolVO.getEnableMcpService() != null && toolVO.getEnableMcpService());
		if (!hasEnabledService) {
			throw new CoordinatorToolException("VALIDATION_ERROR", "At least one service must be enabled");
		}
	}

	/**
	 * Set default values for CoordinatorToolVO
	 */
	private void setDefaultValues(CoordinatorToolVO toolVO) {
		if (toolVO.getInputSchema() == null || toolVO.getInputSchema().trim().isEmpty()) {
			toolVO.setInputSchema("[]");
		}
		if (toolVO.getPublishStatus() == null) {
			toolVO.setPublishStatus("UNPUBLISHED");
		}
		if (toolVO.getEnableInternalToolcall() == null) {
			toolVO.setEnableInternalToolcall(false);
		}
		if (toolVO.getEnableHttpService() == null) {
			toolVO.setEnableHttpService(false);
		}
		if (toolVO.getEnableMcpService() == null) {
			toolVO.setEnableMcpService(false);
		}
	}

	/**
	 * Update CoordinatorToolEntity from CoordinatorToolVO
	 */
	private void updateCoordinatorToolEntityFromVO(CoordinatorToolEntity entity, CoordinatorToolVO toolVO) {
		entity.setToolName(toolVO.getToolName());
		entity.setToolDescription(toolVO.getToolDescription());
		entity.setInputSchema(toolVO.getInputSchema());
		entity.setPlanTemplateId(toolVO.getPlanTemplateId());
		entity.setMcpEndpoint(toolVO.getMcpEndpoint());
		// serviceGroup is no longer stored in CoordinatorToolEntity - it's in PlanTemplate

		// Set service enablement flags
		entity.setEnableInternalToolcall(
				toolVO.getEnableInternalToolcall() != null ? toolVO.getEnableInternalToolcall() : true);
		entity.setEnableHttpService(toolVO.getEnableHttpService() != null ? toolVO.getEnableHttpService() : false);
		entity.setEnableMcpService(toolVO.getEnableMcpService() != null ? toolVO.getEnableMcpService() : false);
	}

	/**
	 * Create SubplanToolDef from CoordinatorToolVO
	 */
	private SubplanToolDef createSubplanToolDefFromVO(CoordinatorToolVO toolVO) {
		SubplanToolDef toolDef = new SubplanToolDef();
		toolDef.setToolName(toolVO.getToolName());
		toolDef.setToolDescription(toolVO.getToolDescription());
		toolDef.setPlanTemplateId(toolVO.getPlanTemplateId());

		// Set endpoint based on enabled services
		if (toolVO.getEnableMcpService() != null && toolVO.getEnableMcpService() && toolVO.getMcpEndpoint() != null) {
			toolDef.setEndpoint(toolVO.getMcpEndpoint());
		}
		else if (toolVO.getEnableInternalToolcall() != null && toolVO.getEnableInternalToolcall()) {
			toolDef.setEndpoint("internal-toolcall");
		}
		else {
			toolDef.setEndpoint("internal-toolcall");
		}

		// Get serviceGroup from PlanTemplate instead of toolVO
		PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(toolVO.getPlanTemplateId())
			.orElse(null);
		if (planTemplate != null && planTemplate.getServiceGroup() != null) {
			toolDef.setServiceGroup(planTemplate.getServiceGroup());
		}
		else {
			toolDef.setServiceGroup(toolVO.getServiceGroup()); // Fallback to VO if PlanTemplate not found
		}

		// Parse input schema and create parameters
		try {
			JsonNode inputParams = objectMapper.readTree(toolVO.getInputSchema());
			List<SubplanParamDef> parameters = new ArrayList<>();

			if (inputParams.isArray()) {
				for (JsonNode param : inputParams) {
					String paramName = param.get("name").asText();
					String paramType = param.get("type").asText();
					String paramDescription = param.get("description").asText();
					boolean required = param.has("required") ? param.get("required").asBoolean() : true;

					SubplanParamDef paramDef = new SubplanParamDef(paramName, paramType, paramDescription, required);
					parameters.add(paramDef);
				}
			}

			toolDef.setInputSchema(parameters);

		}
		catch (Exception e) {
			log.warn("Failed to parse input schema for subplan tool: {}", e.getMessage());
			toolDef.setInputSchema(new ArrayList<>());
		}

		return toolDef;
	}

	/**
	 * Create or update coordinator tool from PlanTemplateConfigVO Uses planTemplateId as
	 * the key identity to determine if tool exists
	 * @param configVO Plan template configuration VO
	 * @return Created or updated CoordinatorToolVO
	 * @throws CoordinatorToolException if validation or operation fails
	 */
	@Transactional
	public CoordinatorToolVO createOrUpdateCoordinatorToolFromPlanTemplateConfig(PlanTemplateConfigVO configVO)
			throws CoordinatorToolException {
		if (configVO == null) {
			throw new CoordinatorToolException("VALIDATION_ERROR", "PlanTemplateConfigVO cannot be null");
		}

		String planTemplateId = configVO.getPlanTemplateId();
		if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
			throw new CoordinatorToolException("VALIDATION_ERROR",
					"planTemplateId is required in PlanTemplateConfigVO");
		}

		log.info("Creating or updating coordinator tool from PlanTemplateConfigVO for planTemplateId: {}",
				planTemplateId);
		try {
			// Check if coordinator tool already exists by planTemplateId
			Optional<CoordinatorToolVO> existingToolOpt = getCoordinatorToolByPlanTemplateId(planTemplateId);

			// Get plan template for default values, create if it doesn't exist
			PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(planTemplateId).orElse(null);
			if (planTemplate == null) {
				log.info("Plan template not found for planTemplateId: {}, creating new PlanTemplate", planTemplateId);
				planTemplate = createPlanTemplateFromConfig(configVO);
			}
			else {
				// Update serviceGroup on existing PlanTemplate if provided in configVO
				String serviceGroup = configVO.getServiceGroup();
				if (serviceGroup != null && !serviceGroup.trim().isEmpty() && !serviceGroup.equals(planTemplate.getServiceGroup())) {
					planTemplate.setServiceGroup(serviceGroup);
					planTemplateRepository.save(planTemplate);
					log.info("Updated serviceGroup to '{}' on PlanTemplate with ID: {}", serviceGroup, planTemplateId);
				}
			}

			// Convert PlanTemplateConfigVO to CoordinatorToolVO
			CoordinatorToolVO coordinatorToolVO = convertPlanTemplateConfigToCoordinatorToolVO(configVO, planTemplate);

			// Set input schema: use from toolConfig if provided, otherwise generate from
			// plan template
			String inputSchema;
			PlanTemplateConfigVO.ToolConfigVO toolConfig = configVO.getToolConfig();
			if (toolConfig != null && toolConfig.getInputSchema() != null && !toolConfig.getInputSchema().isEmpty()) {
				// Use inputSchema from toolConfig
				inputSchema = convertInputSchemaListToJson(toolConfig.getInputSchema());
				log.debug("Using inputSchema from toolConfig for plan template {}: {}", planTemplateId, inputSchema);
			}
			else {
				// Generate input schema from plan template parameters
				inputSchema = generateInputSchemaFromPlanTemplate(planTemplateId);
				log.debug("Generated inputSchema from plan template parameters for plan template {}: {}",
						planTemplateId, inputSchema);
			}
			coordinatorToolVO.setInputSchema(inputSchema);

			if (existingToolOpt.isPresent()) {
				// Tool exists, update it
				Long toolId = existingToolOpt.get().getId();
				log.info("Coordinator tool already exists for plan template {}, updating with ID: {}", planTemplateId,
						toolId);
				return updateCoordinatorTool(toolId, coordinatorToolVO);
			}
			else {
				// Tool doesn't exist, create it
				log.info("Creating new coordinator tool for plan template: {}", planTemplateId);
				return createCoordinatorTool(coordinatorToolVO);
			}

		}
		catch (CoordinatorToolException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create or update coordinator tool from PlanTemplateConfigVO for planTemplateId: {}",
					planTemplateId, e);
			throw new CoordinatorToolException("INTERNAL_ERROR",
					"An unexpected error occurred while creating or updating coordinator tool: " + e.getMessage());
		}
	}

	/**
	 * Convert PlanTemplateConfigVO to CoordinatorToolVO Applies toolConfig settings with
	 * fallback to defaults from plan template
	 * @param configVO Plan template configuration VO
	 * @param planTemplate Plan template entity (can be null)
	 * @return CoordinatorToolVO
	 */
	private CoordinatorToolVO convertPlanTemplateConfigToCoordinatorToolVO(PlanTemplateConfigVO configVO,
			PlanTemplate planTemplate) {
		CoordinatorToolVO toolVO = new CoordinatorToolVO();
		PlanTemplateConfigVO.ToolConfigVO toolConfig = configVO.getToolConfig();

		// Set planTemplateId
		toolVO.setPlanTemplateId(configVO.getPlanTemplateId());

		// Apply toolConfig if available, otherwise use defaults
		if (toolConfig != null) {
			// Tool name: use from toolConfig if not null, otherwise default to "tool_" +
			// title
			if (configVO.getTitle() != null && !configVO.getTitle().trim().isEmpty()) {
				toolVO.setToolName(configVO.getTitle());
			}

			// Tool description: use from toolConfig if not null, otherwise default to
			// userRequest
			if (toolConfig.getToolDescription() != null && !toolConfig.getToolDescription().trim().isEmpty()) {
				toolVO.setToolDescription(toolConfig.getToolDescription());
			}
			else {
				String defaultDescription = planTemplate != null ? planTemplate.getTitle() : configVO.getTitle();
				toolVO.setToolDescription(defaultDescription);
			}

			// Service group: get from PlanTemplate (already set when creating/updating PlanTemplate)
			// Set it on VO for response, but it's stored in PlanTemplate, not CoordinatorToolEntity
			String serviceGroup = planTemplate != null ? planTemplate.getServiceGroup() : null;
			if (serviceGroup == null || serviceGroup.trim().isEmpty()) {
				// Fallback: prefer root level serviceGroup, then toolConfig.serviceGroup, then default
				serviceGroup = configVO.getServiceGroup();
				serviceGroup = serviceGroup != null && !serviceGroup.trim().isEmpty() ? serviceGroup : "inited-toolcall";
			}
			toolVO.setServiceGroup(serviceGroup);

			// Service enablement flags
			toolVO.setEnableInternalToolcall(
					toolConfig.getEnableInternalToolcall() != null ? toolConfig.getEnableInternalToolcall() : true);
			toolVO.setEnableHttpService(
					toolConfig.getEnableHttpService() != null ? toolConfig.getEnableHttpService() : false);
			toolVO.setEnableMcpService(
					toolConfig.getEnableMcpService() != null ? toolConfig.getEnableMcpService() : false);

			// Publish status
			toolVO
				.setPublishStatus(toolConfig.getPublishStatus() != null ? toolConfig.getPublishStatus() : "PUBLISHED");
		}
		else {
			// No toolConfig provided, use defaults
			toolVO
				.setToolName(planTemplate != null ? "tool_" + planTemplate.getTitle() : "tool_" + configVO.getTitle());
			toolVO.setToolDescription(planTemplate != null ? planTemplate.getTitle() : configVO.getTitle());
			// Service group: get from PlanTemplate (already set when creating/updating PlanTemplate)
			// Set it on VO for response, but it's stored in PlanTemplate, not CoordinatorToolEntity
			String serviceGroup = planTemplate != null ? planTemplate.getServiceGroup() : null;
			if (serviceGroup == null || serviceGroup.trim().isEmpty()) {
				// Fallback: use root level serviceGroup if provided, otherwise default
				serviceGroup = configVO.getServiceGroup();
				serviceGroup = serviceGroup != null && !serviceGroup.trim().isEmpty() ? serviceGroup : "inited-toolcall";
			}
			toolVO.setServiceGroup(serviceGroup);
			toolVO.setEnableInternalToolcall(true);
			toolVO.setEnableHttpService(false);
			toolVO.setEnableMcpService(false);
			toolVO.setPublishStatus("PUBLISHED");
		}

		return toolVO;
	}

	/**
	 * Generate input schema JSON string from plan template parameters InputSchema format:
	 * [{"name": "paramName", "type": "string", "description": "param description"}]
	 * @param planTemplateId Plan template ID
	 * @return JSON string representation of input schema array
	 */
	private String generateInputSchemaFromPlanTemplate(String planTemplateId) {
		try {
			// Get plan template JSON
			String planJson = planTemplateService.getLatestPlanVersion(planTemplateId);
			if (planJson == null) {
				log.warn("Plan JSON not found for template {}, using empty inputSchema", planTemplateId);
				return "[]";
			}

			// Extract parameter placeholders from plan JSON (e.g., <<userRequirement>>)
			List<String> parameters = parameterMappingService.extractParameterPlaceholders(planJson);

			// Build input schema array
			List<Map<String, Object>> inputSchemaArray = new ArrayList<>();

			for (String paramName : parameters) {
				Map<String, Object> paramSchema = new HashMap<>();
				paramSchema.put("name", paramName);
				paramSchema.put("type", "string");
				paramSchema.put("description", "Parameter: " + paramName);
				paramSchema.put("required", true);
				inputSchemaArray.add(paramSchema);
			}

			// Convert to JSON string
			String inputSchemaJson = objectMapper.writeValueAsString(inputSchemaArray);
			log.debug("Generated inputSchema with {} parameters: {}", parameters.size(), inputSchemaJson);
			return inputSchemaJson;

		}
		catch (Exception e) {
			log.error("Failed to generate inputSchema for plan template: {}", planTemplateId, e);
			// Return empty array as fallback
			return "[]";
		}
	}

	/**
	 * Convert List of InputSchemaParam to JSON string
	 * @param inputSchemaParams List of input schema parameters
	 * @return JSON string representation of input schema array
	 */
	private String convertInputSchemaListToJson(List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams) {
		try {
			List<Map<String, Object>> inputSchemaArray = new ArrayList<>();

			for (PlanTemplateConfigVO.InputSchemaParam param : inputSchemaParams) {
				Map<String, Object> paramSchema = new HashMap<>();
				paramSchema.put("name", param.getName());
				paramSchema.put("type", param.getType() != null ? param.getType() : "string");
				paramSchema.put("description", param.getDescription());
				paramSchema.put("required", param.getRequired() != null ? param.getRequired() : true);
				inputSchemaArray.add(paramSchema);
			}

			String inputSchemaJson = objectMapper.writeValueAsString(inputSchemaArray);
			log.debug("Converted inputSchema list to JSON: {}", inputSchemaJson);
			return inputSchemaJson;

		}
		catch (Exception e) {
			log.error("Failed to convert inputSchema list to JSON: {}", e.getMessage(), e);
			return "[]";
		}
	}


	/**
	 * Create PlanTemplate from PlanTemplateConfigVO and save it to database
	 * @param configVO Plan template configuration VO
	 * @return Created PlanTemplate
	 */
	private PlanTemplate createPlanTemplateFromConfig(PlanTemplateConfigVO configVO) {
		try {
			String planTemplateId = configVO.getPlanTemplateId();
			String title = configVO.getTitle() != null ? configVO.getTitle() : "Untitled Plan";

			// Convert PlanTemplateConfigVO to JSON string (excluding toolConfig)
			// Create a copy without toolConfig for the plan JSON
			PlanTemplateConfigVO planJsonConfig = new PlanTemplateConfigVO();
			planJsonConfig.setTitle(configVO.getTitle());
			planJsonConfig.setSteps(configVO.getSteps());
			planJsonConfig.setDirectResponse(configVO.getDirectResponse());
			planJsonConfig.setPlanType(configVO.getPlanType());
			planJsonConfig.setPlanTemplateId(configVO.getPlanTemplateId());
			planJsonConfig.setReadOnly(configVO.getReadOnly());
			// Explicitly do not set toolConfig

			String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(planJsonConfig);

			// Check if PlanTemplate already exists
			Optional<PlanTemplate> existingTemplateOpt = planTemplateRepository.findByPlanTemplateId(planTemplateId);
			boolean isNewTemplate = existingTemplateOpt.isEmpty();

			// Save plan template
			if (isNewTemplate) {
				// Create new plan template
				PlanTemplate template = new PlanTemplate(planTemplateId, title, false);
				planTemplateRepository.save(template);
				log.debug("Created new plan template: {}", planTemplateId);
			}
			else {
				// Update existing plan template
				PlanTemplate template = existingTemplateOpt.get();
				template.setTitle(title);
				template.setInternalToolcall(false);
				template.setUpdateTime(java.time.LocalDateTime.now());
				planTemplateRepository.save(template);
				log.debug("Updated existing plan template: {}", planTemplateId);
			}

			// Use PlanTemplateService.saveToVersionHistory() which automatically:
			// 1. Checks if content is the same as the latest version
			// 2. Only saves a new version if content is different
			PlanTemplateService.VersionSaveResult result = planTemplateService.saveToVersionHistory(planTemplateId,
					planJson);

			if (result.isSaved()) {
				log.debug("Saved new version {} for PlanTemplate {} (content was different from latest version)",
						result.getVersionIndex(), planTemplateId);
			}
			else if (result.isDuplicate()) {
				log.debug("Skipped saving version for PlanTemplate {} (content is the same as latest version: {})",
						planTemplateId, result.getVersionIndex());
			}

			// Retrieve the saved plan template
			PlanTemplate savedTemplate = planTemplateRepository.findByPlanTemplateId(planTemplateId)
					.orElseThrow(() -> new CoordinatorToolException("INTERNAL_ERROR",
							"Failed to retrieve created PlanTemplate with ID: " + planTemplateId));

			// Set serviceGroup on PlanTemplate from configVO
			String serviceGroup = configVO.getServiceGroup();
			if (serviceGroup != null && !serviceGroup.trim().isEmpty()) {
				savedTemplate.setServiceGroup(serviceGroup);
				planTemplateRepository.save(savedTemplate);
				log.info("Set serviceGroup '{}' on PlanTemplate with ID: {}", serviceGroup, planTemplateId);
			}
			else {
				// Set default serviceGroup if not provided
				savedTemplate.setServiceGroup("inited-toolcall");
				planTemplateRepository.save(savedTemplate);
				log.info("Set default serviceGroup 'inited-toolcall' on PlanTemplate with ID: {}", planTemplateId);
			}

			log.info("Successfully created PlanTemplate with ID: {}", planTemplateId);
			return savedTemplate;

		}
		catch (CoordinatorToolException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create PlanTemplate from PlanTemplateConfigVO: {}", e.getMessage(), e);
			throw new CoordinatorToolException("INTERNAL_ERROR", "Failed to create PlanTemplate: " + e.getMessage());
		}
	}

}
