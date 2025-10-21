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

import com.alibaba.cloud.ai.manus.agent.BaseAgent;
import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.agent.entity.DynamicAgentEntity;
import com.alibaba.cloud.ai.manus.agent.service.AgentService;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.model.repository.DynamicModelRepository;
import com.alibaba.cloud.ai.manus.runtime.service.AgentInterruptionHelper;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionContext;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.manus.runtime.service.FileUploadService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	public DynamicToolPlanExecutor(List<DynamicAgentEntity> agents, PlanExecutionRecorder recorder,
			AgentService agentService, LlmService llmService, ManusProperties manusProperties,
			LevelBasedExecutorPool levelBasedExecutorPool, DynamicModelRepository dynamicModelRepository,
			FileUploadService fileUploadService, AgentInterruptionHelper agentInterruptionHelper) {
		super(agents, recorder, agentService, llmService, manusProperties, levelBasedExecutorPool, fileUploadService,
				agentInterruptionHelper);
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

			BaseAgent executor = agentService.createDynamicBaseAgent("ConfigurableDynaAgent",
					context.getPlan().getCurrentPlanId(), context.getPlan().getRootPlanId(), initSettings,
					expectedReturnInfo, step, modelName, selectedToolKeys);
			return executor;
		}
		else {
			return super.getExecutorForStep(context, step);
		}
	}

}
