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
import org.springframework.ai.chat.messages.Message;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Memory value object representing conversation memory information Simple structure for
 * memory data transfer and API responses
 */
public class Memory {

	private Long id;

	@JsonProperty("conversation_id")
	private String conversationId;

	@JsonProperty("memory_name")
	private String memoryName;

	@JsonProperty("create_time")
	private LocalDateTime createTime;

	private List<Message> messages;

	// Constructors
	public Memory() {
	}

	public Memory(String conversationId, String memoryName) {
		this.conversationId = conversationId;
		this.memoryName = memoryName;
		this.createTime = LocalDateTime.now();
	}

	public Memory(Long id, String conversationId, String memoryName, LocalDateTime createTime) {
		this.id = id;
		this.conversationId = conversationId;
		this.memoryName = memoryName;
		this.createTime = createTime;
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

	public String getMemoryName() {
		return memoryName;
	}

	public void setMemoryName(String memoryName) {
		this.memoryName = memoryName;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public List<Message> getMessages() {
		return messages;
	}

	public void setMessages(List<Message> messages) {
		this.messages = messages;
	}

	@Override
	public String toString() {
		return "Memory{" + "id=" + id + ", conversationId='" + conversationId + '\'' + ", memoryName='" + memoryName
				+ '\'' + ", createTime=" + createTime + '}';
	}

}
