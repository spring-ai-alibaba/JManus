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
package com.alibaba.cloud.ai.lynxe.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.alibaba.cloud.ai.lynxe.event.LynxeEventPublisher;
import com.alibaba.cloud.ai.lynxe.event.PlanExceptionEvent;

import reactor.core.publisher.Flux;

/**
 * A utility class for handling streaming chat responses with periodic progress logging.
 * This class merges text content and tool calls from multiple streaming responses and
 * provides regular progress updates to prevent users from thinking the model is
 * unresponsive.
 */
@Component
public class StreamingResponseHandler {

	private static final Logger log = LoggerFactory.getLogger(StreamingResponseHandler.class);

	// Logger for streaming progress log file (separate from llm-requests)
	private static final Logger streamingProgressLogger = LoggerFactory.getLogger("STREAMING_PROGRESS_LOGGER");

	@Autowired
	private LynxeEventPublisher lynxeEventPublisher;

	@Autowired
	private LlmTraceRecorder llmTraceRecorder;

	/**
	 * Result container for streaming response processing
	 */
	public static class StreamingResult {

		private final ChatResponse lastResponse;

		public StreamingResult(ChatResponse lastResponse) {
			this.lastResponse = lastResponse;
		}

		public ChatResponse getLastResponse() {
			return lastResponse;
		}

		/**
		 * Get tool calls, preferring merged calls if available, otherwise fall back to
		 * last response
		 */
		public List<ToolCall> getEffectiveToolCalls() {
			return lastResponse != null && lastResponse.getResult() != null
					&& lastResponse.getResult().getOutput() != null
							? lastResponse.getResult().getOutput().getToolCalls() : Collections.emptyList();
		}

		/**
		 * Get text content, preferring merged text if available, otherwise fall back to
		 * last response
		 */
		public String getEffectiveText() {
			return lastResponse != null && lastResponse.getResult() != null
					&& lastResponse.getResult().getOutput() != null ? lastResponse.getResult().getOutput().getText()
							: "";
		}

	}

	/**
	 * Process a streaming chat response flux with periodic progress logging
	 * @param responseFlux The streaming chat response flux
	 * @param contextName A descriptive name for logging context (e.g., "Agent thinking",
	 * "Plan creation")
	 * @param planId The plan ID for event publishing
	 * @param isDebugModel Whether debug mode is enabled. If false, will early-terminate
	 * when only thinking text (no tool calls) is detected
	 * @param enableEarlyTermination Whether to enable early termination for thinking-only
	 * responses. Should be false for text-only generation tasks (e.g., summaries)
	 * @return StreamingResult containing merged content and the last response
	 */
	public StreamingResult processStreamingResponse(Flux<ChatResponse> responseFlux, String contextName, String planId,
			boolean isDebugModel, boolean enableEarlyTermination) {
		try {
			LlmTraceRecorder.initRequest();
			AtomicReference<Long> lastLogTime = new AtomicReference<>(System.currentTimeMillis());

			// Assistant Message
			AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(new StringBuilder());
			AtomicReference<List<ToolCall>> messageToolCallRef = new AtomicReference<>(
					Collections.synchronizedList(new ArrayList<>()));
			AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();

			// ChatGeneration Metadata
			AtomicReference<ChatGenerationMetadata> generationMetadataRef = new AtomicReference<>(
					ChatGenerationMetadata.NULL);

			// Usage
			AtomicReference<Integer> metadataUsagePromptTokensRef = new AtomicReference<Integer>(0);
			AtomicReference<Integer> metadataUsageGenerationTokensRef = new AtomicReference<Integer>(0);
			AtomicReference<Integer> metadataUsageTotalTokensRef = new AtomicReference<Integer>(0);

			AtomicReference<PromptMetadata> metadataPromptMetadataRef = new AtomicReference<>(PromptMetadata.empty());
			AtomicReference<RateLimit> metadataRateLimitRef = new AtomicReference<>(new EmptyRateLimit());

			AtomicReference<String> metadataIdRef = new AtomicReference<>("");
			AtomicReference<String> metadataModelRef = new AtomicReference<>("");
			AtomicReference<ChatResponse> finalChatResponseRef = new AtomicReference<>(null);

			AtomicInteger responseCounter = new AtomicInteger(0);
			long startTime = System.currentTimeMillis();

			// Early termination flag: when non-debug mode detects thinking-only response
			AtomicReference<Boolean> shouldEarlyTerminate = new AtomicReference<>(false);

			// Apply early termination logic for non-debug mode using takeUntil
			// Only enable if both isDebugModel is false AND enableEarlyTermination is
			// true
			Flux<ChatResponse> processedFlux = responseFlux;
			if (!isDebugModel && enableEarlyTermination) {
				processedFlux = responseFlux.takeUntil(chatResponse -> {
					// This predicate will be evaluated for each response
					// The actual termination logic is in doOnNext, but we use this to
					// stop the stream
					return shouldEarlyTerminate.get();
				});
			}

			Flux<ChatResponse> finalFlux = processedFlux.doOnSubscribe(subscription -> {
				messageTextContentRef.set(new StringBuilder());
				messageMetadataMapRef.set(new HashMap<>());
				metadataIdRef.set("");
				metadataModelRef.set("");
				metadataUsagePromptTokensRef.set(0);
				metadataUsageGenerationTokensRef.set(0);
				metadataUsageTotalTokensRef.set(0);
				metadataPromptMetadataRef.set(PromptMetadata.empty());
				metadataRateLimitRef.set(new EmptyRateLimit());

			}).doOnNext(chatResponse -> {
				responseCounter.incrementAndGet();

				if (chatResponse.getResult() != null) {
					if (chatResponse.getResult().getMetadata() != null
							&& chatResponse.getResult().getMetadata() != ChatGenerationMetadata.NULL) {
						generationMetadataRef.set(chatResponse.getResult().getMetadata());
					}
					if (chatResponse.getResult().getOutput().getText() != null) {
						messageTextContentRef.get().append(chatResponse.getResult().getOutput().getText());
					}
					messageToolCallRef.get().addAll(chatResponse.getResult().getOutput().getToolCalls());
					messageMetadataMapRef.get().putAll(chatResponse.getResult().getOutput().getMetadata());
				}

				// Early termination check for non-debug mode: detect thinking-only
				// response
				// Check after updating accumulated state to use latest data
				// Only check if early termination is enabled
				if (!isDebugModel && enableEarlyTermination && !shouldEarlyTerminate.get()) {
					// Wait for at least 3 responses to avoid premature termination
					if (responseCounter.get() >= 10) {
						// Check accumulated state (already updated above)
						boolean accumulatedHasText = StringUtils.hasText(messageTextContentRef.get().toString());
						boolean accumulatedHasToolCalls = !messageToolCallRef.get().isEmpty();

						// If we have text but no tool calls, terminate
						if (accumulatedHasText && !accumulatedHasToolCalls) {
							shouldEarlyTerminate.set(true);
							String textContent = messageTextContentRef.get().toString();
							String preview = getTextPreviewWithHeadAndTail(textContent, 200); // Show
																								// first
																								// 200
																								// and
																								// last
																								// 200
																								// chars
							log.info(
									"🛑 Early termination detected: thinking-only response ({} characters, no tool calls) in non-debug mode. Stopping stream. Content preview: '{}'",
									textContent.length(), preview);
						}
					}
				}
				if (chatResponse.getMetadata() != null) {
					if (chatResponse.getMetadata().getUsage() != null) {
						Usage usage = chatResponse.getMetadata().getUsage();
						metadataUsagePromptTokensRef.set(usage.getPromptTokens() > 0 ? usage.getPromptTokens()
								: metadataUsagePromptTokensRef.get());
						metadataUsageGenerationTokensRef.set(usage.getCompletionTokens() > 0
								? usage.getCompletionTokens() : metadataUsageGenerationTokensRef.get());
						metadataUsageTotalTokensRef.set(usage.getTotalTokens() > 0 ? usage.getTotalTokens()
								: metadataUsageTotalTokensRef.get());
					}
					if (chatResponse.getMetadata().getPromptMetadata() != null
							&& chatResponse.getMetadata().getPromptMetadata().iterator().hasNext()) {
						metadataPromptMetadataRef.set(chatResponse.getMetadata().getPromptMetadata());
					}
					if (chatResponse.getMetadata().getRateLimit() != null
							&& !(metadataRateLimitRef.get() instanceof EmptyRateLimit)) {
						metadataRateLimitRef.set(chatResponse.getMetadata().getRateLimit());
					}
					if (StringUtils.hasText(chatResponse.getMetadata().getId())) {
						metadataIdRef.set(chatResponse.getMetadata().getId());
					}
					if (StringUtils.hasText(chatResponse.getMetadata().getModel())) {
						metadataModelRef.set(chatResponse.getMetadata().getModel());
					}
				}

				// Check if 10 seconds have passed since last log output
				long currentTime = System.currentTimeMillis();
				long timeSinceLastLog = currentTime - lastLogTime.get();
				if (timeSinceLastLog >= 10000) { // 10 seconds = 10000 milliseconds
					logProgress(contextName, messageTextContentRef.get().toString(), messageToolCallRef.get(),
							responseCounter.get(), startTime);
					lastLogTime.set(currentTime);
				}
			}).doOnComplete(() -> {

				var usage = new MessageAggregator.DefaultUsage(metadataUsagePromptTokensRef.get(),
						metadataUsageGenerationTokensRef.get(), metadataUsageTotalTokensRef.get());

				var chatResponseMetadata = ChatResponseMetadata.builder()
					.id(metadataIdRef.get())
					.model(metadataModelRef.get())
					.rateLimit(metadataRateLimitRef.get())
					.usage(usage)
					.promptMetadata(metadataPromptMetadataRef.get())
					.build();
				finalChatResponseRef.set(
						new ChatResponse(List.of(new Generation(
								new AssistantMessage(messageTextContentRef.get().toString(),
										messageMetadataMapRef.get(), messageToolCallRef.get()),
								generationMetadataRef.get())), chatResponseMetadata));
				logCompletion(contextName, messageTextContentRef.get().toString(), messageToolCallRef.get().size(),
						responseCounter.get(), startTime, usage);

				messageTextContentRef.set(new StringBuilder());
				messageToolCallRef.set(Collections.synchronizedList(new ArrayList<>()));
				messageMetadataMapRef.set(new HashMap<>());
				metadataIdRef.set("");
				metadataModelRef.set("");
				metadataUsagePromptTokensRef.set(0);
				metadataUsageGenerationTokensRef.set(0);
				metadataUsageTotalTokensRef.set(0);
				metadataPromptMetadataRef.set(PromptMetadata.empty());
				metadataRateLimitRef.set(new EmptyRateLimit());

			}).doOnError(e -> {
				// Record error in trace logger
				llmTraceRecorder.recordError(e);

				// Enhanced error logging for API errors
				if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
					String responseBody = webClientException.getResponseBodyAsString();
					log.error(
							"❌ API Error - Status: {}, Response Body: {}, Request URL: {}, Request Method: {}. Check LLM_REQUEST_LOGGER for full request details.",
							webClientException.getStatusCode(),
							responseBody != null && !responseBody.isEmpty() ? responseBody : "(empty)",
							webClientException.getRequest() != null ? webClientException.getRequest().getURI() : "N/A",
							webClientException.getRequest() != null ? webClientException.getRequest().getMethod()
									: "N/A",
							webClientException);
				}
				else {
					log.error("Aggregation Error: {}", e.getMessage(), e);
				}
				lynxeEventPublisher.publish(new PlanExceptionEvent(planId, e));
			}).doOnCancel(() -> {
				if (shouldEarlyTerminate.get()) {
					log.info("Stream cancelled due to early termination (thinking-only response detected)");
				}
			});

			try {
				finalFlux.blockLast();
			}
			catch (Exception e) {
				// Record error in trace logger
				llmTraceRecorder.recordError(e);

				// Enhanced error logging for API errors
				if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
					String responseBody = webClientException.getResponseBodyAsString();
					log.error(
							"❌ API Error during blockLast() - Status: {}, Response Body: {}, Request URL: {}. Check LLM_REQUEST_LOGGER for full request details.",
							webClientException.getStatusCode(),
							responseBody != null && !responseBody.isEmpty() ? responseBody : "(empty)",
							webClientException.getRequest() != null ? webClientException.getRequest().getURI() : "N/A",
							webClientException);
				}
				else {
					log.error("Error during blockLast(): {}", e.getMessage(), e);
				}
				// Re-throw to be handled by outer try-catch
				throw e;
			}

			llmTraceRecorder.recordResponse(finalChatResponseRef.get());
			return new StreamingResult(finalChatResponseRef.get());
		}
		catch (Exception e) {
			// Final error handling - log and re-throw
			if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
				String responseBody = webClientException.getResponseBodyAsString();
				log.error(
						"❌ Final API Error - Status: {}, Response Body: {}. Full request details logged in LLM_REQUEST_LOGGER.",
						webClientException.getStatusCode(),
						responseBody != null && !responseBody.isEmpty() ? responseBody : "(empty)", webClientException);
			}
			throw e;
		}
		finally {
			LlmTraceRecorder.clearRequest();
		}
	}

	/**
	 * Process a streaming chat response flux for text-only content (e.g., summaries) This
	 * method does NOT enable early termination since text-only generation doesn't require
	 * tool calls
	 * @param responseFlux The streaming chat response flux
	 * @param contextName A descriptive name for logging context
	 * @param planId The plan ID for event publishing
	 * @param isDebugModel Whether debug mode is enabled
	 * @return The merged text content
	 */
	public String processStreamingTextResponse(Flux<ChatResponse> responseFlux, String contextName, String planId,
			boolean isDebugModel) {
		// For text-only responses, disable early termination (no tool calls expected)
		StreamingResult result = processStreamingResponse(responseFlux, contextName, planId, isDebugModel, false);
		return result.getEffectiveText();
	}

	private void logProgress(String contextName, String currentText, List<ToolCall> toolCalls, int responseCount,
			long startTime) {
		int textLength = currentText != null ? currentText.length() : 0;
		int toolCallCount = toolCalls != null ? toolCalls.size() : 0;
		String preview = getLastTextPreview(currentText, 100); // Show last 100 chars

		// Calculate characters per second
		long elapsedTime = System.currentTimeMillis() - startTime;
		double charsPerSecond = elapsedTime > 0 ? (textLength * 1000.0 / elapsedTime) : 0;

		// Build tool call details with parameters
		StringBuilder toolCallDetails = new StringBuilder();
		if (toolCalls != null && !toolCalls.isEmpty()) {
			toolCallDetails.append("Tool calls: ");
			for (int i = 0; i < toolCalls.size(); i++) {
				ToolCall toolCall = toolCalls.get(i);
				if (i > 0)
					toolCallDetails.append(", ");

				// Format: [id]name(args)
				toolCallDetails.append(String.format("[%s]%s", toolCall.id(), toolCall.name()));

				// Add parameters if available
				if (toolCall.arguments() != null && !toolCall.arguments().isEmpty()) {
					toolCallDetails.append("(");
					toolCallDetails.append(toolCall.arguments());
					toolCallDetails.append(")");
				}
			}
		}
		else {
			toolCallDetails.append("No tool calls");
		}

		// Log only to streaming progress log file
		String progressMessage = String.format(
				"🔄 %s - Progress[%dms]: %d responses received, %d characters (%.1f chars/sec), %d tool calls. %s. Last 100 chars: '%s'",
				contextName, elapsedTime, responseCount, textLength, charsPerSecond, toolCallCount,
				toolCallDetails.toString(), preview);

		streamingProgressLogger.info(progressMessage);
	}

	private void logCompletion(String contextName, String finalText, int toolCallCount, int responseCount,
			long startTime, Usage usage) {
		int textLength = finalText != null ? finalText.length() : 0;
		String preview = getTextPreview(finalText, 200); // Show first 200 chars for
		// completion

		log.info(
				"✅ {} - Completed[{}ms]: {} responses processed, {} characters, {} tool calls, {} prompt tokens, "
						+ "{} completion tokens, {} total tokens. Preview: '{}'",
				contextName, System.currentTimeMillis() - startTime, responseCount, textLength, toolCallCount,
				usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens(), preview);
	}

	private String getTextPreview(String text, int maxLength) {
		if (text == null || text.isEmpty()) {
			return "(empty)";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return text.substring(0, maxLength) + "...";
	}

	/**
	 * Get the last N characters of the text for preview
	 */
	private String getLastTextPreview(String text, int maxLength) {
		if (text == null || text.isEmpty()) {
			return "(empty)";
		}
		if (text.length() <= maxLength) {
			return text;
		}
		return "..." + text.substring(text.length() - maxLength);
	}

	/**
	 * Get text preview with first N and last N characters, with ellipsis in between
	 */
	private String getTextPreviewWithHeadAndTail(String text, int headLength) {
		if (text == null || text.isEmpty()) {
			return "(empty)";
		}
		int totalLength = text.length();
		if (totalLength <= headLength * 2) {
			return text; // If text is short enough, return it all
		}
		String head = text.substring(0, headLength);
		String tail = text.substring(totalLength - headLength);
		return head + "...[omitted " + (totalLength - headLength * 2) + " characters]..." + tail;
	}

}
