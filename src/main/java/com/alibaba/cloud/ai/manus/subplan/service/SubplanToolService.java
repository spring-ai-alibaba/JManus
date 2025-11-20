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
package com.alibaba.cloud.ai.manus.subplan.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory;
import com.alibaba.cloud.ai.manus.planning.model.po.FuncAgentToolEntity;
import com.alibaba.cloud.ai.manus.planning.repository.FuncAgentToolRepository;
import com.alibaba.cloud.ai.manus.planning.service.IPlanParameterMappingService;
import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateService;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.runtime.service.PlanningCoordinator;
import com.alibaba.cloud.ai.manus.subplan.model.vo.SubplanToolWrapper;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service implementation for managing subplan tools
 *
 * Integrates with the existing PlanningFactory tool registry system
 */
@Service
@Transactional
public class SubplanToolService {

	private static final Logger logger = LoggerFactory.getLogger(SubplanToolService.class);

	@Autowired
	private FuncAgentToolRepository funcAgentToolRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PlanTemplateService planTemplateService;

	@Autowired
	@Lazy
	private PlanningCoordinator planningCoordinator;


	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Autowired
	private IPlanParameterMappingService parameterMappingService;

	public List<FuncAgentToolEntity> getAllSubplanTools() {
		logger.debug("Fetching all coordinator tools from database");
		return funcAgentToolRepository.findAll();
	}

	public Optional<FuncAgentToolEntity> getSubplanToolByTemplate(String planTemplateId) {
		logger.debug("Fetching coordinator tool for template: {}", planTemplateId);
		List<FuncAgentToolEntity> tools = funcAgentToolRepository.findByPlanTemplateId(planTemplateId);
		return tools.isEmpty() ? Optional.empty() : Optional.of(tools.get(0));
	}

	public Map<String, PlanningFactory.ToolCallBackContext> createSubplanToolCallbacks(String planId, String rootPlanId,
			String expectedReturnInfo) {

		logger.info("Creating subplan tool callbacks for planId: {}, rootPlanId: {}", planId, rootPlanId);

		Map<String, PlanningFactory.ToolCallBackContext> toolCallbackMap = new HashMap<>();

		try {
			// Get all coordinator tools from database, filter by enableInternalToolcall =
			// true
			List<FuncAgentToolEntity> coordinatorTools = funcAgentToolRepository.findAll()
				.stream()
				.filter(tool -> tool.getEnableInternalToolcall() != null && tool.getEnableInternalToolcall())
				.collect(java.util.stream.Collectors.toList());

			if (coordinatorTools.isEmpty()) {
				logger.info("No coordinator tools with enableInternalToolcall=true found in database");
				return toolCallbackMap;
			}

			logger.info("Found {} coordinator tools to register", coordinatorTools.size());

			for (FuncAgentToolEntity coordinatorTool : coordinatorTools) {

			// Get PlanTemplate for this coordinator tool
			com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate planTemplate = planTemplateService
				.getPlanTemplate(coordinatorTool.getPlanTemplateId());
			if (planTemplate == null) {
				logger.info("PlanTemplate not found for planTemplateId: {}, skipping tool registration",
						coordinatorTool.getPlanTemplateId());
				continue;
			}

				String toolName = planTemplate.getTitle() != null ? planTemplate.getTitle()
						: coordinatorTool.getPlanTemplateId();
				try {
					// Create a SubplanToolWrapper that extends AbstractBaseTool
					SubplanToolWrapper toolWrapper = new SubplanToolWrapper(coordinatorTool, planTemplate, planId,
							rootPlanId, planTemplateService, planningCoordinator, planIdDispatcher, objectMapper,
							parameterMappingService);

					// Get tool name from wrapper (uses PlanTemplate title)
					toolName = toolWrapper.getName();

					// Create FunctionToolCallback
					FunctionToolCallback<Map<String, Object>, ToolExecuteResult> functionToolCallback = FunctionToolCallback
						.builder(toolName, toolWrapper)
						.description(coordinatorTool.getToolDescription())
						.inputSchema(toolWrapper.getParameters())
						.inputType(Map.class) // Map input type for coordinator tools
						.toolMetadata(ToolMetadata.builder().returnDirect(false).build())
						.build();

					// Create ToolCallBackContext
					PlanningFactory.ToolCallBackContext context = new PlanningFactory.ToolCallBackContext(
							functionToolCallback, toolWrapper);

					toolCallbackMap.put(toolName, context);

					logger.info("Successfully registered coordinator tool: {} -> {}", toolName,
							coordinatorTool.getPlanTemplateId());

				}
				catch (Exception e) {
					logger.error("Failed to register coordinator tool for planTemplateId: {}",
							coordinatorTool.getPlanTemplateId(), e);
				}
			}

		}
		catch (Exception e) {
			logger.error("Error creating coordinator tool callbacks", e);
		}

		logger.info("Created {} coordinator tool callbacks", toolCallbackMap.size());
		return toolCallbackMap;
	}

}
