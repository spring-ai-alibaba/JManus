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

import com.alibaba.cloud.ai.manus.planning.service.PlanTemplatePublishService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for publishing plan templates as inner toolcalls
 */
@RestController
@RequestMapping("/api/plan-template-publish")
@CrossOrigin(origins = "*")
public class PlanTemplatePublishController {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplatePublishController.class);

	@Autowired
	private PlanTemplatePublishService planTemplatePublishService;

	@Autowired
	private PlanTemplateInitializationService planTemplateInitializationService;

	/**
	 * Initialize and register plan templates as inner toolcalls
	 * @param request Request containing language (plan names are automatically
	 * discovered)
	 * @return Initialization and registration result
	 */
	@PostMapping("/init-and-register")
	public ResponseEntity<Map<String, Object>> initAndRegisterPlanTemplates(@RequestBody Map<String, Object> request) {
		try {
			String language = (String) request.get("language");

			if (language == null || language.trim().isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "Language is required"));
			}

			log.info("Initializing and registering plan templates for language: {}", language);

			// Step 1: Initialize plan templates (automatically finds all plan names)
			Map<String, Object> initResult = planTemplateInitializationService
				.initializePlanTemplatesForLanguage(language);

			// Extract plan names from init result for registration
			@SuppressWarnings("unchecked")
			List<String> planNames = (List<String>) initResult.get("successList");

			// Step 2: Register initialized plan templates as inner toolcalls
			Map<String, Object> registerResult = planTemplatePublishService.registerPlanTemplatesAsToolcalls(planNames);

			// Combine results
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("language", language);
			response.put("planNames", planNames);
			response.put("initResult", initResult);
			response.put("registerResult", registerResult);
			response.put("message", "Plan templates initialized and registered successfully");

			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			log.error("Failed to initialize and register plan templates", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to initialize and register plan templates: " + e.getMessage()));
		}
	}

	/**
	 * Register specific plan templates as inner toolcalls
	 * @param request Request containing plan names
	 * @return Registration result
	 */
	@PostMapping("/register")
	public ResponseEntity<Map<String, Object>> registerPlanTemplates(@RequestBody Map<String, Object> request) {
		try {
			@SuppressWarnings("unchecked")
			List<String> planNames = (List<String>) request.get("planNames");

			if (planNames == null || planNames.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "Plan names are required"));
			}

			log.info("Registering plan templates as inner toolcalls: {}", planNames);

			Map<String, Object> result = planTemplatePublishService.registerPlanTemplatesAsToolcalls(planNames);

			return ResponseEntity.ok(result);

		}
		catch (Exception e) {
			log.error("Failed to register plan templates", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to register plan templates: " + e.getMessage()));
		}
	}

	/**
	 * Unregister plan templates from inner toolcalls
	 * @param request Request containing plan names
	 * @return Unregistration result
	 */
	@PostMapping("/unregister")
	public ResponseEntity<Map<String, Object>> unregisterPlanTemplates(@RequestBody Map<String, Object> request) {
		try {
			@SuppressWarnings("unchecked")
			List<String> planNames = (List<String>) request.get("planNames");

			if (planNames == null || planNames.isEmpty()) {
				return ResponseEntity.badRequest().body(Map.of("error", "Plan names are required"));
			}

			log.info("Unregistering plan templates from inner toolcalls: {}", planNames);

			Map<String, Object> result = planTemplatePublishService.unregisterPlanTemplatesAsToolcalls(planNames);

			return ResponseEntity.ok(result);

		}
		catch (Exception e) {
			log.error("Failed to unregister plan templates", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to unregister plan templates: " + e.getMessage()));
		}
	}

	/**
	 * Get status of plan template registrations
	 * @return Registration status
	 */
	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> getRegistrationStatus() {
		try {
			Map<String, Object> status = planTemplatePublishService.getRegistrationStatus();
			return ResponseEntity.ok(status);
		}
		catch (Exception e) {
			log.error("Failed to get registration status", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to get registration status: " + e.getMessage()));
		}
	}

	/**
	 * Get all registered plan templates
	 * @return List of registered plan templates
	 */
	@GetMapping("/registered")
	public ResponseEntity<Map<String, Object>> getRegisteredPlanTemplates() {
		try {
			Map<String, Object> result = planTemplatePublishService.getRegisteredPlanTemplates();
			return ResponseEntity.ok(result);
		}
		catch (Exception e) {
			log.error("Failed to get registered plan templates", e);
			return ResponseEntity.internalServerError()
				.body(Map.of("error", "Failed to get registered plan templates: " + e.getMessage()));
		}
	}

}
