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
package com.alibaba.cloud.ai.manus.workspace.conversation.entity.po;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author dahua
 * @time 2025/8/5
 * @desc memory entity
 */
@Entity
@Table(name = "dynamic_memories", indexes = {
		@Index(name = "idx_create_time", columnList = "createTime") })
public class MemoryEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String conversationId;

	@Column(nullable = false)
	private String memoryName;

	@Column(nullable = false)
	private Date createTime;

	@OneToMany(mappedBy = "memoryEntity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<ConversationMessage> messages = new ArrayList<>();

	public MemoryEntity() {
		this.createTime = new Date();
	}

	public MemoryEntity(String conversationId, String memoryName) {
		this.conversationId = conversationId;
		this.memoryName = memoryName;
		this.createTime = new Date();
	}

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

	public Date getCreateTime() {
		return createTime;
	}

	public void setCreateTime(Date createTime) {
		this.createTime = createTime;
	}

	public List<ConversationMessage> getMessages() {
		return messages;
	}

	public void setMessages(List<ConversationMessage> messages) {
		this.messages = messages;
	}

	public void addMessage(ConversationMessage message) {
		this.messages.add(message);
		message.setMemoryEntity(this);
	}

	public void removeMessage(ConversationMessage message) {
		this.messages.remove(message);
		message.setMemoryEntity(null);
	}

}
