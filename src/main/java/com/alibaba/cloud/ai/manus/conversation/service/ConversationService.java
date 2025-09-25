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
package com.alibaba.cloud.ai.manus.conversation.service;

import com.alibaba.cloud.ai.manus.conversation.model.po.ConversationEntity;
import com.alibaba.cloud.ai.manus.conversation.repository.ConversationRepository;
import com.alibaba.cloud.ai.manus.conversation.vo.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing conversations
 * Replaces MemoryService with conversation-focused functionality
 */
@Service
@Transactional
public class ConversationService {

	private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

	private final ConversationRepository conversationRepository;

	@Autowired
	public ConversationService(ConversationRepository conversationRepository) {
		this.conversationRepository = conversationRepository;
	}

	/**
	 * Get all conversations
	 * @return List of all conversations
	 */
	public List<Conversation> getAllConversations() {
		logger.debug("Retrieving all conversations");
		return conversationRepository.findAll().stream().map(this::mapToConversation).collect(Collectors.toList());
	}

	/**
	 * Get conversation by conversation ID
	 * @param conversationId The conversation ID
	 * @return Conversation if found, null otherwise
	 */
	public Conversation getConversationById(String conversationId) {
		logger.debug("Retrieving conversation by ID: {}", conversationId);
		Optional<ConversationEntity> entity = conversationRepository.findByConversationId(conversationId);
		return entity.map(this::mapToConversation).orElse(null);
	}

	/**
	 * Get conversations by user ID
	 * @param userId The user ID
	 * @return List of conversations for the user
	 */
	public List<Conversation> getConversationsByUserId(Long userId) {
		logger.debug("Retrieving conversations for user ID: {}", userId);
		return conversationRepository.findByUserIdOrderByLastActivityDesc(userId).stream()
				.map(this::mapToConversation).collect(Collectors.toList());
	}

	/**
	 * Get conversations by user ID and status
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return List of conversations matching the criteria
	 */
	public List<Conversation> getConversationsByUserIdAndStatus(Long userId, String status) {
		logger.debug("Retrieving conversations for user ID: {} with status: {}", userId, status);
		return conversationRepository.findByUserIdAndStatusOrderByLastActivityDesc(userId, status).stream()
				.map(this::mapToConversation).collect(Collectors.toList());
	}

	/**
	 * Create a new conversation
	 * @param conversationName The conversation name
	 * @param userId The user ID (nullable)
	 * @return The created conversation
	 */
	public Conversation createConversation(String conversationName, Long userId) {
		logger.info("Creating new conversation: {} for user: {}", conversationName, userId);
		
		String conversationId = generateConversationId();
		ConversationEntity entity = new ConversationEntity(conversationId, conversationName, userId);
		entity.setLastActivityAt(LocalDateTime.now());
		
		ConversationEntity savedEntity = conversationRepository.save(entity);
		logger.info("Created conversation with ID: {}", savedEntity.getConversationId());
		
		return mapToConversation(savedEntity);
	}

	/**
	 * Update conversation
	 * @param conversationId The conversation ID
	 * @param conversationName The new conversation name
	 * @param description The new description
	 * @return The updated conversation, null if not found
	 */
	public Conversation updateConversation(String conversationId, String conversationName, String description) {
		logger.info("Updating conversation: {}", conversationId);
		
		Optional<ConversationEntity> entityOpt = conversationRepository.findByConversationId(conversationId);
		if (entityOpt.isEmpty()) {
			logger.warn("Conversation not found: {}", conversationId);
			return null;
		}
		
		ConversationEntity entity = entityOpt.get();
		entity.setConversationName(conversationName);
		entity.setDescription(description);
		entity.setUpdatedAt(LocalDateTime.now());
		entity.setLastActivityAt(LocalDateTime.now());
		
		ConversationEntity savedEntity = conversationRepository.save(entity);
		logger.info("Updated conversation: {}", conversationId);
		
		return mapToConversation(savedEntity);
	}

	/**
	 * Update conversation activity timestamp
	 * @param conversationId The conversation ID
	 */
	public void updateConversationActivity(String conversationId) {
		logger.debug("Updating activity for conversation: {}", conversationId);
		
		Optional<ConversationEntity> entityOpt = conversationRepository.findByConversationId(conversationId);
		if (entityOpt.isPresent()) {
			ConversationEntity entity = entityOpt.get();
			entity.setLastActivityAt(LocalDateTime.now());
			conversationRepository.save(entity);
		}
	}

	/**
	 * Delete conversation
	 * @param conversationId The conversation ID
	 * @return True if deleted, false if not found
	 */
	public boolean deleteConversation(String conversationId) {
		logger.info("Deleting conversation: {}", conversationId);
		
		Optional<ConversationEntity> entityOpt = conversationRepository.findByConversationId(conversationId);
		if (entityOpt.isEmpty()) {
			logger.warn("Conversation not found for deletion: {}", conversationId);
			return false;
		}
		
		conversationRepository.delete(entityOpt.get());
		logger.info("Deleted conversation: {}", conversationId);
		return true;
	}

	/**
	 * Check if conversation exists
	 * @param conversationId The conversation ID
	 * @return True if exists, false otherwise
	 */
	public boolean conversationExists(String conversationId) {
		return conversationRepository.existsByConversationId(conversationId);
	}

	/**
	 * Get conversation count by user ID
	 * @param userId The user ID
	 * @return Number of conversations for the user
	 */
	public long getConversationCountByUserId(Long userId) {
		return conversationRepository.countByUserId(userId);
	}

	/**
	 * Get conversation count by user ID and status
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return Number of conversations matching the criteria
	 */
	public long getConversationCountByUserIdAndStatus(Long userId, String status) {
		return conversationRepository.countByUserIdAndStatus(userId, status);
	}

	/**
	 * Search conversations by name
	 * @param conversationName The text to search for
	 * @return List of conversations with names containing the text
	 */
	public List<Conversation> searchConversationsByName(String conversationName) {
		logger.debug("Searching conversations by name: {}", conversationName);
		return conversationRepository.findByConversationNameContainingIgnoreCase(conversationName).stream()
				.map(this::mapToConversation).collect(Collectors.toList());
	}

	/**
	 * Search conversations by user ID and name
	 * @param userId The user ID
	 * @param conversationName The text to search for
	 * @return List of conversations matching the criteria
	 */
	public List<Conversation> searchConversationsByUserIdAndName(Long userId, String conversationName) {
		logger.debug("Searching conversations for user: {} by name: {}", userId, conversationName);
		return conversationRepository.findByUserIdAndConversationNameContainingIgnoreCase(userId, conversationName)
				.stream().map(this::mapToConversation).collect(Collectors.toList());
	}

	/**
	 * Generate a unique conversation ID
	 * @return A unique conversation ID
	 */
	private String generateConversationId() {
		return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	/**
	 * Map ConversationEntity to Conversation VO
	 * @param entity The entity to map
	 * @return The mapped VO
	 */
	private Conversation mapToConversation(ConversationEntity entity) {
		Conversation conversation = new Conversation();
		BeanUtils.copyProperties(entity, conversation);
		return conversation;
	}

	/**
	 * Map Conversation VO to ConversationEntity
	 * @param conversation The VO to map
	 * @return The mapped entity
	 */
	private ConversationEntity mapToConversationEntity(Conversation conversation) {
		ConversationEntity entity = new ConversationEntity();
		BeanUtils.copyProperties(conversation, entity);
		return entity;
	}

}
