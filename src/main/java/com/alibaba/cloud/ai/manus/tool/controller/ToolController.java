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
package com.alibaba.cloud.ai.manus.tool.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.tool.model.ToolInfo;

/**
 * Tool REST API controller
 * Provides endpoints for getting available tool information
 */
@RestController
@RequestMapping("/api/tools")
@CrossOrigin(origins = "*")
public class ToolController {

	private static final Logger logger = LoggerFactory.getLogger(ToolController.class);

	@Autowired
	private PlanningFactory planningFactory;

	/**
	 * Get all available tools
	 * @return List of available tools with their information
	 */
	@GetMapping
	public ResponseEntity<List<ToolInfo>> getAllAvailableTools() {
		try {
			logger.info("Getting all available tools");
			
			// Use a dummy planId and rootPlanId to get tool information
			// This is safe since we're only extracting metadata, not executing tools
			String dummyPlanId = "tool-info-request";
			String dummyRootPlanId = "tool-info-request";
			String dummyExpectedReturnInfo = "";
			
			Map<String, ToolCallBackContext> toolCallbackMap = planningFactory.toolCallbackMap(
				dummyPlanId, dummyRootPlanId, dummyExpectedReturnInfo);
			
			List<ToolInfo> tools = new ArrayList<>();
			
			for (Map.Entry<String, ToolCallBackContext> entry : toolCallbackMap.entrySet()) {
				String toolKey = entry.getKey();
				ToolCallBackContext context = entry.getValue();
				
				if (context != null && context.getFunctionInstance() != null) {
					try {
						ToolInfo toolInfo = new ToolInfo();
						toolInfo.setKey(toolKey);
						toolInfo.setName(context.getFunctionInstance().getName());
						toolInfo.setDescription(context.getFunctionInstance().getDescription());
						toolInfo.setEnabled(true);
						toolInfo.setSelectable(true);
						
						// Try to determine service group from tool name or type
						String serviceGroup = determineServiceGroup(toolKey, context);
						toolInfo.setServiceGroup(serviceGroup);
						
						tools.add(toolInfo);
						
					} catch (Exception e) {
						logger.warn("Failed to extract info for tool: {}, error: {}", toolKey, e.getMessage());
					}
				}
			}
			
			logger.info("Found {} available tools", tools.size());
			return ResponseEntity.ok(tools);
			
		} catch (Exception e) {
			logger.error("Error getting available tools", e);
			return ResponseEntity.internalServerError().build();
		}
	}
	
	/**
	 * Determine service group based on tool key and context
	 */
	private String determineServiceGroup(String toolKey, ToolCallBackContext context) {
		// Built-in tool mappings
		if (toolKey.contains("browser") || toolKey.contains("Browser")) {
			return "browser";
		}
		if (toolKey.contains("database") || toolKey.contains("Database") || toolKey.contains("sql")) {
			return "database";
		}
		if (toolKey.contains("file") || toolKey.contains("File") || toolKey.contains("directory") || toolKey.contains("Directory")) {
			return "filesystem";
		}
		if (toolKey.contains("text") || toolKey.contains("Text")) {
			return "text";
		}
		if (toolKey.contains("bash") || toolKey.contains("Bash") || toolKey.contains("terminal")) {
			return "system";
		}
		if (toolKey.contains("cron") || toolKey.contains("Cron")) {
			return "scheduler";
		}
		if (toolKey.contains("markdown") || toolKey.contains("Markdown") || toolKey.contains("pdf") || toolKey.contains("image")) {
			return "converter";
		}
		if (toolKey.contains("form") || toolKey.contains("Form") || toolKey.contains("input")) {
			return "interaction";
		}
		if (toolKey.contains("parallel") || toolKey.contains("Parallel")) {
			return "execution";
		}
		if (toolKey.contains("terminate") || toolKey.contains("Terminate")) {
			return "control";
		}
		
		// For MCP tools or subplan tools, try to extract from description or use default
		return "general";
	}
}
