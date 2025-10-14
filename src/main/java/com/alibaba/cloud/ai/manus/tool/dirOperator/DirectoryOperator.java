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
package com.alibaba.cloud.ai.manus.tool.dirOperator;

import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Directory operator tool providing file system operations like listing files recursively
 * and copying files/directories similar to Linux commands 'ls -R' and 'cp -r'
 */
@Component
public class DirectoryOperator extends AbstractBaseTool<DirectoryOperator.DirectoryOperationInput> {

	private static final Logger log = LoggerFactory.getLogger(DirectoryOperator.class);

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	private final ObjectMapper objectMapper;

	public DirectoryOperator(UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper) {
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.objectMapper = objectMapper;
	}

	/**
	 * Input class for directory operations
	 */
	public static class DirectoryOperationInput {

		@JsonProperty("action")
		private String action;

		@JsonProperty("source_path")
		private String sourcePath;

		@JsonProperty("target_path")
		private String targetPath;

		@JsonProperty("recursive")
		private Boolean recursive = false;

		@JsonProperty("file_pattern")
		private String filePattern;

		public DirectoryOperationInput() {
		}

		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getSourcePath() {
			return sourcePath;
		}

		public void setSourcePath(String sourcePath) {
			this.sourcePath = sourcePath;
		}

		public String getTargetPath() {
			return targetPath;
		}

		public void setTargetPath(String targetPath) {
			this.targetPath = targetPath;
		}

		public Boolean getRecursive() {
			return recursive;
		}

		public void setRecursive(Boolean recursive) {
			this.recursive = recursive;
		}

		public String getFilePattern() {
			return filePattern;
		}

		public void setFilePattern(String filePattern) {
			this.filePattern = filePattern;
		}

	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public ToolExecuteResult run(DirectoryOperationInput input) {
		try {
			if (input == null || input.getAction() == null) {
				return new ToolExecuteResult("Invalid input: action is required");
			}

			String action = input.getAction().toLowerCase();

			switch (action) {
				case "ls":
				case "list":
					return listFiles(input);
				case "cp":
				case "copy":
					return copyFiles(input);
				default:
					return new ToolExecuteResult(
							"Unsupported action: " + action + ". Supported actions: ls, list, cp, copy");
			}
		}
		catch (Exception e) {
			log.error("Error executing directory operation", e);
			return new ToolExecuteResult("Error executing directory operation: " + e.getMessage());
		}
	}

	/**
	 * List files and directories recursively
	 */
	private ToolExecuteResult listFiles(DirectoryOperationInput input) {
		try {
			if (input.getSourcePath() == null) {
				return new ToolExecuteResult("Source path is required for list operation");
			}

			Path sourcePath = resolvePath(input.getSourcePath());
			if (!Files.exists(sourcePath)) {
				return new ToolExecuteResult("Source path does not exist: " + sourcePath);
			}

			List<FileInfo> fileList = new ArrayList<>();

			if (Files.isDirectory(sourcePath)) {
				if (input.getRecursive() != null && input.getRecursive()) {
					// Recursive listing
					Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
							if (shouldIncludeFile(file, input)) {
								fileList.add(createFileInfo(file, sourcePath, input));
							}
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
								throws IOException {
							if (shouldIncludeFile(dir, input)) {
								fileList.add(createFileInfo(dir, sourcePath, input));
							}
							return FileVisitResult.CONTINUE;
						}
					});
				}
				else {
					// Non-recursive listing
					try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourcePath)) {
						for (Path path : stream) {
							if (shouldIncludeFile(path, input)) {
								fileList.add(createFileInfo(path, sourcePath, input));
							}
						}
					}
				}
			}
			else {
				// Single file
				fileList.add(createFileInfo(sourcePath, sourcePath.getParent(), input));
			}

			// Sort by name
			fileList.sort(Comparator.comparing(FileInfo::getName));

			Map<String, Object> result = new HashMap<>();
			result.put("operation", "list");
			result.put("source_path", input.getSourcePath());
			result.put("recursive", input.getRecursive());
			result.put("total_files", fileList.size());
			result.put("files", fileList);

			return new ToolExecuteResult(objectMapper.writeValueAsString(result));

		}
		catch (IOException e) {
			log.error("Error listing files", e);
			return new ToolExecuteResult("Error listing files: " + e.getMessage());
		}
	}

	/**
	 * Copy files and directories recursively
	 */
	private ToolExecuteResult copyFiles(DirectoryOperationInput input) {
		try {
			if (input.getSourcePath() == null || input.getTargetPath() == null) {
				return new ToolExecuteResult("Both source_path and target_path are required for copy operation");
			}

			Path sourcePath = resolvePath(input.getSourcePath());
			Path targetPath = resolvePath(input.getTargetPath());

			if (!Files.exists(sourcePath)) {
				return new ToolExecuteResult("Source path does not exist: " + sourcePath);
			}

			// Create target directory if it doesn't exist
			if (Files.isDirectory(sourcePath)) {
				Files.createDirectories(targetPath);
			}
			else {
				Files.createDirectories(targetPath.getParent());
			}

			List<String> copiedFiles = new ArrayList<>();
			List<String> errors = new ArrayList<>();

			if (Files.isDirectory(sourcePath)) {
				// Copy directory
				Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						Path relativePath = sourcePath.relativize(dir);
						Path targetDir = targetPath.resolve(relativePath);
						Files.createDirectories(targetDir);
						copiedFiles.add("DIR: " + relativePath.toString());
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						try {
							Path relativePath = sourcePath.relativize(file);
							Path targetFile = targetPath.resolve(relativePath);
							Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
							copiedFiles.add("FILE: " + relativePath.toString());
						}
						catch (IOException e) {
							errors.add("Failed to copy file " + file + ": " + e.getMessage());
						}
						return FileVisitResult.CONTINUE;
					}
				});
			}
			else {
				// Copy single file
				try {
					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
					copiedFiles.add("FILE: " + sourcePath.getFileName().toString());
				}
				catch (IOException e) {
					errors.add("Failed to copy file " + sourcePath + ": " + e.getMessage());
				}
			}

			Map<String, Object> result = new HashMap<>();
			result.put("operation", "copy");
			result.put("source_path", input.getSourcePath());
			result.put("target_path", input.getTargetPath());
			result.put("copied_files", copiedFiles);
			result.put("total_copied", copiedFiles.size());

			if (!errors.isEmpty()) {
				result.put("errors", errors);
			}

			return new ToolExecuteResult(objectMapper.writeValueAsString(result));

		}
		catch (IOException e) {
			log.error("Error copying files", e);
			return new ToolExecuteResult("Error copying files: " + e.getMessage());
		}
	}

	/**
	 * Check if file should be included based on filters
	 */
	private boolean shouldIncludeFile(Path file, DirectoryOperationInput input) {
		// Check file pattern
		if (input.getFilePattern() != null && !input.getFilePattern().trim().isEmpty()) {
			String fileName = file.getFileName().toString();
			if (!fileName.matches(input.getFilePattern())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Create file info object
	 */
	private FileInfo createFileInfo(Path file, Path basePath, DirectoryOperationInput input) throws IOException {
		FileInfo info = new FileInfo();
		info.setName(file.getFileName().toString());

		// Always use subplan-relative paths by default
		info.setPath(createSubplanRelativePath(file));

		info.setIsDirectory(Files.isDirectory(file));

		if (Files.isRegularFile(file)) {
			info.setSize(Files.size(file));
		}

		BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
		info.setLastModified(attrs.lastModifiedTime().toString());
		info.setCreated(attrs.creationTime().toString());

		return info;
	}

	/**
	 * Create subplan-relative path by removing inner_storage and plan ID directories
	 */
	private String createSubplanRelativePath(Path file) {
		String fullPath = unifiedDirectoryManager.getRelativePathFromWorkingDirectory(file);

		// Pattern: inner_storage/plan-{planId}/subplan-{subplanId}/...
		// We want to extract: subplan-{subplanId}/...

		if (fullPath.startsWith("inner_storage/")) {
			String withoutInnerStorage = fullPath.substring("inner_storage/".length());

			// Find the first subplan directory
			String[] parts = withoutInnerStorage.split("/");
			if (parts.length >= 2) {
				// Skip the plan directory (parts[0]) and start from subplan directory
				// (parts[1])
				StringBuilder subplanPath = new StringBuilder();
				for (int i = 1; i < parts.length; i++) {
					if (subplanPath.length() > 0) {
						subplanPath.append("/");
					}
					subplanPath.append(parts[i]);
				}
				return subplanPath.toString();
			}
		}

		// If no subplan pattern found, return the original path
		return fullPath;
	}

	/**
	 * Resolve path using unified directory manager
	 */
	private Path resolvePath(String pathString) throws IOException {
		// Relative path from working directory
		return unifiedDirectoryManager.getRootPlanDirectory(rootPlanId).resolve(pathString);

	}

	// Interface implementation methods
	private static final String TOOL_NAME = "directory_operator";

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return """
				Directory operator tool providing file system operations similar to Linux commands.
				Supports listing files recursively (like 'ls -R') and copying files/directories (like 'cp -r').

				Operations:
				- list/ls: List files and directories with optional recursive traversal
				- copy/cp: Copy files and directories recursively

				Features:
				- Recursive directory traversal
				- File pattern filtering
				- Detailed file information (size, timestamps)
				- Error handling and validation
				""";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "properties": {
				        "action": {
				            "type": "string",
				            "enum": ["ls", "list", "cp", "copy"],
				            "description": "Operation to perform: ls/list for listing files, cp/copy for copying files"
				        },
				        "source_path": {
				            "type": "string",
				            "description": "Source path for the operation (required for all operations)"
				        },
				        "target_path": {
				            "type": "string",
				            "description": "Target path for copy operations (required for copy operations)"
				        },
				        "recursive": {
				            "type": "boolean",
				            "default": false,
				            "description": "Whether to perform recursive operations (for listing directories)"
				        },
				        "file_pattern": {
				            "type": "string",
				            "description": "Regex pattern to filter files by name"
				        }
				    },
				    "required": ["action", "source_path"]
				}
				""";
	}

	@Override
	public Class<DirectoryOperationInput> getInputType() {
		return DirectoryOperationInput.class;
	}

	@Override
	public String getCurrentToolStateString() {
		return "Directory operator ready for file system operations";
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up directory operator resources for plan: {}", planId);
			// No specific cleanup needed for this tool
		}
	}

	/**
	 * File information class
	 */
	public static class FileInfo {

		private String name;

		private String path;

		private boolean isDirectory;

		private long size;

		private String lastModified;

		private String created;

		public FileInfo() {
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean getIsDirectory() {
			return isDirectory;
		}

		public void setIsDirectory(boolean isDirectory) {
			this.isDirectory = isDirectory;
		}

		public long getSize() {
			return size;
		}

		public void setSize(long size) {
			this.size = size;
		}

		public String getLastModified() {
			return lastModified;
		}

		public void setLastModified(String lastModified) {
			this.lastModified = lastModified;
		}

		public String getCreated() {
			return created;
		}

		public void setCreated(String created) {
			this.created = created;
		}

	}

}
