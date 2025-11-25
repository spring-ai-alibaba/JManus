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
package com.alibaba.cloud.ai.lynxe.tool.textOperator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.innerStorage.SmartContentSavingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Linked folder operator that provides read-only access to an external folder configured
 * in LynxeProperties. This operator acts like a symbolic link, allowing users to browse
 * and read files from an external directory that is outside the normal working directory.
 *
 * Keywords: external folder, linked directory, external access, read-only access,
 * symbolic link, external files.
 *
 * Use this tool to access files in the external linked folder configured in system
 * settings.
 */
public class LinkedFolderOperator extends AbstractBaseTool<LinkedFolderOperator.LinkedFolderInput> {

	private static final Logger log = LoggerFactory.getLogger(LinkedFolderOperator.class);

	private static final String TOOL_NAME = "linked_folder_operator";

	/**
	 * Set of supported text file extensions
	 */
	private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Set.of(".txt", ".md", ".markdown", // Plain
																												// text
																												// and
																												// Markdown
			".java", ".py", ".js", ".ts", ".jsx", ".tsx", // Common programming languages
			".html", ".htm", ".mhtml", ".css", ".scss", ".sass", ".less", // Web-related
			".xml", ".json", ".yaml", ".yml", ".properties", // Configuration files
			".sql", ".sh", ".bat", ".cmd", // Scripts and database
			".log", ".conf", ".ini", // Logs and configuration
			".gradle", ".pom", ".mvn", // Build tools
			".csv", ".rst", ".adoc", // Documentation and data
			".cpp", ".c", ".h", ".go", ".rs", ".php", ".rb", ".swift", ".kt", ".scala" // Additional
																						// programming
																						// languages
	));

	/**
	 * Input class for linked folder operations
	 */
	public static class LinkedFolderInput {

		private String action;

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		@com.fasterxml.jackson.annotation.JsonProperty("start_line")
		private Integer startLine;

		@com.fasterxml.jackson.annotation.JsonProperty("end_line")
		private Integer endLine;

		@com.fasterxml.jackson.annotation.JsonProperty("pattern")
		private String pattern;

		@com.fasterxml.jackson.annotation.JsonProperty("case_sensitive")
		private Boolean caseSensitive;

		@com.fasterxml.jackson.annotation.JsonProperty("whole_word")
		private Boolean wholeWord;

		// Getters and setters
		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getFilePath() {
			return filePath;
		}

		public void setFilePath(String filePath) {
			this.filePath = filePath;
		}

		public Integer getStartLine() {
			return startLine;
		}

		public void setStartLine(Integer startLine) {
			this.startLine = startLine;
		}

		public Integer getEndLine() {
			return endLine;
		}

		public void setEndLine(Integer endLine) {
			this.endLine = endLine;
		}

		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public Boolean getCaseSensitive() {
			return caseSensitive;
		}

		public void setCaseSensitive(Boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
		}

		public Boolean getWholeWord() {
			return wholeWord;
		}

		public void setWholeWord(Boolean wholeWord) {
			this.wholeWord = wholeWord;
		}

	}

	private final LynxeProperties lynxeProperties;

	private final SmartContentSavingService innerStorageService;

	private final ObjectMapper objectMapper;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	public LinkedFolderOperator(LynxeProperties lynxeProperties, SmartContentSavingService innerStorageService,
			ObjectMapper objectMapper, UnifiedDirectoryManager unifiedDirectoryManager) {
		this.lynxeProperties = lynxeProperties;
		this.innerStorageService = innerStorageService;
		this.objectMapper = objectMapper;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("LinkedFolderOperator toolInput: {}", toolInput);
		try {
			Map<String, Object> toolInputMap = objectMapper.readValue(toolInput,
					new TypeReference<Map<String, Object>>() {
					});

			String action = (String) toolInputMap.get("action");
			String filePath = (String) toolInputMap.get("file_path");

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}
			// file_path is optional for list_files action
			if (filePath == null && !"list_files".equals(action)) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			return switch (action) {
				case "get_text" -> {
					Integer startLine = (Integer) toolInputMap.get("start_line");
					Integer endLine = (Integer) toolInputMap.get("end_line");

					if (startLine == null || endLine == null) {
						yield new ToolExecuteResult(
								"Error: get_text operation requires start_line and end_line parameters");
					}

					yield getTextByLines(filePath, startLine, endLine);
				}
				case "get_all_text" -> getAllText(filePath);
				case "list_files" -> listFiles(filePath != null ? filePath : "");
				case "grep" -> {
					String pattern = (String) toolInputMap.get("pattern");
					Boolean caseSensitive = (Boolean) toolInputMap.get("case_sensitive");
					Boolean wholeWord = (Boolean) toolInputMap.get("whole_word");

					if (pattern == null) {
						yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
					}

					yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
							wholeWord != null ? wholeWord : false);
				}
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: get_text, get_all_text, list_files, grep. Note: This is read-only access.");
			};
		}
		catch (Exception e) {
			log.error("LinkedFolderOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(LinkedFolderInput input) {
		log.info("LinkedFolderOperator input: action={}, filePath={}", input.getAction(), input.getFilePath());
		try {
			String action = input.getAction();
			String filePath = input.getFilePath();

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}
			// file_path is optional for list_files action
			if (filePath == null && !"list_files".equals(action)) {
				return new ToolExecuteResult("Error: file_path parameter is required");
			}

			return switch (action) {
				case "get_text" -> {
					Integer startLine = input.getStartLine();
					Integer endLine = input.getEndLine();

					if (startLine == null || endLine == null) {
						yield new ToolExecuteResult(
								"Error: get_text operation requires start_line and end_line parameters");
					}

					yield getTextByLines(filePath, startLine, endLine);
				}
				case "get_all_text" -> getAllText(filePath);
				case "list_files" -> listFiles(filePath != null ? filePath : "");
				case "grep" -> {
					String pattern = input.getPattern();
					Boolean caseSensitive = input.getCaseSensitive();
					Boolean wholeWord = input.getWholeWord();

					if (pattern == null) {
						yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
					}

					yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
							wholeWord != null ? wholeWord : false);
				}
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: get_text, get_all_text, list_files, grep. Note: This is read-only access.");
			};
		}
		catch (Exception e) {
			log.error("LinkedFolderOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Get the linked external directory path through UnifiedDirectoryManager. This
	 * ensures access is only through the rootPlanId/linked_external symbolic link.
	 */
	private Path getExternalLinkedFolder() throws IOException {
		if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for linked folder operations but is null or empty");
		}

		// Get the linked external directory through UnifiedDirectoryManager
		// This ensures we only access through the symbolic link at
		// rootPlanId/linked_external
		Path linkedExternalDir = unifiedDirectoryManager.getLinkedExternalDirectory(this.rootPlanId);

		log.debug("Linked external folder accessed through UnifiedDirectoryManager: {}", linkedExternalDir);

		return linkedExternalDir;
	}

	/**
	 * Validate and get the absolute path within the external linked folder. User paths
	 * are relative to the external folder root (e.g., "abc.md" not
	 * "~/Downloads/folder1/abc.md"). This ensures users can only access files within the
	 * configured external folder, not outside it.
	 */
	private Path validateLinkedPath(String filePath) throws IOException {
		Path externalFolder = getExternalLinkedFolder();

		// Normalize the file path to remove any relative path elements
		// User paths are relative to the external folder root
		String normalizedPath = normalizeFilePath(filePath);

		// Check file type for non-directory operations
		if (!normalizedPath.isEmpty() && !normalizedPath.endsWith("/") && !isSupportedFileType(normalizedPath)) {
			throw new IOException("Unsupported file type. Only text-based files are supported.");
		}

		// Resolve file path within the external folder
		// User provides paths relative to external folder root (e.g., "abc.md" or
		// "subdir/file.txt")
		Path absolutePath = normalizedPath.isEmpty() ? externalFolder
				: externalFolder.resolve(normalizedPath).normalize();

		// Ensure the path stays within the external folder (prevent path traversal)
		// This ensures users cannot access files outside the configured external folder
		// Note: We resolve the symbolic link to get the actual external folder path
		Path resolvedExternalFolder = externalFolder.toRealPath();
		Path resolvedAbsolutePath = absolutePath.toRealPath();

		if (!resolvedAbsolutePath.startsWith(resolvedExternalFolder)) {
			throw new IOException("Access denied: Invalid file path - path traversal not allowed. "
					+ "File path must be within the external linked folder.");
		}

		return absolutePath;
	}

	/**
	 * Normalize file path by removing leading slashes
	 */
	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "";
		}

		// Remove leading slashes
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		return normalized;
	}

	/**
	 * Check if file type is supported
	 */
	private boolean isSupportedFileType(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return false;
		}

		String extension = getFileExtension(filePath);
		return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
	}

	/**
	 * Get file extension
	 */
	private String getFileExtension(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return "";
		}

		int lastDotIndex = filePath.lastIndexOf('.');
		if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
			return "";
		}

		return filePath.substring(lastDotIndex);
	}

	/**
	 * List files in the external linked folder
	 */
	private ToolExecuteResult listFiles(String directoryPath) {
		try {
			Path externalFolder = getExternalLinkedFolder();

			// Normalize the directory path
			String normalizedPath = normalizeFilePath(directoryPath != null ? directoryPath : "");

			// Resolve target directory
			Path targetDirectory = normalizedPath.isEmpty() ? externalFolder
					: externalFolder.resolve(normalizedPath).normalize();

			// Ensure the target directory stays within external folder
			if (!targetDirectory.startsWith(externalFolder)) {
				return new ToolExecuteResult("Error: Directory path is invalid");
			}

			// Check if directory exists
			if (!Files.exists(targetDirectory)) {
				return new ToolExecuteResult(
						"Error: Directory does not exist: " + (normalizedPath.isEmpty() ? "root" : normalizedPath));
			}

			if (!Files.isDirectory(targetDirectory)) {
				return new ToolExecuteResult("Error: Path is not a directory: " + normalizedPath);
			}

			StringBuilder result = new StringBuilder();
			result.append("Files in linked folder");
			if (!normalizedPath.isEmpty()) {
				result.append(" (").append(normalizedPath).append(")");
			}
			result.append(":\n");
			result.append("=".repeat(60)).append("\n");

			java.util.List<Path> files = Files.list(targetDirectory).sorted().toList();

			if (files.isEmpty()) {
				result.append("(empty directory)\n");
			}
			else {
				for (Path path : files) {
					try {
						String fileName = path.getFileName().toString();
						if (Files.isDirectory(path)) {
							result.append(String.format("📁 %s/\n", fileName));
						}
						else {
							long size = Files.size(path);
							String sizeStr = formatFileSize(size);
							result.append(String.format("📄 %s (%s)\n", fileName, sizeStr));
						}
					}
					catch (IOException e) {
						result.append(String.format("❌ %s (error reading)\n", path.getFileName()));
					}
				}
			}

			result.append("\nNote: This is read-only access to external folder: ").append(externalFolder);

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error listing linked folder files: {}", directoryPath, e);
			return new ToolExecuteResult("Error listing files: " + e.getMessage());
		}
	}

	/**
	 * Format file size in human-readable format
	 */
	private String formatFileSize(long size) {
		if (size < 1024)
			return size + " B";
		if (size < 1024 * 1024)
			return String.format("%.1f KB", size / 1024.0);
		if (size < 1024 * 1024 * 1024)
			return String.format("%.1f MB", size / (1024.0 * 1024));
		return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
	}

	/**
	 * Get text by line range
	 */
	private ToolExecuteResult getTextByLines(String filePath, Integer startLine, Integer endLine) {
		try {
			// Parameter validation
			if (startLine < 1 || endLine < 1) {
				return new ToolExecuteResult("Error: Line numbers must start from 1");
			}
			if (startLine > endLine) {
				return new ToolExecuteResult("Error: Start line number cannot be greater than end line number");
			}

			// Check 500-line limit
			int requestedLines = endLine - startLine + 1;
			if (requestedLines > 500) {
				return new ToolExecuteResult(
						"Error: Maximum 500 lines per request. Please adjust line range or make multiple calls. Current requested lines: "
								+ requestedLines);
			}

			Path absolutePath = validateLinkedPath(filePath);

			if (!Files.exists(absolutePath)) {
				return new ToolExecuteResult("Error: File does not exist: " + filePath);
			}

			java.util.List<String> lines = Files.readAllLines(absolutePath);

			if (lines.isEmpty()) {
				return new ToolExecuteResult("File is empty");
			}

			// Validate line number range
			if (startLine > lines.size()) {
				return new ToolExecuteResult(
						"Error: Start line number exceeds file range (file has " + lines.size() + " lines)");
			}

			// Adjust end line number (not exceeding total file lines)
			int actualEndLine = Math.min(endLine, lines.size());

			StringBuilder result = new StringBuilder();
			result.append(String.format("File: %s (Lines %d-%d, Total %d lines)\n", filePath, startLine, actualEndLine,
					lines.size()));
			result.append("=".repeat(50)).append("\n");

			for (int i = startLine - 1; i < actualEndLine; i++) {
				result.append(String.format("%4d: %s\n", i + 1, lines.get(i)));
			}

			// If file has more content, prompt user
			if (actualEndLine < lines.size()) {
				result.append("\nNote: File has more content (lines ")
					.append(actualEndLine + 1)
					.append("-")
					.append(lines.size())
					.append("), you can continue calling get_text to retrieve.");
			}

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error retrieving text lines from linked folder file: {}", filePath, e);
			return new ToolExecuteResult("Error retrieving text lines from file: " + e.getMessage());
		}
	}

	/**
	 * Get all text from file
	 */
	private ToolExecuteResult getAllText(String filePath) {
		try {
			Path absolutePath = validateLinkedPath(filePath);

			if (!Files.exists(absolutePath)) {
				return new ToolExecuteResult("Error: File does not exist: " + filePath);
			}

			String content = Files.readString(absolutePath);

			// Use InnerStorageService to intelligently process content
			if (this.rootPlanId != null && !this.rootPlanId.isEmpty()) {
				SmartContentSavingService.SmartProcessResult processedResult = innerStorageService
					.processContent(this.rootPlanId, content, "get_all_text_linked");
				return new ToolExecuteResult(processedResult.getSummary());
			}
			else {
				return new ToolExecuteResult(content);
			}
		}
		catch (IOException e) {
			log.error("Error retrieving all text from linked folder file: {}", filePath, e);
			return new ToolExecuteResult("Error retrieving all text from file: " + e.getMessage());
		}
	}

	/**
	 * Search for text patterns in file (grep functionality)
	 */
	private ToolExecuteResult grepText(String filePath, String pattern, boolean caseSensitive, boolean wholeWord) {
		try {
			Path absolutePath = validateLinkedPath(filePath);

			if (!Files.exists(absolutePath)) {
				return new ToolExecuteResult("Error: File does not exist: " + filePath);
			}

			java.util.List<String> lines = Files.readAllLines(absolutePath);

			if (lines.isEmpty()) {
				return new ToolExecuteResult("File is empty");
			}

			// Prepare pattern for matching
			String searchPattern = pattern;
			if (!caseSensitive) {
				searchPattern = pattern.toLowerCase();
			}
			if (wholeWord) {
				searchPattern = "\\b" + java.util.regex.Pattern.quote(searchPattern) + "\\b";
			}

			java.util.regex.Pattern regexPattern;
			if (wholeWord) {
				regexPattern = caseSensitive ? java.util.regex.Pattern.compile(searchPattern)
						: java.util.regex.Pattern.compile(searchPattern, java.util.regex.Pattern.CASE_INSENSITIVE);
			}
			else {
				regexPattern = caseSensitive
						? java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(searchPattern))
						: java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(searchPattern),
								java.util.regex.Pattern.CASE_INSENSITIVE);
			}

			StringBuilder result = new StringBuilder();
			result.append(String.format("Grep results for pattern '%s' in file: %s\n", pattern, filePath));
			result.append("=".repeat(60)).append("\n");

			int matchCount = 0;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				String searchLine = caseSensitive ? line : line.toLowerCase();

				if (wholeWord) {
					if (regexPattern.matcher(line).find()) {
						result.append(String.format("%4d: %s\n", i + 1, line));
						matchCount++;
					}
				}
				else {
					if (searchLine.contains(searchPattern)) {
						result.append(String.format("%4d: %s\n", i + 1, line));
						matchCount++;
					}
				}
			}

			if (matchCount == 0) {
				result.append("No matches found.\n");
			}
			else {
				result.append(String.format("\nTotal matches found: %d\n", matchCount));
			}

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error performing grep search in linked folder file: {}", filePath, e);
			return new ToolExecuteResult("Error performing grep search in file: " + e.getMessage());
		}
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			Path externalFolder = getExternalLinkedFolder();
			return "Linked folder: " + externalFolder.toString();
		}
		catch (IOException e) {
			return "Linked folder not configured or not accessible";
		}
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return """
				Provides read-only access to an external folder linked to the system. This tool accesses files
				through the rootPlanId/linked_external symbolic link, ensuring secure access to external folders.

				Important: All file paths are relative to the external folder root. For example, if the external
				folder is ~/Downloads/folder1, to access ~/Downloads/folder1/abc.md, use file_path "abc.md"
				(not "~/Downloads/folder1/abc.md").

				Keywords: external folder, linked directory, external access, read-only access,
				symbolic link, external files.

				Configuration required:
				- lynxe.general.externalLinkedFolder: Path to the external folder

				Supported operations:
				- list_files: List files and directories in the linked folder, optional file_path parameter (defaults to root)
				  Example: file_path "subdir" to list files in subdir, or empty to list root directory
				- get_text: Get content from specified line range in file, requires start_line and end_line parameters
				  Example: file_path "abc.md" to access abc.md in the external folder root
				  Limitation: Maximum 500 lines per call, use multiple calls for more content
				- get_all_text: Get all content from file
				  Example: file_path "subdir/file.txt" to access subdir/file.txt
				  Note: If file content is too long, it will be automatically stored in temporary file and return file path
				- grep: Search for text patterns in file, similar to Linux grep command
				  Parameters: pattern (required), case_sensitive (optional, default false), whole_word (optional, default false)
				  Example: file_path "document.md" to search in document.md

				Features:
				- Read-only access (no write, delete, or modify operations)
				- Access through rootPlanId/linked_external symbolic link (managed by UnifiedDirectoryManager)
				- File paths are relative to external folder root (e.g., "abc.md" not "~/Downloads/folder1/abc.md")
				- Cannot access files outside the configured external folder
				- Respects system security settings
				- Supports text file formats only

				""";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "oneOf": [
				        {
				            "type": "object",
				            "properties": {
				                "action": {
				                    "type": "string",
				                    "const": "list_files"
				                },
				                "file_path": {
				                    "type": "string",
				                    "description": "Relative directory path to list (optional, defaults to root, e.g., 'subdir' or empty for root)"
				                }
				            },
				            "required": ["action"],
				            "additionalProperties": false
				        },
				        {
				           "type": "object",
				           "properties": {
				               "action": {
				                   "type": "string",
				                   "const": "get_text"
				               },
				               "file_path": {
				                   "type": "string",
				                   "description": "Relative file path (filename or relative path, e.g., 'file.txt' or 'subdir/file.txt')"
				               },
				               "start_line": {
				                   "type": "integer",
				                   "description": "Starting line number (starts from 1)"
				               },
				               "end_line": {
				                   "type": "integer",
				                   "description": "Ending line number (inclusive). Note: Maximum 500 lines per call"
				               }
				           },
				           "required": ["action", "file_path", "start_line", "end_line"],
				           "additionalProperties": false
				       },
				       {
				           "type": "object",
				           "properties": {
				               "action": {
				                   "type": "string",
				                   "const": "get_all_text"
				               },
				               "file_path": {
				                   "type": "string",
				                   "description": "Relative file path (filename or relative path, e.g., 'file.txt' or 'subdir/file.txt')"
				               }
				           },
				           "required": ["action", "file_path"],
				           "additionalProperties": false
				       },
				        {
				            "type": "object",
				            "properties": {
				                "action": {
				                    "type": "string",
				                    "const": "grep"
				                },
				                "file_path": {
				                    "type": "string",
				                    "description": "Relative file path (filename or relative path, e.g., 'file.txt' or 'subdir/file.txt')"
				                },
				                "pattern": {
				                    "type": "string",
				                    "description": "Text pattern to search for"
				                },
				                "case_sensitive": {
				                    "type": "boolean",
				                    "description": "Whether to perform case-sensitive search (default: false)"
				                },
				                "whole_word": {
				                    "type": "boolean",
				                    "description": "Whether to match whole words only (default: false)"
				                }
				            },
				            "required": ["action", "file_path", "pattern"],
				            "additionalProperties": false
				        }
				    ]
				}
				""";
	}

	@Override
	public Class<LinkedFolderInput> getInputType() {
		return LinkedFolderInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up linked folder resources for plan: {}", planId);
			// No specific cleanup needed for read-only access
		}
	}

	@Override
	public String getServiceGroup() {
		return "default-service-group";
	}

	@Override
	public boolean isSelectable() {
		// Only show this tool if external linked folder is configured
		String externalFolder = lynxeProperties.getExternalLinkedFolder();
		return externalFolder != null && !externalFolder.trim().isEmpty();
	}

}
