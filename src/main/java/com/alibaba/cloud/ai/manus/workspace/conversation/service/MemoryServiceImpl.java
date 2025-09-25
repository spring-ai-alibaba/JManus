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
package com.alibaba.cloud.ai.manus.workspace.conversation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.manus.workspace.conversation.entity.po.MemoryEntity;
import com.alibaba.cloud.ai.manus.workspace.conversation.repository.MemoryRepository;
import com.alibaba.cloud.ai.manus.workspace.conversation.entity.vo.Memory;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author dahua
 * @time 2025/8/5
 * @desc memory service impl
 */
@Service
@Transactional
public class MemoryServiceImpl implements MemoryService {

	private static final Logger logger = LoggerFactory.getLogger(MemoryServiceImpl.class);

	@Autowired
	private MemoryRepository memoryRepository;

	@Autowired
	private ChatMemory chatMemory;

	/**
	 * Convert MemoryEntity to Memory VO
	 */
	private Memory convertToMemory(MemoryEntity entity) {
		if (entity == null) {
			return null;
		}
		Memory memory = new Memory();
		memory.setId(entity.getId());
		memory.setConversationId(entity.getConversationId());
		memory.setMemoryName(entity.getMemoryName());
		
		// Convert Date to LocalDateTime
		if (entity.getCreateTime() != null) {
			memory.setCreateTime(entity.getCreateTime().toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDateTime());
		}
		
		return memory;
	}

	/**
	 * Convert Memory VO to MemoryEntity
	 */
	private MemoryEntity convertToEntity(Memory memory) {
		if (memory == null) {
			return null;
		}
		MemoryEntity entity = new MemoryEntity();
		entity.setId(memory.getId());
		entity.setConversationId(memory.getConversationId());
		entity.setMemoryName(memory.getMemoryName());
		
		// Convert LocalDateTime to Date
		if (memory.getCreateTime() != null) {
			entity.setCreateTime(java.sql.Timestamp.valueOf(memory.getCreateTime()));
		}
		
		return entity;
	}

	@Override
	public List<Memory> getMemories() {
		List<MemoryEntity> memoryEntities = memoryRepository.findAll();
		
		// Convert to Memory VO and sort by create time
		return memoryEntities.stream()
			.map(this::convertToMemory)
			.sorted((m1, m2) -> m1.getCreateTime().compareTo(m2.getCreateTime()))
			.collect(Collectors.toList());
	}

	@Override
	public void deleteMemory(String conversationId) {
		chatMemory.clear(conversationId);
		memoryRepository.deleteByConversationId(conversationId);
	}

	@Override
	public Memory saveMemory(Memory memory) {
		MemoryEntity findEntity = memoryRepository.findByConversationId(memory.getConversationId());
		if (findEntity != null) {
			// Update existing entity
			findEntity.setMemoryName(memory.getMemoryName());
		}
		else {
			findEntity = convertToEntity(memory);
		}
		MemoryEntity saveEntity = memoryRepository.save(findEntity);
		return convertToMemory(saveEntity);
	}

	@Override
	public Memory updateMemory(Memory memory) {
		MemoryEntity findEntity = memoryRepository.findByConversationId(memory.getConversationId());
		if (findEntity == null) {
			throw new IllegalArgumentException("Memory not found with ID: " + memory.getConversationId());
		}
		findEntity.setMemoryName(memory.getMemoryName());
		MemoryEntity saveEntity = memoryRepository.save(findEntity);
		return convertToMemory(saveEntity);
	}

	@Override
	public Memory singleMemory(String conversationId) {
		MemoryEntity findEntity = memoryRepository.findByConversationId(conversationId);
		if (findEntity == null) {
			throw new IllegalArgumentException("Memory not found with ID: " + conversationId);
		}
		return convertToMemory(findEntity);
	}

	@Override
	public String generateConversationId() {
		// Use a specific prefix for conversation IDs
		String conversationPrefix = "conversation-";

		// Generate unique conversation ID with multiple uniqueness factors:
		// 1. Specific prefix for conversations
		// 2. Current timestamp in nanoseconds for high precision
		// 3. Random component for additional uniqueness
		// 4. Thread ID to handle concurrent conversation creation
		long timestamp = System.nanoTime();
		int randomComponent = (int) (Math.random() * 10000);
		long threadId = Thread.currentThread().getId();

		String conversationId = String.format("%s%d_%d_%d", conversationPrefix, timestamp, randomComponent, threadId);

		logger.info("Generated unique conversation ID: {}", conversationId);

		return conversationId;
	}

}
