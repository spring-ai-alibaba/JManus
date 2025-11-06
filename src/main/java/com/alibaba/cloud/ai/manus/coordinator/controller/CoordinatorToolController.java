/*
* Copyright 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.alibaba.cloud.ai.manus.coordinator.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.manus.coordinator.entity.vo.CoordinatorToolVO;
import com.alibaba.cloud.ai.manus.coordinator.exception.CoordinatorToolException;
import com.alibaba.cloud.ai.manus.coordinator.service.CoordinatorToolServiceImpl;
import com.alibaba.cloud.ai.manus.subplan.model.po.SubplanToolDef;
import com.alibaba.cloud.ai.manus.subplan.service.SubplanToolService;

@RestController
@RequestMapping("/api/coordinator-tools")
@CrossOrigin(origins = "*")
public class CoordinatorToolController {

	private static final Logger log = LoggerFactory.getLogger(CoordinatorToolController.class);

	@Autowired
	private CoordinatorToolServiceImpl coordinatorToolService;

	@Autowired
	private SubplanToolService subplanToolService;

	/**
	 * Create coordinator tool
	 */
	@PostMapping
	public ResponseEntity<CoordinatorToolVO> createCoordinatorTool(@RequestBody CoordinatorToolVO toolVO) {
		try {
			log.info("Creating coordinator tool: {}", toolVO);
			CoordinatorToolVO result = coordinatorToolService.createCoordinatorTool(toolVO);
			return ResponseEntity.ok(result);
		}
		catch (CoordinatorToolException e) {
			// Re-throw custom exceptions - they will be handled by @ControllerAdvice
			throw e;
		}
		catch (Exception e) {
			log.error("Unexpected error creating coordinator tool: {}", e.getMessage(), e);
			throw new CoordinatorToolException("INTERNAL_ERROR",
					"An unexpected error occurred while creating coordinator tool");
		}
	}

	/**
	 * Update coordinator tool
	 */
	@PutMapping("/{id}")
	public ResponseEntity<CoordinatorToolVO> updateCoordinatorTool(@PathVariable("id") Long id,
			@RequestBody CoordinatorToolVO toolVO) {
		try {
			log.info("Updating coordinator tool with ID: {}", id);
			CoordinatorToolVO result = coordinatorToolService.updateCoordinatorTool(id, toolVO);
			return ResponseEntity.ok(result);
		}
		catch (CoordinatorToolException e) {
			// Re-throw custom exceptions - they will be handled by @ControllerAdvice
			throw e;
		}
		catch (Exception e) {
			log.error("Unexpected error updating coordinator tool: {}", e.getMessage(), e);
			throw new CoordinatorToolException("INTERNAL_ERROR",
					"An unexpected error occurred while updating coordinator tool");
		}
	}

	/**
	 * Get coordinator tool by plan template ID
	 * @param planTemplateId Plan template ID
	 * @return Coordinator tool if exists, null in response body if not found (200 OK)
	 */
	@GetMapping("/by-template/{planTemplateId}")
	public ResponseEntity<CoordinatorToolVO> getCoordinatorToolByTemplate(
			@PathVariable("planTemplateId") String planTemplateId) {
		try {
			log.info("Getting coordinator tool for plan template: {}", planTemplateId);
			Optional<CoordinatorToolVO> toolOpt = coordinatorToolService
				.getCoordinatorToolByPlanTemplateId(planTemplateId);
			if (toolOpt.isPresent()) {
				return ResponseEntity.ok(toolOpt.get());
			}
			else {
				// Return 200 OK with null JSON response instead of 404
				log.debug("Coordinator tool not found for plan template: {}", planTemplateId);
				return ResponseEntity.ok().body((CoordinatorToolVO) null);
			}
		}
		catch (Exception e) {
			log.error("Error getting coordinator tool: {}", e.getMessage(), e);
			return ResponseEntity.status(500).build();
		}
	}

	/**
	 * Get all coordinator tools
	 * @return List of all coordinator tools
	 */
	@GetMapping
	public ResponseEntity<List<CoordinatorToolVO>> getAllCoordinatorTools() {
		try {
			log.info("Getting all coordinator tools");
			List<CoordinatorToolVO> tools = coordinatorToolService.getAllCoordinatorTools();
			return ResponseEntity.ok(tools);
		}
		catch (Exception e) {
			log.error("Error getting all coordinator tools: {}", e.getMessage(), e);
			return ResponseEntity.status(500).build();
		}
	}

	/**
	 * Get CoordinatorTool configuration information
	 */
	@GetMapping("/config")
	public ResponseEntity<Map<String, Object>> getCoordinatorToolConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("enabled", true);
		config.put("success", true);
		return ResponseEntity.ok(config);
	}

	/**
	 * Get all unique endpoints
	 */
	@GetMapping("/endpoints")
	public ResponseEntity<List<String>> getAllEndpoints() {
		try {
			// Get MCP endpoints from CoordinatorToolEntity
			List<String> mcpEndpoints = coordinatorToolService.getAllUniqueMcpEndpoints();

			// Get endpoints from SubplanToolDef (for backward compatibility)
			List<SubplanToolDef> allTools = subplanToolService.getAllSubplanTools();
			List<String> subplanEndpoints = allTools.stream()
				.map(SubplanToolDef::getEndpoint)
				.distinct()
				.collect(java.util.stream.Collectors.toList());

			// Combine all endpoints
			List<String> allEndpoints = new ArrayList<>();
			allEndpoints.addAll(mcpEndpoints);
			allEndpoints.addAll(subplanEndpoints);

			// Remove duplicates and null values
			List<String> uniqueEndpoints = allEndpoints.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(java.util.stream.Collectors.toList());

			log.info("Found {} unique endpoints (MCP: {}, Subplan: {})", uniqueEndpoints.size(), mcpEndpoints.size(),
					subplanEndpoints.size());
			return ResponseEntity.ok(uniqueEndpoints);

		}
		catch (Exception e) {
			log.error("Error getting endpoints: {}", e.getMessage(), e);
			return ResponseEntity.status(500).build();
		}
	}

	/**
	 * Delete coordinator tool
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Map<String, Object>> deleteCoordinatorTool(@PathVariable("id") Long id) {
		Map<String, Object> result = new HashMap<>();

		try {
			log.info("Deleting coordinator tool with ID: {}", id);
			coordinatorToolService.deleteCoordinatorTool(id);

			result.put("success", true);
			result.put("message", "Coordinator tool deleted successfully");
			return ResponseEntity.ok(result);

		}
		catch (CoordinatorToolException e) {
			log.error("Error deleting coordinator tool: {}", e.getMessage(), e);
			result.put("success", false);
			result.put("message", e.getMessage());
			return ResponseEntity.status(400).body(result);
		}
		catch (Exception e) {
			log.error("Unexpected error deleting coordinator tool: {}", e.getMessage(), e);
			result.put("success", false);
			result.put("message", "An unexpected error occurred while deleting coordinator tool");
			return ResponseEntity.status(500).body(result);
		}
	}

}
