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
package com.alibaba.cloud.ai.manus.agent.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.manus.agent.entity.DynamicAgentEntity;
import com.alibaba.cloud.ai.manus.agent.model.enums.AgentEnum;
 

/**
 * Agent management REST API controller
 */
@RestController
@RequestMapping("/api/agent-management")
@CrossOrigin(origins = "*")
public class AgentManagementController {

	private static final Logger logger = LoggerFactory.getLogger(AgentManagementController.class);

 

    // No repository anymore; agents are provided by code

	@Value("${namespace.value}")
	private String namespace;

	/**
	 * Get all agents for current namespace
	 */
	@GetMapping
    public ResponseEntity<List<DynamicAgentEntity>> getAllAgents() {
		try {
            return ResponseEntity.ok(java.util.List.of(createDefaultAgent()));
		}
		catch (Exception e) {
			logger.error("Error getting all agents", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Get supported languages
	 */
	@GetMapping("/languages")
	public ResponseEntity<Map<String, Object>> getSupportedLanguages() {
		try {
			String[] languages = AgentEnum.getSupportedLanguages();
			return ResponseEntity.ok(Map.of("languages", languages, "default", "en"));
		}
		catch (Exception e) {
			logger.error("Error getting supported languages", e);
			return ResponseEntity.internalServerError().build();
		}
	}

	/**
	 * Reset all agents to specific language
	 */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetAllAgents(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(Map.of("message", "No operation. Reset is disabled.", "namespace", namespace));
    }

	/**
	 * Initialize agents for specific language (used during initial setup)
	 */
    @PostMapping("/initialize")
    public ResponseEntity<Map<String, String>> initializeAgents(@RequestBody Map<String, String> request) {
        // Create DEFAULT_AGENT by code (non-persistent)
        DynamicAgentEntity agent = createDefaultAgent();
        logger.info("Initialized DEFAULT_AGENT for namespace: {}", namespace);
        return ResponseEntity.ok(Map.of(
                "message", "Initialized DEFAULT_AGENT (in-memory)",
                "agentName", agent.getAgentName(),
                "namespace", namespace
        ));
    }

	/**
	 * Get agent statistics
	 */
	@GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAgentStats() {
        return ResponseEntity.ok(Map.of("message", "No operation. Stats disabled.", "namespace", namespace));
    }

    private DynamicAgentEntity createDefaultAgent() {
        DynamicAgentEntity entity = new DynamicAgentEntity();
        entity.setAgentName("DEFAULT_AGENT");
        entity.setNamespace(namespace);
        entity.setClassName("");
        entity.setAgentDescription("Default agent created by code");
        entity.setNextStepPrompt("Default next step prompt");
        entity.setAvailableToolKeys(java.util.List.of());
        return entity;
    }

}
