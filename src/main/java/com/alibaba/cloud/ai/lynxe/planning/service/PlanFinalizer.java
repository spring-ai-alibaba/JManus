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
package com.alibaba.cloud.ai.lynxe.planning.service;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.llm.LlmService;
import com.alibaba.cloud.ai.lynxe.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.lynxe.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.ExecutionContext;
import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.PlanExecutionResult;
import com.alibaba.cloud.ai.lynxe.runtime.service.TaskInterruptionManager;
import com.alibaba.cloud.ai.lynxe.workspace.conversation.service.MemoryService;

import reactor.core.publisher.Flux;

/**
 * Refactored PlanFinalizer with improved code organization and reduced duplication
 */
@Service
public class PlanFinalizer {

	private static final Logger log = LoggerFactory.getLogger(PlanFinalizer.class);

	private final LlmService llmService;

	private final PlanExecutionRecorder recorder;

	private final ManusProperties manusProperties;

	private final StreamingResponseHandler streamingResponseHandler;

	private final TaskInterruptionManager taskInterruptionManager;

	private final MemoryService memoryService;

	public PlanFinalizer(LlmService llmService, PlanExecutionRecorder recorder, ManusProperties manusProperties,
			StreamingResponseHandler streamingResponseHandler, TaskInterruptionManager taskInterruptionManager,
			MemoryService memoryService) {
		this.llmService = llmService;
		this.recorder = recorder;
		this.manusProperties = manusProperties;
		this.streamingResponseHandler = streamingResponseHandler;
		this.taskInterruptionManager = taskInterruptionManager;
		this.memoryService = memoryService;
	}

	/**
	 * Generate the execution summary of the plan using LLM
	 */
	private void generateSummary(ExecutionContext context, PlanExecutionResult result) {
		validateContextWithPlan(context, "ExecutionContext or its plan or title cannot be null");

		Map<String, Object> promptVariables = Map.of("executionDetail",
				context.getPlan().getPlanExecutionStateStringFormat(false), "title", context.getTitle());

		String summaryPrompt = """
				You are jmanus, an AI assistant capable of responding to user requests. You need to respond to the user's request based on the execution results of this step-by-step execution plan.


				Step-by-step plan execution details:
				{executionDetail}

				Please respond to the user's request based on the information in the execution details.
				""";
		generateWithLlm(context, result, summaryPrompt, promptVariables, "summary", "Generated summary: {}");
	}

	/**
	 * Generate direct LLM response for simple requests
	 */
	private void generateDirectResponse(ExecutionContext context, PlanExecutionResult result) {
		validateForGeneration(context, "ExecutionContext or title cannot be null");

		String title = context.getTitle();
		log.info("Generating direct response for user request: {}", title);

		Map<String, Object> promptVariables = Map.of("title", title);

		String directResponsePrompt = """
				You are lynxe, an AI assistant capable of responding to user requests. Currently in direct feedback mode, you need to directly respond to the user's simple requests without complex planning and decomposition.

				The current user request is:

				{title}
				""";
		generateWithLlm(context, result, directResponsePrompt, promptVariables, "direct response",
				"Generated direct response: {}");
	}

	/**
	 * Core method for generating LLM responses with common logic
	 */
	private String generateLlmResponse(ExecutionContext context, String promptName, Map<String, Object> variables,
			String operationName) {

		PromptTemplate template = new PromptTemplate(promptName);

		Message message = template.createMessage(variables != null ? variables : Map.of());

		Prompt prompt = new Prompt(List.of(message));

		ChatClient.ChatClientRequestSpec requestSpec = llmService.getDiaChatClient().prompt(prompt);
		configureMemoryAdvisors(requestSpec, context);

		Flux<ChatResponse> responseFlux = requestSpec.stream().chatResponse();
		boolean isDebugModel = manusProperties.getDebugDetail() != null && manusProperties.getDebugDetail();
		return streamingResponseHandler.processStreamingTextResponse(responseFlux, operationName,
				context.getCurrentPlanId(), isDebugModel);
	}

	/**
	 * Configure memory advisors for the request
	 */
	private void configureMemoryAdvisors(ChatClient.ChatClientRequestSpec requestSpec, ExecutionContext context) {
		if (context.getConversationId() == null) {
			return;
		}

		requestSpec.advisors(memoryAdvisor -> memoryAdvisor.param(CONVERSATION_ID, context.getConversationId()));
	}

	/**
	 * Record plan completion with the given context and summary
	 */
	private void recordPlanCompletion(ExecutionContext context, String summary) {
		if (context == null || context.getPlan() == null) {
			log.warn("Cannot record plan completion: context or plan is null");
			return;
		}

		String currentPlanId = context.getPlan().getCurrentPlanId();
		recorder.recordPlanCompletion(currentPlanId, summary);
	}

	/**
	 * Validate execution context with plan validation
	 */
	private void validateContextWithPlan(ExecutionContext context, String errorMessage) {
		if (context == null || context.getPlan() == null) {
			throw new IllegalArgumentException(errorMessage);
		}
	}

	/**
	 * Handle post-execution processing based on context requirements
	 * @param context Execution context
	 * @param result Execution result
	 * @return The processed execution result
	 */
	public PlanExecutionResult handlePostExecution(ExecutionContext context, PlanExecutionResult result) {
		if (context == null || result == null) {
			return result;
		}

		try {
			// Check if the task was interrupted
			if (isTaskInterrupted(context, result)) {
				log.debug("Task was interrupted for plan: {}", context.getCurrentPlanId());
				handleInterruptedTask(context, result);
				// Save interrupted task result to conversation memory
				saveResultToConversationMemory(context, result.getFinalResult());
				return result;
			}

			// Check if we need to generate a summary
			if (context.isNeedSummary()) {
				log.debug("Generating LLM summary for plan: {}", context.getCurrentPlanId());
				generateSummary(context, result);
			}
			// Check if this is a direct response plan
			else if (context.getPlan() != null && context.getPlan().isDirectResponse()) {
				log.debug("Generating direct response for plan: {}", context.getCurrentPlanId());
				generateDirectResponse(context, result);
			}
			else {
				log.debug("No need to generate summary or direct response for plan: {}", context.getCurrentPlanId());
				processAndRecordResult(context, result, result.getFinalResult(), "Final result: {}");
			}

			// Save final result to conversation memory after all processing
			saveResultToConversationMemory(context, result.getFinalResult());

			// Add rootPlanId to conversation memory mapping (only for root plans)
			if (context.getConversationId() != null && context.getRootPlanId() != null
					&& context.getRootPlanId().equals(context.getCurrentPlanId())) {
				log.debug("Adding rootPlanId {} to conversation {} in memory mappings", context.getRootPlanId(),
						context.getConversationId());
				memoryService.addRootPlanIdToConversation(context.getConversationId(), context.getRootPlanId());
			}

			return result;

		}
		catch (Exception e) {
			log.warn("Error during post-execution processing for plan: {}, but continuing", context.getCurrentPlanId(),
					e);
			// Don't fail the entire execution for post-processing errors
		}

		return result;
	}

	/**
	 * Unified method for generating LLM responses with common processing
	 */
	private void generateWithLlm(ExecutionContext context, PlanExecutionResult result, String promptName,
			Map<String, Object> variables, String operationType, String successLogTemplate) {
		try {
			String llmResult = generateLlmResponse(context, promptName, variables,
					Character.toUpperCase(operationType.charAt(0)) + operationType.substring(1) + " generation");
			processAndRecordResult(context, result, llmResult, successLogTemplate);
		}
		catch (Exception e) {
			handleLlmError(operationType, e);
		}
	}

	/**
	 * Common result processing and recording logic
	 */
	private void processAndRecordResult(ExecutionContext context, PlanExecutionResult result, String llmResult,
			String logTemplate) {
		// Set result in PlanExecutionResult
		result.setFinalResult(llmResult);
		recordPlanCompletion(context, llmResult);
		log.info(logTemplate, llmResult);
	}

	/**
	 * Unified validation for generation methods
	 */
	private void validateForGeneration(ExecutionContext context, String errorMessage) {
		if (context == null) {
			throw new IllegalArgumentException(errorMessage);
		}
		if (context.getTitle() == null) {
			throw new IllegalArgumentException("Title cannot be null");
		}
	}

	/**
	 * Handle LLM generation errors with consistent error handling
	 */
	private void handleLlmError(String operationType, Exception e) {
		log.error("Error generating {} with LLM", operationType, e);
		throw new RuntimeException("Failed to generate " + operationType, e);
	}

	/**
	 * Check if the task execution was interrupted
	 * @param context Execution context containing root plan ID
	 * @param result The execution result to check
	 * @return true if the task was interrupted, false otherwise
	 */
	private boolean isTaskInterrupted(ExecutionContext context, PlanExecutionResult result) {
		if (result == null) {
			return false;
		}

		// First, check the actual database state to verify if task was marked for
		// interruption
		if (context != null && context.getRootPlanId() != null && taskInterruptionManager != null) {
			try {
				boolean shouldInterrupt = taskInterruptionManager.shouldInterruptTask(context.getRootPlanId());
				if (shouldInterrupt) {
					log.debug("Task {} is marked for interruption in database", context.getRootPlanId());
					return true;
				}
			}
			catch (Exception e) {
				log.warn("Error checking interruption status for planId: {}, falling back to error message check",
						context.getRootPlanId(), e);
				// Fall through to error message check
			}
		}

		// Fallback: Check if errorMessage indicates interruption with specific patterns
		// Only match specific interruption messages to avoid false positives
		String errorMessage = result.getErrorMessage();
		if (errorMessage != null) {
			String lowerErrorMessage = errorMessage.toLowerCase();
			// Match specific interruption patterns that are set by the system
			if (lowerErrorMessage.contains("plan execution interrupted by user")
					|| lowerErrorMessage.contains("execution interrupted by user")
					|| lowerErrorMessage.contains("tool execution interrupted by user")
					|| lowerErrorMessage.contains("action interrupted by user")
					|| lowerErrorMessage.contains("agent thinking interrupted")
					|| lowerErrorMessage.contains("task execution was interrupted by user")) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Handle interrupted task execution
	 * @param context Execution context
	 * @param result Execution result to update
	 */
	private void handleInterruptedTask(ExecutionContext context, PlanExecutionResult result) {
		log.info("Handling interrupted task for plan: {}", context.getCurrentPlanId());

		// Set correct status for interrupted task
		result.setSuccess(false);
		result.setErrorMessage("Task execution was interrupted by user");

		// Generate appropriate interruption message
		String interruptionMessage = generateInterruptionMessage(context, result);
		result.setFinalResult(interruptionMessage);

		// Record the interruption
		recordPlanCompletion(context, interruptionMessage);
		log.info("Task interruption handled: {}", interruptionMessage);
	}

	/**
	 * Generate appropriate message for interrupted task
	 * @param context Execution context
	 * @param result Execution result
	 * @return Formatted interruption message
	 */
	private String generateInterruptionMessage(ExecutionContext context, PlanExecutionResult result) {
		String title = context.getTitle();
		StringBuilder message = new StringBuilder();
		message.append("❌ **Task Interrupted**\n\n");
		message.append("Your request \"").append(title).append("\" was interrupted and could not be completed.\n\n");
		message.append("**Status:** Task stopped by user request\n");

		return message.toString();
	}

	/**
	 * Save agent execution result to conversation memory
	 * @param context Execution context containing conversationId
	 * @param result The execution result to save
	 */
	private void saveResultToConversationMemory(ExecutionContext context, String result) {
		if (context == null || context.getConversationId() == null || context.getConversationId().trim().isEmpty()) {
			log.debug("No conversationId available, skipping conversation memory save");
			return;
		}

		if (result == null || result.trim().isEmpty()) {
			log.debug("Result is empty, skipping conversation memory save");
			return;
		}

		try {
			AssistantMessage assistantMessage = new AssistantMessage(result);
			llmService.addToConversationMemoryWithLimit(manusProperties.getMaxMemory(), context.getConversationId(),
					assistantMessage);
			log.info("Saved agent execution result to conversation memory for conversationId: {}, result length: {}",
					context.getConversationId(), result.length());
		}
		catch (Exception e) {
			log.warn("Failed to save agent execution result to conversation memory for conversationId: {}",
					context.getConversationId(), e);
		}
	}

}
