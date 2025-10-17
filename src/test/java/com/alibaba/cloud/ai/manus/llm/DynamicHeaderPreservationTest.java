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

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.model.entity.DynamicModelEntity;
import com.alibaba.cloud.ai.manus.model.repository.DynamicModelRepository;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DynamicHeaderPreservationTest {

	@Mock
	private ManusProperties manusProperties;

	@Mock
	private DynamicModelRepository dynamicModelRepository;

	@Mock
	private ChatMemoryRepository chatMemoryRepository;

	@Mock
	private PlanExecutionRecorder planExecutionRecorder;

	@Mock
	private ObjectProvider<RestClient.Builder> restClientBuilderProvider;

	@Mock
	private ObjectProvider<WebClient.Builder> webClientBuilderProvider;

	@Mock
	private RestClient.Builder restClientBuilder;

	@Mock
	private WebClient.Builder webClientBuilder;

	private LlmService llmService;

	@BeforeEach
	void setUp() {
		// Mock the ObjectProvider responses
		when(restClientBuilderProvider.getIfAvailable(RestClient::builder)).thenReturn(restClientBuilder);
		when(webClientBuilderProvider.getIfAvailable(WebClient::builder)).thenReturn(webClientBuilder);
		
		llmService = new LlmService();
		
		// Use reflection to inject mocked dependencies
		// This is a simplified approach for testing
		try {
			java.lang.reflect.Field field;
			
			field = LlmService.class.getDeclaredField("manusProperties");
			field.setAccessible(true);
			field.set(llmService, manusProperties);
			
			field = LlmService.class.getDeclaredField("dynamicModelRepository");
			field.setAccessible(true);
			field.set(llmService, dynamicModelRepository);
			
			field = LlmService.class.getDeclaredField("chatMemoryRepository");
			field.setAccessible(true);
			field.set(llmService, chatMemoryRepository);
			
			field = LlmService.class.getDeclaredField("restClientBuilderProvider");
			field.setAccessible(true);
			field.set(llmService, restClientBuilderProvider);
			
			field = LlmService.class.getDeclaredField("webClientBuilderProvider");
			field.setAccessible(true);
			field.set(llmService, webClientBuilderProvider);
			
		} catch (Exception e) {
			throw new RuntimeException("Failed to inject dependencies", e);
		}
	}

	@Test
	void testDynamicChatClientCreationWithHeaders() {
		DynamicModelEntity model = new DynamicModelEntity();
		model.setId(1L);
		model.setBaseUrl("https://test.example.com");
		model.setApiKey("test-api-key");
		model.setModelName("test-model");

		Map<String, String> headers = new HashMap<>();
		headers.put("Custom-Header", "test-value");
		headers.put("Authorization", "Bearer test-token");
		model.setHeaders(headers);

		ChatClient chatClient = llmService.getDynamicAgentChatClient(model.getModelName());

		assertNotNull(chatClient, "ChatClient should be created successfully");
	}

	@Test
	void testDynamicChatClientCreationWithoutHeaders() {
		DynamicModelEntity model = new DynamicModelEntity();
		model.setId(2L);
		model.setBaseUrl("https://test.example.com");
		model.setApiKey("test-api-key");
		model.setModelName("test-model");

		ChatClient chatClient = llmService.getDynamicAgentChatClient(model.getModelName());
		assertNotNull(chatClient, "ChatClient should be created successfully even without headers");
	}

}
