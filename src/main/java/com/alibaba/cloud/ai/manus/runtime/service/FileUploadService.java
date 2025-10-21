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
import com.alibaba.cloud.ai.manus.runtime.entity.vo.FileUploadResult;
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
import java.util.ArrayList;
import java.util.List;
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

	// Upload key prefix for unique upload sessions
	private static final String UPLOAD_KEY_PREFIX = "upload-";

	/**
	 * Generate a unique upload key for file upload session
	 * @return Unique upload key
	 */
	public String generateUploadKey() {
		// Generate unique upload key with multiple uniqueness factors:
		// 1. Specific prefix for uploads
		// 2. Current timestamp in milliseconds
		// 3. Random component for additional uniqueness
		// 4. Thread ID to handle concurrent uploads
		long timestamp = System.currentTimeMillis();
		int randomComponent = (int) (Math.random() * 10000);
		long threadId = Thread.currentThread().getId();

		String uploadKey = String.format("%s%d_%d_%d", UPLOAD_KEY_PREFIX, timestamp, randomComponent, threadId);

		logger.debug("Generated unique upload key: {}", uploadKey);
		return uploadKey;
	}

	/**
	 * Upload files to a temporary directory
	 * @param files The files to upload
	 * @return FileUploadResult with upload information
	 * @throws IOException if upload fails
	 */
	public FileUploadResult uploadFiles(MultipartFile[] files) throws IOException {
		logger.info("Starting file upload with {} files", files.length);

		// Validate input
		validateUploadRequest(files);

		// Generate unique upload key for this upload session
		String uploadKey = generateUploadKey();
		logger.info("Generated upload key: {}", uploadKey);

		// Create upload directory using upload key
		Path uploadDirectory = directoryManager.getWorkingDirectory().resolve(UPLOADED_FILES_DIR).resolve(uploadKey);

		// Ensure upload directory exists
		directoryManager.ensureDirectoryExists(uploadDirectory);

		List<FileUploadResult.FileInfo> uploadedFiles = new ArrayList<>();
		int successfulFiles = 0;
		int failedFiles = 0;

		for (MultipartFile file : files) {
			try {
				FileUploadResult.FileInfo fileInfo = uploadSingleFile(file, uploadDirectory);
				uploadedFiles.add(fileInfo);
				successfulFiles++;
				logger.info("Successfully uploaded file: {} with uploadKey: {}", file.getOriginalFilename(), uploadKey);
			}
			catch (Exception e) {
				logger.error("Failed to upload file: {}", file.getOriginalFilename(), e);
				// Continue with other files, but log the error
				FileUploadResult.FileInfo errorInfo = new FileUploadResult.FileInfo();
				errorInfo.setOriginalName(file.getOriginalFilename());
				errorInfo.setError("Upload failed: " + e.getMessage());
				errorInfo.setSuccess(false);
				uploadedFiles.add(errorInfo);
				failedFiles++;
			}
		}

		// Create result
		FileUploadResult result = new FileUploadResult();
		result.setSuccess(failedFiles == 0);
		result.setMessage(failedFiles == 0 ? "Files uploaded successfully" : String
			.format("Upload completed with %d successful and %d failed files", successfulFiles, failedFiles));
		result.setUploadKey(uploadKey);
		result.setUploadedFiles(uploadedFiles);
		result.setTotalFiles(files.length);
		result.setSuccessfulFiles(successfulFiles);
		result.setFailedFiles(failedFiles);

		logger.info("Completed file upload. Successfully uploaded: {}/{} files", successfulFiles, files.length);

		return result;
	}

	/**
	 * Upload a single file
	 * @param file The file to upload
	 * @param uploadDirectory The target directory
	 * @param uploadKey The upload key for this session
	 * @return File information
	 * @throws IOException if upload fails
	 */
	private FileUploadResult.FileInfo uploadSingleFile(MultipartFile file, Path uploadDirectory) throws IOException {
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
		;

		// Create file information
		FileUploadResult.FileInfo fileInfo = new FileUploadResult.FileInfo();
		fileInfo.setOriginalName(originalFileName);
		fileInfo.setSize(file.getSize());
		fileInfo.setType(file.getContentType());
		fileInfo.setUploadTime(LocalDateTime.now());
		fileInfo.setSuccess(true);

		return fileInfo;
	}

	/**
	 * Get uploaded files for a specific upload key
	 * @param uploadKey The upload key to get files for
	 * @return List of file information
	 * @throws IOException if reading fails
	 */
	public List<FileUploadResult.FileInfo> getUploadedFiles(String uploadKey) throws IOException {
		logger.info("Getting uploaded files for uploadKey: {}", uploadKey);

		// Validate upload key
		if (uploadKey == null || uploadKey.trim().isEmpty()) {
			throw new IllegalArgumentException("Upload key cannot be empty");
		}

		if (!uploadKey.startsWith(UPLOAD_KEY_PREFIX)) {
			throw new IllegalArgumentException("Invalid upload key format. Must start with: " + UPLOAD_KEY_PREFIX);
		}

		Path uploadDirectory = directoryManager.getWorkingDirectory().resolve(UPLOADED_FILES_DIR).resolve(uploadKey);

		if (!Files.exists(uploadDirectory)) {
			logger.info("No uploaded files directory found for uploadKey: {}", uploadKey);
			return new ArrayList<>();
		}

		List<FileUploadResult.FileInfo> files = new ArrayList<>();

		// Get files only from the specific upload key directory
		try (Stream<Path> fileStream = Files.list(uploadDirectory)) {
			fileStream.filter(Files::isRegularFile).forEach(filePath -> {
				try {
					FileUploadResult.FileInfo fileInfo = createFileInfo(filePath);
					files.add(fileInfo);
				}
				catch (IOException e) {
					logger.warn("Error reading file info for: {}", filePath, e);
				}
			});
		}

		// Sort by upload time (newest first)
		files.sort((a, b) -> {
			LocalDateTime timeA = a.getUploadTime();
			LocalDateTime timeB = b.getUploadTime();
			if (timeA == null && timeB == null)
				return 0;
			if (timeA == null)
				return 1;
			if (timeB == null)
				return -1;
			return timeB.compareTo(timeA);
		});

		logger.info("Found {} uploaded files for uploadKey: {}", files.size(), uploadKey);
		return files;
	}

	/**
	 * Delete an uploaded file from a specific upload key directory
	 * @param fileName The file name to delete
	 * @param uploadKey The upload key directory to delete from
	 * @return true if deleted successfully, false otherwise
	 */
	public boolean deleteFile(String fileName, String uploadKey) {
		logger.info("Deleting file: {} from uploadKey: {}", fileName, uploadKey);

		try {
			// Validate upload key
			if (uploadKey == null || uploadKey.trim().isEmpty()) {
				logger.warn("Upload key cannot be empty");
				return false;
			}

			if (!uploadKey.startsWith(UPLOAD_KEY_PREFIX)) {
				logger.warn("Invalid upload key format. Must start with: {}", UPLOAD_KEY_PREFIX);
				return false;
			}

			Path uploadDirectory = directoryManager.getWorkingDirectory()
				.resolve(UPLOADED_FILES_DIR)
				.resolve(uploadKey);

			if (!Files.exists(uploadDirectory)) {
				logger.warn("Upload directory not found for uploadKey: {}", uploadKey);
				return false;
			}

			Path filePath = uploadDirectory.resolve(fileName);

			// Security check: ensure file is within the specific upload key directory
			if (filePath.normalize().startsWith(uploadDirectory.normalize()) && Files.exists(filePath)) {
				Files.delete(filePath);
				logger.info("Successfully deleted file: {} from uploadKey: {}", fileName, uploadKey);
				return true;
			}

			logger.warn("File not found: {} in uploadKey: {}", fileName, uploadKey);
			return false;

		}
		catch (Exception e) {
			logger.error("Error deleting file: {} from uploadKey: {}", fileName, uploadKey, e);
			return false;
		}
	}

	/**
	 * Validate upload request
	 * @param files The files to validate
	 */
	private void validateUploadRequest(MultipartFile[] files) {
		if (files == null || files.length == 0) {
			throw new IllegalArgumentException("No files provided for upload");
		}

		if (files.length > MAX_FILES_PER_UPLOAD) {
			throw new IllegalArgumentException("Too many files. Maximum allowed: " + MAX_FILES_PER_UPLOAD);
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
	 * Create file information
	 * @param filePath The file path
	 * @param uploadKey The upload key for this file
	 * @return File information
	 * @throws IOException if reading fails
	 */
	private FileUploadResult.FileInfo createFileInfo(Path filePath) throws IOException {
		FileUploadResult.FileInfo fileInfo = new FileUploadResult.FileInfo();

		String fileName = filePath.getFileName().toString();

		fileInfo.setOriginalName(fileName); // Assume same as fileName for existing files
		fileInfo.setSize(Files.size(filePath));
		fileInfo.setType(Files.probeContentType(filePath));
		fileInfo.setUploadTime(LocalDateTime.now()); // Use current time as fallback
		fileInfo.setSuccess(true);

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

	/**
	 * Synchronize uploaded files from upload directory to plan execution directory This
	 * method copies files from upload_files/uploadKey to
	 * extensions/inner_storage/rootPlanId
	 * @param uploadKey The upload key for the uploaded files
	 * @param rootPlanId The root plan ID for the target directory
	 * @return List of synchronized file information
	 * @throws IOException if synchronization fails
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public List<FileUploadResult.FileInfo> syncUploadedFilesToPlan(String uploadKey, String rootPlanId)
			throws IOException {
		logger.info("Starting file synchronization from uploadKey: {} to rootPlanId: {}", uploadKey, rootPlanId);

		// Validate parameters
		validateSyncParameters(uploadKey, rootPlanId);

		// Get source directory (upload_files/uploadKey)
		Path sourceDirectory = directoryManager.getWorkingDirectory().resolve(UPLOADED_FILES_DIR).resolve(uploadKey);

		if (!Files.exists(sourceDirectory)) {
			logger.warn("Source upload directory not found: {}", sourceDirectory);
			return new ArrayList<>();
		}

		// Get target directory (extensions/inner_storage/rootPlanId)
		Path targetDirectory = directoryManager.getRootPlanDirectory(rootPlanId);

		// Ensure target directory exists
		directoryManager.ensureDirectoryExists(targetDirectory);

		List<FileUploadResult.FileInfo> synchronizedFiles = new ArrayList<>();
		int successfulSyncs = 0;
		int failedSyncs = 0;

		try (Stream<Path> fileStream = Files.list(sourceDirectory)) {
			List<Path> files = fileStream.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toList());

			if (files.isEmpty()) {
				logger.info("No files found in upload directory: {}", sourceDirectory);
				return synchronizedFiles;
			}

			logger.info("Found {} files to synchronize from uploadKey: {} to rootPlanId: {}", files.size(), uploadKey,
					rootPlanId);

			for (Path sourceFile : files) {
				try {
					FileUploadResult.FileInfo fileInfo = syncSingleFile(sourceFile, targetDirectory, uploadKey);
					synchronizedFiles.add(fileInfo);
					successfulSyncs++;
					logger.info("Successfully synchronized file: {} from uploadKey: {} to rootPlanId: {}",
							sourceFile.getFileName(), uploadKey, rootPlanId);
				}
				catch (Exception e) {
					logger.error("Failed to synchronize file: {} from uploadKey: {} to rootPlanId: {}",
							sourceFile.getFileName(), uploadKey, rootPlanId, e);

					// Create error file info
					FileUploadResult.FileInfo errorInfo = new FileUploadResult.FileInfo();
					errorInfo.setOriginalName(sourceFile.getFileName().toString());
					errorInfo.setError("Sync failed: " + e.getMessage());
					errorInfo.setSuccess(false);
					synchronizedFiles.add(errorInfo);
					failedSyncs++;
				}
			}
		}

		logger.info(
				"Completed file synchronization. Successfully synchronized: {}/{} files from uploadKey: {} to rootPlanId: {}",
				successfulSyncs, successfulSyncs + failedSyncs, uploadKey, rootPlanId);

		return synchronizedFiles;
	}

	/**
	 * Synchronize a single file from source to target directory
	 * @param sourceFile The source file path
	 * @param targetDirectory The target directory path
	 * @param uploadKey The upload key for logging purposes
	 * @return File information for the synchronized file
	 * @throws IOException if synchronization fails
	 */
	private FileUploadResult.FileInfo syncSingleFile(Path sourceFile, Path targetDirectory, String uploadKey)
			throws IOException {
		String fileName = sourceFile.getFileName().toString();
		Path targetFile = targetDirectory.resolve(fileName);

		// Generate unique file name if target file already exists
		if (Files.exists(targetFile)) {
			String uniqueFileName = generateUniqueFileName(fileName, targetDirectory);
			targetFile = targetDirectory.resolve(uniqueFileName);
			logger.debug("Target file exists, using unique name: {}", uniqueFileName);
		}

		// Copy file to target location
		Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

		// Create file information
		FileUploadResult.FileInfo fileInfo = new FileUploadResult.FileInfo();
		fileInfo.setOriginalName(fileName);
		fileInfo.setSize(Files.size(targetFile));
		fileInfo.setType(Files.probeContentType(targetFile));
		fileInfo.setUploadTime(LocalDateTime.now());
		fileInfo.setSuccess(true);

		// Log relative path information
		String relativePath = directoryManager.getRelativePathFromWorkingDirectory(targetFile);
		logger.debug("Synchronized file: {} to relative path: {}", fileName, relativePath);

		return fileInfo;
	}

	/**
	 * Validate synchronization parameters
	 * @param uploadKey The upload key to validate
	 * @param rootPlanId The root plan ID to validate
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	private void validateSyncParameters(String uploadKey, String rootPlanId) {
		if (uploadKey == null || uploadKey.trim().isEmpty()) {
			throw new IllegalArgumentException("Upload key cannot be null or empty");
		}

		if (!uploadKey.startsWith(UPLOAD_KEY_PREFIX)) {
			throw new IllegalArgumentException("Invalid upload key format. Must start with: " + UPLOAD_KEY_PREFIX);
		}

		if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
			throw new IllegalArgumentException("Root plan ID cannot be null or empty");
		}

		logger.debug("Sync parameters validated - uploadKey: {}, rootPlanId: {}", uploadKey, rootPlanId);
	}

	/**
	 * Get synchronized files information for a specific root plan
	 * @param rootPlanId The root plan ID
	 * @return List of file information in the plan directory
	 * @throws IOException if reading fails
	 */
	public List<FileUploadResult.FileInfo> getSynchronizedFiles(String rootPlanId) throws IOException {
		logger.info("Getting synchronized files for rootPlanId: {}", rootPlanId);

		if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
			throw new IllegalArgumentException("Root plan ID cannot be null or empty");
		}

		Path planDirectory = directoryManager.getRootPlanDirectory(rootPlanId);

		if (!Files.exists(planDirectory)) {
			logger.info("Plan directory not found for rootPlanId: {}", rootPlanId);
			return new ArrayList<>();
		}

		List<FileUploadResult.FileInfo> files = new ArrayList<>();

		try (Stream<Path> fileStream = Files.list(planDirectory)) {
			fileStream.filter(Files::isRegularFile).forEach(filePath -> {
				try {
					FileUploadResult.FileInfo fileInfo = createFileInfo(filePath);
					files.add(fileInfo);
				}
				catch (IOException e) {
					logger.warn("Error reading file info for: {}", filePath, e);
				}
			});
		}

		// Sort by upload time (newest first)
		files.sort((a, b) -> {
			LocalDateTime timeA = a.getUploadTime();
			LocalDateTime timeB = b.getUploadTime();
			if (timeA == null && timeB == null)
				return 0;
			if (timeA == null)
				return 1;
			if (timeB == null)
				return -1;
			return timeB.compareTo(timeA);
		});

		logger.info("Found {} synchronized files for rootPlanId: {}", files.size(), rootPlanId);
		return files;
	}

}
