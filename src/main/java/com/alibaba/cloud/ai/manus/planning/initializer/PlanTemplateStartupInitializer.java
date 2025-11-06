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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.coordinator.entity.vo.PlanTemplateConfigVO;
import com.alibaba.cloud.ai.manus.coordinator.exception.CoordinatorToolException;
import com.alibaba.cloud.ai.manus.coordinator.service.CoordinatorToolServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Startup initializer for plan templates from startup-plans directory Also registers
 * default plan templates as coordinator tools (internal toolcalls)
 */
@Component
public class PlanTemplateStartupInitializer {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplateStartupInitializer.class);

	private static final String CONFIG_BASE_PATH = "prompts/startup-plans/";

	private static final String DEFAULT_LANGUAGE = "en";

	@Autowired
	private CoordinatorToolServiceImpl coordinatorToolService;

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
			// Register all plan templates with toolConfig as coordinator tools
			// This will also create PlanTemplate if it doesn't exist
			registerPlanTemplatesAsTools();

		}
		catch (Exception e) {
			log.error("Failed to initialize startup plan templates for namespace: {}", namespace, e);
		}
	}

	/**
	 * Register all plan templates with toolConfig as coordinator tools Scans all JSON
	 * files in startup-plans directory and registers those with toolConfig
	 */
	private void registerPlanTemplatesAsTools() {
		log.info("Starting registration of plan templates as coordinator tools");

		int successCount = 0;
		int errorCount = 0;

		// Scan for all JSON files in startup-plans directory
		List<String> configFilePaths = scanPlanTemplateConfigFiles();

		if (configFilePaths.isEmpty()) {
			log.info("No plan template configuration files found to register as coordinator tools");
			return;
		}

		log.info("Found {} plan template configuration files to process", configFilePaths.size());

		// Process each configuration file
		for (String configPath : configFilePaths) {
			try {
				// Load and parse PlanTemplateConfigVO from JSON file
				PlanTemplateConfigVO configVO = loadPlanTemplateConfigFromFile(configPath);
				if (configVO == null) {
					log.warn("Failed to load PlanTemplateConfigVO from file: {}. Skipping.", configPath);
					errorCount++;
					continue;
				}

				// Only register if toolConfig is present
				if (configVO.getToolConfig() == null) {
					log.debug("Plan template {} does not have toolConfig, skipping coordinator tool registration",
							configVO.getPlanTemplateId());
					continue;
				}

				// Validate planTemplateId
				String planTemplateId = configVO.getPlanTemplateId();
				if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
					log.warn("Plan template in file {} does not have planTemplateId. Skipping.", configPath);
					errorCount++;
					continue;
				}

				// Use the service method to create or update coordinator tool
				coordinatorToolService.createOrUpdateCoordinatorToolFromPlanTemplateConfig(configVO);
				log.info("Successfully registered coordinator tool for plan template: {} from file: {}", planTemplateId,
						configPath);
				successCount++;

			}
			catch (CoordinatorToolException e) {
				log.error("Failed to register coordinator tool from file {}: {}", configPath, e.getMessage(), e);
				errorCount++;
			}
			catch (Exception e) {
				log.error("Unexpected error while registering coordinator tool from file {}", configPath, e);
				errorCount++;
			}
		}

		log.info("Completed registration of plan templates as coordinator tools. Success: {}, Errors: {}", successCount,
				errorCount);
	}

	/**
	 * Scan for all plan template configuration files in startup-plans directory
	 * @return List of configuration file paths
	 */
	private List<String> scanPlanTemplateConfigFiles() {
		List<String> configFilePaths = new ArrayList<>();

		try {
			PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

			// Scan for JSON files in the default language directory only
			String pattern = CONFIG_BASE_PATH + DEFAULT_LANGUAGE + "/*.json";
			try {
				Resource[] resources = resolver.getResources("classpath:" + pattern);
				for (Resource resource : resources) {
					if (resource.exists() && resource.isReadable()) {
						String path = CONFIG_BASE_PATH + DEFAULT_LANGUAGE + "/" + resource.getFilename();
						configFilePaths.add(path);
						log.debug("Found plan template configuration file: {}", path);
					}
				}
			}
			catch (Exception ex) {
				log.debug("No resources found for pattern: {}", pattern);
			}

			log.info("Scanned {} plan template configuration files", configFilePaths.size());
			return configFilePaths;

		}
		catch (Exception e) {
			log.error("Failed to scan plan template configuration directory", e);
			return configFilePaths;
		}
	}

	/**
	 * Load PlanTemplateConfigVO from JSON configuration file
	 * @param configPath Configuration file path
	 * @return PlanTemplateConfigVO if loaded successfully, null otherwise
	 */
	private PlanTemplateConfigVO loadPlanTemplateConfigFromFile(String configPath) {
		try {
			org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource(
					configPath);
			if (!resource.exists()) {
				log.warn("Plan template configuration file does not exist: {}", configPath);
				return null;
			}

			StringBuilder content = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					content.append(line).append("\n");
				}
			}

			String jsonContent = content.toString().trim();
			if (jsonContent.isEmpty()) {
				log.warn("Plan template configuration file is empty: {}", configPath);
				return null;
			}

			// Parse JSON to PlanTemplateConfigVO
			PlanTemplateConfigVO configVO = objectMapper.readValue(jsonContent, PlanTemplateConfigVO.class);
			log.debug("Successfully loaded PlanTemplateConfigVO from file: {}", configPath);
			return configVO;

		}
		catch (IOException e) {
			log.error("Failed to load plan template configuration file: {}", configPath, e);
			return null;
		}
		catch (Exception e) {
			log.error("Failed to parse PlanTemplateConfigVO from file: {}", configPath, e);
			return null;
		}
	}

}
