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

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.manus.planning.service.PlanTemplatePublishService;

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
