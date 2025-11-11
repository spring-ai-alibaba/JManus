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
package com.alibaba.cloud.ai.manus.planning.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.manus.coordinator.entity.vo.CoordinatorToolVO;
import com.alibaba.cloud.ai.manus.coordinator.entity.vo.PlanTemplateConfigVO;
import com.alibaba.cloud.ai.manus.coordinator.exception.CoordinatorToolException;
import com.alibaba.cloud.ai.manus.coordinator.service.CoordinatorToolServiceImpl;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.PlanInterface;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Plan template controller, handles API requests for the plan template page
 */
@RestController
@RequestMapping("/api/plan-template")
public class PlanTemplateController {

	private static final Logger logger = LoggerFactory.getLogger(PlanTemplateController.class);

	@Autowired
	@Lazy
	private PlanningFactory planningFactory;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	@Autowired
	private CoordinatorToolServiceImpl coordinatorToolService;

	/**
	 * Save version history
	 * @param planJson Plan JSON data
	 * @param planId Plan template ID (already generated)
	 * @return Save result
	 */
	private PlanTemplateService.VersionSaveResult saveToVersionHistory(String planJson, String planId) {
		try {
			// Parse JSON to extract title
			PlanInterface planData = objectMapper.readValue(planJson, PlanInterface.class);

			// Use the provided planId instead of generating a new one
			String planTemplateId = planId;

			String title = planData.getTitle();

			if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
				throw new IllegalArgumentException("Plan ID cannot be found in JSON");
			}
			if (title == null || title.trim().isEmpty()) {
				title = "Untitled Plan";
			}

			// Check if the plan exists
			PlanTemplate template = planTemplateService.getPlanTemplate(planTemplateId);
			if (template == null) {
				// If it doesn't exist, create a new plan
				planTemplateService.savePlanTemplate(planTemplateId, title, planJson, false);
				logger.info("New plan created: {}", planTemplateId);
				return new PlanTemplateService.VersionSaveResult(true, false, "New plan created", 0);
			}
			else {
				// If it exists, update the template with new title and save a new version
				boolean updated = planTemplateService.updatePlanTemplate(planTemplateId, title, planJson, false);
				if (updated) {
					logger.info("Updated plan template {} with new title and saved new version", planTemplateId);
					// Get the latest version index after update
					Integer maxVersionIndex = planTemplateService.getPlanVersions(planTemplateId).size() - 1;
					return new PlanTemplateService.VersionSaveResult(true, false,
							"Plan template updated and new version saved", maxVersionIndex);
				}
				else {
					// Fallback to just saving version if update failed
					PlanTemplateService.VersionSaveResult result = planTemplateService
						.saveToVersionHistory(planTemplateId, planJson);
					if (result.isSaved()) {
						logger.info("New version of plan {} saved", planTemplateId, result.getVersionIndex());
					}
					else {
						logger.info("Plan {} is the same, no new version saved", planTemplateId);
					}
					return result;
				}
			}
		}
		catch (Exception e) {
			logger.error("Failed to parse plan JSON", e);
			throw new RuntimeException("Failed to save version history: " + e.getMessage());
		}
	}

	/**
	 * Save plan
	 * @param request Request containing plan ID and JSON
	 * @return Save result
	 */
	@PostMapping("/save")
	@Transactional
	public ResponseEntity<Map<String, Object>> savePlan(@RequestBody Map<String, String> request) {
		String planJson = request.get("planJson");

		if (planJson == null || planJson.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Plan data cannot be empty"));
		}

		try {
			// Parse JSON to get planId
			PlanInterface planData = objectMapper.readValue(planJson, PlanInterface.class);
			String planId = planData.getPlanTemplateId();
			if (planId == null) {
				planId = planData.getRootPlanId();
			}

			// Check if planId is empty or starts with "new-", then generate a new one
			if (planId == null || planId.trim().isEmpty() || planId.startsWith("new-")) {
				String newPlanId = planIdDispatcher.generatePlanTemplateId();
				logger.info("Original planId '{}' is empty or starts with 'new-', generated new planId: {}", planId,
						newPlanId);

				// Update the plan object with new ID
				planData.setCurrentPlanId(newPlanId);
				planData.setRootPlanId(newPlanId);

				// Re-serialize the updated plan object to JSON
				planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(planData);
				planId = newPlanId;
			}

			if (planId == null || planId.trim().isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "Plan ID cannot be found in JSON"));
			}

			// Save to version history
			PlanTemplateService.VersionSaveResult saveResult = saveToVersionHistory(planJson, planId);

			// Calculate version count
			List<String> versions = planTemplateService.getPlanVersions(planId);
			int versionCount = versions.size();

			// Build response
			Map<String, Object> response = new HashMap<>();
			response.put("status", "success");
			response.put("planId", planId);
			response.put("versionCount", versionCount);
			response.put("saved", saveResult.isSaved());
			response.put("duplicate", saveResult.isDuplicate());
			response.put("message", saveResult.getMessage());
			response.put("versionIndex", saveResult.getVersionIndex());

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Failed to save plan", e);
			return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save plan: " + e.getMessage()));
		}
	}

	/**
	 * Get the version history of the plan
	 * @param request Request containing plan ID
	 * @return Version history list
	 */
	@PostMapping("/versions")
	public ResponseEntity<Map<String, Object>> getPlanVersions(@RequestBody Map<String, String> request) {
		String planId = request.get("planId");

		if (planId == null || planId.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Plan ID cannot be empty"));
		}

		List<String> versions = planTemplateService.getPlanVersions(planId);

		Map<String, Object> response = new HashMap<>();
		response.put("planId", planId);
		response.put("versionCount", versions.size());
		response.put("versions", versions);

		return ResponseEntity.ok(response);
	}

	/**
	 * Get a specific version of the plan
	 * @param request Request containing plan ID and version index
	 * @return Specific version of the plan
	 */
	@PostMapping("/get-version")
	public ResponseEntity<Map<String, Object>> getVersionPlan(@RequestBody Map<String, String> request) {
		String planId = request.get("planId");
		String versionIndex = request.get("versionIndex");

		if (planId == null || planId.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Plan ID cannot be empty"));
		}

		try {
			int index = Integer.parseInt(versionIndex);
			List<String> versions = planTemplateService.getPlanVersions(planId);

			if (versions.isEmpty()) {
				return ResponseEntity.notFound().build();
			}

			if (index < 0 || index >= versions.size()) {
				return ResponseEntity.badRequest().body(Map.of("error", "Version index out of range"));
			}

			String planJson = planTemplateService.getPlanVersion(planId, index);

			if (planJson == null) {
				return ResponseEntity.notFound().build();
			}

			Map<String, Object> response = new HashMap<>();
			response.put("planId", planId);
			response.put("versionIndex", index);
			response.put("versionCount", versions.size());
			response.put("planJson", planJson);

			return ResponseEntity.ok(response);
		}
		catch (NumberFormatException e) {
			return ResponseEntity.badRequest().body(Map.of("error", "Version index must be a number"));
		}
		catch (Exception e) {
			logger.error("Failed to get plan version", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to get plan version: " + e.getMessage()));
		}
	}

	/**
	 * Get all plan templates
	 * @return All plan templates
	 */
	@GetMapping("/list")
	public ResponseEntity<Map<String, Object>> getAllPlanTemplates() {
		try {
			// Use PlanTemplateService to get all plan templates
			// Since there is no direct method to get all templates, we use the findAll
			// method of PlanTemplateRepository
			List<PlanTemplate> templates = planTemplateService.getAllPlanTemplates();

			// Build response data
			List<Map<String, Object>> templateList = new ArrayList<>();
			for (PlanTemplate template : templates) {
				Map<String, Object> templateData = new HashMap<>();
				if (template.isInternalToolcall()) {
					continue;
				}
				templateData.put("id", template.getPlanTemplateId());
				templateData.put("title", template.getTitle());
				templateData.put("description", template.getTitle());
				templateData.put("createTime", template.getCreateTime());
				templateData.put("updateTime", template.getUpdateTime());
				templateList.add(templateData);
			}

			Map<String, Object> response = new HashMap<>();
			response.put("templates", templateList);
			response.put("count", templateList.size());

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Failed to get plan template list", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to get plan template list: " + e.getMessage()));
		}
	}

	/**
	 * Delete plan template
	 * @param request Request containing plan ID
	 * @return Delete result
	 */
	@PostMapping("/delete")
	public ResponseEntity<Map<String, Object>> deletePlanTemplate(@RequestBody Map<String, String> request) {
		String planId = request.get("planId");

		if (planId == null || planId.trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("error", "Plan ID cannot be empty"));
		}

		try {
			// Check if the plan template exists
			PlanTemplate template = planTemplateService.getPlanTemplate(planId);
			if (template == null) {
				return ResponseEntity.notFound().build();
			}

			// Delete the plan template and all versions
			boolean deleted = planTemplateService.deletePlanTemplate(planId);

			if (deleted) {
				logger.info("Plan template deleted successfully: {}", planId);
				return ResponseEntity
					.ok(Map.of("status", "success", "message", "Plan template deleted", "planId", planId));
			}
			else {
				logger.error("Failed to delete plan template: {}", planId);
				return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete plan template"));
			}
		}
		catch (Exception e) {
			logger.error("Failed to delete plan template", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to delete plan template: " + e.getMessage()));
		}
	}

	/**
	 * Get parameter requirements for a plan template
	 * @param planTemplateId The plan template ID
	 * @return List of required parameters
	 */
	@GetMapping("/{planTemplateId}/parameters")
	public ResponseEntity<Map<String, Object>> getParameterRequirements(@PathVariable String planTemplateId) {
		try {
			PlanTemplate planTemplate = planTemplateService.getPlanTemplate(planTemplateId);
			if (planTemplate == null) {
				return ResponseEntity.notFound().build();
			}

			String planJson = planTemplateService.getLatestPlanVersion(planTemplateId);
			if (planJson == null) {
				return ResponseEntity.notFound().build();
			}

			List<String> parameters = parameterMappingService.extractParameterPlaceholders(planJson);

			Map<String, Object> response = new HashMap<>();
			response.put("parameters", parameters);
			response.put("hasParameters", !parameters.isEmpty());
			response.put("requirements", parameterMappingService.getParameterRequirements(planJson));

			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Failed to get parameter requirements for plan template: " + planTemplateId, e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to get parameter requirements: " + e.getMessage()));
		}
	}

	/**
	 * Create or update plan template and register as coordinator tool
	 * This method combines the functionality of both "Save Plan Template" and
	 * "Register Plan Templates as Toolcalls" by using PlanTemplateConfigVO.
	 * It will:
	 * 1. Create or update the PlanTemplate in the database
	 * 2. Create or update the CoordinatorTool (if toolConfig is provided)
	 * @param configVO Plan template configuration VO containing plan template data and
	 * optional toolConfig
	 * @return Response containing created/updated plan template and coordinator tool
	 * information
	 */
	@PostMapping("/create-or-update-with-tool")
	@Transactional
	public ResponseEntity<Map<String, Object>> createOrUpdatePlanTemplateWithTool(
			@RequestBody PlanTemplateConfigVO configVO) {
		try {
			if (configVO == null) {
				return ResponseEntity.badRequest().body(Map.of("error", "PlanTemplateConfigVO cannot be null"));
			}

			String planTemplateId = configVO.getPlanTemplateId();
			if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
				return ResponseEntity.badRequest()
					.body(Map.of("error", "planTemplateId is required in PlanTemplateConfigVO"));
			}

			logger.info("Creating or updating plan template with tool registration for planTemplateId: {}",
					planTemplateId);

			// Use the unified service method that handles both plan template and coordinator
			// tool creation/update
			CoordinatorToolVO coordinatorToolVO = coordinatorToolService
				.createOrUpdateCoordinatorToolFromPlanTemplateConfig(configVO);

			// Build simple response
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("planTemplateId", planTemplateId);
			response.put("toolRegistered", coordinatorToolVO != null);

			return ResponseEntity.ok(response);

		}
		catch (CoordinatorToolException e) {
			logger.error("CoordinatorToolException while creating/updating plan template with tool: {}", e.getMessage(),
					e);
			return ResponseEntity.badRequest()
				.body(Map.of("error", e.getMessage(), "errorCode", e.getErrorCode()));
		}
		catch (Exception e) {
			logger.error("Failed to create or update plan template with tool", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to create or update plan template with tool: " + e.getMessage()));
		}
	}

	/**
	 * Get all plan template configuration VOs
	 * @return List of PlanTemplateConfigVO containing plan template and tool configuration
	 */
	@GetMapping("/list-config")
	public ResponseEntity<List<PlanTemplateConfigVO>> getAllPlanTemplateConfigVOs() {
		try {
			logger.info("Getting all plan template configurations");

			// Get all plan templates
			List<PlanTemplate> templates = planTemplateService.getAllPlanTemplates();
			List<PlanTemplateConfigVO> configVOs = new ArrayList<>();

			for (PlanTemplate planTemplate : templates) {
				// Skip internal toolcall templates
				if (planTemplate.isInternalToolcall()) {
					continue;
				}

				String planTemplateId = planTemplate.getPlanTemplateId();

				// Get latest plan JSON version
				String planJson = planTemplateService.getLatestPlanVersion(planTemplateId);
				if (planJson == null || planJson.trim().isEmpty()) {
					logger.warn("Plan JSON not found for planTemplateId: {}, skipping", planTemplateId);
					continue;
				}

				try {
					// Parse plan JSON to PlanInterface
					PlanInterface planInterface = objectMapper.readValue(planJson, PlanInterface.class);

					// Create PlanTemplateConfigVO from plan template and plan JSON
					PlanTemplateConfigVO configVO = new PlanTemplateConfigVO();
					configVO.setPlanTemplateId(planTemplate.getPlanTemplateId());
					configVO.setTitle(planTemplate.getTitle());
					configVO.setPlanType(planInterface.getPlanType());
					configVO.setServiceGroup(planTemplate.getServiceGroup());
					configVO.setDirectResponse(planInterface.isDirectResponse());
					configVO.setReadOnly(false); // Default to false, can be set based on business logic
					// Set createTime and updateTime from PlanTemplate entity
					if (planTemplate.getCreateTime() != null) {
						configVO.setCreateTime(planTemplate.getCreateTime().toString());
					}
					if (planTemplate.getUpdateTime() != null) {
						configVO.setUpdateTime(planTemplate.getUpdateTime().toString());
					}

					// Convert ExecutionStep list to StepConfig list
					if (planInterface.getAllSteps() != null) {
						List<PlanTemplateConfigVO.StepConfig> stepConfigs = new ArrayList<>();
						for (ExecutionStep step : planInterface.getAllSteps()) {
							PlanTemplateConfigVO.StepConfig stepConfig = new PlanTemplateConfigVO.StepConfig();
							stepConfig.setStepRequirement(step.getStepRequirement());
							stepConfig.setAgentName(step.getAgentName());
							stepConfig.setModelName(step.getModelName());
							stepConfig.setTerminateColumns(step.getTerminateColumns());
							stepConfigs.add(stepConfig);
						}
						configVO.setSteps(stepConfigs);
					}

					// Get coordinator tool if exists to populate toolConfig
					Optional<CoordinatorToolVO> coordinatorToolOpt = coordinatorToolService
						.getCoordinatorToolByPlanTemplateId(planTemplateId);
					if (coordinatorToolOpt.isPresent()) {
						CoordinatorToolVO toolVO = coordinatorToolOpt.get();
						PlanTemplateConfigVO.ToolConfigVO toolConfig = new PlanTemplateConfigVO.ToolConfigVO();
						toolConfig.setToolName(toolVO.getToolName());
						toolConfig.setToolDescription(toolVO.getToolDescription());
						toolConfig.setServiceGroup(toolVO.getServiceGroup());
						toolConfig.setEnableInternalToolcall(toolVO.getEnableInternalToolcall());
						toolConfig.setEnableHttpService(toolVO.getEnableHttpService());
						toolConfig.setEnableMcpService(toolVO.getEnableMcpService());
						toolConfig.setPublishStatus(toolVO.getPublishStatus());

						// Parse inputSchema JSON string to InputSchemaParam list
						if (toolVO.getInputSchema() != null && !toolVO.getInputSchema().trim().isEmpty()) {
							try {
								com.fasterxml.jackson.databind.JsonNode inputSchemaNode = objectMapper
									.readTree(toolVO.getInputSchema());
								if (inputSchemaNode.isArray()) {
									List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams = new ArrayList<>();
									for (com.fasterxml.jackson.databind.JsonNode paramNode : inputSchemaNode) {
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
								logger.warn("Failed to parse inputSchema for planTemplateId: {}", planTemplateId, e);
								// Set empty list if parsing fails
								toolConfig.setInputSchema(new ArrayList<>());
							}
						}

						configVO.setToolConfig(toolConfig);
					}

					configVOs.add(configVO);
				}
				catch (Exception e) {
					logger.warn("Failed to process plan template {}: {}", planTemplateId, e.getMessage());
					// Continue processing other templates even if one fails
				}
			}

			logger.info("Successfully retrieved {} plan template configurations", configVOs.size());
			return ResponseEntity.ok(configVOs);

		}
		catch (Exception e) {
			logger.error("Failed to get all plan template configurations", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Get plan template configuration VO by plan template ID
	 * @param planTemplateId The plan template ID
	 * @return PlanTemplateConfigVO containing plan template and tool configuration
	 */
	@GetMapping("/{planTemplateId}/config")
	public ResponseEntity<PlanTemplateConfigVO> getPlanTemplateConfigVO(
			@PathVariable("planTemplateId") String planTemplateId) {
		try {
			if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			logger.info("Getting plan template configuration for planTemplateId: {}", planTemplateId);

			// Get plan template from database
			PlanTemplate planTemplate = planTemplateService.getPlanTemplate(planTemplateId);
			if (planTemplate == null) {
				logger.warn("Plan template not found for planTemplateId: {}", planTemplateId);
				return ResponseEntity.notFound().build();
			}

			// Get latest plan JSON version
			String planJson = planTemplateService.getLatestPlanVersion(planTemplateId);
			if (planJson == null || planJson.trim().isEmpty()) {
				logger.warn("Plan JSON not found for planTemplateId: {}", planTemplateId);
				return ResponseEntity.notFound().build();
			}

			// Parse plan JSON to PlanInterface
			PlanInterface planInterface = objectMapper.readValue(planJson, PlanInterface.class);

			// Create PlanTemplateConfigVO from plan template and plan JSON
			PlanTemplateConfigVO configVO = new PlanTemplateConfigVO();
			configVO.setPlanTemplateId(planTemplate.getPlanTemplateId());
			configVO.setTitle(planTemplate.getTitle());
			configVO.setPlanType(planInterface.getPlanType());
			configVO.setServiceGroup(planTemplate.getServiceGroup());
			configVO.setDirectResponse(planInterface.isDirectResponse());
			configVO.setReadOnly(false); // Default to false, can be set based on business logic
			// Set createTime and updateTime from PlanTemplate entity
			if (planTemplate.getCreateTime() != null) {
				configVO.setCreateTime(planTemplate.getCreateTime().toString());
			}
			if (planTemplate.getUpdateTime() != null) {
				configVO.setUpdateTime(planTemplate.getUpdateTime().toString());
			}

			// Convert ExecutionStep list to StepConfig list
			if (planInterface.getAllSteps() != null) {
				List<PlanTemplateConfigVO.StepConfig> stepConfigs = new ArrayList<>();
				for (ExecutionStep step : planInterface.getAllSteps()) {
					PlanTemplateConfigVO.StepConfig stepConfig = new PlanTemplateConfigVO.StepConfig();
					stepConfig.setStepRequirement(step.getStepRequirement());
					stepConfig.setAgentName(step.getAgentName());
					stepConfig.setModelName(step.getModelName());
					stepConfig.setTerminateColumns(step.getTerminateColumns());
					stepConfigs.add(stepConfig);
				}
				configVO.setSteps(stepConfigs);
			}

			// Get coordinator tool if exists to populate toolConfig
			Optional<CoordinatorToolVO> coordinatorToolOpt = coordinatorToolService
				.getCoordinatorToolByPlanTemplateId(planTemplateId);
			if (coordinatorToolOpt.isPresent()) {
				CoordinatorToolVO toolVO = coordinatorToolOpt.get();
				PlanTemplateConfigVO.ToolConfigVO toolConfig = new PlanTemplateConfigVO.ToolConfigVO();
				toolConfig.setToolName(toolVO.getToolName());
				toolConfig.setToolDescription(toolVO.getToolDescription());
				toolConfig.setEnableInternalToolcall(toolVO.getEnableInternalToolcall());
				toolConfig.setEnableHttpService(toolVO.getEnableHttpService());
				toolConfig.setEnableMcpService(toolVO.getEnableMcpService());
				toolConfig.setPublishStatus(toolVO.getPublishStatus());

				// Parse inputSchema JSON string to InputSchemaParam list
				if (toolVO.getInputSchema() != null && !toolVO.getInputSchema().trim().isEmpty()) {
					try {
						com.fasterxml.jackson.databind.JsonNode inputSchemaNode = objectMapper
							.readTree(toolVO.getInputSchema());
						if (inputSchemaNode.isArray()) {
							List<PlanTemplateConfigVO.InputSchemaParam> inputSchemaParams = new ArrayList<>();
							for (com.fasterxml.jackson.databind.JsonNode paramNode : inputSchemaNode) {
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
						logger.warn("Failed to parse inputSchema for planTemplateId: {}", planTemplateId, e);
						// Set empty list if parsing fails
						toolConfig.setInputSchema(new ArrayList<>());
					}
				}

				configVO.setToolConfig(toolConfig);
			}

			logger.info("Successfully retrieved plan template configuration for planTemplateId: {}", planTemplateId);
			return ResponseEntity.ok(configVO);

		}
		catch (Exception e) {
			logger.error("Failed to get plan template configuration for planTemplateId: {}", planTemplateId, e);
			return ResponseEntity.internalServerError().build();
		}
	}

}
