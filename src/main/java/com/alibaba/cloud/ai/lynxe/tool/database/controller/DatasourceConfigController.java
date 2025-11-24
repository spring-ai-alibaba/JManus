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
package com.alibaba.cloud.ai.lynxe.tool.database.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

import com.alibaba.cloud.ai.lynxe.tool.database.model.vo.DatasourceConfigVO;
import com.alibaba.cloud.ai.lynxe.tool.database.service.DatasourceConfigService;

/**
 * REST Controller for managing datasource configurations
 */
@RestController
@RequestMapping("/api/datasource-configs")
@CrossOrigin(origins = "*")
public class DatasourceConfigController {

	private static final Logger logger = LoggerFactory.getLogger(DatasourceConfigController.class);

	private final DatasourceConfigService service;

	public DatasourceConfigController(DatasourceConfigService service) {
		this.service = service;
	}

	/**
	 * Get all datasource configurations
	 */
	@GetMapping
	public ResponseEntity<List<DatasourceConfigVO>> getAllConfigs() {
		try {
			logger.info("Retrieving all datasource configurations");
			List<DatasourceConfigVO> configs = service.getAllConfigs();
			return ResponseEntity.ok(configs);
		}
		catch (Exception e) {
			logger.error("Error retrieving datasource configurations", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Get datasource configuration by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<DatasourceConfigVO> getConfigById(@PathVariable Long id) {
		try {
			logger.info("Retrieving datasource configuration by ID: {}", id);
			DatasourceConfigVO config = service.getConfigById(id);
			if (config != null) {
				return ResponseEntity.ok(config);
			}
			else {
				return ResponseEntity.notFound().build();
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving datasource configuration by ID: {}", id, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Get datasource configuration by name
	 */
	@GetMapping("/name/{name}")
	public ResponseEntity<DatasourceConfigVO> getConfigByName(@PathVariable String name) {
		try {
			logger.info("Retrieving datasource configuration by name: {}", name);
			DatasourceConfigVO config = service.getConfigByName(name);
			if (config != null) {
				return ResponseEntity.ok(config);
			}
			else {
				return ResponseEntity.notFound().build();
			}
		}
		catch (Exception e) {
			logger.error("Error retrieving datasource configuration by name: {}", name, e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Get enabled datasource configurations
	 */
	@GetMapping("/enabled")
	public ResponseEntity<List<DatasourceConfigVO>> getEnabledConfigs() {
		try {
			logger.info("Retrieving enabled datasource configurations");
			List<DatasourceConfigVO> configs = service.getEnabledConfigs();
			return ResponseEntity.ok(configs);
		}
		catch (Exception e) {
			logger.error("Error retrieving enabled datasource configurations", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Create new datasource configuration
	 */
	@PostMapping
	public ResponseEntity<DatasourceConfigVO> createConfig(@RequestBody DatasourceConfigVO config) {
		try {
			logger.info("Creating datasource configuration: {}", config.getName());
			DatasourceConfigVO created = service.createConfig(config);
			return ResponseEntity.status(HttpStatus.CREATED).body(created);
		}
		catch (IllegalArgumentException e) {
			logger.warn("Invalid request for creating datasource configuration: {}", e.getMessage());
			return ResponseEntity.badRequest().build();
		}
		catch (Exception e) {
			logger.error("Error creating datasource configuration", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Update existing datasource configuration
	 */
	@PutMapping("/{id}")
	public ResponseEntity<DatasourceConfigVO> updateConfig(@PathVariable Long id,
			@RequestBody DatasourceConfigVO config) {
		try {
			logger.info("Updating datasource configuration with ID: {}", id);
			DatasourceConfigVO updated = service.updateConfig(id, config);
			return ResponseEntity.ok(updated);
		}
		catch (IllegalArgumentException e) {
			logger.warn("Invalid request for updating datasource configuration: {}", e.getMessage());
			return ResponseEntity.badRequest().build();
		}
		catch (Exception e) {
			logger.error("Error updating datasource configuration", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Delete datasource configuration
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteConfig(@PathVariable Long id) {
		try {
			logger.info("Deleting datasource configuration with ID: {}", id);
			service.deleteConfig(id);
			return ResponseEntity.ok().build();
		}
		catch (IllegalArgumentException e) {
			logger.warn("Invalid request for deleting datasource configuration: {}", e.getMessage());
			return ResponseEntity.notFound().build();
		}
		catch (Exception e) {
			logger.error("Error deleting datasource configuration", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Check if datasource configuration exists by name
	 */
	@GetMapping("/exists/{name}")
	public ResponseEntity<Boolean> existsByName(@PathVariable String name) {
		try {
			boolean exists = service.existsByName(name);
			return ResponseEntity.ok(exists);
		}
		catch (Exception e) {
			logger.error("Error checking datasource configuration existence", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}

	/**
	 * Test database connection
	 */
	@PostMapping("/test-connection")
	public ResponseEntity<Map<String, Object>> testConnection(@RequestBody DatasourceConfigVO config) {
		try {
			logger.info("Testing database connection for: {}", config.getUrl());
			boolean success = service.testConnection(config);
			Map<String, Object> response = new java.util.HashMap<>();
			response.put("success", success);
			if (success) {
				response.put("message", "Connection test successful");
			}
			else {
				response.put("message", "Connection test failed");
			}
			return ResponseEntity.ok(response);
		}
		catch (Exception e) {
			logger.error("Error testing database connection", e);
			Map<String, Object> response = new java.util.HashMap<>();
			response.put("success", false);
			response.put("message", "Connection test failed: " + e.getMessage());
			return ResponseEntity.ok(response);
		}
	}

}
