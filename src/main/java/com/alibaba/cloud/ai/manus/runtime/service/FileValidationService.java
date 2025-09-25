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
package com.alibaba.cloud.ai.manus.runtime.service;

import com.alibaba.cloud.ai.manus.config.FileTypeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * File Validation Service - Validates file uploads at the controller layer
 */
@Service
public class FileValidationService {

	private static final Logger logger = LoggerFactory.getLogger(FileValidationService.class);

	/**
	 * Validate if a single file is allowed for upload
	 */
	public FileValidationResult validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			return FileValidationResult.failure("File is empty");
		}

		String filename = file.getOriginalFilename();
		if (filename == null || filename.trim().isEmpty()) {
			return FileValidationResult.failure("Filename is empty");
		}

		// Check if file type is blocked
		if (FileTypeConfiguration.isBlocked(filename)) {
			return FileValidationResult.failure("File type is blocked for upload: " + getFileExtension(filename)
					+ "\nBlocked types: " + FileTypeConfiguration.getBlockedExtensionsString());
		}

		// Check if file type is supported
		if (!FileTypeConfiguration.isUploadAllowed(filename)) {
			return FileValidationResult.failure("Unsupported file type: " + getFileExtension(filename)
					+ "\nSupported types: " + FileTypeConfiguration.getSupportedExtensionsString());
		}

		// Get file category information
		String category = FileTypeConfiguration.getFileCategory(filename);

		logger.info("File validation passed: {} (type: {}, size: {} bytes)", filename, category, file.getSize());

		return FileValidationResult.success("File validation passed - " + filename + " (" + category + " type)");
	}

	/**
	 * Batch validate files
	 */
	public List<FileValidationResult> validateFiles(List<MultipartFile> files) {
		List<FileValidationResult> results = new ArrayList<>();

		if (files == null || files.isEmpty()) {
			results.add(FileValidationResult.failure("No files to validate"));
			return results;
		}

		for (MultipartFile file : files) {
			results.add(validateFile(file));
		}

		return results;
	}

	/**
	 * Get file extension
	 */
	private String getFileExtension(String filename) {
		if (filename == null || filename.isEmpty()) {
			return "";
		}

		int lastDotIndex = filename.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
			return "";
		}

		return filename.substring(lastDotIndex);
	}

	/**
	 * File validation result class
	 */
	public static class FileValidationResult {

		private final boolean valid;

		private final String message;

		private FileValidationResult(boolean valid, String message) {
			this.valid = valid;
			this.message = message;
		}

		public static FileValidationResult success(String message) {
			return new FileValidationResult(true, message);
		}

		public static FileValidationResult failure(String message) {
			return new FileValidationResult(false, message);
		}

		public boolean isValid() {
			return valid;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public String toString() {
			return (valid ? "✅ " : "❌ ") + message;
		}

	}

}
