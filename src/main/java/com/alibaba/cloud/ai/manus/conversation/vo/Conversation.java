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

/**
 * Conversation value object for API responses
 * Replaces Memory VO with conversation-focused design
 */
public class Conversation {

	private Long id;

	@JsonProperty("conversation_id")
	private String conversationId;

	@JsonProperty("conversation_name")
	private String conversationName;

	@JsonProperty("user_id")
	private Long userId;

	@JsonProperty("created_at")
	private LocalDateTime createdAt;

	@JsonProperty("updated_at")
	private LocalDateTime updatedAt;

	@JsonProperty("last_activity_at")
	private LocalDateTime lastActivityAt;

	private String status;

	private String description;

	// Constructors
	public Conversation() {
	}

	public Conversation(String conversationId, String conversationName, Long userId) {
		this.conversationId = conversationId;
		this.conversationName = conversationName;
		this.userId = userId;
		this.status = "active";
		this.createdAt = LocalDateTime.now();
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	public String getConversationName() {
		return conversationName;
	}

	public void setConversationName(String conversationName) {
		this.conversationName = conversationName;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public LocalDateTime getLastActivityAt() {
		return lastActivityAt;
	}

	public void setLastActivityAt(LocalDateTime lastActivityAt) {
		this.lastActivityAt = lastActivityAt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return "Conversation{" + "id=" + id + ", conversationId='" + conversationId + '\''
				+ ", conversationName='" + conversationName + '\'' + ", userId=" + userId + ", status='" + status
				+ '\'' + '}';
	}

}
