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
package com.alibaba.cloud.ai.manus.workspace.conversation.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Memory response model for API responses Contains memory data with additional metadata
 */
public class MemoryResponse {

	private boolean success;

	private String message;

	private Memory data;

	private List<Memory> memories;

	@JsonProperty("total_count")
	private Integer totalCount;

	@JsonProperty("response_time")
	private LocalDateTime responseTime;

	// Constructors
	public MemoryResponse() {
		this.responseTime = LocalDateTime.now();
	}

	public MemoryResponse(boolean success, String message) {
		this();
		this.success = success;
		this.message = message;
	}

	public MemoryResponse(boolean success, String message, Memory data) {
		this(success, message);
		this.data = data;
	}

	public MemoryResponse(boolean success, String message, List<Memory> memories) {
		this(success, message);
		this.memories = memories;
		this.totalCount = memories != null ? memories.size() : 0;
	}

	// Getters and Setters
	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Memory getData() {
		return data;
	}

	public void setData(Memory data) {
		this.data = data;
	}

	public List<Memory> getMemories() {
		return memories;
	}

	public void setMemories(List<Memory> memories) {
		this.memories = memories;
		this.totalCount = memories != null ? memories.size() : 0;
	}

	public Integer getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(Integer totalCount) {
		this.totalCount = totalCount;
	}

	public LocalDateTime getResponseTime() {
		return responseTime;
	}

	public void setResponseTime(LocalDateTime responseTime) {
		this.responseTime = responseTime;
	}

	// Static factory methods
	public static MemoryResponse success(Memory memory) {
		return new MemoryResponse(true, "Memory retrieved successfully", memory);
	}

	public static MemoryResponse success(List<Memory> memories) {
		return new MemoryResponse(true, "Memories retrieved successfully", memories);
	}

	public static MemoryResponse error(String message) {
		return new MemoryResponse(false, message);
	}

	public static MemoryResponse notFound() {
		return new MemoryResponse(false, "Memory not found");
	}

	public static MemoryResponse created(Memory memory) {
		return new MemoryResponse(true, "Memory created successfully", memory);
	}

	public static MemoryResponse updated(Memory memory) {
		return new MemoryResponse(true, "Memory updated successfully", memory);
	}

	public static MemoryResponse deleted() {
		return new MemoryResponse(true, "Memory deleted successfully");
	}

}
