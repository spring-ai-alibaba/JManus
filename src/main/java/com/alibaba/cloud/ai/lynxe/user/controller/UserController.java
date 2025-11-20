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
package com.alibaba.cloud.ai.lynxe.user.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.alibaba.cloud.ai.lynxe.user.model.vo.User;
import com.alibaba.cloud.ai.lynxe.user.model.vo.UserResponse;
import com.alibaba.cloud.ai.lynxe.user.service.UserService;

import java.util.List;
import java.util.Map;

/**
 * User Controller for handling user-related REST endpoints Provides simple query
 * operations without complex user management
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

	private static final Logger logger = LoggerFactory.getLogger(UserController.class);

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	/**
	 * Get all users
	 */
	@GetMapping
	public ResponseEntity<UserResponse> getAllUsers() {
		try {
			logger.info("Retrieving all users");
			List<User> users = userService.getAllUsers();
			UserResponse response = UserResponse.success(users);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error retrieving all users", e);
			UserResponse response = UserResponse.error("Failed to retrieve users: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get user by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
		try {
			logger.info("Retrieving user by ID: {}", id);
			User user = userService.getUserById(id);
			if (user != null) {
				UserResponse response = UserResponse.success(user);
				return ResponseEntity.ok(response);
			}
			else {
				UserResponse response = UserResponse.notFound();
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving user by ID: {}", id, e);
			UserResponse response = UserResponse.error("Failed to retrieve user: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get user by username
	 */
	@GetMapping("/username/{username}")
	public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
		try {
			logger.info("Retrieving user by username: {}", username);
			User user = userService.getUserByUsername(username);
			if (user != null) {
				UserResponse response = UserResponse.success(user);
				return ResponseEntity.ok(response);
			}
			else {
				UserResponse response = UserResponse.notFound();
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving user by username: {}", username, e);
			UserResponse response = UserResponse.error("Failed to retrieve user: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get user by email
	 */
	@GetMapping("/email/{email}")
	public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String email) {
		try {
			logger.info("Retrieving user by email: {}", email);
			User user = userService.getUserByEmail(email);
			if (user != null) {
				UserResponse response = UserResponse.success(user);
				return ResponseEntity.ok(response);
			}
			else {
				UserResponse response = UserResponse.notFound();
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving user by email: {}", email, e);
			UserResponse response = UserResponse.error("Failed to retrieve user: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get active users only
	 */
	@GetMapping("/active")
	public ResponseEntity<UserResponse> getActiveUsers() {
		try {
			logger.info("Retrieving active users");
			List<User> users = userService.getActiveUsers();
			UserResponse response = UserResponse.success(users);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error retrieving active users", e);
			UserResponse response = UserResponse.error("Failed to retrieve active users: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Search users by display name
	 */
	@GetMapping("/search")
	public ResponseEntity<UserResponse> searchUsers(@RequestParam(required = false) String displayName) {
		try {
			logger.info("Searching users by display name: {}", displayName);
			List<User> users = userService.searchUsersByDisplayName(displayName);
			UserResponse response = UserResponse.success(users);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error searching users by display name: {}", displayName, e);
			UserResponse response = UserResponse.error("Failed to search users: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get user statistics
	 */
	@GetMapping("/statistics")
	public ResponseEntity<Map<String, Object>> getUserStatistics() {
		try {
			logger.info("Retrieving user statistics");
			Map<String, Object> statistics = userService.getUserStatistics();
			return ResponseEntity.ok(statistics);
		}
		catch (Exception e) {
			logger.error("Error retrieving user statistics", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to retrieve statistics: " + e.getMessage()));
		}
	}

	/**
	 * Check if user exists
	 */
	@GetMapping("/{id}/exists")
	public ResponseEntity<Map<String, Object>> checkUserExists(@PathVariable Long id) {
		try {
			logger.info("Checking if user exists: {}", id);
			boolean exists = userService.userExists(id);
			return ResponseEntity.ok(Map.of("exists", exists, "user_id", id));
		}
		catch (Exception e) {
			logger.error("Error checking if user exists: {}", id, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to check user existence: " + e.getMessage()));
		}
	}

	/**
	 * Check if username is available
	 */
	@GetMapping("/username/{username}/available")
	public ResponseEntity<Map<String, Object>> checkUsernameAvailability(@PathVariable String username) {
		try {
			logger.info("Checking username availability: {}", username);
			boolean available = userService.isUsernameAvailable(username);
			return ResponseEntity.ok(Map.of("available", available, "username", username));
		}
		catch (Exception e) {
			logger.error("Error checking username availability: {}", username, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to check username availability: " + e.getMessage()));
		}
	}

	/**
	 * Check if email is available
	 */
	@GetMapping("/email/{email}/available")
	public ResponseEntity<Map<String, Object>> checkEmailAvailability(@PathVariable String email) {
		try {
			logger.info("Checking email availability: {}", email);
			boolean available = userService.isEmailAvailable(email);
			return ResponseEntity.ok(Map.of("available", available, "email", email));
		}
		catch (Exception e) {
			logger.error("Error checking email availability: {}", email, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to check email availability: " + e.getMessage()));
		}
	}

	/**
	 * Set user's current conversation
	 * @param userId The user ID
	 * @param request The request containing conversation ID
	 * @return Success response
	 */
	@PostMapping("/{userId}/conversation")
	public ResponseEntity<Map<String, Object>> setCurrentConversation(@PathVariable Long userId,
			@RequestBody Map<String, Object> request) {
		try {
			String conversationId = (String) request.get("conversationId");
			logger.info("Setting current conversation for user {} to {}", userId, conversationId);

			boolean success = userService.setCurrentConversation(userId, conversationId);
			if (success) {
				return ResponseEntity
					.ok(Map.of("success", true, "message", "Current conversation updated successfully"));
			}
			else {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("success", false, "error", "User not found"));
			}
		}
		catch (Exception e) {
			logger.error("Error setting current conversation for user: {}", userId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("success", false, "error", "Failed to update current conversation: " + e.getMessage()));
		}
	}

	/**
	 * Clear user's current conversation
	 * @param userId The user ID
	 * @return Success response
	 */
	@DeleteMapping("/{userId}/conversation")
	public ResponseEntity<Map<String, Object>> clearCurrentConversation(@PathVariable Long userId) {
		try {
			logger.info("Clearing current conversation for user {}", userId);
			boolean success = userService.clearCurrentConversation(userId);
			if (success) {
				return ResponseEntity
					.ok(Map.of("success", true, "message", "Current conversation cleared successfully"));
			}
			else {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(Map.of("success", false, "error", "User not found"));
			}
		}
		catch (Exception e) {
			logger.error("Error clearing current conversation for user: {}", userId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("success", false, "error", "Failed to clear current conversation: " + e.getMessage()));
		}
	}

	/**
	 * Get user's current conversation ID
	 * @param userId The user ID
	 * @return The current conversation ID
	 */
	@GetMapping("/{userId}/conversation")
	public ResponseEntity<Map<String, Object>> getCurrentConversation(@PathVariable Long userId) {
		try {
			logger.info("Getting current conversation for user {}", userId);
			String conversationId = userService.getCurrentConversationId(userId);
			return ResponseEntity.ok(Map.of("userId", userId, "currentConversationId", conversationId));
		}
		catch (Exception e) {
			logger.error("Error getting current conversation for user: {}", userId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to get current conversation: " + e.getMessage()));
		}
	}

	/**
	 * Health check endpoint for user service
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> health() {
		try {
			logger.info("User service health check");
			int userCount = userService.getUserCount();
			return ResponseEntity.ok(Map.of("status", "healthy", "service", "User Service", "user_count", userCount,
					"timestamp", System.currentTimeMillis()));
		}
		catch (Exception e) {
			logger.error("User service health check failed", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("status", "unhealthy", "error", e.getMessage()));
		}
	}

}
