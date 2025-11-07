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
package com.alibaba.cloud.ai.manus.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.event.JmanusEventPublisher;
import com.alibaba.cloud.ai.manus.event.PlanExceptionClearedEvent;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder.ActToolParam;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder.ThinkActRecordParams;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.manus.runtime.executor.AbstractPlanExecutor;
import com.alibaba.cloud.ai.manus.runtime.service.AgentInterruptionHelper;
import com.alibaba.cloud.ai.manus.runtime.service.ParallelToolExecutionService;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.runtime.service.TaskInterruptionCheckerService;
import com.alibaba.cloud.ai.manus.runtime.service.UserInputService;
import com.alibaba.cloud.ai.manus.tool.FormInputTool;
import com.alibaba.cloud.ai.manus.tool.TerminableTool;
import com.alibaba.cloud.ai.manus.tool.ToolCallBiFunctionDef;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.common.util.StringUtils;
import reactor.core.publisher.Flux;

public class DynamicAgent extends ReActAgent {

	private static final String CURRENT_STEP_ENV_DATA_KEY = "current_step_env_data";

	private static final Logger log = LoggerFactory.getLogger(DynamicAgent.class);

	private final ObjectMapper objectMapper;

	private final String agentName;

	private final String agentDescription;

	private final String nextStepPrompt;

	protected ToolCallbackProvider toolCallbackProvider;

	protected final List<String> availableToolKeys;

	private ChatResponse response;

	private StreamingResponseHandler.StreamingResult streamResult;

	private Prompt userPrompt;

	private List<ActToolParam> actToolInfoList = new ArrayList<>();

	private final ToolCallingManager toolCallingManager;

	private final UserInputService userInputService;

	private final String modelName;

	private final StreamingResponseHandler streamingResponseHandler;

	private JmanusEventPublisher jmanusEventPublisher;

	private AgentInterruptionHelper agentInterruptionHelper;

	private ParallelToolExecutionService parallelToolExecutionService;

	public void clearUp(String planId) {
		Map<String, ToolCallBackContext> toolCallBackContext = toolCallbackProvider.getToolCallBackContext();
		for (ToolCallBackContext toolCallBack : toolCallBackContext.values()) {
			try {
				toolCallBack.getFunctionInstance().cleanup(planId);
			}
			catch (Exception e) {
				log.error("Error cleaning up tool callback context: {}", e.getMessage(), e);
			}
		}
		// Also remove any pending form input tool for this root plan ID
		if (userInputService != null) {
			String rootPlanId = getRootPlanId();
			if (rootPlanId != null) {
				userInputService.removeFormInputTool(rootPlanId);
			}
		}
	}

	public DynamicAgent(LlmService llmService, PlanExecutionRecorder planExecutionRecorder,
			ManusProperties manusProperties, String name, String description, String nextStepPrompt,
			List<String> availableToolKeys, ToolCallingManager toolCallingManager,
			Map<String, Object> initialAgentSetting, UserInputService userInputService, String modelName,
			StreamingResponseHandler streamingResponseHandler, ExecutionStep step, PlanIdDispatcher planIdDispatcher,
			JmanusEventPublisher jmanusEventPublisher, AgentInterruptionHelper agentInterruptionHelper,
			ObjectMapper objectMapper, ParallelToolExecutionService parallelToolExecutionService) {
		super(llmService, planExecutionRecorder, manusProperties, initialAgentSetting, step, planIdDispatcher);
		this.objectMapper = objectMapper;
		this.agentName = name;
		this.agentDescription = description;
		this.nextStepPrompt = nextStepPrompt;
		if (availableToolKeys == null) {
			this.availableToolKeys = new ArrayList<>();
		}
		else {
			this.availableToolKeys = availableToolKeys;
		}
		this.toolCallingManager = toolCallingManager;
		this.userInputService = userInputService;
		this.modelName = modelName;
		this.streamingResponseHandler = streamingResponseHandler;
		this.jmanusEventPublisher = jmanusEventPublisher;
		this.agentInterruptionHelper = agentInterruptionHelper;
		this.parallelToolExecutionService = parallelToolExecutionService;
	}

	@Override
	protected boolean think() {
		// Check for interruption before starting thinking process
		if (agentInterruptionHelper != null && !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
			log.info("Agent {} thinking process interrupted for rootPlanId: {}", getName(), getRootPlanId());
			// Throw exception to signal interruption instead of returning false
			throw new TaskInterruptionCheckerService.TaskInterruptedException(
					"Agent thinking interrupted for rootPlanId: " + getRootPlanId());
		}

		collectAndSetEnvDataForTools();

		try {
			return executeWithRetry(3);
		}
		catch (TaskInterruptionCheckerService.TaskInterruptedException e) {
			log.info("Agent {} thinking process interrupted: {}", getName(), e.getMessage());
			throw e; // Re-throw the interruption exception
		}
		catch (Exception e) {
			log.error(String.format("🚨 Oops! The %s's thinking process hit a snag: %s", getName(), e.getMessage()), e);
			log.info("Exception occurred", e);
			return false;
		}
	}

	private boolean executeWithRetry(int maxRetries) throws Exception {
		int attempt = 0;
		Exception lastException = null;

		while (attempt < maxRetries) {
			attempt++;

			// Check for interruption before each retry attempt
			if (agentInterruptionHelper != null
					&& !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
				log.info("Agent {} retry process interrupted at attempt {}/{} for rootPlanId: {}", getName(), attempt,
						maxRetries, getRootPlanId());
				throw new TaskInterruptionCheckerService.TaskInterruptedException(
						"Agent thinking interrupted at attempt " + attempt);
			}

			try {
				log.info("Attempt {}/{}: Executing agent thinking process", attempt, maxRetries);

				Message systemMessage = getThinkMessage();
				// Use current env as user message
				Message currentStepEnvMessage = currentStepEnvMessage();
				// Record think message
				List<Message> thinkMessages = Arrays.asList(systemMessage, currentStepEnvMessage);
				String thinkInput = thinkMessages.toString();

				//log.debug("Messages prepared for the prompt: {}", thinkMessages);
				// Build current prompt. System message is the first message
				List<Message> messages = new ArrayList<>(Collections.singletonList(systemMessage));
				// Add history message.
				ChatMemory chatMemory = llmService.getAgentMemory(manusProperties.getMaxMemory());
				List<Message> historyMem = chatMemory.get(getCurrentPlanId());
				messages.addAll(historyMem);
				messages.add(currentStepEnvMessage);
				String toolcallId = planIdDispatcher.generateToolCallId();
				// Call the LLM
				Map<String, Object> toolContextMap = new HashMap<>();
				toolContextMap.put("toolcallId", toolcallId);
				toolContextMap.put("planDepth", getPlanDepth());
				ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder()
					.internalToolExecutionEnabled(false)
					.toolContext(toolContextMap)
					// can't support by toocall options :
					// .parallelToolCalls(manusProperties.getParallelToolCalls())
					.build();
				userPrompt = new Prompt(messages, chatOptions);
				List<ToolCallback> callbacks = getToolCallList();
				ChatClient chatClient;
				if (modelName == null || modelName.isEmpty()) {
					chatClient = llmService.getDefaultDynamicAgentChatClient();
				}
				else {
					chatClient = llmService.getDynamicAgentChatClient(modelName);
				}
				// Use streaming response handler for better user experience and content
				// merging
				Flux<ChatResponse> responseFlux = chatClient.prompt(userPrompt)
					.toolCallbacks(callbacks)
					.stream()
					.chatResponse();
				boolean isDebugModel = manusProperties.getDebugDetail() != null
						&& manusProperties.getDebugDetail();
				// Enable early termination for agent thinking (should have tool calls)
				streamResult = streamingResponseHandler.processStreamingResponse(responseFlux,
						"Agent " + getName() + " thinking", getCurrentPlanId(), isDebugModel, true);

				response = streamResult.getLastResponse();

				// Use merged content from streaming handler
				List<ToolCall> toolCalls = streamResult.getEffectiveToolCalls();
				String responseByLLm = streamResult.getEffectiveText();

				log.info(String.format("✨ %s's thoughts: %s", getName(), responseByLLm));
				log.info(String.format("🛠️ %s selected %d tools to use", getName(), toolCalls.size()));

				if (!toolCalls.isEmpty()) {
					log.info(String.format("🧰 Tools being prepared: %s",
							toolCalls.stream().map(ToolCall::name).collect(Collectors.toList())));

					String stepId = super.step.getStepId();
					String thinkActId = planIdDispatcher.generateThinkActId();

					actToolInfoList = new ArrayList<>();
					for (ToolCall toolCall : toolCalls) {
						ActToolParam actToolInfo = new ActToolParam(toolCall.name(), toolCall.arguments(), toolcallId);
						actToolInfoList.add(actToolInfo);
					}

					ThinkActRecordParams paramsN = new ThinkActRecordParams(thinkActId, stepId, thinkInput,
							responseByLLm, null, actToolInfoList);
					planExecutionRecorder.recordThinkingAndAction(step, paramsN);

					// Clear exception cache if this was a retry attempt
					if (attempt > 1 && jmanusEventPublisher != null) {
						log.info("Retry successful for planId: {}, clearing exception cache", getCurrentPlanId());
						jmanusEventPublisher.publish(new PlanExceptionClearedEvent(getCurrentPlanId()));
					}

					return true;
				}
				log.warn("Attempt {}: No tools selected. Retrying...", attempt);

			}
			catch (Exception e) {
				lastException = e;
				log.warn("Attempt {} failed: {}", attempt, e.getMessage());

				// Check if this is a network-related error that should be retried
				if (isRetryableException(e)) {
					if (attempt < maxRetries) {
						long waitTime = calculateBackoffDelay(attempt);
						log.info("Retrying in {}ms due to retryable error: {}", waitTime, e.getMessage());
						try {
							Thread.sleep(waitTime);
						}
						catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							throw new Exception("Retry interrupted", ie);
						}
						continue;
					}
				}
				else {
					// Non-retryable error, throw immediately
					throw e;
				}
			}
		}

		// All retries exhausted
		if (lastException != null) {
			throw new Exception("All retry attempts failed. Last error: " + lastException.getMessage(), lastException);
		}
		return false;
	}

	/**
	 * Check if the exception is retryable (network issues, timeouts, etc.)
	 */
	private boolean isRetryableException(Exception e) {
		String message = e.getMessage();
		if (message == null)
			return false;

		// Check for network-related errors
		return message.contains("Failed to resolve") || message.contains("timeout") || message.contains("connection")
				|| message.contains("DNS") || message.contains("WebClientRequestException")
				|| message.contains("DnsNameResolverTimeoutException");
	}

	/**
	 * Calculate exponential backoff delay
	 */
	private long calculateBackoffDelay(int attempt) {
		// Exponential backoff: 2^attempt * 1000ms, max 30 seconds
		long delay = Math.min(1000L * (1L << (attempt - 1)), 30000L);
		return delay;
	}

	@Override
	protected AgentExecResult act() {
		// Check for interruption before starting action process
		if (agentInterruptionHelper != null && !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
			log.info("Agent {} action process interrupted for rootPlanId: {}", getName(), getRootPlanId());
			return new AgentExecResult("Action interrupted by user", AgentState.FAILED);
		}

		try {
			List<ToolCall> toolCalls = streamResult.getEffectiveToolCalls();

			// Route to appropriate handler based on tool count
			if (toolCalls == null || toolCalls.isEmpty()) {
				return new AgentExecResult("tool call is empty , please retry", AgentState.IN_PROGRESS);
			}
			else if (toolCalls.size() == 1) {
				// Single tool execution - core logic
				return processSingleTool(toolCalls.get(0));
			}
			else {
				// Multiple tools execution - TODO: implement parallel/sequential execution
				return processMultipleTools(toolCalls);
			}
		}
		catch (Exception e) {
			log.error("Error executing tools: {}", e.getMessage(), e);

			StringBuilder errorMessage = new StringBuilder("Error executing tools: ");
			errorMessage.append(e.getMessage());

			String firstToolcall = actToolInfoList != null && !actToolInfoList.isEmpty()
					&& actToolInfoList.get(0).getParameters() != null
							? actToolInfoList.get(0).getParameters().toString() : "unknown";
			errorMessage.append("  . llm return param :  ").append(firstToolcall);

			// Clean up form input tool using root plan ID on error
			String rootPlanId = getRootPlanId();
			if (rootPlanId != null) {
				userInputService.removeFormInputTool(rootPlanId);
			}
			return new AgentExecResult(e.getMessage(), AgentState.FAILED);
		}
	}

	/**
	 * Process a single tool execution
	 * This is the core logic for tool execution
	 * @param toolCall The tool call to execute
	 * @return AgentExecResult containing the execution result
	 */
	private AgentExecResult processSingleTool(ToolCall toolCall) {
		ToolExecutionResult toolExecutionResult = null;
		try {
			// Check for interruption before tool execution
			if (agentInterruptionHelper != null
					&& !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
				log.info("Agent {} tool execution interrupted for rootPlanId: {}", getName(), getRootPlanId());
				return new AgentExecResult("Tool execution interrupted by user", AgentState.FAILED);
			}

			// Execute tool call
			toolExecutionResult = toolCallingManager.executeToolCalls(userPrompt, response);
			processMemory(toolExecutionResult);

			// Get tool response message
			ToolResponseMessage toolResponseMessage = (ToolResponseMessage) toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1);

			if (toolResponseMessage.getResponses().isEmpty()) {
				return new AgentExecResult("Tool response is empty", AgentState.IN_PROGRESS);
			}

			// Process single tool response
			ToolResponseMessage.ToolResponse toolCallResponse = toolResponseMessage.getResponses().get(0);
			String toolName = toolCall.name();
			ActToolParam param = actToolInfoList.get(0);
			ToolCallBiFunctionDef<?> toolInstance = getToolCallBackContext(toolName).getFunctionInstance();

			String result;
			boolean shouldTerminate = false;

			// Handle different tool types
			if (toolInstance instanceof FormInputTool) {
				AgentExecResult formResult = handleFormInputTool((FormInputTool) toolInstance, param);
				result = formResult.getResult();
				param.setResult(result);
			}
			else if (toolInstance instanceof TerminableTool) {
				TerminableTool terminableTool = (TerminableTool) toolInstance;
				result = processToolResult(toolCallResponse.responseData());
				param.setResult(result);

				if (terminableTool.canTerminate()) {
					log.info("TerminableTool can terminate for planId: {}", getCurrentPlanId());
					String rootPlanId = getRootPlanId();
					if (rootPlanId != null) {
						userInputService.removeFormInputTool(rootPlanId);
					}
					shouldTerminate = true;
				}
				else {
					log.info("TerminableTool cannot terminate yet for planId: {}", getCurrentPlanId());
				}
			}
			else {
				// Regular tool
				result = processToolResult(toolCallResponse.responseData());
				param.setResult(result);
				log.info("Tool {} executed successfully for planId: {}", toolName, getCurrentPlanId());
			}

			// Record the result
			recordActionResult(List.of(param));

			// Return result with appropriate state
			return new AgentExecResult(result, shouldTerminate ? AgentState.COMPLETED : AgentState.IN_PROGRESS);
		}
		catch (Exception e) {
			log.error("Error executing single tool: {}", e.getMessage(), e);
			processMemory(toolExecutionResult); // Process memory even on error
			return new AgentExecResult("Error executing tool: " + e.getMessage(), AgentState.FAILED);
		}
	}

	/**
	 * Process multiple tools execution using parallel execution service
	 * Multiple tools execution does not support TerminableTool and FormInputTool.
	 * If these tools are present, return error message asking LLM to retry without them.
	 * @param toolCalls List of tool calls to execute
	 * @return AgentExecResult containing the execution results
	 */
	private AgentExecResult processMultipleTools(List<ToolCall> toolCalls) {
		// Check for interruption before starting
		if (agentInterruptionHelper != null
				&& !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
			log.info("Agent {} tool execution interrupted before starting for rootPlanId: {}", getName(),
					getRootPlanId());
			return new AgentExecResult("Tool execution interrupted by user", AgentState.FAILED);
		}

		try {
			// Check for TerminableTool and FormInputTool in multiple tools
			List<String> restrictedToolNames = new ArrayList<>();
			for (ToolCall toolCall : toolCalls) {
				String toolName = toolCall.name();
				ToolCallBackContext context = getToolCallBackContext(toolName);
				if (context != null) {
					ToolCallBiFunctionDef<?> toolInstance = context.getFunctionInstance();
					if (toolInstance instanceof TerminableTool || toolInstance instanceof FormInputTool) {
						restrictedToolNames.add(toolName);
					}
				}
			}

			// If restricted tools found, return error asking LLM to retry without them
			if (!restrictedToolNames.isEmpty()) {
				String errorMessage = String.format(
						"Multiple tools execution does not support TerminableTool and FormInputTool. "
								+ "Found restricted tools: %s. Please retry by calling tools separately, "
								+ "excluding TerminableTool and FormInputTool from multiple tool calls.",
						String.join(", ", restrictedToolNames));
				log.warn("Multiple tools execution rejected: {}", errorMessage);
				return new AgentExecResult(errorMessage, AgentState.IN_PROGRESS);
			}

			// Execute all tools in parallel
			if (parallelToolExecutionService == null) {
				log.error("ParallelToolExecutionService is not available");
				return new AgentExecResult("Parallel execution service is not available", AgentState.FAILED);
			}

			Map<String, ToolCallBackContext> toolCallbackMap = toolCallbackProvider.getToolCallBackContext();
			Map<String, Object> toolContextMap = new HashMap<>();
			toolContextMap.put("toolcallId", planIdDispatcher.generateToolCallId());
			toolContextMap.put("planDepth", getPlanDepth());
			ToolContext parentToolContext = new ToolContext(
					toolContextMap);

			List<ParallelToolExecutionService.ToolExecutionResult> parallelResults = parallelToolExecutionService
					.executeToolsInParallel(toolCalls, toolCallbackMap, planIdDispatcher, parentToolContext);
			log.info("Executed {} tools in parallel", parallelResults.size());

			// Process results and update actToolInfoList
			List<String> resultList = new ArrayList<>();
			for (int i = 0; i < toolCalls.size() && i < actToolInfoList.size(); i++) {
				ToolCall toolCall = toolCalls.get(i);
				String toolName = toolCall.name();
				ActToolParam param = actToolInfoList.get(i);

				// Find corresponding result
				String processedResult = null;
				for (ParallelToolExecutionService.ToolExecutionResult result : parallelResults) {
					if (result.getToolName().equals(toolName)) {
						if (result.isSuccess()) {
							processedResult = processToolResult(result.getResult().getOutput());
						}
						else {
							processedResult = "Error: " + result.getResult().getOutput();
						}
						break;
					}
				}

				if (processedResult == null) {
					processedResult = "Tool execution result not found";
					log.warn("Result not found for tool: {}", toolName);
				}

				param.setResult(processedResult);
				resultList.add(processedResult);
				log.info("Tool {} executed successfully for planId: {}", toolName, getCurrentPlanId());
			}

			// Record the results
			recordActionResult(actToolInfoList);

			// Update memory using ToolCallingManager (for compatibility)
			try {
				ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(userPrompt, response);
				processMemory(toolExecutionResult);
			}
			catch (Exception e) {
				log.warn("Error processing memory after parallel execution: {}", e.getMessage());
			}

			// Return result
			return new AgentExecResult(resultList.toString(), AgentState.IN_PROGRESS);
		}
		catch (Exception e) {
			log.error("Error executing multiple tools: {}", e.getMessage(), e);
			return new AgentExecResult("Error executing tools: " + e.getMessage(), AgentState.FAILED);
		}
	}

	/**
	 * Handle FormInputTool specific logic with exclusive storage
	 */
	private AgentExecResult handleFormInputTool(FormInputTool formInputTool, ActToolParam param) {
		// Ensure the form input tool has the correct plan IDs set
		formInputTool.setCurrentPlanId(getCurrentPlanId());
		formInputTool.setRootPlanId(getRootPlanId());

		// Check if the tool is waiting for user input
		if (formInputTool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) {
			String rootPlanId = getRootPlanId();
			String currentPlanId = getCurrentPlanId();
			log.info("FormInputTool is awaiting user input for rootPlanId: {} (currentPlanId: {})", rootPlanId,
					currentPlanId);

			// Use exclusive storage method - this will handle waiting and queuing
			// automatically
			boolean stored = userInputService.storeFormInputToolExclusive(rootPlanId, formInputTool, currentPlanId);
			if (!stored) {
				log.error("Failed to store form for sub-plan {} due to lock timeout or interruption", currentPlanId);
				param.setResult("Failed to store form due to system timeout");
				return new AgentExecResult("Failed to store form due to system timeout", AgentState.FAILED);
			}

			// Wait for user input or timeout
			waitForUserInputOrTimeout(formInputTool);

			// After waiting, check the state again
			if (formInputTool.getInputState() == FormInputTool.InputState.INPUT_RECEIVED) {
				log.info("User input received for rootPlanId: {} from sub-plan {}", rootPlanId, currentPlanId);

				UserMessage userMessage = UserMessage.builder()
					.text("User input received for form: " + formInputTool.getCurrentToolStateString())
					.build();
				processUserInputToMemory(userMessage);

				// Update the result in actToolInfoList
				param.setResult(formInputTool.getCurrentToolStateString());
				return new AgentExecResult(param.getResult(), AgentState.IN_PROGRESS);

			}
			else if (formInputTool.getInputState() == FormInputTool.InputState.INPUT_TIMEOUT) {
				log.warn("Input timeout occurred for FormInputTool for rootPlanId: {} from sub-plan {}", rootPlanId,
						currentPlanId);

				UserMessage userMessage = UserMessage.builder().text("Input timeout occurred for form: ").build();
				processUserInputToMemory(userMessage);
				userInputService.removeFormInputTool(rootPlanId);
				param.setResult("Input timeout occurred");

				return new AgentExecResult("Input timeout occurred.", AgentState.IN_PROGRESS);
			}
			else {
				throw new RuntimeException("FormInputTool is not in the correct state");
			}
		}
		else {
			throw new RuntimeException("FormInputTool is not in the correct state");
		}
	}

	/**
	 * Process tool result to remove escaped JSON if it's a valid JSON string. This fixes
	 * the issue where DefaultToolCallingManager returns escaped JSON strings.
	 * @param result The raw tool result string
	 * @return Processed result with unescaped JSON if applicable
	 */
	private String processToolResult(String result) {
		if (result == null || result.trim().isEmpty()) {
			return result;
		}

		// Try to parse and re-serialize if it's a valid JSON string
		// This removes escaping that might have been added by DefaultToolCallingManager
		try {
			// First, try to parse as JSON object
			Object jsonObject = objectMapper.readValue(result, Object.class);

			// Check if it's a Map with "output" field (from DefaultToolCallingManager
			// format)
			if (jsonObject instanceof Map<?, ?> map) {
				Object outputValue = map.get("output");
				if (outputValue instanceof String outputString) {
					// The output field contains an escaped JSON string, parse it
					try {
						Object innerJsonObject = objectMapper.readValue(outputString, Object.class);
						// Create a new map with the parsed inner JSON object, preserving
						// the "output" field
						Map<String, Object> resultMap = new HashMap<>();
						// Copy all entries from the original map
						for (Map.Entry<?, ?> entry : map.entrySet()) {
							if (entry.getKey() instanceof String key) {
								resultMap.put(key, entry.getValue());
							}
						}
						resultMap.put("output", innerJsonObject);
						// Return the unescaped JSON string with output field preserved
						return objectMapper.writeValueAsString(resultMap);
					}
					catch (Exception innerException) {
						// If inner parsing fails, return the original map as-is
						return objectMapper.writeValueAsString(jsonObject);
					}
				}
				else {
					// It's a Map but no "output" field or output is not a string,
					// re-serialize as-is
					return objectMapper.writeValueAsString(jsonObject);
				}
			}
			// If the parsed object is a String, it means the input was a JSON string
			// (e.g., "\"{\\\"message\\\":[...]}\""), so we need to parse it again
			else if (jsonObject instanceof String jsonString) {
				// Try to parse the inner JSON string
				try {
					Object innerJsonObject = objectMapper.readValue(jsonString, Object.class);
					// Re-serialize the inner JSON object
					return objectMapper.writeValueAsString(innerJsonObject);
				}
				catch (Exception innerException) {
					// If inner parsing fails, return the parsed string as-is
					return jsonString;
				}
			}
			else {
				// It's already a JSON object, re-serialize it
				return objectMapper.writeValueAsString(jsonObject);
			}
		}
		catch (Exception e) {
			// If it's not valid JSON, return as-is
			return result;
		}
	}

	/**
	 * Record action result with simplified parameters
	 */
	private void recordActionResult(List<ActToolParam> actToolInfoList) {
		planExecutionRecorder.recordActionResult(actToolInfoList);
	}

	private void processUserInputToMemory(UserMessage userMessage) {
		if (userMessage != null && userMessage.getText() != null) {
			// Process the user message to update memory
			String userInput = userMessage.getText();

			if (!StringUtils.isBlank(userInput)) {
				// Add user input to memory

				llmService.getAgentMemory(manusProperties.getMaxMemory()).add(getCurrentPlanId(), userMessage);

			}
		}
	}

	private void processMemory(ToolExecutionResult toolExecutionResult) {
		if (toolExecutionResult == null) {
			return;
		}
		// Process the conversation history to update memory
		List<Message> messages = toolExecutionResult.conversationHistory();
		if (messages.isEmpty()) {
			return;
		}
		// clear current plan memory
		llmService.getAgentMemory(manusProperties.getMaxMemory()).clear(getCurrentPlanId());
		for (Message message : messages) {
			// exclude all system message
			if (message instanceof SystemMessage) {
				continue;
			}
			// exclude env data message
			if (message instanceof UserMessage userMessage
					&& userMessage.getMetadata().containsKey(CURRENT_STEP_ENV_DATA_KEY)) {
				continue;
			}
			// only keep assistant message and tool_call message
			llmService.getAgentMemory(manusProperties.getMaxMemory()).add(getCurrentPlanId(), message);
		}
	}

	@Override
	public String getName() {
		return this.agentName;
	}

	@Override
	public String getDescription() {
		return this.agentDescription;
	}

	@Override
	protected Message getNextStepWithEnvMessage() {
		if (StringUtils.isBlank(this.nextStepPrompt)) {
			return new UserMessage("");
		}
		PromptTemplate promptTemplate = new SystemPromptTemplate(this.nextStepPrompt);
		Message userMessage = promptTemplate.createMessage(getMergedData());
		return userMessage;
	}

	private Map<String, Object> getMergedData() {
		Map<String, Object> data = new HashMap<>();
		data.putAll(getInitSettingData());
		data.put(AbstractPlanExecutor.EXECUTION_ENV_STRING_KEY, convertEnvDataToString());
		return data;
	}

	@Override
	protected Message getThinkMessage() {
		Message baseThinkPrompt = super.getThinkMessage();
		Message nextStepWithEnvMessage = getNextStepWithEnvMessage();
		SystemMessage thinkMessage = new SystemMessage("""
				<SystemInfo>
				%s
				</SystemInfo>

				<AgentInfo>
				%s
				</AgentInfo>
				""".formatted(baseThinkPrompt.getText(), nextStepWithEnvMessage.getText()));
		return thinkMessage;
	}

	/**
	 * Current step env data
	 * @return User message for current step environment data
	 */
	private Message currentStepEnvMessage() {
		String currentStepEnv = """
				- Current step environment information:
				{current_step_env_data}
				""";
		PromptTemplate template = new PromptTemplate(currentStepEnv);
		Message stepEnvMessage = template.createMessage(getMergedData());
		// mark as current step env data
		stepEnvMessage.getMetadata().put(CURRENT_STEP_ENV_DATA_KEY, Boolean.TRUE);
		return stepEnvMessage;
	}

	public ToolCallBackContext getToolCallBackContext(String toolKey) {
		Map<String, ToolCallBackContext> toolCallBackContext = toolCallbackProvider.getToolCallBackContext();
		if (toolCallBackContext.containsKey(toolKey)) {
			return toolCallBackContext.get(toolKey);
		}
		else {
			log.warn("Tool callback for {} not found in the map.", toolKey);
			return null;
		}
	}

	@Override
	public List<ToolCallback> getToolCallList() {
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		Map<String, ToolCallBackContext> toolCallBackContext = toolCallbackProvider.getToolCallBackContext();
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
		return toolCallbacks;
	}

	public void addEnvData(String key, String value) {
		Map<String, Object> data = super.getInitSettingData();
		if (data == null) {
			throw new IllegalStateException("Data map is null. Cannot add environment data.");
		}
		data.put(key, value);
	}

	public void setToolCallbackProvider(ToolCallbackProvider toolCallbackProvider) {
		this.toolCallbackProvider = toolCallbackProvider;
	}

	protected String collectEnvData(String toolCallName) {
		log.info("🔍 collectEnvData called for tool: {}", toolCallName);
		ToolCallBackContext context = toolCallbackProvider.getToolCallBackContext().get(toolCallName);
		if (context != null) {
			String envData = context.getFunctionInstance().getCurrentToolStateString();
			return envData;
		}
		// If corresponding tool callback context is not found, return empty string
		log.warn("⚠️ No context found for tool: {}", toolCallName);
		return "";
	}

	public void collectAndSetEnvDataForTools() {

		Map<String, Object> toolEnvDataMap = new HashMap<>();

		Map<String, Object> oldMap = getEnvData();
		toolEnvDataMap.putAll(oldMap);

		// Overwrite old data with new data
		for (String toolKey : availableToolKeys) {
			String envData = collectEnvData(toolKey);
			toolEnvDataMap.put(toolKey, envData);
		}
		//log.debug("Collected tool environment data: {}", toolEnvDataMap);

		setEnvData(toolEnvDataMap);
	}

	public String convertEnvDataToString() {
		StringBuilder envDataStringBuilder = new StringBuilder();

		for (String toolKey : availableToolKeys) {
			Object value = getEnvData().get(toolKey);
			if (value == null || value.toString().isEmpty()) {
				continue; // Skip tools with no data
			}
			envDataStringBuilder.append(toolKey).append(" context information:\n");
			envDataStringBuilder.append("    ").append(value.toString()).append("\n");
		}

		return envDataStringBuilder.toString();
	}

	// Add a method to wait for user input or handle timeout.
	private void waitForUserInputOrTimeout(FormInputTool formInputTool) {
		log.info("Waiting for user input for planId: {}...", getCurrentPlanId());
		long startTime = System.currentTimeMillis();
		long lastInterruptionCheck = startTime;
		// Get timeout from ManusProperties and convert to milliseconds
		long userInputTimeoutMs = getManusProperties().getUserInputTimeout() * 1000L;
		long interruptionCheckIntervalMs = 2000L; // Check for interruption every 2
													// seconds

		while (formInputTool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) {
			long currentTime = System.currentTimeMillis();

			// Check for interruption periodically
			if (currentTime - lastInterruptionCheck >= interruptionCheckIntervalMs) {
				if (agentInterruptionHelper != null
						&& !agentInterruptionHelper.checkInterruptionAndContinue(getRootPlanId())) {
					log.info("User input wait interrupted for rootPlanId: {}", getRootPlanId());
					formInputTool.handleInputTimeout(); // Treat interruption as timeout
					break;
				}
				lastInterruptionCheck = currentTime;
			}

			if (currentTime - startTime > userInputTimeoutMs) {
				log.warn("Timeout waiting for user input for planId: {}", getCurrentPlanId());
				formInputTool.handleInputTimeout(); // This will change its state to
				// INPUT_TIMEOUT
				break;
			}
			try {
				// Poll for input state change. In a real scenario, this might involve
				// a more sophisticated mechanism like a Future or a callback from the UI.
				TimeUnit.MILLISECONDS.sleep(500); // Check every 500ms
			}
			catch (InterruptedException e) {
				log.warn("Interrupted while waiting for user input for planId: {}", getCurrentPlanId());
				Thread.currentThread().interrupt();
				formInputTool.handleInputTimeout(); // Treat interruption as timeout for
				// simplicity
				break;
			}
		}
		if (formInputTool.getInputState() == FormInputTool.InputState.INPUT_RECEIVED) {
			log.info("User input received for planId: {}", getCurrentPlanId());
		}
		else if (formInputTool.getInputState() == FormInputTool.InputState.INPUT_TIMEOUT) {
			log.warn("User input timed out for planId: {}", getCurrentPlanId());
		}
	}

}
