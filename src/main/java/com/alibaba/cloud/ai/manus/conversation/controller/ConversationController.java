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
package com.alibaba.cloud.ai.manus.conversation.controller;

import com.alibaba.cloud.ai.manus.conversation.service.ConversationService;
import com.alibaba.cloud.ai.manus.conversation.vo.Conversation;
import com.alibaba.cloud.ai.manus.conversation.vo.ConversationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for conversation management
 * Replaces MemoryController with conversation-focused endpoints
 */
@RestController
@RequestMapping("/api/v1/conversations")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ConversationController {

	private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

	private final ConversationService conversationService;

	@Autowired
	public ConversationController(ConversationService conversationService) {
		this.conversationService = conversationService;
	}

	/**
	 * Get all conversations
	 * @return List of all conversations
	 */
	@GetMapping
	public ResponseEntity<ConversationResponse> getAllConversations() {
		try {
			logger.info("Retrieving all conversations");
			List<Conversation> conversations = conversationService.getAllConversations();
			ConversationResponse response = ConversationResponse.success(conversations);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error retrieving all conversations", e);
			ConversationResponse response = ConversationResponse.error("Failed to retrieve conversations: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get conversation by conversation ID
	 * @param conversationId The conversation ID
	 * @return The conversation if found
	 */
	@GetMapping("/{conversationId}")
	public ResponseEntity<ConversationResponse> getConversationById(@PathVariable String conversationId) {
		try {
			logger.info("Retrieving conversation by ID: {}", conversationId);
			Conversation conversation = conversationService.getConversationById(conversationId);
			if (conversation != null) {
				ConversationResponse response = ConversationResponse.success(conversation);
				return ResponseEntity.ok(response);
			}
			else {
				ConversationResponse response = ConversationResponse.notFound();
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving conversation by ID: {}", conversationId, e);
			ConversationResponse response = ConversationResponse.error("Failed to retrieve conversation: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get conversations by user ID
	 * @param userId The user ID
	 * @return List of conversations for the user
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<ConversationResponse> getConversationsByUserId(@PathVariable Long userId) {
		try {
			logger.info("Retrieving conversations for user ID: {}", userId);
			List<Conversation> conversations = conversationService.getConversationsByUserId(userId);
			ConversationResponse response = ConversationResponse.success(conversations);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error retrieving conversations for user ID: {}", userId, e);
			ConversationResponse response = ConversationResponse.error("Failed to retrieve conversations: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get conversations by user ID and status
	 * @param userId The user ID
	 * @param status The conversation status
	 * @return List of conversations matching the criteria
	 */
	@GetMapping("/user/{userId}/status/{status}")
	public ResponseEntity<ConversationResponse> getConversationsByUserIdAndStatus(@PathVariable Long userId, @PathVariable String status) {
		try {
			logger.info("Retrieving conversations for user ID: {} with status: {}", userId, status);
			List<Conversation> conversations = conversationService.getConversationsByUserIdAndStatus(userId, status);
			ConversationResponse response = ConversationResponse.success(conversations);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error retrieving conversations for user ID: {} with status: {}", userId, status, e);
			ConversationResponse response = ConversationResponse.error("Failed to retrieve conversations: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Create a new conversation
	 * @param request The conversation creation request
	 * @return The created conversation
	 */
	@PostMapping
	public ResponseEntity<ConversationResponse> createConversation(@RequestBody Map<String, Object> request) {
		try {
			String conversationName = (String) request.get("conversationName");
			Long userId = request.get("userId") != null ? Long.valueOf(request.get("userId").toString()) : null;
			
			if (conversationName == null || conversationName.trim().isEmpty()) {
				ConversationResponse response = ConversationResponse.error("Conversation name is required");
				return ResponseEntity.badRequest().body(response);
			}
			
			logger.info("Creating conversation: {} for user: {}", conversationName, userId);
			Conversation conversation = conversationService.createConversation(conversationName, userId);
			ConversationResponse response = ConversationResponse.success(conversation);
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		}
		catch (Exception e) {
			logger.error("Error creating conversation", e);
			ConversationResponse response = ConversationResponse.error("Failed to create conversation: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Update conversation
	 * @param conversationId The conversation ID
	 * @param request The update request
	 * @return The updated conversation
	 */
	@PutMapping("/{conversationId}")
	public ResponseEntity<ConversationResponse> updateConversation(@PathVariable String conversationId, @RequestBody Map<String, Object> request) {
		try {
			String conversationName = (String) request.get("conversationName");
			String description = (String) request.get("description");
			
			logger.info("Updating conversation: {}", conversationId);
			Conversation conversation = conversationService.updateConversation(conversationId, conversationName, description);
			
			if (conversation != null) {
				ConversationResponse response = ConversationResponse.success(conversation);
				return ResponseEntity.ok(response);
			}
			else {
				ConversationResponse response = ConversationResponse.notFound();
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
		}
		catch (Exception e) {
			logger.error("Error updating conversation: {}", conversationId, e);
			ConversationResponse response = ConversationResponse.error("Failed to update conversation: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Update conversation activity
	 * @param conversationId The conversation ID
	 * @return Success response
	 */
	@PostMapping("/{conversationId}/activity")
	public ResponseEntity<Map<String, Object>> updateConversationActivity(@PathVariable String conversationId) {
		try {
			logger.info("Updating activity for conversation: {}", conversationId);
			conversationService.updateConversationActivity(conversationId);
			return ResponseEntity.ok(Map.of("success", true, "message", "Activity updated successfully"));
		}
		catch (Exception e) {
			logger.error("Error updating conversation activity: {}", conversationId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("success", false, "error", "Failed to update activity: " + e.getMessage()));
		}
	}

	/**
	 * Delete conversation
	 * @param conversationId The conversation ID
	 * @return Success response
	 */
	@DeleteMapping("/{conversationId}")
	public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String conversationId) {
		try {
			logger.info("Deleting conversation: {}", conversationId);
			boolean deleted = conversationService.deleteConversation(conversationId);
			
			if (deleted) {
				return ResponseEntity.ok(Map.of("success", true, "message", "Conversation deleted successfully"));
			}
			else {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(Map.of("success", false, "error", "Conversation not found"));
			}
		}
		catch (Exception e) {
			logger.error("Error deleting conversation: {}", conversationId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("success", false, "error", "Failed to delete conversation: " + e.getMessage()));
		}
	}

	/**
	 * Check if conversation exists
	 * @param conversationId The conversation ID
	 * @return Existence status
	 */
	@GetMapping("/{conversationId}/exists")
	public ResponseEntity<Map<String, Object>> checkConversationExists(@PathVariable String conversationId) {
		try {
			logger.info("Checking if conversation exists: {}", conversationId);
			boolean exists = conversationService.conversationExists(conversationId);
			return ResponseEntity.ok(Map.of("exists", exists, "conversationId", conversationId));
		}
		catch (Exception e) {
			logger.error("Error checking if conversation exists: {}", conversationId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to check conversation existence: " + e.getMessage()));
		}
	}

	/**
	 * Search conversations by name
	 * @param name The name to search for
	 * @return List of matching conversations
	 */
	@GetMapping("/search")
	public ResponseEntity<ConversationResponse> searchConversations(@RequestParam String name) {
		try {
			logger.info("Searching conversations by name: {}", name);
			List<Conversation> conversations = conversationService.searchConversationsByName(name);
			ConversationResponse response = ConversationResponse.success(conversations);
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error searching conversations by name: {}", name, e);
			ConversationResponse response = ConversationResponse.error("Failed to search conversations: " + e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}

	/**
	 * Get conversation statistics for a user
	 * @param userId The user ID
	 * @return Conversation statistics
	 */
	@GetMapping("/user/{userId}/statistics")
	public ResponseEntity<Map<String, Object>> getConversationStatistics(@PathVariable Long userId) {
		try {
			logger.info("Getting conversation statistics for user: {}", userId);
			long totalCount = conversationService.getConversationCountByUserId(userId);
			long activeCount = conversationService.getConversationCountByUserIdAndStatus(userId, "active");
			long inactiveCount = conversationService.getConversationCountByUserIdAndStatus(userId, "inactive");
			
			Map<String, Object> statistics = Map.of(
					"userId", userId,
					"totalConversations", totalCount,
					"activeConversations", activeCount,
					"inactiveConversations", inactiveCount
			);
			
			return ResponseEntity.ok(statistics);
		}
		catch (Exception e) {
			logger.error("Error getting conversation statistics for user: {}", userId, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Failed to get statistics: " + e.getMessage()));
		}
	}

	/**
	 * Health check endpoint
	 * @return Health status
	 */
	@GetMapping("/health")
	public ResponseEntity<Map<String, Object>> healthCheck() {
		return ResponseEntity.ok(Map.of("status", "healthy", "service", "conversation"));
	}

}
