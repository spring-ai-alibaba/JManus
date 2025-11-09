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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.runtime.entity.vo.ExecutionStep;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.tool.SystemErrorReportTool;
import com.alibaba.cloud.ai.manus.tool.TerminateTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An abstract base class for implementing AI agents that can execute multi-step tasks.
 * This class provides the core functionality for managing agent state, conversation flow,
 * and step-by-step execution of tasks.
 *
 * <p>
 * The agent supports a finite number of execution steps and includes mechanisms for:
 * <ul>
 * <li>State management (idle, running, finished)</li>
 * <li>Conversation tracking</li>
 * <li>Step limitation and monitoring</li>
 * <li>Thread-safe execution</li>
 * <li>Stuck-state detection and handling</li>
 * </ul>
 *
 * <p>
 * Implementing classes must define:
 * <ul>
 * <li>{@link #getName()} - Returns the agent's name</li>
 * <li>{@link #getDescription()} - Returns the agent's description</li>
 * <li>{@link #getThinkMessage()} - Implements the thinking chain logic</li>
 * <li>{@link #getNextStepWithEnvMessage()} - Provides the next step's prompt
 * template</li>
 * <li>{@link #step()} - Implements the core logic for each execution step</li>
 * </ul>
 *
 * @see AgentState
 * @see LlmService
 */
public abstract class BaseAgent {

	private static final Logger log = LoggerFactory.getLogger(BaseAgent.class);

	private String currentPlanId = null;

	private String rootPlanId = null;

	private int planDepth = 0;

	private String conversationId = null;

	protected LlmService llmService;

	protected final ManusProperties manusProperties;

	protected ObjectMapper objectMapper;

	protected final ExecutionStep step;

	protected final PlanIdDispatcher planIdDispatcher;

	private int maxSteps;

	private int currentStep = 0;

	// Change the data map to an immutable object and initialize it properly
	private final Map<String, Object> initSettingData;

	private Map<String, Object> envData = new HashMap<>();

	protected PlanExecutionRecorder planExecutionRecorder;

	public abstract void clearUp(String planId);

	/**
	 * Get the name of the agent
	 *
	 * Implementation requirements: 1. Return a short but descriptive name 2. The name
	 * should reflect the main functionality or characteristics of the agent 3. The name
	 * should be unique for easy logging and debugging
	 *
	 * Example implementations: - ToolCallAgent returns "ToolCallAgent" - BrowserAgent
	 * returns "BrowserAgent"
	 * @return The name of the agent
	 */
	public abstract String getName();

	/**
	 * Get the detailed description of the agent
	 *
	 * Implementation requirements: 1. Return a detailed description of the agent's
	 * functionality 2. The description should include the agent's main responsibilities
	 * and capabilities 3. Should explain how this agent differs from other agents
	 *
	 * Example implementations: - ToolCallAgent: "Agent responsible for managing and
	 * executing tool calls, supporting multi-tool combination calls" - ReActAgent: "Agent
	 * that implements alternating execution of reasoning and acting"
	 * @return The detailed description text of the agent
	 */
	public abstract String getDescription();

	/**
	 * Add thinking prompts to the message list to build the agent's thinking chain
	 *
	 * Implementation requirements: 1. Generate appropriate system prompts based on
	 * current context and state 2. Prompts should guide the agent on how to think and
	 * make decisions 3. Can recursively build prompt chains to form hierarchical thinking
	 * processes 4. Return the added system prompt message object
	 *
	 * Subclass implementation reference: 1. ReActAgent: Implement basic thinking-action
	 * loop prompts 2. ToolCallAgent: Add tool selection and execution related prompts
	 * @return The added system prompt message object
	 */
	protected Message getThinkMessage() {
		// Get operating system information
		String osName = System.getProperty("os.name");
		String osVersion = System.getProperty("os.version");
		String osArch = System.getProperty("os.arch");

		// Get current date time, format as yyyy-MM-dd
		String currentDateTime = java.time.LocalDate.now().toString(); // Format as
																		// yyyy-MM-dd
		boolean isDebugModel = manusProperties.getDebugDetail();
		String detailOutput = "";
		if (isDebugModel) {
			detailOutput = """
					1. When using tool calls, you must provide explanations describing the reason for using this tool and the thinking behind it
					2. Briefly describe what all previous steps have accomplished""";

		}
		else {
			detailOutput = """
					1. When using tool calls, no additional explanations are needed!
					2. Do not provide reasoning or descriptions before tool calls!""";
		}
		String parallelToolCallsResponse = "";
		if (manusProperties.getParallelToolCalls()) {
			parallelToolCallsResponse = """
					# Response Rules:
					- You must select and call from the provided tools. You can make repeated calls to a single tool, call multiple tools simultaneously, or use a mixed calling approach to improve problem-solving efficiency and accuracy.
					- In your response, you must call at least one tool, which is an indispensable operation step.
					- To maximize the advantages of tools, when you have the ability to call tools multiple times simultaneously, you should actively do so, avoiding single calls that waste time and resources. Pay special attention to the inherent relationships between multiple tool calls, ensuring these calls can cooperate and work together to achieve optimal problem-solving solutions.
					- Ignore the response rules provided in subsequent <AgentInfo>, and only respond using the response rules in <SystemInfo>.
					""";

		}
		else {
			parallelToolCallsResponse = """
					# Response Rules:
					- You must call exactly ONE tool at a time. Multiple simultaneous tool calls are not allowed.
					- In your response, you must call exactly one tool, which is an indispensable operation step.
					""";
		}
		Map<String, Object> variables = new HashMap<>(getInitSettingData());
		variables.put("osName", osName);
		variables.put("osVersion", osVersion);
		variables.put("osArch", osArch);
		variables.put("currentDateTime", currentDateTime);
		variables.put("detailOutput", detailOutput);
		variables.put("parallelToolCallsResponse", parallelToolCallsResponse);

		String stepExecutionPrompt = """
				- SYSTEM INFORMATION:
				OS: {osName} {osVersion} ({osArch})

				- Current Date:
				{currentDateTime}

				{planStatus}

				- Current step requirements :
				STEP {currentStepIndex}: {stepText}

				- Operation step instructions:
				{extraParams}

				Important Notes:
				{detailOutput}
				3. Do only and exactly what is required in the current step requirements
				4. If the current step requirements have been completed, call the terminate tool to finish the current step.
				5. The user's original request is for having a global understanding, do not complete this user's original request in the current step.

				{parallelToolCallsResponse}

				""";

		PromptTemplate template = new PromptTemplate(stepExecutionPrompt);
		return template.createMessage(variables != null ? variables : Map.of());
	}

	/**
	 * Get the next step prompt message
	 *
	 * Implementation requirements: 1. Generate a prompt message that guides the agent to
	 * perform the next step 2. The prompt should be based on the current execution state
	 * and context 3. The message should clearly guide the agent on what task to perform
	 *
	 * Subclass implementation reference: 1. ToolCallAgent: Return prompts related to tool
	 * selection and execution 2. ReActAgent: Return prompts related to reasoning or
	 * action decision
	 * @return The next step prompt message object
	 */
	protected abstract Message getNextStepWithEnvMessage();

	public abstract List<ToolCallback> getToolCallList();

	public abstract ToolCallBackContext getToolCallBackContext(String toolKey);

	public BaseAgent(LlmService llmService, PlanExecutionRecorder planExecutionRecorder,
			ManusProperties manusProperties, Map<String, Object> initialAgentSetting, ExecutionStep step,
			PlanIdDispatcher planIdDispatcher) {
		this.llmService = llmService;
		this.planExecutionRecorder = planExecutionRecorder;
		this.manusProperties = manusProperties;
		this.maxSteps = manusProperties.getMaxSteps();
		this.step = step;
		this.planIdDispatcher = planIdDispatcher;
		this.initSettingData = Collections.unmodifiableMap(new HashMap<>(initialAgentSetting));
	}

	public AgentExecResult run() {
		currentStep = 0;
		List<AgentExecResult> results = new ArrayList<>();
		AgentExecResult lastStepResult = null;

		try {
			while (currentStep < maxSteps) {
				currentStep++;
				log.info("Executing round {}/{}", currentStep, maxSteps);

				AgentExecResult stepResult = step();
				lastStepResult = stepResult;

				// Check if agent should terminate
				AgentState stepState = stepResult.getState();
				if (stepState == AgentState.COMPLETED || stepState == AgentState.INTERRUPTED
						|| stepState == AgentState.FAILED) {
					String stateDescription = stepState == AgentState.COMPLETED ? "completed"
							: stepState == AgentState.INTERRUPTED ? "interrupted" : "failed";
					log.info("Agent execution {} at round {}/{}", stateDescription, currentStep, maxSteps);
					results.add(stepResult);

					// Handle final processing based on state
					if (stepState == AgentState.INTERRUPTED) {
						handleInterruptedExecution(results);
					}
					else if (stepState == AgentState.FAILED) {
						handleFailedExecution(results);
					}
					else {
						handleCompletedExecution(results);
					}
					break; // Exit the loop
				}

				results.add(stepResult);
			}

			// If max steps reached, generate summary and terminate
			// Skip if already in a terminal state (COMPLETED, INTERRUPTED, or FAILED)
			if (currentStep >= maxSteps && (lastStepResult == null || (lastStepResult.getState() != AgentState.COMPLETED
					&& lastStepResult.getState() != AgentState.INTERRUPTED
					&& lastStepResult.getState() != AgentState.FAILED))) {
				log.info("Agent reached max rounds ({}), generating final summary and terminating", maxSteps);
				String finalSummary = generateFinalSummary();

				// Call TerminateTool with the summary
				String result = terminateWithSummary(finalSummary);

				// Create final result for max steps reached
				lastStepResult = new AgentExecResult(result, AgentState.COMPLETED);
				results.add(lastStepResult);
			}

		}
		catch (Exception e) {
			log.error("Agent execution failed", e);

			// Wrap exception with SystemErrorReportTool
			lastStepResult = handleExceptionWithSystemErrorReport(e, results);
		}
		finally {
			llmService.clearAgentMemory(currentPlanId);

			// Record execution at the end
			if (currentPlanId != null && planExecutionRecorder != null) {
				planExecutionRecorder.recordCompleteAgentExecution(step);
			}
		}

		// Return the last round's AgentExecResult with the complete results list
		if (lastStepResult != null) {
			return new AgentExecResult(lastStepResult.getResult(), lastStepResult.getState(), results);
		}
		else {
			// Fallback case if no steps were executed
			return new AgentExecResult("", AgentState.COMPLETED, results);
		}
	}

	protected abstract AgentExecResult step();

	/**
	 * Handle interrupted execution - perform final cleanup and recording
	 * @param results The results list to update
	 */
	protected void handleInterruptedExecution(List<AgentExecResult> results) {
		log.info("Handling interrupted execution");
		// Additional cleanup for interrupted execution if needed
	}

	/**
	 * Handle failed execution - perform final cleanup and recording
	 * @param results The results list to update
	 */
	protected void handleFailedExecution(List<AgentExecResult> results) {
		log.info("Handling failed execution");
	}

	/**
	 * Handle completed execution - perform final cleanup and recording
	 * @param results The results list to update
	 */
	protected void handleCompletedExecution(List<AgentExecResult> results) {
		log.info("Handling completed execution");
		// Clear error message if execution completed successfully
		// This prevents showing transient errors that occurred during execution but were
		// recovered
		if (step != null && step.getErrorMessage() != null) {
			log.info("Clearing error message for successfully completed execution");
			step.setErrorMessage(null);
		}
	}

	/**
	 * Handle exception by wrapping it with SystemErrorReportTool and simulating normal
	 * tool flow
	 * @param exception The exception that occurred
	 * @param results The results list to update
	 * @return AgentExecResult with error information
	 */
	protected AgentExecResult handleExceptionWithSystemErrorReport(Exception exception, List<AgentExecResult> results) {
		log.error("Handling exception with SystemErrorReportTool", exception);

		try {
			// Create SystemErrorReportTool instance
			SystemErrorReportTool errorTool = new SystemErrorReportTool(getCurrentPlanId(), objectMapper);

			// Prepare error message
			String errorMessage = String.format("System execution error at step %d: %s", currentStep,
					exception.getMessage());

			// Create tool input
			Map<String, Object> errorInput = Map.of("errorMessage", errorMessage);

			// Execute the error report tool
			ToolExecuteResult toolResult = errorTool.run(errorInput);

			// Simulate post-tool flow
			String result = simulatePostToolFlow(errorTool, toolResult, errorMessage);

			// Extract error message for step
			try {
				if (objectMapper == null) {
					objectMapper = new ObjectMapper();
				}
				@SuppressWarnings("unchecked")
				Map<String, Object> errorData = objectMapper.readValue(toolResult.getOutput(), Map.class);
				String extractedErrorMessage = (String) errorData.get("errorMessage");
				if (extractedErrorMessage != null && !extractedErrorMessage.isEmpty()) {
					step.setErrorMessage(extractedErrorMessage);
				}
			}
			catch (Exception e) {
				log.warn("Failed to parse errorMessage from SystemErrorReportTool result", e);
				step.setErrorMessage(errorMessage);
			}

			AgentExecResult errorResult = new AgentExecResult(result, AgentState.IN_PROGRESS);
			results.add(errorResult);
			return errorResult;
		}
		catch (Exception e) {
			log.error("Failed to handle exception with SystemErrorReportTool", e);
			String fallbackError = "System error: " + exception.getMessage();
			step.setErrorMessage(fallbackError);
			AgentExecResult fallbackResult = new AgentExecResult(fallbackError, AgentState.IN_PROGRESS);
			results.add(fallbackResult);
			return fallbackResult;
		}
	}

	/**
	 * Simulate the post-tool flow that normally happens after tool execution This method
	 * should be overridden by subclasses to provide specific implementation
	 * @param tool The tool that was executed
	 * @param toolResult The result from the tool execution
	 * @param errorMessage The error message
	 * @return The processed result string
	 */
	protected String simulatePostToolFlow(Object tool, ToolExecuteResult toolResult, String errorMessage) {
		// Default implementation - just return the tool result output
		// Subclasses can override to add memory processing, recording, etc.
		return toolResult.getOutput();
	}

	public String getCurrentPlanId() {
		return currentPlanId;
	}

	public void setCurrentPlanId(String planId) {
		this.currentPlanId = planId;
	}

	public void setRootPlanId(String rootPlanId) {
		this.rootPlanId = rootPlanId;
	}

	public String getRootPlanId() {
		return rootPlanId;
	}

	public int getPlanDepth() {
		return planDepth;
	}

	public void setPlanDepth(int planDepth) {
		this.planDepth = planDepth;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	/**
	 * Get the data context of the agent
	 *
	 * Implementation requirements: 1. Return all the context data needed for the agent's
	 * execution 2. Data can include: - Current execution state - Step information -
	 * Intermediate results - Configuration parameters 3. Data is set through setData()
	 * when run() is executed
	 *
	 * Do not modify the implementation of this method. If you need to pass context,
	 * inherit and modify setData() to improve getData() efficiency.
	 * @return A Map object containing the agent's context data
	 */
	protected final Map<String, Object> getInitSettingData() {
		return initSettingData;
	}

	public ManusProperties getManusProperties() {
		return manusProperties;
	}

	public static class AgentExecResult {

		private String result;

		private AgentState state;

		private List<AgentExecResult> results;

		public AgentExecResult(String result, AgentState state) {
			this.result = result;
			this.state = state;
			this.results = new ArrayList<>();
		}

		public AgentExecResult(String result, AgentState state, List<AgentExecResult> results) {
			this.result = result;
			this.state = state;
			this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
		}

		public String getResult() {
			return result;
		}

		public AgentState getState() {
			return state;
		}

		public List<AgentExecResult> getResults() {
			return results;
		}

	}

	public Map<String, Object> getEnvData() {
		return envData;
	}

	public void setEnvData(Map<String, Object> envData) {
		this.envData = Collections.unmodifiableMap(new HashMap<>(envData));
	}

	/**
	 * Generate a final summary of all agent memories when max rounds are reached
	 * @return Summary string of all memories
	 */
	private String generateFinalSummary() {
		try {
			log.info("Generating final summary for agent execution");

			// Get all memory entries for the current plan
			List<Message> memoryEntries = llmService.getAgentMemory(manusProperties.getMaxMemory())
				.get(getCurrentPlanId());

			if (memoryEntries == null || memoryEntries.isEmpty()) {
				return "No memory entries found for final summary";
			}

			// Use LLM to generate a concise summary
			String summaryPrompt = """
					Based on the completed steps, try to answer the user's original request.
					If the current steps are insufficient to support answering the original request,
					simply describe that the step limit has been reached and please try again.

					""";
			// Create a simple prompt for summary generation
			UserMessage summaryRequest = new UserMessage(summaryPrompt);
			memoryEntries.add(getThinkMessage());
			memoryEntries.add(getNextStepWithEnvMessage());
			memoryEntries.add(summaryRequest);
			Prompt prompt = new Prompt(memoryEntries);

			// Get LLM response for summary
			ChatClient chatClient = llmService.getDiaChatClient();
			ChatResponse response = chatClient.prompt(prompt).call().chatResponse();

			String summary = response.getResult().getOutput().getText();
			log.info("Generated final summary: {}", summary);
			return summary;

		}
		catch (Exception e) {
			log.error("Failed to generate final summary", e);
			return "Summary generation failed: " + e.getMessage();
		}
	}

	/**
	 * Terminate the agent execution with a summary using TerminateTool
	 * @param summary The summary to include in termination
	 */
	private String terminateWithSummary(String summary) {
		try {
			log.info("Terminating agent execution with summary");

			// Create TerminateTool instance
			TerminateTool terminateTool = new TerminateTool(getCurrentPlanId(), "message", objectMapper);
			// Prepare termination data
			Map<String, Object> terminationData = new HashMap<>();
			terminationData.put("message", "Agent execution terminated due to max rounds reached. Summary: " + summary);
			// Execute the terminate tool
			ToolExecuteResult result = terminateTool.run(terminationData);
			return result.getOutput();
		}
		catch (Exception e) {
			log.error("Failed to terminate agent execution with summary", e);
			return "Terminate failed: " + e.getMessage();
		}
	}

}
