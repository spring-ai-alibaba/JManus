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
package com.alibaba.cloud.ai.lynxe.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.event.LynxeEventPublisher;
import com.alibaba.cloud.ai.lynxe.llm.ConversationMemoryLimitService;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.lynxe.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.lynxe.runtime.service.AgentInterruptionHelper;
import com.alibaba.cloud.ai.lynxe.runtime.service.ParallelToolExecutionService;
import com.alibaba.cloud.ai.lynxe.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.lynxe.runtime.service.UserInputService;
import com.alibaba.cloud.ai.lynxe.tool.TerminableTool;
import com.alibaba.cloud.ai.lynxe.tool.TerminateTool;
import com.alibaba.cloud.ai.lynxe.workspace.conversation.service.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ConfigurableDynaAgent - A flexible agent that allows passing tool lists dynamically
 * This agent can be configured with different tool sets at runtime and extends
 * DynamicAgent to inherit all the core functionality while adding configurable tool
 * management.
 */
public class ConfigurableDynaAgent extends DynamicAgent {

	private static final Logger log = LoggerFactory.getLogger(ConfigurableDynaAgent.class);

	/**
	 * Constructor for ConfigurableDynaAgent with configurable parameters
	 * @param llmService LLM service
	 * @param planExecutionRecorder Plan execution recorder
	 * @param manusProperties Manus properties
	 * @param name Agent name (configurable)
	 * @param description Agent description (configurable)
	 * @param nextStepPrompt Next step prompt (configurable)
	 * @param availableToolKeys List of available tool keys (can be null/empty)
	 * @param toolCallingManager Tool calling manager
	 * @param initialAgentSetting Initial agent settings
	 * @param userInputService User input service
	 * @param promptService Prompt service
	 * @param model Dynamic model entity
	 * @param streamingResponseHandler Streaming response handler
	 * @param step Execution step
	 * @param planIdDispatcher Plan ID dispatcher
	 */
	public ConfigurableDynaAgent(LlmService llmService, PlanExecutionRecorder planExecutionRecorder,
			ManusProperties manusProperties, String name, String description, String nextStepPrompt,
			List<String> availableToolKeys, ToolCallingManager toolCallingManager,
			Map<String, Object> initialAgentSetting, UserInputService userInputService, String modelName,
			StreamingResponseHandler streamingResponseHandler, ExecutionStep step, PlanIdDispatcher planIdDispatcher,
			LynxeEventPublisher lynxeEventPublisher, AgentInterruptionHelper agentInterruptionHelper,
			ObjectMapper objectMapper, ParallelToolExecutionService parallelToolExecutionService,
			MemoryService memoryService, ConversationMemoryLimitService conversationMemoryLimitService) {
		super(llmService, planExecutionRecorder, manusProperties, name, description, nextStepPrompt, availableToolKeys,
				toolCallingManager, initialAgentSetting, userInputService, modelName, streamingResponseHandler, step,
				planIdDispatcher, lynxeEventPublisher, agentInterruptionHelper, objectMapper,
				parallelToolExecutionService, memoryService, conversationMemoryLimitService);
	}

	/**
	 * Override getToolCallList to handle null/empty availableToolKeys If
	 * availableToolKeys is null or empty, return all available tools from
	 * toolCallbackProvider Also ensures TerminateTool is always included
	 * @return List of tool callbacks
	 */
	@Override
	public List<ToolCallback> getToolCallList() {
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		Map<String, ToolCallBackContext> toolCallBackContext = toolCallbackProvider.getToolCallBackContext();

		// Add all available tool keys that are not already in availableToolKeys
		if (availableToolKeys == null || availableToolKeys.isEmpty()) {
			// If availableToolKeys is null or empty, add all available tools
			availableToolKeys.addAll(toolCallBackContext.keySet());
			log.info("No specific tools configured, added all available tools: {}", availableToolKeys);
		}

		// Check if any TerminableTool is already included
		boolean hasTerminableTool = false;
		for (String toolKey : availableToolKeys) {
			if (toolCallBackContext.containsKey(toolKey)) {
				ToolCallBackContext toolCallback = toolCallBackContext.get(toolKey);
				if (toolCallback != null && toolCallback.getFunctionInstance() instanceof TerminableTool) {
					hasTerminableTool = true;
					break;
				}
			}
		}

		// Add TerminateTool if no TerminableTool is present
		if (!hasTerminableTool) {
			availableToolKeys.add(TerminateTool.name);
			log.debug("No TerminableTool found, added TerminateTool to tool list for agent {}", getName());
		}
		else {
			log.debug("Found existing TerminableTool in tool list for agent {}", getName());
		}

		// Build the tool callbacks list
		for (String toolKey : availableToolKeys) {
			if (toolCallBackContext.containsKey(toolKey)) {
				ToolCallBackContext toolCallback = toolCallBackContext.get(toolKey);
				if (toolCallback != null) {
					toolCallbacks.add(toolCallback.getToolCallback());
				}
			}
			else {
				log.warn("Tool callback for {} not found in the map.", toolKey);
			}
		}

		log.info("Agent {} configured with {} tools: {}", getName(), toolCallbacks.size(), availableToolKeys);
		return toolCallbacks;
	}

}
