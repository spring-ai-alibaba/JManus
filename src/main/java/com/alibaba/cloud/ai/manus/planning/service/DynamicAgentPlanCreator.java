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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.manus.prompt.service.PromptService;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionContext;
import com.alibaba.cloud.ai.manus.tool.PlanningToolInterface;

/**
 * The class responsible for creating dynamic agent execution plans
 */
public class DynamicAgentPlanCreator implements IPlanCreator {

	private static final Logger log = LoggerFactory.getLogger(DynamicAgentPlanCreator.class);


	public DynamicAgentPlanCreator(LlmService llmService, PlanningToolInterface planningTool,
			PlanExecutionRecorder recorder, PromptService promptService, ManusProperties manusProperties,
			StreamingResponseHandler streamingResponseHandler) {
	}

	/**
	 * Create a dynamic agent execution plan with memory support
	 * @param context execution context, containing the user request and the execution
	 * process information
	 */
	@Override
	public void createPlanWithMemory(ExecutionContext context) {
		createPlanInternal(context, true);
	}

	/**
	 * Create a dynamic agent execution plan without memory support
	 * @param context execution context, containing the user request and the execution
	 * process information
	 */
	@Override
	public void createPlanWithoutMemory(ExecutionContext context) {
		createPlanInternal(context, false);
	}

	/**
	 * Internal method that handles the common dynamic agent plan creation logic
	 * @param context execution context, containing the user request and the execution
	 * process information
	 * @param useMemory whether to use memory support
	 */
	private void createPlanInternal(ExecutionContext context, boolean useMemory) {
		throw new UnsupportedOperationException("Dynamic agent plan creation is not supported");
	}

}
