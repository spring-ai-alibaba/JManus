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
package com.alibaba.cloud.ai.lynxe.tool.i18n;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import com.alibaba.cloud.ai.lynxe.user.service.UserService;

/**
 * Service for loading tool internationalization (i18n) content from YAML files Loads tool
 * descriptions and parameters based on current user language preference
 */
@Service
public class ToolI18nService {

	private static final Logger logger = LoggerFactory.getLogger(ToolI18nService.class);

	private static final String I18N_BASE_PATH = "i18n/tools/";

	private static final long CACHE_EXPIRY_SECONDS = 10;

	private final UserService userService;

	private final Yaml yaml;

	// Cache for loaded YAML content with expiration (10 seconds)
	private final Map<String, CacheEntry> yamlCache = new ConcurrentHashMap<>();

	/**
	 * Cache entry that stores content and timestamp
	 */
	private static class CacheEntry {

		private final Map<String, Object> content;

		private final long timestamp;

		public CacheEntry(Map<String, Object> content) {
			this.content = content;
			this.timestamp = System.currentTimeMillis();
		}

		public Map<String, Object> getContent() {
			return content;
		}

		public boolean isExpired(long expiryMillis) {
			return (System.currentTimeMillis() - timestamp) > expiryMillis;
		}

	}

	public ToolI18nService(UserService userService) {
		this.userService = userService;
		this.yaml = new Yaml();
	}

	/**
	 * Get tool description in current user's language
	 * @param toolName Tool name (e.g., "file-splitter-tool")
	 * @return Description string, or empty string if not found
	 */
	public String getDescription(String toolName) {
		try {
			Map<String, Object> content = loadToolContent(toolName);
			if (content != null && content.containsKey("description")) {
				Object description = content.get("description");
				return description != null ? description.toString() : "";
			}
			logger.warn("Description not found for tool: {}", toolName);
			return "";
		}
		catch (Exception e) {
			logger.error("Error getting description for tool: {}", toolName, e);
			return "";
		}
	}

	/**
	 * Get tool parameters JSON schema in current user's language
	 * @param toolName Tool name (e.g., "file-splitter-tool")
	 * @return Parameters JSON string, or empty string if not found
	 */
	public String getParameters(String toolName) {
		try {
			Map<String, Object> content = loadToolContent(toolName);
			if (content != null && content.containsKey("parameters")) {
				Object parameters = content.get("parameters");
				return parameters != null ? parameters.toString() : "";
			}
			logger.warn("Parameters not found for tool: {}", toolName);
			return "";
		}
		catch (Exception e) {
			logger.error("Error getting parameters for tool: {}", toolName, e);
			return "";
		}
	}

	/**
	 * Load tool content from YAML file based on current language
	 * @param toolName Tool name (e.g., "file-splitter-tool")
	 * @return Map containing description and parameters, or null if not found
	 */
	private Map<String, Object> loadToolContent(String toolName) {
		// Get current language from UserService
		String language = userService.getLanguage();
		if (language == null || language.trim().isEmpty()) {
			language = "zh"; // Default to Chinese
		}

		// Build cache key
		String cacheKey = toolName + "-" + language;

		// Check cache first
		CacheEntry cachedEntry = yamlCache.get(cacheKey);
		if (cachedEntry != null) {
			// Check if cache entry is expired (10 seconds)
			long expiryMillis = CACHE_EXPIRY_SECONDS * 1000;
			if (!cachedEntry.isExpired(expiryMillis)) {
				logger.debug("Using cached content for tool: {} (language: {})", toolName, language);
				return cachedEntry.getContent();
			}
			else {
				// Remove expired entry
				yamlCache.remove(cacheKey);
				logger.debug("Cache entry expired for tool: {} (language: {}), reloading", toolName, language);
			}
		}

		// Try to load for requested language
		Map<String, Object> content = loadYamlFile(toolName, language);
		if (content != null) {
			yamlCache.put(cacheKey, new CacheEntry(content));
			return content;
		}

		// Fallback to Chinese if requested language not found
		if (!"zh".equals(language)) {
			logger.warn("YAML file not found for tool: {} (language: {}), falling back to zh", toolName, language);
			String fallbackCacheKey = toolName + "-zh";
			CacheEntry fallbackEntry = yamlCache.get(fallbackCacheKey);
			if (fallbackEntry != null) {
				long expiryMillis = CACHE_EXPIRY_SECONDS * 1000;
				if (!fallbackEntry.isExpired(expiryMillis)) {
					logger.debug("Using cached fallback content for tool: {} (language: zh)", toolName);
					return fallbackEntry.getContent();
				}
				else {
					yamlCache.remove(fallbackCacheKey);
				}
			}
			content = loadYamlFile(toolName, "zh");
			if (content != null) {
				yamlCache.put(fallbackCacheKey, new CacheEntry(content));
				return content;
			}
		}

		logger.error("YAML file not found for tool: {} in any language", toolName);
		return null;
	}

	/**
	 * Load YAML file for specific tool and language
	 * @param toolName Tool name
	 * @param language Language code ("zh" or "en")
	 * @return Parsed YAML content as Map, or null if file not found
	 */
	private Map<String, Object> loadYamlFile(String toolName, String language) {
		String resourcePath = I18N_BASE_PATH + toolName + "-" + language + ".yml";
		try {
			ClassPathResource resource = new ClassPathResource(resourcePath);
			if (!resource.exists()) {
				logger.debug("YAML resource not found: {}", resourcePath);
				return null;
			}

			try (InputStream inputStream = resource.getInputStream()) {
				Map<String, Object> content = yaml.loadAs(inputStream, Map.class);
				logger.debug("Successfully loaded YAML file: {}", resourcePath);
				return content;
			}
		}
		catch (Exception e) {
			logger.error("Error loading YAML file: {}", resourcePath, e);
			return null;
		}
	}

	/**
	 * Clear the YAML cache (useful for testing or reloading content)
	 */
	public void clearCache() {
		yamlCache.clear();
		logger.debug("YAML cache cleared");
	}

}
