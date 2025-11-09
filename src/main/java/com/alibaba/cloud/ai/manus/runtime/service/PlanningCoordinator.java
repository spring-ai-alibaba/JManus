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
package com.alibaba.cloud.ai.manus.runtime.service;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory;
import com.alibaba.cloud.ai.manus.planning.service.PlanFinalizer;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionContext;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.PlanExecutionResult;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.PlanInterface;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.RequestSource;
import com.alibaba.cloud.ai.manus.runtime.executor.PlanExecutorInterface;
import com.alibaba.cloud.ai.manus.runtime.executor.factory.PlanExecutorFactory;
import com.alibaba.cloud.ai.manus.workspace.conversation.service.MemoryService;

/**
 * Enhanced Planning Coordinator that uses PlanExecutorFactory to dynamically select the
 * appropriate executor based on plan type
 */
@Service
public class PlanningCoordinator {

	private static final Logger log = LoggerFactory.getLogger(PlanningCoordinator.class);

	private final PlanExecutorFactory planExecutorFactory;

	private final PlanFinalizer planFinalizer;

	private final MemoryService memoryService;

	public PlanningCoordinator(PlanningFactory planningFactory, PlanExecutorFactory planExecutorFactory,
			PlanFinalizer planFinalizer, MemoryService memoryService) {
		this.planExecutorFactory = planExecutorFactory;
		this.planFinalizer = planFinalizer;
		this.memoryService = memoryService;
	}

	/**
	 *
	 * key method Execute a plan directly using the provided plan interface
	 * @param plan The plan to execute
	 * @param rootPlanId The root plan ID for the execution context
	 * @param parentPlanId The ID of the parent plan (can be null for root plans)
	 * @param currentPlanId The current plan ID for execution
	 * @param toolcallId The ID of the tool call that triggered this plan execution
	 * @param requestSource Request source (HTTP_REQUEST, VUE_SIDEBAR, or VUE_DIALOG)
	 * @param uploadKey The upload key for file upload context (can be null)
	 * @param planDepth The depth of the plan in the execution hierarchy (0 for root, 1
	 * for first level, etc.)
	 * @param conversationId The conversation ID for the execution (can be null, will be
	 * generated if needed)
	 * @return A CompletableFuture that completes with the execution result
	 */
	public CompletableFuture<PlanExecutionResult> executeByPlan(PlanInterface plan, String rootPlanId,
			String parentPlanId, String currentPlanId, String toolcallId, RequestSource requestSource,
			String uploadKey, int planDepth, String conversationId) {
		try {
			log.info("Starting direct plan execution for plan: {} at depth: {}", plan.getCurrentPlanId(), planDepth);

			// Create execution context
			ExecutionContext context = new ExecutionContext();
			String userRequest = plan.getUserRequest();
			if (userRequest == null) {
				userRequest = plan.getTitle();
			}
			context.setUserRequest(userRequest);
			context.setCurrentPlanId(currentPlanId);
			context.setRootPlanId(rootPlanId);
			context.setPlan(plan);
			context.setPlanDepth(planDepth); // Set the plan depth
			boolean isVueRequest = requestSource.isVueRequest();
			if (toolcallId == null && isVueRequest) {
				context.setNeedSummary(true);
				log.debug("Setting needSummary=true for planId: {}, toolcallId: {}, requestSource: {}", currentPlanId,
						toolcallId, requestSource);
			}
			else {
				// in sub plan or non-Vue request, we don't need to generate summary
				context.setNeedSummary(false);
				log.debug("Setting needSummary=false for planId: {}, toolcallId: {}, requestSource: {}", currentPlanId,
						toolcallId, requestSource);
			}
			// Set conversation ID (use provided or generate ONLY for VUE_DIALOG requests)
			if (conversationId != null && !conversationId.trim().isEmpty()) {
				context.setConversationId(conversationId);
			}
			else if (requestSource == RequestSource.VUE_DIALOG) {
				// Generate conversation ID ONLY for VUE_DIALOG requests (user-initiated from chat dialog)
				// VUE_SIDEBAR and internal calls (subplans, cron tasks) should not generate conversationId
				String generatedConversationId = memoryService.generateConversationId();
				context.setConversationId(generatedConversationId);
				log.info("Generated conversation ID for VUE_DIALOG request plan execution: {} (source: {})",
						generatedConversationId, requestSource);
			}
			else {
				// For non-VUE_DIALOG requests (VUE_SIDEBAR, HTTP_REQUEST, internal calls), do not set conversationId
				// This ensures conversationId is ONLY generated when user sends message in chat dialog
				log.debug("No conversationId provided for non-VUE_DIALOG request, skipping conversationId (source: {})",
						requestSource);
			}
			context.setUseConversation(true);
			context.setParentPlanId(parentPlanId);
			context.setToolCallId(toolcallId);
			context.setUploadKey(uploadKey);

			// Log toolcallId if provided
			if (toolcallId != null) {
				log.debug("Plan execution triggered by tool call: {}", toolcallId);
			}

			// Log uploadKey if provided
			if (uploadKey != null) {
				log.debug("Plan execution with upload key: {}", uploadKey);
			}

			// Execute the plan using PlanExecutorFactory
			PlanExecutorInterface executor = planExecutorFactory.createExecutor(plan);
			CompletableFuture<PlanExecutionResult> executionFuture = executor.executeAllStepsAsync(context);

			// Add post-execution processing
			return executionFuture.thenCompose(result -> {
				try {
					PlanExecutionResult processedResult = planFinalizer.handlePostExecution(context, result);
					return CompletableFuture.completedFuture(processedResult);
				}
				catch (Exception e) {
					log.error("Error during post-execution processing for plan: {}", context.getCurrentPlanId(), e);
					return CompletableFuture.failedFuture(e);
				}
			});

		}
		catch (Exception e) {
			log.error("Error during direct plan execution", e);
			PlanExecutionResult errorResult = new PlanExecutionResult();
			errorResult.setSuccess(false);
			errorResult.setErrorMessage("Direct plan execution failed: " + e.getMessage());
			return CompletableFuture.completedFuture(errorResult);
		}
	}

}
