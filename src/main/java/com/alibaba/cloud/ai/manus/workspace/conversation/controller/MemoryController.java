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
package com.alibaba.cloud.ai.manus.workspace.conversation.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.alibaba.cloud.ai.manus.workspace.conversation.entity.vo.Memory;
import com.alibaba.cloud.ai.manus.workspace.conversation.entity.vo.MemoryResponse;
import com.alibaba.cloud.ai.manus.workspace.conversation.service.MemoryService;

import java.util.List;

/**
 * @author dahua
 * @time 2025/8/5
 * @desc memory controller
 */
@RestController
@RequestMapping("/api/memories")
@CrossOrigin(origins = "*") // Add cross-origin support
public class MemoryController {

	@Autowired
	private MemoryService memoryService;

	@GetMapping
	public ResponseEntity<MemoryResponse> getAllMemories() {
		try {
			List<Memory> memories = memoryService.getMemories();
			return ResponseEntity.ok(MemoryResponse.success(memories));
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to retrieve memories: " + e.getMessage()));
		}
	}

	@GetMapping("/single")
	public ResponseEntity<MemoryResponse> singleMemory(@RequestParam String conversationId) {
		try {
			Memory memory = memoryService.singleMemory(conversationId);
			return ResponseEntity.ok(MemoryResponse.success(memory));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.ok(MemoryResponse.notFound());
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to retrieve memory: " + e.getMessage()));
		}
	}

	@PostMapping
	public ResponseEntity<MemoryResponse> createMemory(@RequestBody Memory memory) {
		try {
			Memory createdMemory = memoryService.saveMemory(memory);
			return ResponseEntity.ok(MemoryResponse.created(createdMemory));
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to create memory: " + e.getMessage()));
		}
	}

	@PutMapping
	public ResponseEntity<MemoryResponse> updateMemory(@RequestBody Memory memory) {
		try {
			Memory updatedMemory = memoryService.updateMemory(memory);
			return ResponseEntity.ok(MemoryResponse.updated(updatedMemory));
		}
		catch (IllegalArgumentException e) {
			return ResponseEntity.ok(MemoryResponse.notFound());
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to update memory: " + e.getMessage()));
		}
	}

	@DeleteMapping("/{conversationId}")
	public ResponseEntity<MemoryResponse> deleteMemory(@PathVariable String conversationId) {
		try {
			memoryService.deleteMemory(conversationId);
			return ResponseEntity.ok(MemoryResponse.deleted());
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to delete memory: " + e.getMessage()));
		}
	}

	@GetMapping("/generate-id")
	public ResponseEntity<MemoryResponse> generateConversationId() {
		try {
			String conversationId = memoryService.generateConversationId();
			// Create a simple response with the generated ID
			MemoryResponse response = new MemoryResponse(true, "Conversation ID generated successfully");
			response.setData(new Memory(conversationId, "Generated Conversation"));
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			return ResponseEntity.ok(MemoryResponse.error("Failed to generate conversation ID: " + e.getMessage()));
		}
	}

}
