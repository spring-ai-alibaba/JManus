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
package com.alibaba.cloud.ai.manus.runtime.controller;

import com.alibaba.cloud.ai.manus.runtime.service.FileUploadService;
import com.alibaba.cloud.ai.manus.runtime.service.FileValidationService;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * File upload controller for handling file upload operations
 */
@RestController
@RequestMapping("/api/file-upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

	private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

	@Autowired
	private FileUploadService fileUploadService;

	@Autowired
	private FileValidationService fileValidationService;

	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Autowired
	private UnifiedDirectoryManager directoryManager;

	/**
	 * Upload files and create a new plan ID
	 * @param files The uploaded files
	 * @return Upload result with plan ID and file information
	 */
	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
		try {
			logger.info("Uploading {} files with new plan ID", files.length);

			// 1. Validate file types
			List<FileValidationService.FileValidationResult> validationResults = fileValidationService
				.validateFiles(Arrays.asList(files));

			// Check if there are validation failed files
			List<String> errors = validationResults.stream()
				.filter(result -> !result.isValid())
				.map(FileValidationService.FileValidationResult::getMessage)
				.collect(Collectors.toList());

			if (!errors.isEmpty()) {
				logger.warn("File validation failed: {}", errors);
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("success", false);
				errorResponse.put("error", "File validation failed");
				errorResponse.put("validationErrors", errors);
				return ResponseEntity.badRequest().body(errorResponse);
			}

			// Generate new plan ID
			String planId = planIdDispatcher.generatePlanId();
			logger.info("Generated new planId for file upload: {}", planId);

			// Upload files to the new plan directory
			List<Map<String, Object>> uploadedFiles = fileUploadService.uploadFiles(files, planId);

			// Build response
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("planId", planId);
			response.put("uploadedFiles", uploadedFiles);
			response.put("message", "Files uploaded successfully");
			response.put("totalFiles", uploadedFiles.size());

			logger.info("Successfully uploaded {} files to planId: {}", uploadedFiles.size(), planId);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			logger.error("Error uploading files", e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("error", "File upload failed: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Upload files to an existing plan
	 * @param planId The existing plan ID
	 * @param files The uploaded files
	 * @return Upload result with file information
	 */
	@PostMapping("/upload/{planId}")
	public ResponseEntity<Map<String, Object>> uploadFilesToPlan(@PathVariable("planId") String planId,
			@RequestParam("files") MultipartFile[] files) {
		try {
			logger.info("Uploading {} files to existing planId: {}", files.length, planId);

			// Validate plan ID
			if (planId == null || planId.trim().isEmpty()) {
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("success", false);
				errorResponse.put("error", "Plan ID cannot be empty");
				return ResponseEntity.badRequest().body(errorResponse);
			}

			// 1. Validate file types
			List<FileValidationService.FileValidationResult> validationResults = fileValidationService
				.validateFiles(Arrays.asList(files));

			// Check if there are validation failed files
			List<String> errors = validationResults.stream()
				.filter(result -> !result.isValid())
				.map(FileValidationService.FileValidationResult::getMessage)
				.collect(Collectors.toList());

			if (!errors.isEmpty()) {
				logger.warn("File validation failed for planId {}: {}", planId, errors);
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("success", false);
				errorResponse.put("error", "File validation failed");
				errorResponse.put("validationErrors", errors);
				errorResponse.put("planId", planId);
				return ResponseEntity.badRequest().body(errorResponse);
			}

			// Upload files to the existing plan directory
			List<Map<String, Object>> uploadedFiles = fileUploadService.uploadFiles(files, planId);

			// Build response
			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("planId", planId);
			response.put("uploadedFiles", uploadedFiles);
			response.put("message", "Files uploaded successfully to existing plan");
			response.put("totalFiles", uploadedFiles.size());

			logger.info("Successfully uploaded {} files to existing planId: {}", uploadedFiles.size(), planId);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			logger.error("Error uploading files to planId: {}", planId, e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("error", "File upload failed: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Get uploaded files for a plan
	 * @param planId The plan ID
	 * @return List of uploaded files
	 */
	@GetMapping("/files/{planId}")
	public ResponseEntity<Map<String, Object>> getUploadedFiles(@PathVariable("planId") String planId) {
		try {
			logger.info("Getting uploaded files for planId: {}", planId);

			List<Map<String, Object>> files = fileUploadService.getUploadedFiles(planId);

			Map<String, Object> response = new HashMap<>();
			response.put("success", true);
			response.put("planId", planId);
			response.put("files", files);
			response.put("totalCount", files.size());

			logger.info("Found {} uploaded files for planId: {}", files.size(), planId);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			logger.error("Error getting uploaded files for planId: {}", planId, e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("error", "Failed to get uploaded files: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Delete an uploaded file
	 * @param planId The plan ID
	 * @param fileName The file name to delete
	 * @return Delete result
	 */
	@DeleteMapping("/files/{planId}/{fileName}")
	public ResponseEntity<Map<String, Object>> deleteFile(@PathVariable("planId") String planId,
			@PathVariable("fileName") String fileName) {
		try {
			logger.info("Deleting file: {} from planId: {}", fileName, planId);

			boolean deleted = fileUploadService.deleteFile(planId, fileName);

			Map<String, Object> response = new HashMap<>();
			if (deleted) {
				response.put("success", true);
				response.put("message", "File deleted successfully");
				response.put("planId", planId);
				response.put("fileName", fileName);
				logger.info("Successfully deleted file: {} from planId: {}", fileName, planId);
				return ResponseEntity.ok(response);
			}
			else {
				response.put("success", false);
				response.put("error", "File not found or could not be deleted");
				return ResponseEntity.notFound().build();
			}

		}
		catch (Exception e) {
			logger.error("Error deleting file: {} from planId: {}", fileName, planId, e);
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("success", false);
			errorResponse.put("error", "Failed to delete file: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Get upload configuration and limits
	 * @return Upload configuration
	 */
	@GetMapping("/config")
	public ResponseEntity<Map<String, Object>> getUploadConfig() {
		Map<String, Object> config = new HashMap<>();
		config.put("maxFileSize", "1GB");
		config.put("maxFiles", 10);
		config.put("allowedTypes",
				List.of("application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
						"application/vnd.ms-excel", "text/csv", "text/plain", "text/markdown", "application/json",
						"text/xml", "application/xml", "image/jpeg", "image/png", "image/gif", "application/zip"));
		config.put("success", true);

		return ResponseEntity.ok(config);
	}

}
