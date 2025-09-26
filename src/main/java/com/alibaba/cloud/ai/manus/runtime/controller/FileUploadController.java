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

import com.alibaba.cloud.ai.manus.runtime.entity.vo.FileUploadResult;
import com.alibaba.cloud.ai.manus.runtime.service.FileUploadService;
import com.alibaba.cloud.ai.manus.runtime.service.FileValidationService;
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

	/**
	 * Upload files
	 * @param files The uploaded files
	 * @return Upload result with file information
	 */
	@PostMapping("/upload")
	public ResponseEntity<FileUploadResult> uploadFiles(@RequestParam("files") MultipartFile[] files) {
		try {
			logger.info("Uploading {} files", files.length);

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
				FileUploadResult errorResult = new FileUploadResult();
				errorResult.setSuccess(false);
				errorResult.setMessage("File validation failed: " + String.join(", ", errors));
				return ResponseEntity.badRequest().body(errorResult);
			}

			// Upload files
			FileUploadResult result = fileUploadService.uploadFiles(files);

			logger.info("Successfully uploaded {} files with uploadKey: {}", result.getSuccessfulFiles(),
					result.getUploadKey());
			return ResponseEntity.ok(result);

		}
		catch (Exception e) {
			logger.error("Error uploading files", e);
			FileUploadResult errorResult = new FileUploadResult();
			errorResult.setSuccess(false);
			errorResult.setMessage("File upload failed: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResult);
		}
	}

	/**
	 * Get uploaded files for a specific upload key
	 * @param uploadKey The upload key to get files for
	 * @return List of uploaded files
	 */
	@GetMapping("/files/{uploadKey}")
	public ResponseEntity<GetUploadedFilesResponse> getUploadedFiles(@PathVariable("uploadKey") String uploadKey) {
		try {
			logger.info("Getting uploaded files for uploadKey: {}", uploadKey);

			List<FileUploadResult.FileInfo> files = fileUploadService.getUploadedFiles(uploadKey);

			GetUploadedFilesResponse response = new GetUploadedFilesResponse();
			response.setSuccess(true);
			response.setUploadKey(uploadKey);
			response.setFiles(files);
			response.setTotalCount(files.size());

			logger.info("Found {} uploaded files for uploadKey: {}", files.size(), uploadKey);
			return ResponseEntity.ok(response);

		}
		catch (Exception e) {
			logger.error("Error getting uploaded files for uploadKey: {}", uploadKey, e);
			GetUploadedFilesResponse errorResponse = new GetUploadedFilesResponse();
			errorResponse.setSuccess(false);
			errorResponse.setError("Failed to get uploaded files: " + e.getMessage());
			return ResponseEntity.internalServerError().body(errorResponse);
		}
	}

	/**
	 * Delete an uploaded file from a specific upload key directory
	 * @param uploadKey The upload key directory
	 * @param fileName The file name to delete
	 * @return Delete result
	 */
	@DeleteMapping("/files/{uploadKey}/{fileName}")
	public ResponseEntity<DeleteFileResponse> deleteFile(@PathVariable("uploadKey") String uploadKey,
			@PathVariable("fileName") String fileName) {
		try {
			logger.info("Deleting file: {} from uploadKey: {}", fileName, uploadKey);

			boolean deleted = fileUploadService.deleteFile(fileName, uploadKey);

			DeleteFileResponse response = new DeleteFileResponse();
			if (deleted) {
				response.setSuccess(true);
				response.setMessage("File deleted successfully");
				response.setUploadKey(uploadKey);
				response.setFileName(fileName);
				logger.info("Successfully deleted file: {} from uploadKey: {}", fileName, uploadKey);
				return ResponseEntity.ok(response);
			}
			else {
				response.setSuccess(false);
				response.setError("File not found or could not be deleted");
				return ResponseEntity.notFound().build();
			}

		}
		catch (Exception e) {
			logger.error("Error deleting file: {} from uploadKey: {}", fileName, uploadKey, e);
			DeleteFileResponse errorResponse = new DeleteFileResponse();
			errorResponse.setSuccess(false);
			errorResponse.setError("Failed to delete file: " + e.getMessage());
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

	/**
	 * Inner class for get uploaded files response
	 */
	public static class GetUploadedFilesResponse {

		private boolean success;

		private String uploadKey;

		private List<FileUploadResult.FileInfo> files;

		private int totalCount;

		private String error;

		// Getters and setters
		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getUploadKey() {
			return uploadKey;
		}

		public void setUploadKey(String uploadKey) {
			this.uploadKey = uploadKey;
		}

		public List<FileUploadResult.FileInfo> getFiles() {
			return files;
		}

		public void setFiles(List<FileUploadResult.FileInfo> files) {
			this.files = files;
		}

		public int getTotalCount() {
			return totalCount;
		}

		public void setTotalCount(int totalCount) {
			this.totalCount = totalCount;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

	}

	/**
	 * Inner class for delete file response
	 */
	public static class DeleteFileResponse {

		private boolean success;

		private String message;

		private String error;

		private String uploadKey;

		private String fileName;

		// Getters and setters
		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}

		public String getUploadKey() {
			return uploadKey;
		}

		public void setUploadKey(String uploadKey) {
			this.uploadKey = uploadKey;
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

	}

}
