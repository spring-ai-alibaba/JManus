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
package com.alibaba.cloud.ai.lynxe.planning.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.lynxe.planning.exception.PlanTemplateConfigException;
import com.alibaba.cloud.ai.lynxe.planning.model.vo.PlanTemplateConfigVO;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Plan template initialization service for managing plan template configurations with
 * multi-language support
 */
@Service
public class PlanTemplateInitializationService {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplateInitializationService.class);

	private static final String CONFIG_BASE_PATH = "prompts/startup-plans/";

	private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("en", "zh");

	@Autowired
	private PlanTemplateConfigService planTemplateConfigService;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${namespace.value}")
	private String namespace;

	/**
	 * Initialize plan templates for namespace with default language
	 * @param namespace Namespace
	 */
	public void initializePlanTemplatesForNamespace(String namespace) {
		String defaultLanguage = "zh";
		initializePlanTemplatesForNamespaceWithLanguage(namespace, defaultLanguage);
	}

	/**
	 * Initialize plan templates for namespace with specific language Only processes
	 * templates with toolConfig, throws error if toolConfig is missing
	 * @param namespace Namespace
	 * @param language Language code
	 */
	@Transactional
	public void initializePlanTemplatesForNamespaceWithLanguage(String namespace, String language) {
		try {
			log.info("Starting plan template initialization for namespace: {} with language: {}", namespace, language);

			// Get available plan names for the specific language only
			List<String> planNames = scanAvailablePlansForLanguage(language);

			for (String planName : planNames) {
				try {
					// Load and parse PlanTemplateConfigVO from JSON file
					String configPath = buildConfigPath(planName, language);
					PlanTemplateConfigVO configVO = loadPlanTemplateConfigFromFile(configPath);
					if (configVO == null) {
						log.warn("Failed to load PlanTemplateConfigVO from file: {}. Skipping.", configPath);
						continue;
					}

					// Validate planTemplateId
					String planTemplateId = configVO.getPlanTemplateId();
					if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
						log.warn("Plan template in file {} does not have planTemplateId. Skipping.", configPath);
						continue;
					}

					// Only process templates with toolConfig, throw error if missing
					if (configVO.getToolConfig() == null) {
						throw new RuntimeException("Plan template " + planName + " (planTemplateId: " + planTemplateId
								+ ") does not have toolConfig. toolConfig is required for startup initialization.");
					}

					// Create or update coordinator tool (this will also create
					// PlanTemplate if it doesn't exist)
					try {
						planTemplateConfigService.createOrUpdateCoordinatorToolFromPlanTemplateConfig(configVO);
						log.info(
								"Successfully initialized plan template and coordinator tool: {} -> planTemplateId: {}",
								planName, planTemplateId);
					}
					catch (PlanTemplateConfigException e) {
						log.error(
								"Failed to create or update coordinator tool for plan template: {} (planTemplateId: {})",
								planName, planTemplateId, e);
						throw new RuntimeException("Failed to create or update coordinator tool: " + e.getMessage(), e);
					}
				}
				catch (Exception e) {
					log.error("Failed to initialize plan template: {} for namespace: {} with language: {}", planName,
							namespace, language, e);
					throw e; // Throw error instead of continuing
				}
			}

			log.info("Completed plan template initialization for namespace: {} with language: {}", namespace, language);
		}
		catch (Exception e) {
			log.error("Failed to initialize plan templates for namespace: {} with language: {}", namespace, language,
					e);
			throw e;
		}
	}

	/**
	 * Scan all available plan template configuration directories
	 * @return List of plan names
	 */
	public List<String> scanAvailablePlans() {
		try {
			List<String> planList = new ArrayList<>();

			// Scan for *.json files
			for (String language : SUPPORTED_LANGUAGES) {
				String pattern = CONFIG_BASE_PATH + language + "/*.json";
				try {
					// Use Spring's resource pattern resolver
					org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
					org.springframework.core.io.Resource[] springResources = resolver
						.getResources("classpath:" + pattern);
					for (org.springframework.core.io.Resource resource : springResources) {
						String path = resource.getURL().getPath();
						String[] pathParts = path.split("/");
						for (int i = 0; i < pathParts.length - 1; i++) {
							if (language.equals(pathParts[i]) && i + 1 < pathParts.length) {
								String fileName = pathParts[i + 1];
								// Extract plan name from filename (remove .json
								// extension)
								if (fileName.endsWith(".json")) {
									String planName = fileName.substring(0, fileName.length() - 5);
									if (!planList.contains(planName)) {
										planList.add(planName);
										log.debug("Found plan template: {}", planName);
									}
								}
								break;
							}
						}
					}
				}
				catch (Exception ex) {
					log.debug("No resources found for pattern: {}", pattern);
				}
			}

			log.info("Scanned {} plan template configurations: {}", planList.size(), planList);
			return planList;
		}
		catch (Exception e) {
			log.error("Failed to scan plan template configuration directory", e);
			return List.of();
		}
	}

	/**
	 * Build configuration file path
	 * @param planName Plan name
	 * @param language Language code
	 * @return Configuration file path
	 */
	private String buildConfigPath(String planName, String language) {
		return CONFIG_BASE_PATH + language + "/" + planName + ".json";
	}

	/**
	 * Get supported languages
	 * @return List of supported language codes
	 */
	public List<String> getSupportedLanguages() {
		return new ArrayList<>(SUPPORTED_LANGUAGES);
	}

	/**
	 * Scan available plan template configuration files for a specific language
	 * @param language Language code
	 * @return List of plan names for the specified language
	 */
	public List<String> scanAvailablePlansForLanguage(String language) {
		try {
			List<String> planList = new ArrayList<>();
			String pattern = CONFIG_BASE_PATH + language + "/*.json";

			// Use Spring's resource pattern resolver
			org.springframework.core.io.support.PathMatchingResourcePatternResolver resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
			org.springframework.core.io.Resource[] springResources = resolver.getResources("classpath:" + pattern);

			for (org.springframework.core.io.Resource resource : springResources) {
				String path = resource.getURL().getPath();
				String[] pathParts = path.split("/");
				for (int i = 0; i < pathParts.length - 1; i++) {
					if (language.equals(pathParts[i]) && i + 1 < pathParts.length) {
						String fileName = pathParts[i + 1];
						// Extract plan name from filename (remove .json extension)
						if (fileName.endsWith(".json")) {
							String planName = fileName.substring(0, fileName.length() - 5);
							if (!planList.contains(planName)) {
								planList.add(planName);
								log.debug("Found plan template: {} for language: {}", planName, language);
							}
						}
						break;
					}
				}
			}

			log.info("Scanned {} plan template configurations for language {}: {}", planList.size(), language,
					planList);
			return planList;
		}
		catch (Exception e) {
			log.error("Failed to scan plan template configuration directory for language: {}", language, e);
			return List.of();
		}
	}

	/**
	 * Load PlanTemplateConfigVO from JSON configuration file
	 * @param configPath Configuration file path
	 * @return PlanTemplateConfigVO if loaded successfully, null otherwise
	 */
	private PlanTemplateConfigVO loadPlanTemplateConfigFromFile(String configPath) {
		try {
			ClassPathResource resource = new ClassPathResource(configPath);
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
