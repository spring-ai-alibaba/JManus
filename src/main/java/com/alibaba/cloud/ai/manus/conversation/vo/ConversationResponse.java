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
package com.alibaba.cloud.ai.manus.conversation.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Conversation response model for API responses
 * Contains conversation data with additional metadata
 */
public class ConversationResponse {

	private boolean success;

	private String message;

	private Conversation data;

	private List<Conversation> conversations;

	@JsonProperty("total_count")
	private Integer totalCount;

	@JsonProperty("response_time")
	private LocalDateTime responseTime;

	// Constructors
	public ConversationResponse() {
		this.responseTime = LocalDateTime.now();
	}

	public ConversationResponse(boolean success, String message) {
		this();
		this.success = success;
		this.message = message;
	}

	public ConversationResponse(boolean success, String message, Conversation data) {
		this(success, message);
		this.data = data;
	}

	public ConversationResponse(boolean success, String message, List<Conversation> conversations) {
		this(success, message);
		this.conversations = conversations;
		this.totalCount = conversations != null ? conversations.size() : 0;
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

	public Conversation getData() {
		return data;
	}

	public void setData(Conversation data) {
		this.data = data;
	}

	public List<Conversation> getConversations() {
		return conversations;
	}

	public void setConversations(List<Conversation> conversations) {
		this.conversations = conversations;
		this.totalCount = conversations != null ? conversations.size() : 0;
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
	public static ConversationResponse success(Conversation conversation) {
		return new ConversationResponse(true, "Conversation retrieved successfully", conversation);
	}

	public static ConversationResponse success(List<Conversation> conversations) {
		return new ConversationResponse(true, "Conversations retrieved successfully", conversations);
	}

	public static ConversationResponse error(String message) {
		return new ConversationResponse(false, message);
	}

	public static ConversationResponse notFound() {
		return new ConversationResponse(false, "Conversation not found");
	}

}
