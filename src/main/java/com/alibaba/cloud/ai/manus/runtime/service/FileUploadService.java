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
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Service for handling file upload operations
 */
@Service
public class FileUploadService {

	private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);

	@Autowired
	private UnifiedDirectoryManager directoryManager;

	// Subdirectory for uploaded files within plan directory
	private static final String UPLOADED_FILES_DIR = "uploaded_files";

	// Maximum file size (1GB)
	private static final long MAX_FILE_SIZE = 1024 * 1024 * 1024;

	// Maximum number of files per upload
	private static final int MAX_FILES_PER_UPLOAD = 10;

	/**
	 * Upload files to a plan directory
	 * @param files The files to upload
	 * @param planId The plan ID
	 * @return List of uploaded file information
	 * @throws IOException if upload fails
	 */
	public List<Map<String, Object>> uploadFiles(MultipartFile[] files, String planId) throws IOException {
		logger.info("Starting file upload for planId: {} with {} files", planId, files.length);

		// Validate input
		validateUploadRequest(files, planId);

		// Get or create the plan directory
		Path planDirectory = directoryManager.getRootPlanDirectory(planId);
		Path uploadDirectory = planDirectory.resolve(UPLOADED_FILES_DIR);

		// Ensure upload directory exists
		directoryManager.ensureDirectoryExists(uploadDirectory);

		List<Map<String, Object>> uploadedFiles = new ArrayList<>();

		for (MultipartFile file : files) {
			try {
				Map<String, Object> fileInfo = uploadSingleFile(file, uploadDirectory, planId);
				uploadedFiles.add(fileInfo);
				logger.info("Successfully uploaded file: {} to planId: {}", file.getOriginalFilename(), planId);
			}
			catch (Exception e) {
				logger.error("Failed to upload file: {} to planId: {}", file.getOriginalFilename(), planId, e);
				// Continue with other files, but log the error
				Map<String, Object> errorInfo = new HashMap<>();
				errorInfo.put("originalName", file.getOriginalFilename());
				errorInfo.put("error", "Upload failed: " + e.getMessage());
				errorInfo.put("success", false);
				uploadedFiles.add(errorInfo);
			}
		}

		logger.info("Completed file upload for planId: {}. Successfully uploaded: {}/{} files", planId,
				uploadedFiles.stream().mapToInt(f -> (Boolean) f.getOrDefault("success", true) ? 1 : 0).sum(),
				files.length);

		return uploadedFiles;
	}

	/**
	 * Upload a single file
	 * @param file The file to upload
	 * @param uploadDirectory The target directory
	 * @param planId The plan ID
	 * @return File information map
	 * @throws IOException if upload fails
	 */
	private Map<String, Object> uploadSingleFile(MultipartFile file, Path uploadDirectory, String planId)
			throws IOException {
		// Validate single file
		validateSingleFile(file);

		String originalFileName = file.getOriginalFilename();
		if (originalFileName == null || originalFileName.trim().isEmpty()) {
			throw new IllegalArgumentException("File name cannot be empty");
		}

		// Generate unique file name to avoid conflicts
		String uniqueFileName = generateUniqueFileName(originalFileName, uploadDirectory);
		Path targetPath = uploadDirectory.resolve(uniqueFileName);

		// Copy file to target location
		Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

		// Calculate relative path from plan directory
		Path planDirectory = directoryManager.getRootPlanDirectory(planId);
		String relativePath = planDirectory.relativize(targetPath).toString();

		// Create file information
		Map<String, Object> fileInfo = new HashMap<>();
		fileInfo.put("originalName", originalFileName);
		fileInfo.put("fileName", uniqueFileName);
		fileInfo.put("size", file.getSize());
		fileInfo.put("type", file.getContentType());
		fileInfo.put("relativePath", relativePath);
		fileInfo.put("planId", planId);
		fileInfo.put("uploadTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
		fileInfo.put("success", true);

		return fileInfo;
	}

	/**
	 * Get uploaded files for a plan
	 * @param planId The plan ID
	 * @return List of file information
	 * @throws IOException if reading fails
	 */
	public List<Map<String, Object>> getUploadedFiles(String planId) throws IOException {
		logger.info("Getting uploaded files for planId: {}", planId);

		Path planDirectory = directoryManager.getRootPlanDirectory(planId);
		Path uploadDirectory = planDirectory.resolve(UPLOADED_FILES_DIR);

		if (!Files.exists(uploadDirectory)) {
			logger.info("No uploaded files directory found for planId: {}", planId);
			return new ArrayList<>();
		}

		List<Map<String, Object>> files = new ArrayList<>();

		try (Stream<Path> fileStream = Files.list(uploadDirectory)) {
			fileStream.filter(Files::isRegularFile).forEach(filePath -> {
				try {
					Map<String, Object> fileInfo = createFileInfo(filePath, planId);
					files.add(fileInfo);
				}
				catch (IOException e) {
					logger.warn("Error reading file info for: {}", filePath, e);
				}
			});
		}

		// Sort by upload time (newest first)
		files.sort((a, b) -> {
			String timeA = (String) a.getOrDefault("lastModified", "");
			String timeB = (String) b.getOrDefault("lastModified", "");
			return timeB.compareTo(timeA);
		});

		logger.info("Found {} uploaded files for planId: {}", files.size(), planId);
		return files;
	}

	/**
	 * Delete an uploaded file
	 * @param planId The plan ID
	 * @param fileName The file name to delete
	 * @return true if deleted successfully, false otherwise
	 */
	public boolean deleteFile(String planId, String fileName) {
		logger.info("Deleting file: {} from planId: {}", fileName, planId);

		try {
			Path planDirectory = directoryManager.getRootPlanDirectory(planId);
			Path uploadDirectory = planDirectory.resolve(UPLOADED_FILES_DIR);
			Path filePath = uploadDirectory.resolve(fileName);

			// Security check: ensure file is within upload directory
			if (!filePath.normalize().startsWith(uploadDirectory.normalize())) {
				logger.warn("Security violation: Attempt to delete file outside upload directory: {}", filePath);
				return false;
			}

			if (Files.exists(filePath)) {
				Files.delete(filePath);
				logger.info("Successfully deleted file: {} from planId: {}", fileName, planId);
				return true;
			}
			else {
				logger.warn("File not found: {} in planId: {}", fileName, planId);
				return false;
			}

		}
		catch (Exception e) {
			logger.error("Error deleting file: {} from planId: {}", fileName, planId, e);
			return false;
		}
	}

	/**
	 * Validate upload request
	 * @param files The files to validate
	 * @param planId The plan ID
	 */
	private void validateUploadRequest(MultipartFile[] files, String planId) {
		if (files == null || files.length == 0) {
			throw new IllegalArgumentException("No files provided for upload");
		}

		if (files.length > MAX_FILES_PER_UPLOAD) {
			throw new IllegalArgumentException("Too many files. Maximum allowed: " + MAX_FILES_PER_UPLOAD);
		}

		if (planId == null || planId.trim().isEmpty()) {
			throw new IllegalArgumentException("Plan ID cannot be empty");
		}
	}

	/**
	 * Validate a single file
	 * @param file The file to validate
	 */
	private void validateSingleFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("File cannot be empty");
		}

		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException(
					"File size exceeds maximum allowed size: " + (MAX_FILE_SIZE / 1024 / 1024 / 1024) + "GB");
		}

		String originalFileName = file.getOriginalFilename();
		if (originalFileName != null) {
			// Use new file type configuration system
			if (FileTypeConfiguration.isBlocked(originalFileName)) {
				throw new IllegalArgumentException(
						"File type blocked for security reasons: " + getFileExtension(originalFileName));
			}
			if (!FileTypeConfiguration.isUploadAllowed(originalFileName)) {
				throw new IllegalArgumentException("File type not supported: " + getFileExtension(originalFileName)
						+ ". Supported types: " + FileTypeConfiguration.getSupportedExtensionsString());
			}
		}

		String contentType = file.getContentType();
		if (contentType != null) {
			logger.debug("File MIME type: {}", contentType);
			// MIME type validation is handled by FileTypeConfiguration through extension
			// validation
		}
	}

	/**
	 * Generate unique file name to avoid conflicts
	 * @param originalFileName The original file name
	 * @param targetDirectory The target directory
	 * @return Unique file name
	 */
	private String generateUniqueFileName(String originalFileName, Path targetDirectory) {
		String baseName = getFileBaseName(originalFileName);
		String extension = getFileExtension(originalFileName);

		String uniqueName = originalFileName;
		int counter = 1;

		while (Files.exists(targetDirectory.resolve(uniqueName))) {
			uniqueName = baseName + "_" + counter + extension;
			counter++;
		}

		return uniqueName;
	}

	/**
	 * Create file information map
	 * @param filePath The file path
	 * @param planId The plan ID
	 * @return File information map
	 * @throws IOException if reading fails
	 */
	private Map<String, Object> createFileInfo(Path filePath, String planId) throws IOException {
		Map<String, Object> fileInfo = new HashMap<>();

		String fileName = filePath.getFileName().toString();
		Path planDirectory = directoryManager.getRootPlanDirectory(planId);
		String relativePath = planDirectory.relativize(filePath).toString();

		fileInfo.put("fileName", fileName);
		fileInfo.put("originalName", fileName); // Assume same as fileName for existing
												// files
		fileInfo.put("size", Files.size(filePath));
		fileInfo.put("type", Files.probeContentType(filePath));
		fileInfo.put("relativePath", relativePath);
		fileInfo.put("planId", planId);
		fileInfo.put("lastModified", Files.getLastModifiedTime(filePath).toString());

		return fileInfo;
	}

	/**
	 * Get file extension including the dot
	 * @param fileName The file name
	 * @return File extension (e.g., ".pdf")
	 */
	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
	}

	/**
	 * Get file base name without extension
	 * @param fileName The file name
	 * @return Base name without extension
	 */
	private String getFileBaseName(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(0, lastDotIndex) : fileName;
	}

}
