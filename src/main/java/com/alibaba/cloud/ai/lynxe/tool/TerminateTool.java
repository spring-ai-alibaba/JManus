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
package com.alibaba.cloud.ai.lynxe.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.config.IManusProperties;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.shortUrl.ShortUrlService;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TerminateTool extends AbstractBaseTool<Map<String, Object>> implements TerminableTool {

	private static final Logger log = LoggerFactory.getLogger(TerminateTool.class);

	public static final String name = "terminate";

	private final String expectedReturnInfo;


	private final ObjectMapper objectMapper;

	private final ShortUrlService shortUrlService;

	private final IManusProperties manusProperties;

	private static String getDescriptions(String expectedReturnInfo) {
		// Simple description to avoid generating overly long content
		return "Terminate the current execution step with structured data. "
				+ "Provide data in JSON format with 'message' field.";
	}

	private static String generateMessageField(String expectedReturnInfo) {
		// Check if expectedReturnInfo is not null and not empty
		if (expectedReturnInfo != null && !expectedReturnInfo.trim().isEmpty()) {
			// Generate JSON list structure for specific return info
			// Support both English comma (,) and Chinese comma (，) as separators
			String[] columns = expectedReturnInfo.split("[,，]");
			String exampleJson = generateExampleJson(columns);

			return String.format(
					"""
							"message": {
							  "type": "array",
							  "items": {
							    "type": "object",
							    "properties": {
							      %s
							    }
							  },
							  "description": "Comprehensive termination message that should include all relevant facts, viewpoints, details, and conclusions from the execution step. This message should provide a complete summary of what was accomplished, any important observations, key findings, and final outcomes. The message must be returned as a JSON array containing objects with the following columns: %s. Example format: %s"
							}""",
					generateColumnProperties(columns), expectedReturnInfo, exampleJson);
		}
		else {
			// Default string type for empty or null expectedReturnInfo
			return """
					"message": {
					  "type": "string",
					  "description": "Comprehensive termination message that should include all relevant facts, viewpoints, details, and conclusions from the execution step. This message should provide a complete summary of what was accomplished, any important observations, key findings, and final outcomes."
					}""";
		}
	}

	private static String generateExampleJson(String[] columns) {
		StringBuilder exampleJson = new StringBuilder();
		exampleJson.append("[");

		// Generate example structure with sample data
		for (int i = 0; i < 2; i++) {
			exampleJson.append("{");
			for (int j = 0; j < columns.length; j++) {
				String column = columns[j].trim();
				exampleJson.append("\\\"")
					.append(column)
					.append("\\\":\\\"sample_row")
					.append(i + 1)
					.append("_")
					.append(column)
					.append("\\\"");
				if (j < columns.length - 1) {
					exampleJson.append(",");
				}
			}
			exampleJson.append("}");
			if (i < 1) {
				exampleJson.append(",");
			}
		}
		exampleJson.append("]");
		return exampleJson.toString();
	}

	private static String generateColumnProperties(String[] columns) {
		StringBuilder properties = new StringBuilder();
		for (int i = 0; i < columns.length; i++) {
			String column = columns[i].trim();
			properties.append("\"").append(column).append("\":{");
			properties.append("\"type\":\"string\",");
			properties.append("\"description\":\"Value for column ").append(column).append("\"");
			properties.append("}");
			if (i < columns.length - 1) {
				properties.append(",");
			}
		}
		return properties.toString();
	}

	private static String generateParametersJson(String expectedReturnInfo) {
		String messageField = generateMessageField(expectedReturnInfo);
		String template = """
				{
				  "type": "object",
				  "properties": {
				    %s
				  },
				  "required": ["message"]
				}
				""";

		return String.format(template, messageField);
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

	public TerminateTool(String planId, String expectedReturnInfo) {
		this.currentPlanId = planId;
		// If expectedReturnInfo is null or empty, use "message" as default
		this.expectedReturnInfo = expectedReturnInfo;
		this.objectMapper = new ObjectMapper();
		this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		this.shortUrlService = null;
		this.manusProperties = null;
	}

	public TerminateTool(String planId, String expectedReturnInfo, ObjectMapper objectMapper) {
		this.currentPlanId = planId;
		this.expectedReturnInfo = expectedReturnInfo;
		if (objectMapper != null) {
			this.objectMapper = objectMapper;
		}
		else {
			this.objectMapper = new ObjectMapper();
			this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		}
		this.shortUrlService = null;
		this.manusProperties = null;
	}

	public TerminateTool(String planId, String expectedReturnInfo, ObjectMapper objectMapper,
			ShortUrlService shortUrlService) {
		this.currentPlanId = planId;
		this.expectedReturnInfo = expectedReturnInfo;
		if (objectMapper != null) {
			this.objectMapper = objectMapper;
		}
		else {
			this.objectMapper = new ObjectMapper();
			this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		}
		this.shortUrlService = shortUrlService;
		this.manusProperties = null;
	}

	public TerminateTool(String planId, String expectedReturnInfo, ObjectMapper objectMapper,
			ShortUrlService shortUrlService, IManusProperties manusProperties) {
		this.currentPlanId = planId;
		this.expectedReturnInfo = expectedReturnInfo;
		if (objectMapper != null) {
			this.objectMapper = objectMapper;
		}
		else {
			this.objectMapper = new ObjectMapper();
			this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		}
		this.shortUrlService = shortUrlService;
		this.manusProperties = manusProperties;
	}

	@Override
	public ToolExecuteResult run(Map<String, Object> input) {
		log.info("Terminate with input: {}", input);

		// Replace short URLs in input before processing
		Map<String, Object> processedInput = replaceShortUrlsInMap(input);

		// Extract message from the structured data
		String message = formatStructuredData(processedInput);
		return new ToolExecuteResult(message);
	}

	/**
	 * Replace short URLs in a string with real URLs
	 * @param text The text that may contain short URLs
	 * @return The text with short URLs replaced by real URLs
	 */
	private String replaceShortUrls(String text) {
		if (text == null || text.isEmpty() || this.rootPlanId == null || this.rootPlanId.isEmpty()
				|| this.shortUrlService == null) {
			return text;
		}

		// Check if short URL feature is enabled
		if (manusProperties != null) {
			Boolean enableShortUrl = manusProperties.getEnableShortUrl();
			if (enableShortUrl == null || !enableShortUrl) {
				return text; // Skip replacement if disabled
			}
		}

		// Pattern to match short URLs: http://s@Url.a/ followed by digits
		Pattern shortUrlPattern = Pattern.compile(Pattern.quote(ShortUrlService.SHORT_URL_PREFIX) + "\\d+");
		Matcher matcher = shortUrlPattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String shortUrl = matcher.group();
			String realUrl = shortUrlService.getRealUrl(this.rootPlanId, shortUrl);
			if (realUrl != null) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(realUrl));
				log.debug("Replaced short URL {} with real URL {}", shortUrl, realUrl);
			}
			else {
				log.warn("Short URL not found in mapping: {}", shortUrl);
				// Keep the short URL if mapping not found
				matcher.appendReplacement(result, Matcher.quoteReplacement(shortUrl));
			}
		}
		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * Recursively replace short URLs in a Map structure
	 * @param input The input map that may contain short URLs
	 * @return A new map with short URLs replaced by real URLs
	 */
	private Map<String, Object> replaceShortUrlsInMap(Map<String, Object> input) {
		if (input == null || this.shortUrlService == null) {
			return input;
		}

		// Check if short URL feature is enabled
		if (manusProperties != null) {
			Boolean enableShortUrl = manusProperties.getEnableShortUrl();
			if (enableShortUrl == null || !enableShortUrl) {
				return input; // Skip replacement if disabled
			}
		}

		Map<String, Object> result = new java.util.HashMap<>();
		for (Map.Entry<String, Object> entry : input.entrySet()) {
			Object value = entry.getValue();
			Object processedValue = replaceShortUrlsInValue(value);
			result.put(entry.getKey(), processedValue);
		}
		return result;
	}

	/**
	 * Recursively replace short URLs in a value (handles String, Map, List)
	 * @param value The value that may contain short URLs
	 * @return The processed value with short URLs replaced
	 */
	@SuppressWarnings("unchecked")
	private Object replaceShortUrlsInValue(Object value) {
		if (value == null) {
			return null;
		}

		if (value instanceof String) {
			return replaceShortUrls((String) value);
		}
		else if (value instanceof Map) {
			return replaceShortUrlsInMap((Map<String, Object>) value);
		}
		else if (value instanceof List) {
			List<Object> list = (List<Object>) value;
			List<Object> result = new ArrayList<>();
			for (Object item : list) {
				result.add(replaceShortUrlsInValue(item));
			}
			return result;
		}
		else {
			// For other types, try to convert to string and replace
			String stringValue = value.toString();
			if (stringValue.contains(ShortUrlService.SHORT_URL_PREFIX)) {
				return replaceShortUrls(stringValue);
			}
			return value;
		}
	}

	private String formatStructuredData(Map<String, Object> input) {
		// Convert input to JSON format without double escaping
		// Return the JSON string directly - it will be stored as-is and serialized
		// properly
		// by Jackson when included in other JSON objects
		try {
			// Note: NON_EMPTY is set by default, so we don't need to set it twice
			String jsonString = objectMapper.writeValueAsString(input);
			// Return the JSON string - when this is later serialized as a field value,
			// Jackson will properly escape it, but we want it to be stored as JSON object
			// not as escaped string, so we return it directly
			return jsonString;
		}
		catch (Exception e) {
			log.error("Failed to convert input to JSON format", e);
			// Fallback to simple string representation
			return input.toString();
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return getDescriptions(this.expectedReturnInfo);
	}

	@Override
	public String getParameters() {
		return generateParametersJson(this.expectedReturnInfo);
	}

	@Override
	public Class<Map<String, Object>> getInputType() {
		@SuppressWarnings("unchecked")
		Class<Map<String, Object>> clazz = (Class<Map<String, Object>>) (Class<?>) Map.class;
		return clazz;
	}

	@Override
	public boolean isReturnDirect() {
		return true;
	}

	@Override
	public void cleanup(String planId) {
		// do nothing
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	// ==================== TerminableTool interface implementation ====================

	@Override
	public boolean canTerminate() {
		// TerminateTool can always be terminated as its purpose is to terminate execution
		return true;
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
