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

import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplateVersion;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateVersionRepository;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.DynamicAgentExecutionPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Optional;

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
	private PlanTemplateRepository planTemplateRepository;

	@Autowired
	private PlanTemplateVersionRepository planTemplateVersionRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${namespace.value}")
	private String namespace;

	/**
	 * Initialize plan templates for namespace with default language
	 * @param namespace Namespace
	 */
	public void initializePlanTemplatesForNamespace(String namespace) {
		String defaultLanguage = "en";
		initializePlanTemplatesForNamespaceWithLanguage(namespace, defaultLanguage);
	}

	/**
	 * Initialize plan templates for namespace with specific language
	 * @param namespace Namespace
	 * @param language Language code
	 */
	@Transactional
	public void initializePlanTemplatesForNamespaceWithLanguage(String namespace, String language) {
		try {
			log.info("Starting plan template initialization for namespace: {} with language: {}", namespace, language);

			// Get available plan names
			List<String> planNames = scanAvailablePlans();

			for (String planName : planNames) {
				try {
					String planTemplateId = initializePlanTemplate(planName, language);
					if (planTemplateId != null) {
						log.debug("Successfully initialized plan template: {} -> planTemplateId: {}", planName,
								planTemplateId);
					}
					else {
						log.warn("Failed to initialize plan template: {} - no planTemplateId returned", planName);
					}
				}
				catch (Exception e) {
					log.error("Failed to initialize plan template: {} for namespace: {} with language: {}", planName,
							namespace, language, e);
					// Continue with other plans even if one fails
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
	 * Reset all plan templates to specific language for a namespace
	 * @param namespace Namespace
	 * @param language Target language
	 */
	@Transactional
	public void resetAllPlanTemplatesToLanguage(String namespace, String language) {
		log.info("Resetting all plan templates to language: {} for namespace: {}", language, namespace);

		// Get all plan templates for this namespace
		List<PlanTemplate> existingTemplates = planTemplateRepository.findAll();

		// Delete all existing plan templates and their versions
		for (PlanTemplate template : existingTemplates) {
			List<PlanTemplateVersion> versions = planTemplateVersionRepository
				.findByPlanTemplateIdOrderByVersionIndexAsc(template.getPlanTemplateId());
			planTemplateVersionRepository.deleteAll(versions);
		}
		planTemplateRepository.deleteAll(existingTemplates);

		log.info("Deleted {} existing plan templates for namespace: {}", existingTemplates.size(), namespace);

		// Reinitialize with new language
		initializePlanTemplatesForNamespaceWithLanguage(namespace, language);

		log.info("Successfully reset all plan templates to language: {} for namespace: {}", language, namespace);
	}

	/**
	 * Initialize a specific plan template
	 * @param planName Plan name
	 * @param language Language code
	 * @return Plan template ID if successful, null if failed
	 */
	@Transactional
	public String initializePlanTemplate(String planName, String language) {
		try {
			log.debug("Initializing plan template: {} (language: {})", planName, language);

			// Load JSON configuration directly
			String planJson = loadPlanConfigJson(planName, language);
			if (planJson == null || planJson.trim().isEmpty()) {
				log.warn("No configuration found for plan: {} (language: {})", planName, language);
				return null;
			}

			// Create DynamicAgentExecutionPlan by reading the JSON directly
			DynamicAgentExecutionPlan executionPlan;
			try {
				executionPlan = objectMapper.readValue(planJson, DynamicAgentExecutionPlan.class);

				// Use the pre-defined planTemplateId from JSON
				String planTemplateId = executionPlan.getPlanTemplateId();
				if (planTemplateId == null || planTemplateId.trim().isEmpty()) {
					throw new RuntimeException(
							"planTemplateId is required in JSON configuration for plan: " + planName);
				}

				executionPlan.setUserRequest(String.format("Execute %s plan using %s language", planName, language));
			}
			catch (JsonProcessingException e) {
				log.error("Failed to convert JSON to DynamicAgentExecutionPlan: {}", planName, e);
				throw new RuntimeException("Failed to convert JSON to DynamicAgentExecutionPlan", e);
			}

			// Re-serialize with updated IDs
			try {
				planJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(executionPlan);
			}
			catch (JsonProcessingException e) {
				log.error("Failed to re-serialize execution plan to JSON: {}", planName, e);
				throw new RuntimeException("Failed to re-serialize execution plan to JSON", e);
			}

			// Save to plan template service
			String title = String.format("%s (%s)", executionPlan.getTitle(), language.toUpperCase());
			String userRequest = String.format("Execute %s plan using %s language", planName, language);

			savePlanTemplate(executionPlan.getPlanTemplateId(), title, userRequest, planJson, false);

			log.info("Successfully initialized plan template: {} (language: {}) -> planTemplateId: {}", planName,
					language, executionPlan.getPlanTemplateId());

			return executionPlan.getPlanTemplateId();

		}
		catch (Exception e) {
			log.error("Failed to initialize plan template: {} (language: {})", planName, language, e);
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
	 * Load plan template JSON configuration from file
	 * @param planName Plan name
	 * @param language Language code
	 * @return JSON content or null if not found
	 */
	private String loadPlanConfigJson(String planName, String language) {
		String configPath = buildConfigPath(planName, language);
		String configContent = loadConfigContent(configPath);

		if (configContent.isEmpty()) {
			log.warn("Plan template configuration file does not exist or is empty: {}", configPath);
			return null;
		}

		log.debug("Successfully loaded plan template JSON configuration: {} (language: {})", planName, language);
		return configContent;
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
	 * Load configuration content from file
	 * @param configPath Configuration file path
	 * @return Configuration content
	 */
	private String loadConfigContent(String configPath) {
		try {
			ClassPathResource resource = new ClassPathResource(configPath);
			if (!resource.exists()) {
				log.warn("Configuration file does not exist: {}", configPath);
				return "";
			}

			StringBuilder content = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					content.append(line).append("\n");
				}
			}

			log.debug("Successfully loaded configuration file: {} ({} characters)", configPath, content.length());
			return content.toString().trim();

		}
		catch (IOException e) {
			log.error("Failed to load configuration file: {}", configPath, e);
			return "";
		}
	}

	/**
	 * Save plan template to database
	 * @param planTemplateId Plan template ID
	 * @param title Title
	 * @param userRequest User request
	 * @param planJson Plan JSON
	 * @param isInternalToolcall Is internal toolcall
	 */
	@Transactional
	public void savePlanTemplate(String planTemplateId, String title, String userRequest, String planJson,
			boolean isInternalToolcall) {
		try {
			// Check if plan template already exists
			Optional<PlanTemplate> existingTemplateOpt = planTemplateRepository.findByPlanTemplateId(planTemplateId);
			PlanTemplate existingTemplate = existingTemplateOpt.orElse(null);

			if (existingTemplate == null) {
				// Create new plan template
				PlanTemplate template = new PlanTemplate(planTemplateId, title, userRequest, isInternalToolcall);
				planTemplateRepository.save(template);
				log.debug("Created new plan template: {}", planTemplateId);
			}
			else {
				// Update existing plan template
				existingTemplate.setTitle(title);
				existingTemplate.setUserRequest(userRequest);
				existingTemplate.setInternalToolcall(isInternalToolcall);
				planTemplateRepository.save(existingTemplate);
				log.debug("Updated existing plan template: {}", planTemplateId);
			}

			// Save to version history
			saveToVersionHistory(planTemplateId, planJson);

		}
		catch (Exception e) {
			log.error("Failed to save plan template: {}", planTemplateId, e);
			throw e;
		}
	}

	/**
	 * Save plan template version to history
	 * @param planTemplateId Plan template ID
	 * @param planJson Plan JSON
	 */
	@Transactional
	public void saveToVersionHistory(String planTemplateId, String planJson) {
		try {
			// Get max version index for this plan template
			Integer maxVersionIndex = planTemplateVersionRepository.findMaxVersionIndexByPlanTemplateId(planTemplateId);
			int nextVersionIndex = (maxVersionIndex != null) ? maxVersionIndex + 1 : 1;

			// Create new version
			PlanTemplateVersion version = new PlanTemplateVersion();
			version.setPlanTemplateId(planTemplateId);
			version.setVersionIndex(nextVersionIndex);
			version.setPlanJson(planJson);
			version.setCreateTime(java.time.LocalDateTime.now());

			planTemplateVersionRepository.save(version);
			log.debug("Saved plan template version: {} (version: {})", planTemplateId, nextVersionIndex);

		}
		catch (Exception e) {
			log.error("Failed to save plan template version: {}", planTemplateId, e);
			throw e;
		}
	}

	/**
	 * Get supported languages
	 * @return List of supported language codes
	 */
	public List<String> getSupportedLanguages() {
		return new ArrayList<>(SUPPORTED_LANGUAGES);
	}

	/**
	 * Check if a plan template exists for a specific language
	 * @param planName Plan name
	 * @param language Language code
	 * @return true if plan template exists, false otherwise
	 */
	public boolean planTemplateExists(String planName, String language) {
		String jsonContent = loadPlanConfigJson(planName, language);
		return jsonContent != null && !jsonContent.trim().isEmpty();
	}

	/**
	 * Initialize plan templates for a specific language (automatically finds all plan
	 * names)
	 * @param language Language code (en, zh)
	 * @return Initialization result
	 */
	public Map<String, Object> initializePlanTemplatesForLanguage(String language) {
		Map<String, Object> result = new HashMap<>();
		List<String> successList = new ArrayList<>();
		List<String> errorList = new ArrayList<>();
		Map<String, String> errors = new HashMap<>();

		log.info("Initializing plan templates for language: {}", language);

		// Validate language
		if (!SUPPORTED_LANGUAGES.contains(language)) {
			result.put("success", false);
			result.put("error", "Unsupported language: " + language + ". Supported: " + SUPPORTED_LANGUAGES);
			return result;
		}

		// Automatically find all plan names for the specified language
		List<String> planNames = scanAvailablePlansForLanguage(language);

		if (planNames.isEmpty()) {
			result.put("success", false);
			result.put("error", "No plan templates found for language: " + language);
			result.put("language", language);
			result.put("totalRequested", 0);
			result.put("successCount", 0);
			result.put("errorCount", 0);
			result.put("successList", successList);
			result.put("errorList", errorList);
			result.put("errors", errors);
			result.put("message", "No plan templates found for language: " + language);
			return result;
		}

		for (String planName : planNames) {
			try {
				String planTemplateId = initializePlanTemplate(planName, language);
				if (planTemplateId != null) {
					successList.add(planTemplateId);
					log.info("Successfully initialized plan template: {} (language: {}) -> planTemplateId: {}",
							planName, language, planTemplateId);
				}
				else {
					String error = "Failed to initialize plan template " + planName + ": No planTemplateId returned";
					errorList.add(planName);
					errors.put(planName, error);
					log.error(error);
				}
			}
			catch (Exception e) {
				String error = "Failed to initialize plan template " + planName + ": " + e.getMessage();
				errorList.add(planName);
				errors.put(planName, error);
				log.error(error, e);
			}
		}

		result.put("success", errorList.isEmpty());
		result.put("language", language);
		result.put("totalRequested", planNames.size());
		result.put("successCount", successList.size());
		result.put("errorCount", errorList.size());
		result.put("successList", successList);
		result.put("errorList", errorList);
		result.put("errors", errors);
		result.put("message", String.format("Initialized %d out of %d plan templates for language %s",
				successList.size(), planNames.size(), language));

		return result;
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

}
