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
package com.alibaba.cloud.ai.manus.runtime.entity.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result class for file upload operations
 */
public class FileUploadResult {

	private boolean success;

	private String message;

	private String uploadKey;

	private List<FileInfo> uploadedFiles;

	private int totalFiles;

	private int successfulFiles;

	private int failedFiles;

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

	public String getUploadKey() {
		return uploadKey;
	}

	public void setUploadKey(String uploadKey) {
		this.uploadKey = uploadKey;
	}

	public List<FileInfo> getUploadedFiles() {
		return uploadedFiles;
	}

	public void setUploadedFiles(List<FileInfo> uploadedFiles) {
		this.uploadedFiles = uploadedFiles;
	}

	public int getTotalFiles() {
		return totalFiles;
	}

	public void setTotalFiles(int totalFiles) {
		this.totalFiles = totalFiles;
	}

	public int getSuccessfulFiles() {
		return successfulFiles;
	}

	public void setSuccessfulFiles(int successfulFiles) {
		this.successfulFiles = successfulFiles;
	}

	public int getFailedFiles() {
		return failedFiles;
	}

	public void setFailedFiles(int failedFiles) {
		this.failedFiles = failedFiles;
	}

	/**
	 * Inner class for individual file information
	 */
	public static class FileInfo {
		private String originalName;
		private long size;
		private String type;
		private LocalDateTime uploadTime;
		private boolean success;
		private String error;

		// Getters and setters
		public String getOriginalName() {
			return originalName;
		}

		public void setOriginalName(String originalName) {
			this.originalName = originalName;
		}

		public long getSize() {
			return size;
		}

		public void setSize(long size) {
			this.size = size;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}


		public LocalDateTime getUploadTime() {
			return uploadTime;
		}

		public void setUploadTime(LocalDateTime uploadTime) {
			this.uploadTime = uploadTime;
		}

		public boolean isSuccess() {
			return success;
		}

		public void setSuccess(boolean success) {
			this.success = success;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}
	}
}
