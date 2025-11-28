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

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Request-scoped recorder for LLM requests and responses. Each instance tracks one
 * request/response cycle.
 */
public class LlmTraceRecorder {

	private static final Logger logger = LoggerFactory.getLogger("LLM_REQUEST_LOGGER");

	private static final Logger selfLogger = LoggerFactory.getLogger(LlmTraceRecorder.class);

	private final ObjectMapper objectMapper;

	private final String requestId;

	private Integer inputCharCount;

	private Integer outputCharCount;

	/**
	 * Create a new LlmTraceRecorder instance for a request
	 * @param objectMapper ObjectMapper for JSON serialization
	 */
	public LlmTraceRecorder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
		this.requestId = UUID.randomUUID().toString();
		this.inputCharCount = 0;
		this.outputCharCount = 0;
	}

	public void recordRequest(OpenAiApi.ChatCompletionRequest chatRequest) {
		try {
			logger.info("Request[{}]: {}", requestId, objectMapper.writer().writeValueAsString(chatRequest));

			// Calculate input character count from all messages in the request
			int count = 0;
			if (chatRequest != null && chatRequest.messages() != null) {
				for (OpenAiApi.ChatCompletionMessage message : chatRequest.messages()) {
					try {
						// Try to get content as string (works for text-only messages)
						String content = message.content();
						if (content != null) {
							count += content.length();
						}
					}
					catch (IllegalStateException e) {
						// Content is not a string (e.g., contains media/images)
						// For media content, we can't easily count characters, so we skip it
						// or use a placeholder count. For now, we'll skip it.
						selfLogger.debug("Message contains non-string content (likely media), skipping character count");
					}
				}
			}
			this.inputCharCount = count;
			logger.info("Request[{}] InputCharCount: {}", requestId, count);
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize chat request", e);
		}
	}

	public void recordResponse(ChatResponse chatResponse) {
		try {
			String responseJson = objectMapper.writer().writeValueAsString(chatResponse);
			logger.info("Response[{}]: {}", requestId, objectMapper.writer().writeValueAsString(chatResponse));

			this.outputCharCount = responseJson.length();
			logger.info("Response[{}] OutputCharCount: {}", requestId, this.outputCharCount);
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize chat response", e);
		}
	}

	/**
	 * Record error response from API
	 * @param error The exception that occurred
	 */
	public void recordError(Throwable error) {
		try {
			if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientException) {
				String errorDetails = String.format("Error[%s]: Status=%s, ResponseBody=%s, URL=%s", requestId,
						webClientException.getStatusCode(), webClientException.getResponseBodyAsString(),
						webClientException.getRequest() != null ? webClientException.getRequest().getURI() : "N/A");
				logger.error(errorDetails);
			}
			else {
				logger.error("Error[{}]: {}", requestId, error.getMessage());
			}
		}
		catch (Throwable e) {
			selfLogger.error("Failed to record error", e);
		}
	}

	/**
	 * Get the request ID for this recorder instance
	 * @return Request ID
	 */
	public String getRequestId() {
		return requestId;
	}

	/**
	 * Set input character count (can be called if count is calculated elsewhere)
	 * @param count Input character count
	 */
	public void setInputCharCount(int count) {
		this.inputCharCount = count;
	}

	/**
	 * Get input character count for this request
	 * @return Input character count, or 0 if not available
	 */
	public int getInputCharCount() {
		return inputCharCount != null ? inputCharCount : 0;
	}

	/**
	 * Get output character count for this request
	 * @return Output character count, or 0 if not available
	 */
	public int getOutputCharCount() {
		return outputCharCount != null ? outputCharCount : 0;
	}

}
