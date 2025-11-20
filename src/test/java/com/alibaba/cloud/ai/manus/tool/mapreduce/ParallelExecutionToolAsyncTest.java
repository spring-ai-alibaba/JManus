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

package com.alibaba.cloud.ai.manus.tool.mapreduce;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.manus.planning.PlanningFactory.ToolCallBackContext;
import com.alibaba.cloud.ai.manus.runtime.executor.LevelBasedExecutorPool;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.mapreduce.ParallelExecutionTool.RegisterBatchInput;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test for async execution mode in ParallelExecutionTool
 */
class ParallelExecutionToolAsyncTest {

	private ParallelExecutionTool tool;

	private ObjectMapper objectMapper;

	private Map<String, ToolCallBackContext> toolCallbackMap;

	private PlanIdDispatcher planIdDispatcher;

	private LevelBasedExecutorPool executorPool;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		toolCallbackMap = new HashMap<>();
		planIdDispatcher = Mockito.mock(PlanIdDispatcher.class);
		executorPool = Mockito.mock(LevelBasedExecutorPool.class);

		Mockito.when(planIdDispatcher.generateToolCallId()).thenReturn("test-call-id-1");

		tool = new ParallelExecutionTool(objectMapper, toolCallbackMap, planIdDispatcher, executorPool);
	}

	@Test
	void testApplyAsyncReturnsCompletableFuture() {
		// Arrange
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("getPending");
		ToolContext context = new ToolContext(Map.of());

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		// Assert
		assertNotNull(future, "applyAsync should return a CompletableFuture");
		assertTrue(future.isDone(), "Simple operations should complete immediately");

		ToolExecuteResult result = future.join();
		assertNotNull(result);
		assertNotNull(result.getOutput());
	}

	@Test
	void testAsyncExecutionDoesNotBlock() throws Exception {
		// Arrange
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("getPending");
		ToolContext context = new ToolContext(Map.of());

		long startTime = System.currentTimeMillis();

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		long callTime = System.currentTimeMillis() - startTime;

		// Assert: applyAsync should return immediately (< 100ms)
		assertTrue(callTime < 100, "applyAsync should not block, took: " + callTime + "ms");

		// Wait for result with timeout
		ToolExecuteResult result = future.get(5, TimeUnit.SECONDS);
		assertNotNull(result);
	}

	@Test
	void testApplyMethodDelegatesToAsync() {
		// Arrange - async is now always enabled by default
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("getPending");
		ToolContext context = new ToolContext(Map.of());

		// Act - call sync version (which delegates to async by default)
		ToolExecuteResult result = tool.apply(input, context);

		// Assert
		assertNotNull(result);
		assertNotNull(result.getOutput());
	}

	@Test
	void testAsyncExecutionWithErrorHandling() throws Exception {
		// Arrange - simulate error
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction(null); // This should cause error
		ToolContext context = new ToolContext(Map.of());

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		// Assert - should handle error gracefully
		ToolExecuteResult result = future.get(5, TimeUnit.SECONDS);
		assertNotNull(result);
		assertTrue(result.getOutput().contains("required") || result.getOutput().contains("Action"),
				"Error message should mention action");
	}

	@Test
	void testClearPendingAsync() throws Exception {
		// Arrange
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("clearPending");
		ToolContext context = new ToolContext(Map.of());

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		// Assert
		assertTrue(future.isDone(), "clearPending should complete immediately");
		ToolExecuteResult result = future.get(1, TimeUnit.SECONDS);
		assertNotNull(result);
		assertTrue(result.getOutput().contains("cleared") || result.getOutput().contains("0"),
				"Should indicate functions were cleared");
	}

	@Test
	void testGetPendingAsync() throws Exception {
		// Arrange
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("getPending");
		ToolContext context = new ToolContext(Map.of());

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		// Assert
		assertTrue(future.isDone(), "getPending should complete immediately");
		ToolExecuteResult result = future.get(1, TimeUnit.SECONDS);
		assertNotNull(result);
		assertNotNull(result.getOutput());
		// Should return JSON array (possibly empty)
		assertTrue(result.getOutput().startsWith("["), "Should return JSON array");
	}

	@Test
	void testRegisterBatchAsync() throws Exception {
		// Arrange
		RegisterBatchInput input = new RegisterBatchInput();
		input.setAction("registerBatch");

		List<Object> functions = new ArrayList<>();
		Map<String, Object> func1 = new HashMap<>();
		func1.put("toolName", "testTool");
		func1.put("input", Map.of("key", "value"));
		functions.add(func1);

		input.setFunctions(functions);
		ToolContext context = new ToolContext(Map.of());

		// Act
		CompletableFuture<ToolExecuteResult> future = tool.applyAsync(input, context);

		// Assert
		assertTrue(future.isDone(), "registerBatch should complete immediately");
		ToolExecuteResult result = future.get(1, TimeUnit.SECONDS);
		assertNotNull(result);
		assertTrue(result.getOutput().contains("registered") || result.getOutput().contains("1"),
				"Should indicate function was registered");
	}

}

