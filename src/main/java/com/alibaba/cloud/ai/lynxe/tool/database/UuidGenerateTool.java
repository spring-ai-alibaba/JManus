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
package com.alibaba.cloud.ai.lynxe.tool.database;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class UuidGenerateTool extends AbstractBaseTool<UuidGenerateRequest> {

	private static final Logger log = LoggerFactory.getLogger(UuidGenerateTool.class);

	public UuidGenerateTool(ManusProperties manusProperties, ObjectMapper objectMapper) {
		// Constructor for dependency injection
	}

	private final String name = "uuid_generate";

	@Override
	public String getServiceGroup() {
		return "database-service-group";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return """
				Generate a UUID (Universally Unique Identifier) string.
				Use this tool when you need to generate a unique identifier.

				The generated UUID follows the standard UUID format (e.g., 550e8400-e29b-41d4-a716-446655440000).
				""";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "properties": {
				        "action": { "type": "string", "const": "generate_uuid", "description": "Action to generate UUID" }
				    },
				    "required": ["action"],
				    "additionalProperties": false
				}
				""";
	}

	@Override
	public Class<UuidGenerateRequest> getInputType() {
		return UuidGenerateRequest.class;
	}

	@Override
	public ToolExecuteResult run(UuidGenerateRequest request) {
		log.info("UuidGenerateTool request received");
		try {
			if (request != null && request.getAction() != null && !"generate_uuid".equals(request.getAction())) {
				return new ToolExecuteResult("Only generate_uuid action is supported");
			}

			// Generate UUID
			String uuid = UUID.randomUUID().toString();
			log.info("Generated UUID: {}", uuid);
			return new ToolExecuteResult(uuid);
		}
		catch (Exception e) {
			log.error("UUID generation failed", e);
			return new ToolExecuteResult("UUID generation failed: " + e.getMessage());
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up UUID generation resources for plan: {}", planId);
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			StringBuilder stateBuilder = new StringBuilder();
			stateBuilder.append("\n=== UUID Generate Tool Current State ===\n");
			stateBuilder.append("Tool is ready to generate UUID strings.\n");
			stateBuilder.append("Format: Standard UUID v4 (e.g., 550e8400-e29b-41d4-a716-446655440000)\n");
			stateBuilder.append("\n=== End UUID Generate Tool State ===\n");
			return stateBuilder.toString();
		}
		catch (Exception e) {
			log.error("Failed to get UUID generate tool state", e);
			return String.format("UUID generate tool state error: %s", e.getMessage());
		}
	}

	public static UuidGenerateTool getInstance(ObjectMapper objectMapper) {
		return new UuidGenerateTool(null, objectMapper);
	}

}
