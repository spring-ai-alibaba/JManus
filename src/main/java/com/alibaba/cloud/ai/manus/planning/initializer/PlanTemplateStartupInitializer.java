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
package com.alibaba.cloud.ai.manus.planning.initializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.coordinator.entity.vo.CoordinatorToolVO;
import com.alibaba.cloud.ai.manus.coordinator.exception.CoordinatorToolException;
import com.alibaba.cloud.ai.manus.coordinator.service.CoordinatorToolServiceImpl;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;
import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateInitializationService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Startup initializer for plan templates from startup-plans directory Also registers
 * default plan templates as coordinator tools (internal toolcalls)
 */
@Component
public class PlanTemplateStartupInitializer {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplateStartupInitializer.class);

	private static final String DEFAULT_PLAN_TEMPLATE_ID = "default-plan-id-001000222";

	private static final int MAX_RETRIES = 5;

	private static final long RETRY_DELAY_MS = 1000;

	@Autowired
	private PlanTemplateInitializationService planTemplateInitializationService;

	@Autowired
	private PlanTemplateRepository planTemplateRepository;

	@Autowired
	private CoordinatorToolServiceImpl coordinatorToolService;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${namespace.value}")
	private String namespace;

	/**
	 * Initialize startup plan templates when application is ready
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initializeStartupPlanTemplates() {
		log.info("Starting startup plan templates initialization for namespace: {}", namespace);

		try {
			// Step 1: Initialize plan templates for the current namespace
			planTemplateInitializationService.initializePlanTemplatesForNamespace(namespace);
			log.info("Successfully initialized startup plan templates for namespace: {}", namespace);

			// Step 2: Register default plan template as coordinator tool (internal
			// toolcall)
			registerDefaultPlanTemplateAsTool();

		}
		catch (Exception e) {
			log.error("Failed to initialize startup plan templates for namespace: {}", namespace, e);
		}
	}

	/**
	 * Register default plan template as coordinator tool (internal toolcall) Uses
	 * createCoordinatorTool() if tool doesn't exist, or updateCoordinatorTool() if it
	 * exists
	 */
	private void registerDefaultPlanTemplateAsTool() {
		log.info("Starting registration of default plan template as coordinator tool: {}", DEFAULT_PLAN_TEMPLATE_ID);

		try {
			// Wait for plan template to be available (with retry mechanism)
			Optional<PlanTemplate> templateOpt = waitForPlanTemplate(DEFAULT_PLAN_TEMPLATE_ID);

			if (templateOpt.isEmpty()) {
				log.warn(
						"Default plan template {} not found after {} retries. Registration will be skipped. Make sure the plan template is initialized.",
						DEFAULT_PLAN_TEMPLATE_ID, MAX_RETRIES);
				return;
			}

			PlanTemplate planTemplate = templateOpt.get();

			// Check if coordinator tool already exists
			Optional<CoordinatorToolVO> existingToolOpt = coordinatorToolService
				.getCoordinatorToolByPlanTemplateId(DEFAULT_PLAN_TEMPLATE_ID);

			// Create CoordinatorToolVO for the plan template
			CoordinatorToolVO coordinatorToolVO = new CoordinatorToolVO();
			coordinatorToolVO.setToolName("tool_" + planTemplate.getTitle());
			coordinatorToolVO.setToolDescription(planTemplate.getUserRequest());
			coordinatorToolVO.setPlanTemplateId(planTemplate.getPlanTemplateId());
			coordinatorToolVO.setServiceGroup("inited-toolcall");

			// Enable internal toolcall service
			coordinatorToolVO.setEnableInternalToolcall(true);
			coordinatorToolVO.setEnableHttpService(false);
			coordinatorToolVO.setEnableMcpService(false);

			// Generate input schema from plan template parameters
			String inputSchema = generateInputSchemaFromPlanTemplate(planTemplate.getPlanTemplateId());
			coordinatorToolVO.setInputSchema(inputSchema);
			log.debug("Generated inputSchema for plan template {}: {}", DEFAULT_PLAN_TEMPLATE_ID, inputSchema);

			coordinatorToolVO.setPublishStatus("PUBLISHED");

			CoordinatorToolVO savedTool;
			if (existingToolOpt.isPresent()) {
				// Tool exists, update it
				Long toolId = existingToolOpt.get().getId();
				log.info("Coordinator tool already exists for plan template {}, updating with ID: {}",
						DEFAULT_PLAN_TEMPLATE_ID, toolId);
				savedTool = coordinatorToolService.updateCoordinatorTool(toolId, coordinatorToolVO);
				log.info("Successfully updated coordinator tool for plan template: {}", DEFAULT_PLAN_TEMPLATE_ID);
			}
			else {
				// Tool doesn't exist, create it
				log.info("Creating new coordinator tool for plan template: {}", DEFAULT_PLAN_TEMPLATE_ID);
				savedTool = coordinatorToolService.createCoordinatorTool(coordinatorToolVO);
				log.info("Successfully created coordinator tool for plan template: {} with ID: {}",
						DEFAULT_PLAN_TEMPLATE_ID, savedTool.getId());
			}

		}
		catch (CoordinatorToolException e) {
			log.error("Failed to register default plan template {} as coordinator tool: {}", DEFAULT_PLAN_TEMPLATE_ID,
					e.getMessage(), e);
		}
		catch (Exception e) {
			log.error("Failed to register default plan template {} as coordinator tool", DEFAULT_PLAN_TEMPLATE_ID, e);
		}
	}

	/**
	 * Wait for plan template to be available in database with retry mechanism
	 * @param planTemplateId Plan template ID to wait for
	 * @return Optional containing the plan template if found, empty otherwise
	 */
	private Optional<PlanTemplate> waitForPlanTemplate(String planTemplateId) {
		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			Optional<PlanTemplate> templateOpt = planTemplateRepository.findByPlanTemplateId(planTemplateId);

			if (templateOpt.isPresent()) {
				log.debug("Found plan template {} on attempt {}", planTemplateId, attempt);
				return templateOpt;
			}

			if (attempt < MAX_RETRIES) {
				log.debug("Plan template {} not found, retrying in {}ms (attempt {}/{})", planTemplateId,
						RETRY_DELAY_MS, attempt, MAX_RETRIES);
				try {
					Thread.sleep(RETRY_DELAY_MS);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.warn("Interrupted while waiting for plan template {}", planTemplateId);
					return Optional.empty();
				}
			}
		}

		return Optional.empty();
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

}
