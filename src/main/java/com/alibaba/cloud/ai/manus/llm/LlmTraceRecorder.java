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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Component
public class LlmTraceRecorder {

	private static final Logger logger = LoggerFactory.getLogger("LLM_REQUEST_LOGGER");

	private static final Logger selfLogger = LoggerFactory.getLogger(LlmTraceRecorder.class);

	private String planId;

	@Autowired
	private ObjectMapper objectMapper;

	public void recordRequest(OpenAiApi.ChatCompletionRequest chatRequest) {
		try {
			logger.info("Request[{}]: {}", planId, objectMapper.writer().writeValueAsString(chatRequest));
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize chat request", e);
		}
	}

	public void recordErrorResponse(WebClientResponseException webClientResponseException) {
		try {
			logger.info("Response[{}] :\nStatus code: {} \nStatus Text : {} \nHeaders: {} \nBody: {} \n ", planId,
					webClientResponseException.getStatusCode(), webClientResponseException.getStatusText(),
					webClientResponseException.getHeaders(), webClientResponseException.getResponseBodyAsString());
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize error response", e);
		}

	}

	public void recordResponse(ChatResponse chatResponse) {
		try {
			logger.info("Response[{}]: {}", planId, objectMapper.writer().writeValueAsString(chatResponse));
		}
		catch (Throwable e) {
			selfLogger.error("Failed to serialize chat response", e);
		}
	}

	public void setPlanId(String planId) {
		this.planId = planId;
	}

	public void cleaningPlanId() {
		this.planId = null;
	}

}
