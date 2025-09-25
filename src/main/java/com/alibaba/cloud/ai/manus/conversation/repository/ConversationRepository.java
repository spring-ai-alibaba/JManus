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
package com.alibaba.cloud.ai.manus.conversation.repository;

import com.alibaba.cloud.ai.manus.conversation.model.po.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for ConversationEntity
 * Provides data access methods for conversation management
 */
@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {

	/**
	 * Find conversation by conversation ID
	 * @param conversationId The conversation ID
	 * @return Optional containing the conversation if found
	 */
	Optional<ConversationEntity> findByConversationId(String conversationId);

	/**
	 * Find conversations by user ID
	 * @param userId The user ID
	 * @return List of conversations for the user
	 */
	List<ConversationEntity> findByUserId(Long userId);

	/**
	 * Find conversations by user ID and status
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return List of conversations matching the criteria
	 */
	List<ConversationEntity> findByUserIdAndStatus(Long userId, String status);

	/**
	 * Find conversations by status
	 * @param status The conversation status
	 * @return List of conversations with the specified status
	 */
	List<ConversationEntity> findByStatus(String status);

	/**
	 * Find conversations by conversation name containing the given text
	 * @param conversationName The text to search for in conversation names
	 * @return List of conversations with names containing the text
	 */
	List<ConversationEntity> findByConversationNameContainingIgnoreCase(String conversationName);

	/**
	 * Find conversations by user ID and conversation name containing the given text
	 * @param userId The user ID
	 * @param conversationName The text to search for in conversation names
	 * @return List of conversations matching the criteria
	 */
	List<ConversationEntity> findByUserIdAndConversationNameContainingIgnoreCase(Long userId, String conversationName);

	/**
	 * Find conversations created after the specified date
	 * @param date The date to filter by
	 * @return List of conversations created after the date
	 */
	List<ConversationEntity> findByCreatedAtAfter(LocalDateTime date);

	/**
	 * Find conversations by user ID created after the specified date
	 * @param userId The user ID
	 * @param date The date to filter by
	 * @return List of conversations for the user created after the date
	 */
	List<ConversationEntity> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime date);

	/**
	 * Count conversations by user ID
	 * @param userId The user ID
	 * @return Number of conversations for the user
	 */
	long countByUserId(Long userId);

	/**
	 * Count conversations by user ID and status
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return Number of conversations matching the criteria
	 */
	long countByUserIdAndStatus(Long userId, String status);

	/**
	 * Check if conversation exists by conversation ID
	 * @param conversationId The conversation ID
	 * @return True if conversation exists, false otherwise
	 */
	boolean existsByConversationId(String conversationId);

	/**
	 * Check if conversation exists by user ID and conversation name
	 * @param userId The user ID
	 * @param conversationName The conversation name
	 * @return True if conversation exists, false otherwise
	 */
	boolean existsByUserIdAndConversationName(Long userId, String conversationName);

	/**
	 * Find conversations with last activity before the specified date
	 * @param date The date to filter by
	 * @return List of inactive conversations
	 */
	@Query("SELECT c FROM ConversationEntity c WHERE c.lastActivityAt < :date OR c.lastActivityAt IS NULL")
	List<ConversationEntity> findInactiveConversations(@Param("date") LocalDateTime date);

	/**
	 * Find conversations by user ID ordered by last activity descending
	 * @param userId The user ID
	 * @return List of conversations ordered by most recent activity
	 */
	@Query("SELECT c FROM ConversationEntity c WHERE c.userId = :userId ORDER BY c.lastActivityAt DESC NULLS LAST, c.createdAt DESC")
	List<ConversationEntity> findByUserIdOrderByLastActivityDesc(@Param("userId") Long userId);

	/**
	 * Find conversations by user ID and status ordered by last activity descending
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return List of conversations ordered by most recent activity
	 */
	@Query("SELECT c FROM ConversationEntity c WHERE c.userId = :userId AND c.status = :status ORDER BY c.lastActivityAt DESC NULLS LAST, c.createdAt DESC")
	List<ConversationEntity> findByUserIdAndStatusOrderByLastActivityDesc(@Param("userId") Long userId, @Param("status") String status);

}
