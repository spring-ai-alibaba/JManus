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
package com.alibaba.cloud.ai.lynxe.tool.mapreduce;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.TextFileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File splitter tool that splits text files (markdown, code, HTML, etc.) into smaller
 * pieces. Splits files by lines to ensure content completeness and adds index numbers to
 * split file names.
 */
public class FileSplitterTool extends AbstractBaseTool<FileSplitterTool.FileSplitterInput> {

	private static final Logger log = LoggerFactory.getLogger(FileSplitterTool.class);

	private static final String TOOL_NAME = "file_splitter";

	/**
	 * Number of pieces to split file into
	 */
	private static final int SPLIT_COUNT = 10;

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
	 * Input class for file splitter operations
	 */
	public static class FileSplitterInput {

		private String action;

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private String header;

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

		public String getHeader() {
			return header;
		}

		public void setHeader(String header) {
			this.header = header;
		}

	}

	private final TextFileService textFileService;

	private final ObjectMapper objectMapper;

	public FileSplitterTool(TextFileService textFileService, ObjectMapper objectMapper) {
		this.textFileService = textFileService;
		this.objectMapper = objectMapper;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("FileSplitterTool toolInput: {}", toolInput);
		try {
			Map<String, Object> toolInputMap = objectMapper.readValue(toolInput,
					new TypeReference<Map<String, Object>>() {
					});

			String action = (String) toolInputMap.get("action");
			String filePath = (String) toolInputMap.get("file_path");
			String header = (String) toolInputMap.get("header");

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}

			return switch (action) {
				case "split" -> {
					if (filePath == null || filePath.trim().isEmpty()) {
						yield new ToolExecuteResult("Error: file_path parameter is required for split operation");
					}
					// Header is optional, can be null or empty
					yield splitFile(filePath, header);
				}
				case "count" -> {
					if (filePath == null || filePath.trim().isEmpty()) {
						yield new ToolExecuteResult("Error: file_path parameter is required for count operation");
					}
					yield countFile(filePath);
				}
				default ->
					new ToolExecuteResult("Unknown operation: " + action + ". Supported operations: split, count");
			};
		}
		catch (Exception e) {
			log.error("FileSplitterTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(FileSplitterInput input) {
		log.info("FileSplitterTool input: action={}, filePath={}", input.getAction(), input.getFilePath());
		try {
			String action = input.getAction();
			String filePath = input.getFilePath();
			String header = input.getHeader();

			// Basic parameter validation
			if (action == null) {
				return new ToolExecuteResult("Error: action parameter is required");
			}

			return switch (action) {
				case "split" -> {
					if (filePath == null || filePath.trim().isEmpty()) {
						yield new ToolExecuteResult("Error: file_path parameter is required for split operation");
					}
					// Header is optional, can be null or empty
					yield splitFile(filePath, header);
				}
				case "count" -> {
					if (filePath == null || filePath.trim().isEmpty()) {
						yield new ToolExecuteResult("Error: file_path parameter is required for count operation");
					}
					yield countFile(filePath);
				}
				default ->
					new ToolExecuteResult("Unknown operation: " + action + ". Supported operations: split, count");
			};
		}
		catch (Exception e) {
			log.error("FileSplitterTool execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Validate and get the absolute path for the file. Files are read from
	 * rootPlanId/shared/ directory, same as GlobalFileOperator and MarkdownConverterTool.
	 */
	private Path validateFilePath(String filePath) throws IOException {
		if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for file splitter operations but is null or empty");
		}

		// Check file type
		if (!isSupportedFileType(filePath)) {
			throw new IOException("Unsupported file type. Only text-based files are supported.");
		}

		// Normalize the file path (remove leading slashes, similar to GlobalFileOperator)
		String normalizedPath = normalizeFilePath(filePath);

		// Get the root plan directory and resolve to shared subdirectory
		// Same approach as GlobalFileOperator and MarkdownConverterTool
		Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);
		Path sharedDirectory = rootPlanDirectory.resolve("shared");

		// Resolve file path within the shared directory
		Path absolutePath = sharedDirectory.resolve(normalizedPath).normalize();

		// Ensure the path stays within the shared directory
		if (!absolutePath.startsWith(sharedDirectory)) {
			throw new IOException("Access denied: File path must be within the shared directory");
		}

		if (!Files.exists(absolutePath)) {
			throw new IOException("File does not exist: " + filePath
					+ ". Please ensure the file exists in the root plan shared directory (rootPlanId/shared/).");
		}

		if (!Files.isRegularFile(absolutePath)) {
			throw new IOException("Path is not a regular file: " + filePath);
		}

		return absolutePath;
	}

	/**
	 * Normalize file path by removing leading slashes (similar to GlobalFileOperator)
	 */
	private String normalizeFilePath(String filePath) {
		if (filePath == null || filePath.isEmpty()) {
			return filePath;
		}

		// Remove leading slashes
		String normalized = filePath.trim();
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}

		// Remove "shared/" prefix if present
		if (normalized.startsWith("shared/")) {
			normalized = normalized.substring("shared/".length());
		}

		// Remove any remaining "shared/" in the path
		normalized = normalized.replaceAll("^shared/", "");

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
	 * Split file into multiple pieces
	 */
	private ToolExecuteResult splitFile(String filePath, String header) {
		try {
			Path sourceFile = validateFilePath(filePath);

			// Read all lines from the source file
			List<String> allLines = Files.readAllLines(sourceFile);
			if (allLines.isEmpty()) {
				return new ToolExecuteResult("Error: File is empty, cannot split");
			}

			int totalLines = allLines.size();
			int linesPerPiece = totalLines / SPLIT_COUNT;
			int remainder = totalLines % SPLIT_COUNT;

			// Prepare header (add newline if not empty)
			String headerContent = (header != null && !header.trim().isEmpty()) ? header.trim() + "\n" : "";

			// Get file name and extension
			String fileName = sourceFile.getFileName().toString();
			int lastDotIndex = fileName.lastIndexOf('.');
			String baseName = (lastDotIndex > 0) ? fileName.substring(0, lastDotIndex) : fileName;
			String extension = (lastDotIndex > 0) ? fileName.substring(lastDotIndex) : "";

			// Get parent directory for output files
			Path parentDir = sourceFile.getParent();

			List<String> createdFiles = new ArrayList<>();
			int currentLineIndex = 0;

			// Split into SPLIT_COUNT pieces
			for (int i = 0; i < SPLIT_COUNT; i++) {
				// Calculate lines for this piece (distribute remainder evenly)
				int pieceSize = linesPerPiece + (i < remainder ? 1 : 0);

				if (pieceSize == 0) {
					// Skip empty pieces if file is too small
					continue;
				}

				// Create output file name with index prefix
				String outputFileName = String.format("%d-%s%s", i, baseName, extension);
				Path outputFile = parentDir.resolve(outputFileName);

				// Prepare content for this piece
				StringBuilder content = new StringBuilder();
				if (!headerContent.isEmpty()) {
					content.append(headerContent);
				}

				// Add lines for this piece
				for (int j = 0; j < pieceSize && currentLineIndex < totalLines; j++) {
					content.append(allLines.get(currentLineIndex));
					if (currentLineIndex < totalLines - 1 || j < pieceSize - 1) {
						content.append("\n");
					}
					currentLineIndex++;
				}

				// Write the split file
				Files.writeString(outputFile, content.toString());

				createdFiles.add(outputFileName);
				log.info("Created split file {} with {} lines", outputFileName, pieceSize);
			}

			// Build result message
			StringBuilder result = new StringBuilder();
			result
				.append(String.format("Successfully split file '%s' into %d pieces:\n", fileName, createdFiles.size()));
			result.append("=".repeat(60)).append("\n");
			for (String createdFile : createdFiles) {
				result.append(String.format("  - %s\n", createdFile));
			}
			result.append(String.format("\nTotal lines in original file: %d\n", totalLines));
			result.append(String.format("Lines per piece: approximately %d\n", linesPerPiece));
			if (!headerContent.isEmpty()) {
				result.append("Header added to each split file\n");
			}

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error splitting file: {}", filePath, e);
			return new ToolExecuteResult("Error splitting file: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error splitting file: {}", filePath, e);
			return new ToolExecuteResult("Unexpected error splitting file: " + e.getMessage());
		}
	}

	/**
	 * Count total lines and character size of the file
	 */
	private ToolExecuteResult countFile(String filePath) {
		try {
			Path sourceFile = validateFilePath(filePath);

			// Read all lines
			List<String> allLines = Files.readAllLines(sourceFile);
			int totalLines = allLines.size();

			// Calculate total character count (including newlines)
			long totalChars = 0;
			for (String line : allLines) {
				totalChars += line.length() + 1; // +1 for newline character
			}
			// Subtract 1 if file doesn't end with newline
			if (!allLines.isEmpty() && !allLines.get(allLines.size() - 1).isEmpty()) {
				// Last line might not have trailing newline
				// Actually, Files.readAllLines() doesn't include trailing newline in line
				// content
				// So we need to check if we should add it
			}

			// Get file size from filesystem
			long fileSizeBytes = Files.size(sourceFile);

			// Build result message
			StringBuilder result = new StringBuilder();
			result.append(String.format("File statistics for '%s':\n", sourceFile.getFileName()));
			result.append("=".repeat(60)).append("\n");
			result.append(String.format("Total lines: %d\n", totalLines));
			result.append(String.format("Total characters: %d\n", totalChars));
			result.append(String.format("File size: %s\n", formatFileSize(fileSizeBytes)));
			result.append(String.format("Average characters per line: %.1f\n",
					totalLines > 0 ? (double) totalChars / totalLines : 0.0));

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			log.error("Error counting file: {}", filePath, e);
			return new ToolExecuteResult("Error counting file: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Unexpected error counting file: {}", filePath, e);
			return new ToolExecuteResult("Unexpected error counting file: " + e.getMessage());
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

	@Override
	public String getCurrentToolStateString() {
		return "File splitter tool ready. Use 'split' action to split files or 'count' action to count file statistics.";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return """
				Split text files (markdown, code, HTML, etc.) into smaller pieces. This tool helps break down
				large text files into manageable chunks while preserving content completeness.

				Important: Files are read from rootPlanId/shared/ directory, same as GlobalFileOperator and
				MarkdownConverterTool. For example, to split rootPlanId/shared/projects.md, use file_path "projects.md".

				Supported operations:
				- split: Split a file into 10 smaller pieces (as evenly as possible by lines)
				  Parameters: file_path (required, relative to rootPlanId/shared/), header (optional - text to add at the beginning of each split file)
				  Output files are saved in the same directory as the source file, named with index prefix: 0-filename.ext, 1-filename.ext, etc.
				- count: Count total lines and character size of a file
				  Parameters: file_path (required, relative to rootPlanId/shared/)

				Features:
				- Splits files by lines to ensure content completeness
				- Distributes lines as evenly as possible across 10 pieces
				- Adds optional header to each split file
				- Adds index number prefix to split file names (0-, 1-, 2-, etc.)
				- Supports text file formats: markdown, code files, HTML, configuration files, etc.
				- Files are read from and written to rootPlanId/shared/ directory

				Supported file types:
				- Text files (.txt, .md, .markdown)
				- Programming files (.java, .py, .js, .ts, .cpp, .c, .h, .go, .rs, .php, .rb, .swift, .kt, .scala)
				- Web files (.html, .htm, .mhtml, .css, .scss, .sass, .less)
				- Configuration files (.json, .xml, .yaml, .yml, .properties, .conf, .ini)
				- Scripts (.sql, .sh, .bat, .cmd)
				- Build tools (.gradle, .pom, .mvn)
				- Documentation (.csv, .rst, .adoc)
				- Logs (.log)

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
				                    "const": "split"
				                },
				"file_path": {
				    "type": "string",
				    "description": "Path to the file to split (relative to rootPlanId/shared/ directory, e.g., 'file.md' or 'subdir/file.md')"
				},
				                "header": {
				                    "type": "string",
				                    "description": "Optional header text to add at the beginning of each split file"
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
				                    "const": "count"
				                },
				"file_path": {
				    "type": "string",
				    "description": "Path to the file to count (relative to rootPlanId/shared/ directory, e.g., 'file.md' or 'subdir/file.md')"
				}
				            },
				            "required": ["action", "file_path"],
				            "additionalProperties": false
				        }
				    ]
				}
				""";
	}

	@Override
	public Class<FileSplitterInput> getInputType() {
		return FileSplitterInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up file splitter resources for plan: {}", planId);
		}
	}

	@Override
	public String getServiceGroup() {
		return "parallel-execution";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

}
