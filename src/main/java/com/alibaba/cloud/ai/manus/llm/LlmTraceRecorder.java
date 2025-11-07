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
package com.alibaba.cloud.ai.manus.llm;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class LlmTraceRecorder {

	private static final Logger logger = LoggerFactory.getLogger("LLM_REQUEST_LOGGER");

	private static final Logger selfLogger = LoggerFactory.getLogger(LlmTraceRecorder.class);

	private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

	@Autowired
	private ObjectMapper objectMapper;

	public void recordRequest(OpenAiApi.ChatCompletionRequest chatRequest) {
		try {
			logger.info("Request[{}]: {}", REQUEST_ID.get(), objectMapper.writer().writeValueAsString(chatRequest));
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize chat request", e);
		}
	}

	public void recordResponse(ChatResponse chatResponse) {
		try {
			logger.info("Response[{}]: {}", REQUEST_ID.get(), objectMapper.writer().writeValueAsString(chatResponse));
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
				String errorDetails = String.format(
						"Error[%s]: Status=%s, ResponseBody=%s, URL=%s",
						REQUEST_ID.get(),
						webClientException.getStatusCode(),
						webClientException.getResponseBodyAsString(),
						webClientException.getRequest() != null ? webClientException.getRequest().getURI() : "N/A");
				logger.error(errorDetails);
			}
			else {
				logger.error("Error[{}]: {}", REQUEST_ID.get(), error.getMessage());
			}
		}
		catch (Throwable e) {
			selfLogger.error("Failed to record error", e);
		}
	}

	public static void initRequest() {
		REQUEST_ID.set(UUID.randomUUID().toString());
	}

	public static void clearRequest() {
		REQUEST_ID.remove();
	}

}
