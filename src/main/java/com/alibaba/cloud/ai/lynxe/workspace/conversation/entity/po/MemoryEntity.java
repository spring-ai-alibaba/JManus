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
package com.alibaba.cloud.ai.lynxe.workspace.conversation.entity.po;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * @author dahua
 * @time 2025/8/5
 * @desc memory entity - Stores conversation metadata and references to plan executions
 */
@Entity
@Table(name = "dynamic_memories", indexes = { @Index(name = "idx_create_time", columnList = "createTime") })
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

	/**
	 * List of root plan IDs associated with this conversation Each rootPlanId corresponds
	 * to a complete dialog round (user query + assistant response) The plan execution
	 * records contain all the actual message content and execution details
	 */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "memory_plan_mappings", joinColumns = @JoinColumn(name = "memory_id"))
	@Column(name = "root_plan_id", nullable = false)
	private List<String> rootPlanIds = new ArrayList<>();

	/**
	 * Note: The @OneToMany relationship to ConversationMessage has been removed. This
	 * entity now only maintains references to root plan IDs. The actual conversation
	 * content is retrieved through PlanExecutionRecords using the rootPlanIds list. This
	 * design is more maintainable and avoids data duplication.
	 */

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

	public List<String> getRootPlanIds() {
		return rootPlanIds;
	}

	public void setRootPlanIds(List<String> rootPlanIds) {
		this.rootPlanIds = rootPlanIds;
	}

	/**
	 * Add a root plan ID to this conversation
	 * @param rootPlanId The root plan ID to add
	 */
	public void addRootPlanId(String rootPlanId) {
		if (rootPlanId != null && !rootPlanId.trim().isEmpty()) {
			if (!this.rootPlanIds.contains(rootPlanId)) {
				this.rootPlanIds.add(rootPlanId);
			}
		}
	}

	/**
	 * Remove a root plan ID from this conversation
	 * @param rootPlanId The root plan ID to remove
	 */
	public void removeRootPlanId(String rootPlanId) {
		this.rootPlanIds.remove(rootPlanId);
	}

}
