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
package com.alibaba.cloud.ai.manus.tool.textOperator;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File import operator that imports all files and subdirectories from a specified
 * real path to root-plan-id/shared/ directory. This tool recursively copies all
 * files and folders from the source path to the shared directory.
 */
public class FileImportOperator extends AbstractBaseTool<FileImportOperator.FileImportInput> {

	private static final Logger log = LoggerFactory.getLogger(FileImportOperator.class);

	private static final String TOOL_NAME = "file_import_operator";

	/**
	 * Input class for file import operations
	 */
	public static class FileImportInput {

		@JsonProperty("real_path")
		private String realPath;

		// Getters and setters
		public String getRealPath() {
			return realPath;
		}

		public void setRealPath(String realPath) {
			this.realPath = realPath;
		}

	}

	private final TextFileService textFileService;

	public FileImportOperator(TextFileService textFileService, ObjectMapper objectMapper) {
		this.textFileService = textFileService;
	}

	@Override
	public ToolExecuteResult run(FileImportInput input) {
		log.info("FileImportOperator input: realPath={}", input.getRealPath());
		try {
			String realPath = input.getRealPath();

			// Basic parameter validation
			if (realPath == null || realPath.trim().isEmpty()) {
				return new ToolExecuteResult("Error: real_path parameter is required");
			}

			return importFiles(realPath);
		}
		catch (Exception e) {
			log.error("FileImportOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Import all files and subdirectories from realPath to root-plan-id/shared/
	 * @param realPath The real file system path to import from
	 * @return ToolExecuteResult with import status
	 */
	private ToolExecuteResult importFiles(String realPath) {
		try {
			// Validate rootPlanId
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				return new ToolExecuteResult("Error: rootPlanId is required for file import operations");
			}

			// Resolve source path
			Path sourcePath = Paths.get(realPath).toAbsolutePath().normalize();

			// Check if source path exists
			if (!Files.exists(sourcePath)) {
				return new ToolExecuteResult("Error: Source path does not exist: " + realPath);
			}

			// Get the shared directory
			Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);
			Path sharedDirectory = rootPlanDirectory.resolve("shared");

			// Ensure shared directory exists
			Files.createDirectories(sharedDirectory);

			// Determine target path in shared directory
			Path targetPath;
			if (Files.isDirectory(sourcePath)) {
				// If source is a directory, copy its contents to shared directory
				// Use the directory name as the target folder name
				String sourceDirName = sourcePath.getFileName().toString();
				targetPath = sharedDirectory.resolve(sourceDirName);
			}
			else {
				// If source is a file, copy it directly to shared directory
				targetPath = sharedDirectory.resolve(sourcePath.getFileName());
			}

			List<String> importedFiles = new ArrayList<>();
			List<String> importedDirs = new ArrayList<>();
			List<String> errors = new ArrayList<>();

			if (Files.isDirectory(sourcePath)) {
				// Recursively copy directory
				Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						try {
							Path relativePath = sourcePath.relativize(dir);
							Path targetDir = targetPath.resolve(relativePath);
							Files.createDirectories(targetDir);
							if (!relativePath.toString().isEmpty()) {
								importedDirs.add(relativePath.toString());
							}
						}
						catch (IOException e) {
							errors.add("Failed to create directory " + dir + ": " + e.getMessage());
						}
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						try {
							Path relativePath = sourcePath.relativize(file);
							Path targetFile = targetPath.resolve(relativePath);
							Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
							importedFiles.add(relativePath.toString());
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
					importedFiles.add(sourcePath.getFileName().toString());
				}
				catch (IOException e) {
					errors.add("Failed to copy file " + sourcePath + ": " + e.getMessage());
				}
			}

			// Build result message
			StringBuilder result = new StringBuilder();
			result.append("File import completed successfully\n");
			result.append("Source path: ").append(realPath).append("\n");
			result.append("Target path: shared/").append(targetPath.getFileName()).append("\n");
			result.append("=".repeat(50)).append("\n");
			result.append("Imported directories: ").append(importedDirs.size()).append("\n");
			result.append("Imported files: ").append(importedFiles.size()).append("\n");

			if (!importedDirs.isEmpty()) {
				result.append("\nDirectories:\n");
				for (String dir : importedDirs) {
					result.append("  📁 ").append(dir).append("/\n");
				}
			}

			if (!importedFiles.isEmpty()) {
				result.append("\nFiles:\n");
				for (String file : importedFiles) {
					result.append("  📄 ").append(file).append("\n");
				}
			}

			if (!errors.isEmpty()) {
				result.append("\nErrors:\n");
				for (String error : errors) {
					result.append("  ❌ ").append(error).append("\n");
				}
			}

			log.info("Imported {} files and {} directories from {} to shared/", importedFiles.size(),
					importedDirs.size(), realPath);

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error importing files from: {}", realPath, e);
			return new ToolExecuteResult("Error importing files: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error importing files from: {}", realPath, e);
			return new ToolExecuteResult("Unexpected error importing files: " + e.getMessage());
		}
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				return "Current File Import Operation State:\n- Error: No root plan ID available";
			}

			Path workingDir = textFileService.getRootPlanDirectory(this.rootPlanId);
			Path sharedDir = workingDir.resolve("shared");
			return String.format(
					"""
							Current File Import Operation State:
							- Working Directory: %s
							- Shared Directory: %s
							- Scope: Import files from real file system path to rootPlanId/shared/
							""",
					workingDir.toString(), sharedDir.toString());
		}
		catch (Exception e) {
			return String.format("Current File Import Operation State:\n- Error getting working directory: %s",
					e.getMessage());
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return """
				Import all files and subdirectories from a specified real file system path to
				root-plan-id/shared/ directory. This tool recursively copies all files and folders
				from the source path to the shared directory, making them accessible across all
				sub-plans within the same execution context.

				Keywords: import files, copy files, file import, directory import, bulk import,
				file migration, shared files import.

				Operation:
				- import: Import all files and subdirectories from real_path to shared directory
				  Requires real_path parameter (absolute or relative file system path)
				""";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "properties": {
				        "real_path": {
				            "type": "string",
				            "description": "Real file system path (absolute or relative) to import files from. All files and subdirectories will be recursively copied to root-plan-id/shared/"
				        }
				    },
				    "required": ["real_path"],
				    "additionalProperties": false
				}
				""";
	}

	@Override
	public Class<FileImportInput> getInputType() {
		return FileImportInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up file import resources for plan: {}", planId);
		}
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}

