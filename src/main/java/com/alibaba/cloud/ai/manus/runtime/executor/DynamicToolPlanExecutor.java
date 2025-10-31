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
package com.alibaba.cloud.ai.manus.runtime.executor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.model.tool.ToolCallingManager;

import com.alibaba.cloud.ai.manus.agent.BaseAgent;
import com.alibaba.cloud.ai.manus.agent.ConfigurableDynaAgent;
import com.alibaba.cloud.ai.manus.agent.ToolCallbackProvider;
import com.alibaba.cloud.ai.manus.agent.entity.DynamicAgentEntity;
import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.event.JmanusEventPublisher;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.manus.model.repository.DynamicModelRepository;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionContext;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.manus.runtime.service.AgentInterruptionHelper;
import com.alibaba.cloud.ai.manus.runtime.service.FileUploadService;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.runtime.service.UserInputService;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dynamic Agent Plan Executor - Specialized executor for DynamicAgentExecutionPlan with
 * user-selected tools support
 */
public class DynamicToolPlanExecutor extends AbstractPlanExecutor {

	/**
	 * Constructor for DynamicAgentPlanExecutor
	 * @param agents List of dynamic agent entities
	 * @param recorder Plan execution recorder
	 * @param agentService Agent service
	 * @param llmService LLM service
	 * @param manusProperties Manus properties
	 * @param levelBasedExecutorPool Level-based executor pool for depth-based execution
	 * @param dynamicModelRepository Dynamic model repository
	 */
	private final PlanningFactory planningFactory;

	private final ToolCallingManager toolCallingManager;

	private final UserInputService userInputService;

	private final StreamingResponseHandler streamingResponseHandler;

	private final PlanIdDispatcher planIdDispatcher;

	private final JmanusEventPublisher jmanusEventPublisher;

	private final ObjectMapper objectMapper;

	public DynamicToolPlanExecutor(List<DynamicAgentEntity> agents, PlanExecutionRecorder recorder,
			LlmService llmService, ManusProperties manusProperties, LevelBasedExecutorPool levelBasedExecutorPool,
			DynamicModelRepository dynamicModelRepository, FileUploadService fileUploadService,
			AgentInterruptionHelper agentInterruptionHelper, PlanningFactory planningFactory,
			ToolCallingManager toolCallingManager, UserInputService userInputService,
			StreamingResponseHandler streamingResponseHandler, PlanIdDispatcher planIdDispatcher,
			JmanusEventPublisher jmanusEventPublisher, ObjectMapper objectMapper) {
		super(agents, recorder, llmService, manusProperties, levelBasedExecutorPool, fileUploadService,
				agentInterruptionHelper);
		this.planningFactory = planningFactory;
		this.toolCallingManager = toolCallingManager;
		this.userInputService = userInputService;
		this.streamingResponseHandler = streamingResponseHandler;
		this.planIdDispatcher = planIdDispatcher;
		this.jmanusEventPublisher = jmanusEventPublisher;
		this.objectMapper = objectMapper;
	}

	protected String getStepFromStepReq(String stepRequirement) {
		String stepType = super.getStepFromStepReq(stepRequirement);
		if ("DEFAULT_AGENT".equals(stepType)) {
			return "ConfigurableDynaAgent";
		}
		return stepType;
	}

	/**
	 * Get the executor for the step.
	 */
	protected BaseAgent getExecutorForStep(ExecutionContext context, ExecutionStep step) {

		String stepType = getStepFromStepReq(step.getStepRequirement());
		int stepIndex = step.getStepIndex();
		String expectedReturnInfo = step.getTerminateColumns();

		String planStatus = context.getPlan().getPlanExecutionStateStringFormat(true);
		String stepText = step.getStepRequirement();

		Map<String, Object> initSettings = new HashMap<>();
		initSettings.put(PLAN_STATUS_KEY, planStatus);
		initSettings.put(CURRENT_STEP_INDEX_KEY, String.valueOf(stepIndex));
		initSettings.put(STEP_TEXT_KEY, stepText);
		initSettings.put(EXTRA_PARAMS_KEY, context.getPlan().getExecutionParams());
		if ("ConfigurableDynaAgent".equals(stepType)) {
			String modelName = step.getModelName();
			List<String> selectedToolKeys = step.getSelectedToolKeys();

			BaseAgent executor = createConfigurableDynaAgent(context.getPlan().getCurrentPlanId(),
					context.getPlan().getRootPlanId(), initSettings, expectedReturnInfo, step, modelName,
					selectedToolKeys, context.getPlanDepth());
			return executor;
		}
		else {
			throw new IllegalArgumentException("No executor found for step type: " + stepType);
		}
	}

	private BaseAgent createConfigurableDynaAgent(String planId, String rootPlanId,
			Map<String, Object> initialAgentSetting, String expectedReturnInfo, ExecutionStep step, String modelName,
			List<String> selectedToolKeys, int planDepth) {

		String name = "ConfigurableDynaAgent";
		String description = "A configurable dynamic agent";
		String nextStepPrompt = "Based on the current environment information and prompt to make a next step decision";

		ConfigurableDynaAgent agent = new ConfigurableDynaAgent(llmService, getRecorder(), manusProperties, name,
				description, nextStepPrompt, selectedToolKeys, toolCallingManager, initialAgentSetting,
				userInputService, modelName, streamingResponseHandler, step, planIdDispatcher, jmanusEventPublisher,
				agentInterruptionHelper, objectMapper);

		agent.setCurrentPlanId(planId);
		agent.setRootPlanId(rootPlanId);
		agent.setPlanDepth(planDepth);

		Map<String, ToolCallBackContext> toolCallbackMap = planningFactory.toolCallbackMap(planId, rootPlanId,
				expectedReturnInfo);
		agent.setToolCallbackProvider(new ToolCallbackProvider() {
			@Override
			public Map<String, ToolCallBackContext> getToolCallBackContext() {
				return toolCallbackMap;
			}
		});
		return agent;
	}

}
