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

package com.alibaba.cloud.ai.lynxe.tool;

import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.model.ToolContext;

import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;

/**
 * Async version of ToolCallBiFunctionDef that supports non-blocking execution. This
 * interface allows tools to return CompletableFuture for better resource utilization and
 * prevents thread pool starvation in nested parallel execution scenarios.
 *
 * @param <T> The input type for the tool
 */
public interface AsyncToolCallBiFunctionDef<T> extends ToolCallBiFunctionDef<T> {

	/**
	 * Asynchronous version of apply that returns a CompletableFuture. This method allows
	 * the tool to execute without blocking the calling thread.
	 * @param input Tool input parameters
	 * @param context Tool execution context
	 * @return CompletableFuture that completes with the tool execution result
	 */
	CompletableFuture<ToolExecuteResult> applyAsync(T input, ToolContext context);

	/**
	 * Default synchronous implementation that wraps the async version. This maintains
	 * backward compatibility with existing code that expects synchronous execution.
	 * @param input Tool input parameters
	 * @param context Tool execution context
	 * @return Tool execution result (blocks until async operation completes)
	 */
	@Override
	default ToolExecuteResult apply(T input, ToolContext context) {
		// Default implementation: wrap async call and wait for result
		// This maintains backward compatibility but blocks the thread
		return applyAsync(input, context).join();
	}

}
