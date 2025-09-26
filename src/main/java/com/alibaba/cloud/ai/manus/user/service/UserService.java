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
package com.alibaba.cloud.ai.manus.user.service;

import com.alibaba.cloud.ai.manus.user.model.po.UserEntity;
import com.alibaba.cloud.ai.manus.user.repository.UserRepository;
import com.alibaba.cloud.ai.manus.user.model.vo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User Service for simple user operations Provides basic query functionality without
 * complex user management
 */
@Service
public class UserService {

	private static final Logger logger = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;

	@Autowired
	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/**
	 * Get user by ID
	 */
	public User getUserById(Long id) {
		logger.debug("Retrieving user by ID: {}", id);
		Optional<UserEntity> entity = userRepository.findById(id);
		return entity.map(this::mapToUser).orElse(null);
	}

	/**
	 * Get user by username
	 */
	public User getUserByUsername(String username) {
		logger.debug("Retrieving user by username: {}", username);
		Optional<UserEntity> entity = userRepository.findByUsername(username);
		return entity.map(this::mapToUser).orElse(null);
	}

	/**
	 * Get user by email
	 */
	public User getUserByEmail(String email) {
		logger.debug("Retrieving user by email: {}", email);
		Optional<UserEntity> entity = userRepository.findByEmail(email);
		return entity.map(this::mapToUser).orElse(null);
	}

	/**
	 * Get all users
	 */
	public List<User> getAllUsers() {
		logger.debug("Retrieving all users");
		return userRepository.findAll().stream().map(this::mapToUser).collect(Collectors.toList());
	}

	/**
	 * Get active users only
	 */
	public List<User> getActiveUsers() {
		logger.debug("Retrieving active users");
		return userRepository.findByStatus("active").stream().map(this::mapToUser).collect(Collectors.toList());
	}

	/**
	 * Search users by display name (case-insensitive)
	 */
	public List<User> searchUsersByDisplayName(String displayName) {
		logger.debug("Searching users by display name: {}", displayName);
		if (displayName == null || displayName.trim().isEmpty()) {
			return getAllUsers();
		}

		return userRepository.findByDisplayNameContaining(displayName)
			.stream()
			.map(this::mapToUser)
			.collect(Collectors.toList());
	}

	/**
	 * Get user count
	 */
	public int getUserCount() {
		return (int) userRepository.count();
	}

	/**
	 * Get active user count
	 */
	public int getActiveUserCount() {
		return (int) userRepository.countByStatus("active");
	}

	/**
	 * Check if user exists by ID
	 */
	public boolean userExists(Long id) {
		return userRepository.existsById(id);
	}

	/**
	 * Check if username is available
	 */
	public boolean isUsernameAvailable(String username) {
		return !userRepository.existsByUsername(username);
	}

	/**
	 * Check if email is available
	 */
	public boolean isEmailAvailable(String email) {
		return !userRepository.existsByEmail(email);
	}

	/**
	 * Get user statistics
	 */
	public Map<String, Object> getUserStatistics() {
		Map<String, Object> stats = new HashMap<>();
		long totalUsers = userRepository.count();
		long activeUsers = userRepository.countByStatus("active");

		stats.put("total_users", totalUsers);
		stats.put("active_users", activeUsers);
		stats.put("inactive_users", totalUsers - activeUsers);

		// Calculate average days since creation
		List<UserEntity> allUsers = userRepository.findAll();
		if (!allUsers.isEmpty()) {
			double avgDaysSinceCreation = allUsers.stream()
				.mapToLong(user -> java.time.Duration.between(user.getCreatedAt(), LocalDateTime.now()).toDays())
				.average()
				.orElse(0.0);
			stats.put("avg_days_since_creation", Math.round(avgDaysSinceCreation));
		}
		else {
			stats.put("avg_days_since_creation", 0);
		}

		return stats;
	}

	/**
	 * Map UserEntity to User VO
	 */
	private User mapToUser(UserEntity entity) {
		User user = new User();
		BeanUtils.copyProperties(entity, user);
		return user;
	}

	/**
	 * Set user's current conversation
	 * @param userId The user ID
	 * @param conversationId The conversation ID to set as current
	 * @return True if updated successfully, false if user not found
	 */
	public boolean setCurrentConversation(Long userId, String conversationId) {
		logger.info("Setting current conversation for user {} to {}", userId, conversationId);
		Optional<UserEntity> entityOpt = userRepository.findById(userId);
		if (entityOpt.isEmpty()) {
			logger.warn("User not found: {}", userId);
			return false;
		}

		UserEntity entity = entityOpt.get();
		entity.setCurrentConversationId(conversationId);
		userRepository.save(entity);
		logger.info("Updated current conversation for user {} to {}", userId, conversationId);
		return true;
	}

	/**
	 * Clear user's current conversation
	 * @param userId The user ID
	 * @return True if updated successfully, false if user not found
	 */
	public boolean clearCurrentConversation(Long userId) {
		logger.info("Clearing current conversation for user {}", userId);
		return setCurrentConversation(userId, null);
	}

	/**
	 * Get user's current conversation ID
	 * @param userId The user ID
	 * @return The current conversation ID, null if not set or user not found
	 */
	public String getCurrentConversationId(Long userId) {
		logger.debug("Getting current conversation for user {}", userId);
		Optional<UserEntity> entityOpt = userRepository.findById(userId);
		return entityOpt.map(UserEntity::getCurrentConversationId).orElse(null);
	}

	/**
	 * Map User VO to UserEntity
	 */
	private UserEntity mapToUserEntity(User user) {
		UserEntity entity = new UserEntity();
		BeanUtils.copyProperties(user, entity);
		return entity;
	}

}
