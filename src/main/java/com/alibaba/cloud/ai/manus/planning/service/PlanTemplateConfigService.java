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
package com.alibaba.cloud.ai.manus.planning.service;

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

import com.alibaba.cloud.ai.manus.planning.exception.PlanTemplateConfigException;
import com.alibaba.cloud.ai.manus.planning.model.po.FuncAgentToolEntity;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.model.vo.PlanTemplateConfigVO;
import com.alibaba.cloud.ai.manus.planning.repository.FuncAgentToolRepository;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for processing PlanTemplateConfigVO
 * Handles conversion, validation, and PlanTemplate creation/update from PlanTemplateConfigVO
 */
@Service
public class PlanTemplateConfigService {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplateConfigService.class);

	@Autowired
	private PlanTemplateRepository planTemplateRepository;

	@Autowired
	private FuncAgentToolRepository funcAgentToolRepository;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * Prepare PlanTemplateConfigVO with toolConfig
	 * This method ensures toolConfig is properly set with input schema
	 * @param configVO Plan template configuration VO
	 * @return PlanTemplateConfigVO with toolConfig and input schema set
	 * @throws PlanTemplateConfigException if validation or operation fails
	 */
	@Transactional
	public PlanTemplateConfigVO preparePlanTemplateConfigWithToolConfig(PlanTemplateConfigVO configVO)
			throws PlanTemplateConfigException {
		if (configVO == null) {
			throw new PlanTemplateConfigException("VALIDATION_ERROR", "PlanTemplateConfigVO cannot be null");
		}

		String planTemplateId = configVO.getPlanTemplateId();
		if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
			throw new PlanTemplateConfigException("VALIDATION_ERROR",
					"planTemplateId is required in PlanTemplateConfigVO");
		}

		log.info("Preparing PlanTemplateConfigVO with toolConfig for planTemplateId: {}", planTemplateId);
		try {
			// Ensure PlanTemplate exists, create if it doesn't
			ensurePlanTemplateExists(configVO);

			// Ensure toolConfig exists
			if (configVO.getToolConfig() == null) {
				configVO.setToolConfig(new PlanTemplateConfigVO.ToolConfigVO());
			}

			PlanTemplateConfigVO.ToolConfigVO toolConfig = configVO.getToolConfig();

			// Set input schema: use from toolConfig if provided, otherwise generate from plan template
			if (toolConfig.getInputSchema() == null || toolConfig.getInputSchema().isEmpty()) {
				// Generate input schema from plan template parameters
				String inputSchemaJson = generateInputSchemaFromPlanTemplate(planTemplateId);
				log.debug("Generated inputSchema from plan template parameters for plan template {}: {}",
						planTemplateId, inputSchemaJson);

				// Parse JSON string to InputSchemaParam list
				try {
					JsonNode inputSchemaNode = objectMapper.readTree(inputSchemaJson);
					if (inputSchemaNode.isArray()) {
						List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams = new ArrayList<>();
						for (JsonNode paramNode : inputSchemaNode) {
							PlanTemplateConfigVO.InputSchemaParam param = new PlanTemplateConfigVO.InputSchemaParam();
							if (paramNode.has("name")) {
								param.setName(paramNode.get("name").asText());
							}
							if (paramNode.has("description")) {
								param.setDescription(paramNode.get("description").asText());
							}
							if (paramNode.has("type")) {
								param.setType(paramNode.get("type").asText());
							}
							if (paramNode.has("required")) {
								param.setRequired(paramNode.get("required").asBoolean());
							}
							inputSchemaParams.add(param);
						}
						toolConfig.setInputSchema(inputSchemaParams);
					}
				}
				catch (Exception e) {
					log.warn("Failed to parse generated inputSchema JSON, using empty list: {}", e.getMessage());
					toolConfig.setInputSchema(new ArrayList<>());
				}
			}

			return configVO;

		}
		catch (PlanTemplateConfigException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to prepare PlanTemplateConfigVO with toolConfig for planTemplateId: {}", planTemplateId, e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"An unexpected error occurred while preparing PlanTemplateConfigVO: " + e.getMessage());
		}
	}

	/**
	 * Ensure PlanTemplate exists, create or update it if needed
	 * @param configVO Plan template configuration VO
	 * @return Existing or newly created PlanTemplate
	 * @throws PlanTemplateConfigException if creation fails
	 */
	@Transactional
	public PlanTemplate ensurePlanTemplateExists(PlanTemplateConfigVO configVO) throws PlanTemplateConfigException {
		String planTemplateId = configVO.getPlanTemplateId();
		Optional<PlanTemplate> existingTemplateOpt = planTemplateRepository.findByPlanTemplateId(planTemplateId);

		if (existingTemplateOpt.isPresent()) {
			PlanTemplate planTemplate = existingTemplateOpt.get();
			// Update serviceGroup on existing PlanTemplate if provided in configVO
			String serviceGroup = configVO.getServiceGroup();
			if (serviceGroup != null && !serviceGroup.trim().isEmpty()
					&& !serviceGroup.equals(planTemplate.getServiceGroup())) {
				planTemplate.setServiceGroup(serviceGroup);
				planTemplateRepository.save(planTemplate);
				log.info("Updated serviceGroup to '{}' on PlanTemplate with ID: {}", serviceGroup, planTemplateId);
			}
			return planTemplate;
		}
		else {
			log.info("Plan template not found for planTemplateId: {}, creating new PlanTemplate", planTemplateId);
			return createPlanTemplateFromConfig(configVO);
		}
	}

	/**
	 * Generate input schema JSON string from plan template parameters
	 * InputSchema format: [{"name": "paramName", "type": "string", "description": "param description"}]
	 * @param planTemplateId Plan template ID
	 * @return JSON string representation of input schema array
	 */
	public String generateInputSchemaFromPlanTemplate(String planTemplateId) {
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
	public String convertInputSchemaListToJson(List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams) {
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
	@Transactional
	public PlanTemplate createPlanTemplateFromConfig(PlanTemplateConfigVO configVO) {
		try {
			String planTemplateId = configVO.getPlanTemplateId();
			String title = configVO.getTitle() != null ? configVO.getTitle() : "Untitled Plan";


			String planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(configVO);

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
					.orElseThrow(() -> new PlanTemplateConfigException("INTERNAL_ERROR",
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
				savedTemplate.setServiceGroup("ungrouped");
				planTemplateRepository.save(savedTemplate);
				log.info("Set default serviceGroup 'ungrouped' on PlanTemplate with ID: {}", planTemplateId);
			}

			log.info("Successfully created PlanTemplate with ID: {}", planTemplateId);
			return savedTemplate;

		}
		catch (PlanTemplateConfigException e) {
			throw e;
		}
		catch (org.hibernate.exception.ConstraintViolationException e) {
			// Check if it's a unique constraint violation on title
			if (e.getSQLException() != null && e.getSQLException().getSQLState() != null
					&& e.getSQLException().getSQLState().equals("23505")) {
				String errorMessage = e.getSQLException().getMessage();
				if (errorMessage != null && errorMessage.contains("title")) {
					log.warn("Duplicate plan title detected: {}", configVO.getTitle());
					throw new PlanTemplateConfigException("DUPLICATE_TITLE",
							"Plan title already exists: " + configVO.getTitle());
				}
			}
			log.error("Constraint violation while creating PlanTemplate: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"Failed to create PlanTemplate: " + e.getMessage());
		}
		catch (org.springframework.dao.DataIntegrityViolationException e) {
			// Check if it's a unique constraint violation on title
			Throwable rootCause = e.getRootCause();
			if (rootCause instanceof java.sql.SQLException) {
				java.sql.SQLException sqlException = (java.sql.SQLException) rootCause;
				if (sqlException.getSQLState() != null && sqlException.getSQLState().equals("23505")) {
					String errorMessage = sqlException.getMessage();
					if (errorMessage != null && errorMessage.contains("title")) {
						log.warn("Duplicate plan title detected: {}", configVO.getTitle());
						throw new PlanTemplateConfigException("DUPLICATE_TITLE",
								"Plan title already exists: " + configVO.getTitle());
					}
				}
			}
			log.error("Data integrity violation while creating PlanTemplate: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"Failed to create PlanTemplate: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Failed to create PlanTemplate from PlanTemplateConfigVO: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR", "Failed to create PlanTemplate: " + e.getMessage());
		}
	}

	/**
	 * Create or update coordinator tool from PlanTemplateConfigVO
	 * Uses planTemplateId as the key identity to determine if tool exists
	 * @param configVO Plan template configuration VO
	 * @return Created or updated PlanTemplateConfigVO
	 * @throws PlanTemplateConfigException if validation or operation fails
	 */
	@Transactional
	public PlanTemplateConfigVO createOrUpdateCoordinatorToolFromPlanTemplateConfig(PlanTemplateConfigVO configVO)
			throws PlanTemplateConfigException {
		if (configVO == null) {
			throw new PlanTemplateConfigException("VALIDATION_ERROR", "PlanTemplateConfigVO cannot be null");
		}

		String planTemplateId = configVO.getPlanTemplateId();
		if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
			throw new PlanTemplateConfigException("VALIDATION_ERROR",
					"planTemplateId is required in PlanTemplateConfigVO");
		}

		log.info("Creating or updating coordinator tool from PlanTemplateConfigVO for planTemplateId: {}",
				planTemplateId);
		try {
			// Update or create PlanTemplate with the latest config (including steps with selectedToolKeys)
			// This ensures the plan JSON in the database is updated with the latest configuration
			createPlanTemplateFromConfig(configVO);
			log.info("Updated PlanTemplate and plan JSON for planTemplateId: {}", planTemplateId);

			// Prepare PlanTemplateConfigVO with toolConfig
			PlanTemplateConfigVO preparedConfig = preparePlanTemplateConfigWithToolConfig(configVO);

			// Check if coordinator tool already exists by planTemplateId
			List<FuncAgentToolEntity> existingEntities = funcAgentToolRepository.findByPlanTemplateId(planTemplateId);

			if (!existingEntities.isEmpty()) {
				// Tool exists, update it
				FuncAgentToolEntity existingEntity = existingEntities.get(0);
				Long toolId = existingEntity.getId();
				log.info("Coordinator tool already exists for plan template {}, updating with ID: {}", planTemplateId,
						toolId);
				return updateCoordinatorTool(toolId, preparedConfig);
			}
			else {
				// Tool doesn't exist, create it
				log.info("Creating new coordinator tool for plan template: {}", planTemplateId);
				return createCoordinatorTool(preparedConfig);
			}

		}
		catch (PlanTemplateConfigException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to create or update coordinator tool from PlanTemplateConfigVO for planTemplateId: {}",
					planTemplateId, e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"An unexpected error occurred while creating or updating coordinator tool: " + e.getMessage());
		}
	}

	/**
	 * Update coordinator tool
	 * @param id Coordinator tool ID
	 * @param configVO Plan template configuration VO
	 * @return Updated PlanTemplateConfigVO
	 * @throws PlanTemplateConfigException if update fails
	 */
	@Transactional
	public PlanTemplateConfigVO updateCoordinatorTool(Long id, PlanTemplateConfigVO configVO)
			throws PlanTemplateConfigException {
		try {
			log.info("Updating coordinator tool with ID: {}", id);

			// Check if entity exists
			FuncAgentToolEntity existingEntity = funcAgentToolRepository.findById(id)
					.orElseThrow(() -> new PlanTemplateConfigException("NOT_FOUND",
							"Coordinator tool not found with ID: " + id));

			// Update entity from configVO
			// Always update toolName from title to ensure consistency
			existingEntity.setToolName(configVO.getTitle() != null ? configVO.getTitle() : "");
			existingEntity.setPlanTemplateId(configVO.getPlanTemplateId());

			PlanTemplateConfigVO.ToolConfigVO toolConfig = configVO.getToolConfig();
			if (toolConfig != null) {
				existingEntity.setToolDescription(
						toolConfig.getToolDescription() != null ? toolConfig.getToolDescription() : "");
				existingEntity.setInputSchema(convertInputSchemaListToJson(toolConfig.getInputSchema()));
				existingEntity.setEnableInternalToolcall(
						toolConfig.getEnableInternalToolcall() != null ? toolConfig.getEnableInternalToolcall() : false);
				existingEntity.setEnableHttpService(
						toolConfig.getEnableHttpService() != null ? toolConfig.getEnableHttpService() : false);
				existingEntity.setEnableMcpService(
						toolConfig.getEnableMcpService() != null ? toolConfig.getEnableMcpService() : false);
			}

			FuncAgentToolEntity savedEntity = funcAgentToolRepository.save(existingEntity);
			log.info("Successfully updated FuncAgentToolEntity: {} with ID: {}", savedEntity.getToolDescription(),
					savedEntity.getId());

			// Convert back to PlanTemplateConfigVO
			return convertEntityToPlanTemplateConfigVO(savedEntity);

		}
		catch (PlanTemplateConfigException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Error updating coordinator tool: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"An unexpected error occurred while updating coordinator tool: " + e.getMessage());
		}
	}

	/**
	 * Delete coordinator tool
	 * @param id Coordinator tool ID
	 * @throws PlanTemplateConfigException if deletion fails
	 */
	@Transactional
	public void deleteCoordinatorTool(Long id) throws PlanTemplateConfigException {
		try {
			log.info("Deleting coordinator tool with ID: {}", id);

			// Check if entity exists
			if (!funcAgentToolRepository.existsById(id)) {
				throw new PlanTemplateConfigException("NOT_FOUND", "Coordinator tool not found with ID: " + id);
			}

			// Delete entity
			funcAgentToolRepository.deleteById(id);
			log.info("Successfully deleted CoordinatorToolEntity with ID: {}", id);

		}
		catch (PlanTemplateConfigException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Error deleting coordinator tool: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"An unexpected error occurred while deleting coordinator tool: " + e.getMessage());
		}
	}

	/**
	 * Delete coordinator tool by plan template ID
	 * @param planTemplateId Plan template ID
	 */
	@Transactional
	public void deleteCoordinatorToolByPlanTemplateId(String planTemplateId) {
		try {
			log.info("Deleting coordinator tools for plan template ID: {}", planTemplateId);
			funcAgentToolRepository.deleteByPlanTemplateId(planTemplateId);
			log.info("Successfully deleted coordinator tools for plan template ID: {}", planTemplateId);
		}
		catch (Exception e) {
			log.warn("Error deleting coordinator tools for plan template ID {}: {}", planTemplateId, e.getMessage());
			// Don't throw exception - this is a cleanup operation
		}
	}

	/**
	 * Create coordinator tool
	 * @param configVO Plan template configuration VO
	 * @return Created PlanTemplateConfigVO
	 * @throws PlanTemplateConfigException if creation fails
	 */
	@Transactional
	public PlanTemplateConfigVO createCoordinatorTool(PlanTemplateConfigVO configVO) throws PlanTemplateConfigException {
		try {
			log.info("Creating coordinator tool for planTemplateId: {}", configVO.getPlanTemplateId());

			PlanTemplateConfigVO.ToolConfigVO toolConfig = configVO.getToolConfig();
			if (toolConfig == null) {
				throw new PlanTemplateConfigException("VALIDATION_ERROR", "toolConfig is required");
			}

			// Use title as toolName
			String toolName = configVO.getTitle() != null ? configVO.getTitle() : "";

			// Check if a tool with the same toolName already exists
			// If it exists but with different planTemplateId or null planTemplateId, delete it first
			List<FuncAgentToolEntity> existingToolsWithSameName = funcAgentToolRepository.findByToolName(toolName);
			if (!existingToolsWithSameName.isEmpty()) {
				for (FuncAgentToolEntity existingTool : existingToolsWithSameName) {
					// If the existing tool has a different planTemplateId or null planTemplateId, delete it
					if (existingTool.getPlanTemplateId() == null
							|| !existingTool.getPlanTemplateId().equals(configVO.getPlanTemplateId())) {
						log.warn(
								"Found existing coordinator tool with same toolName '{}' but different planTemplateId (existing: {}, new: {}). Deleting old tool.",
								toolName, existingTool.getPlanTemplateId(), configVO.getPlanTemplateId());
						funcAgentToolRepository.deleteById(existingTool.getId());
					}
					else {
						// Same toolName and same planTemplateId - this should not happen as we check by planTemplateId first
						log.warn(
								"Found existing coordinator tool with same toolName '{}' and planTemplateId '{}'. This should have been handled by update logic.",
								toolName, configVO.getPlanTemplateId());
					}
				}
			}

			// Convert PlanTemplateConfigVO to Entity and save
			FuncAgentToolEntity entity = new FuncAgentToolEntity();
			entity.setToolName(toolName);
			entity.setToolDescription(
					toolConfig.getToolDescription() != null ? toolConfig.getToolDescription() : "");
			entity.setInputSchema(convertInputSchemaListToJson(toolConfig.getInputSchema()));
			entity.setPlanTemplateId(configVO.getPlanTemplateId());
			entity.setEnableInternalToolcall(
					toolConfig.getEnableInternalToolcall() != null ? toolConfig.getEnableInternalToolcall() : false);
			entity.setEnableHttpService(
					toolConfig.getEnableHttpService() != null ? toolConfig.getEnableHttpService() : false);
			entity.setEnableMcpService(
					toolConfig.getEnableMcpService() != null ? toolConfig.getEnableMcpService() : false);

			FuncAgentToolEntity savedEntity = funcAgentToolRepository.save(entity);
			log.info("Successfully saved FuncAgentToolEntity: {} with ID: {}", savedEntity.getToolDescription(),
					savedEntity.getId());

			// Convert back to PlanTemplateConfigVO
			return convertEntityToPlanTemplateConfigVO(savedEntity);

		}
		catch (Exception e) {
			log.error("Failed to create coordinator tool: {}", e.getMessage(), e);
			throw new PlanTemplateConfigException("INTERNAL_ERROR",
					"An unexpected error occurred while creating coordinator tool: " + e.getMessage());
		}
	}

	/**
	 * Get coordinator tool by plan template ID
	 * @param planTemplateId Plan template ID
	 * @return Optional PlanTemplateConfigVO
	 */
	public Optional<PlanTemplateConfigVO> getCoordinatorToolByPlanTemplateId(String planTemplateId) {
		try {
			List<FuncAgentToolEntity> entities = funcAgentToolRepository.findByPlanTemplateId(planTemplateId);
			if (!entities.isEmpty()) {
				return Optional.of(convertEntityToPlanTemplateConfigVO(entities.get(0)));
			}
			return Optional.empty();
		}
		catch (Exception e) {
			log.error("Error getting coordinator tool by plan template ID: {}", e.getMessage(), e);
			return Optional.empty();
		}
	}

	/**
	 * Get plan template ID from tool name
	 * Only returns plan template ID if HTTP service is enabled for the tool
	 * Tool name is matched against PlanTemplate.title
	 * @param toolName Tool name (PlanTemplate.title)
	 * @return Plan template ID if found and HTTP service is enabled, null otherwise
	 */
	public String getPlanTemplateIdFromToolName(String toolName) {
		try {
			// Find PlanTemplate by title (tool name)
			Optional<PlanTemplate> planTemplateOpt = planTemplateRepository.findByTitle(toolName);
			if (planTemplateOpt.isEmpty()) {
				return null;
			}

			PlanTemplate planTemplate = planTemplateOpt.get();
			String planTemplateId = planTemplate.getPlanTemplateId();

			// Find FuncAgentToolEntity by planTemplateId
			List<FuncAgentToolEntity> toolEntities = funcAgentToolRepository.findByPlanTemplateId(planTemplateId);
			if (toolEntities.isEmpty()) {
				return null;
			}

			FuncAgentToolEntity toolEntity = toolEntities.get(0);
			// Only return plan template ID if HTTP service is enabled
			Boolean isHttpEnabled = toolEntity.getEnableHttpService();
			if (isHttpEnabled == null || !isHttpEnabled) {
				return null;
			}
			return planTemplateId;
		}
		catch (Exception e) {
			log.error("Error getting plan template ID from tool name: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Get all coordinator tools
	 * @return List of all PlanTemplateConfigVO
	 */
	public List<PlanTemplateConfigVO> getAllCoordinatorTools() {
		try {
			return funcAgentToolRepository.findAll().stream().map(this::convertEntityToPlanTemplateConfigVO)
					.collect(Collectors.toList());
		}
		catch (Exception e) {
			log.error("Error getting all coordinator tools: {}", e.getMessage(), e);
			return new ArrayList<>();
		}
	}

	/**
	 * Convert FuncAgentToolEntity to PlanTemplateConfigVO
	 * @param entity FuncAgentToolEntity
	 * @return PlanTemplateConfigVO with toolConfig populated
	 */
	private PlanTemplateConfigVO convertEntityToPlanTemplateConfigVO(FuncAgentToolEntity entity) {
		PlanTemplateConfigVO configVO = new PlanTemplateConfigVO();
		configVO.setPlanTemplateId(entity.getPlanTemplateId());

		// Get PlanTemplate for additional info
		PlanTemplate planTemplate = planTemplateRepository.findByPlanTemplateId(entity.getPlanTemplateId())
				.orElse(null);
		if (planTemplate != null) {
			configVO.setServiceGroup(planTemplate.getServiceGroup());
		}

		// Create ToolConfigVO from entity
		PlanTemplateConfigVO.ToolConfigVO toolConfig = new PlanTemplateConfigVO.ToolConfigVO();
		toolConfig.setToolDescription(entity.getToolDescription());
		toolConfig.setEnableInternalToolcall(entity.getEnableInternalToolcall());
		toolConfig.setEnableHttpService(entity.getEnableHttpService());
		toolConfig.setEnableMcpService(entity.getEnableMcpService());

		// Parse inputSchema JSON string to InputSchemaParam list
		if (entity.getInputSchema() != null && !entity.getInputSchema().trim().isEmpty()) {
			try {
				JsonNode inputSchemaNode = objectMapper.readTree(entity.getInputSchema());
				if (inputSchemaNode.isArray()) {
					List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams = new ArrayList<>();
					for (JsonNode paramNode : inputSchemaNode) {
						PlanTemplateConfigVO.InputSchemaParam param = new PlanTemplateConfigVO.InputSchemaParam();
						if (paramNode.has("name")) {
							param.setName(paramNode.get("name").asText());
						}
						if (paramNode.has("description")) {
							param.setDescription(paramNode.get("description").asText());
						}
						if (paramNode.has("type")) {
							param.setType(paramNode.get("type").asText());
						}
						if (paramNode.has("required")) {
							param.setRequired(paramNode.get("required").asBoolean());
						}
						inputSchemaParams.add(param);
					}
					toolConfig.setInputSchema(inputSchemaParams);
				}
			}
			catch (Exception e) {
				log.warn("Failed to parse inputSchema for entity ID: {}", entity.getId(), e);
				toolConfig.setInputSchema(new ArrayList<>());
			}
		}
		else {
			toolConfig.setInputSchema(new ArrayList<>());
		}

		configVO.setToolConfig(toolConfig);
		return configVO;
	}

}

