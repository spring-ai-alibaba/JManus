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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.innerStorage.SmartContentSavingService;
import com.alibaba.cloud.ai.lynxe.tool.shortUrl.ShortUrlService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Global file operator that performs operations on files. This operator provides access
 * to files that can be accessed across all sub-plans within the same execution context.
 *
 * Keywords: global files, root directory, root folder, root plan directory, global file
 * operations, root file access, cross-plan files.
 *
 * Use this tool for operations on global files, root directory files, or root folder
 * files.
 */
public class GlobalFileOperator extends AbstractBaseTool<GlobalFileOperator.GlobalFileInput> {

	private static final Logger log = LoggerFactory.getLogger(GlobalFileOperator.class);

	private static final String TOOL_NAME = "global_file_operator";

	/**
	 * Set of supported text file extensions
	 */
	private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Set.of(".txt", ".md", ".markdown", // Plain
																												// text
																												// and
																												// Markdown
			".java", ".py", ".js", ".ts", ".jsx", ".tsx", // Common programming languages
			".html", ".htm", ".css", ".scss", ".sass", ".less", // Web-related
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
	 * Input class for global file operations
	 */
	public static class GlobalFileInput {

		private String action;

		@com.fasterxml.jackson.annotation.JsonProperty("file_path")
		private String filePath;

		private String content;

		@com.fasterxml.jackson.annotation.JsonProperty("source_text")
		private String sourceText;

		@com.fasterxml.jackson.annotation.JsonProperty("target_text")
		private String targetText;

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

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public String getSourceText() {
			return sourceText;
		}

		public void setSourceText(String sourceText) {
			this.sourceText = sourceText;
		}

		public String getTargetText() {
			return targetText;
		}

		public void setTargetText(String targetText) {
			this.targetText = targetText;
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

	private final TextFileService textFileService;

	private final SmartContentSavingService innerStorageService;

	private final ObjectMapper objectMapper;

	private final ShortUrlService shortUrlService;

	public GlobalFileOperator(TextFileService textFileService, SmartContentSavingService innerStorageService,
			ObjectMapper objectMapper, ShortUrlService shortUrlService) {
		this.textFileService = textFileService;
		this.innerStorageService = innerStorageService;
		this.objectMapper = objectMapper;
		this.shortUrlService = shortUrlService;
	}

	public ToolExecuteResult run(String toolInput) {
		log.info("GlobalFileOperator toolInput: {}", toolInput);
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
				case "replace" -> {
					String sourceText = (String) toolInputMap.get("source_text");
					String targetText = (String) toolInputMap.get("target_text");

					if (sourceText == null || targetText == null) {
						yield new ToolExecuteResult(
								"Error: replace operation requires source_text and target_text parameters");
					}

					// Replace short URLs in sourceText and targetText
					sourceText = replaceShortUrls(sourceText);
					targetText = replaceShortUrls(targetText);

					yield replaceText(filePath, sourceText, targetText);
				}
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
				case "append" -> {
					String appendContent = (String) toolInputMap.get("content");

					if (appendContent == null) {
						yield new ToolExecuteResult("Error: append operation requires content parameter");
					}

					// Replace short URLs in appendContent
					appendContent = replaceShortUrls(appendContent);

					yield appendToFile(filePath, appendContent);
				}
				case "delete" -> deleteFile(filePath);
				case "count_words" -> countWords(filePath);
				case "list_files" -> listFiles(filePath != null ? filePath : "");
				case "grep" -> {
					String pattern = (String) toolInputMap.get("pattern");
					Boolean caseSensitive = (Boolean) toolInputMap.get("case_sensitive");
					Boolean wholeWord = (Boolean) toolInputMap.get("whole_word");

					if (pattern == null) {
						yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
					}

					// Replace short URLs in pattern
					pattern = replaceShortUrls(pattern);

					yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
							wholeWord != null ? wholeWord : false);
				}
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: replace, get_text, get_all_text, append, delete, count_words, list_files, grep");
			};
		}
		catch (Exception e) {
			log.error("GlobalFileOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	@Override
	public ToolExecuteResult run(GlobalFileInput input) {
		log.info("GlobalFileOperator input: action={}, filePath={}", input.getAction(), input.getFilePath());
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

			// Replace short URLs in filePath
			filePath = replaceShortUrls(filePath);

			return switch (action) {
				case "replace" -> {
					String sourceText = input.getSourceText();
					String targetText = input.getTargetText();

					if (sourceText == null || targetText == null) {
						yield new ToolExecuteResult(
								"Error: replace operation requires source_text and target_text parameters");
					}

					// Replace short URLs in sourceText and targetText
					sourceText = replaceShortUrls(sourceText);
					targetText = replaceShortUrls(targetText);

					yield replaceText(filePath, sourceText, targetText);
				}
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
				case "append" -> {
					String appendContent = input.getContent();

					if (appendContent == null) {
						yield new ToolExecuteResult("Error: append operation requires content parameter");
					}

					// Replace short URLs in appendContent
					appendContent = replaceShortUrls(appendContent);

					yield appendToFile(filePath, appendContent);
				}
				case "delete" -> deleteFile(filePath);
				case "count_words" -> countWords(filePath);
				case "list_files" -> listFiles(filePath != null ? filePath : "");
				case "grep" -> {
					String pattern = input.getPattern();
					Boolean caseSensitive = input.getCaseSensitive();
					Boolean wholeWord = input.getWholeWord();

					if (pattern == null) {
						yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
					}

					// Replace short URLs in pattern
					pattern = replaceShortUrls(pattern);

					yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
							wholeWord != null ? wholeWord : false);
				}
				default -> new ToolExecuteResult("Unknown operation: " + action
						+ ". Supported operations: replace, get_text, get_all_text, append, delete, count_words, list_files, grep");
			};
		}
		catch (Exception e) {
			log.error("GlobalFileOperator execution failed", e);
			return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
		}
	}

	/**
	 * Replace short URLs in a string with real URLs
	 * @param text The text that may contain short URLs
	 * @return The text with short URLs replaced by real URLs
	 */
	private String replaceShortUrls(String text) {
		if (text == null || text.isEmpty() || this.rootPlanId == null || this.rootPlanId.isEmpty()
				|| this.shortUrlService == null) {
			return text;
		}

		// Check if short URL feature is enabled
		Boolean enableShortUrl = textFileService.getLynxeProperties().getEnableShortUrl();
		if (enableShortUrl == null || !enableShortUrl) {
			return text; // Skip replacement if disabled
		}

		// Pattern to match short URLs: http://s@Url.a/ followed by digits
		Pattern shortUrlPattern = Pattern.compile(Pattern.quote(ShortUrlService.SHORT_URL_PREFIX) + "\\d+");
		Matcher matcher = shortUrlPattern.matcher(text);
		StringBuffer result = new StringBuffer();

		while (matcher.find()) {
			String shortUrl = matcher.group();
			String realUrl = shortUrlService.getRealUrl(this.rootPlanId, shortUrl);
			if (realUrl != null) {
				matcher.appendReplacement(result, Matcher.quoteReplacement(realUrl));
				log.debug("Replaced short URL {} with real URL {}", shortUrl, realUrl);
			}
			else {
				log.warn("Short URL not found in mapping: {}", shortUrl);
				// Keep the short URL if mapping not found
				matcher.appendReplacement(result, Matcher.quoteReplacement(shortUrl));
			}
		}
		matcher.appendTail(result);

		return result.toString();
	}

	/**
	 * Normalize file path by removing plan ID and shared directory prefixes
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

		// Remove plan ID prefix (e.g., "plan-1763035234741/")
		if (normalized.matches("^plan-[^/]+/.*")) {
			normalized = normalized.replaceFirst("^plan-[^/]+/", "");
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
	 * Validate and get the absolute path within the shared directory
	 */
	private Path validateGlobalPath(String filePath) throws IOException {
		if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
			throw new IOException("Error: rootPlanId is required for global file operations but is null or empty");
		}

		// Normalize the file path to remove plan ID and shared directory prefixes
		String normalizedPath = normalizeFilePath(filePath);

		// Check file type for non-directory operations
		if (!normalizedPath.isEmpty() && !normalizedPath.endsWith("/") && !isSupportedFileType(normalizedPath)) {
			throw new IOException("Unsupported file type. Only text-based files are supported.");
		}

		// Get the root plan directory and resolve to shared subdirectory
		Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);
		Path sharedDirectory = rootPlanDirectory.resolve("shared");

		// Resolve file path within the shared directory
		Path absolutePath = sharedDirectory.resolve(normalizedPath).normalize();

		// Ensure the path stays within the shared directory
		if (!absolutePath.startsWith(sharedDirectory)) {
			throw new IOException("Access denied: Invalid file path");
		}

		return absolutePath;
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
	 * Delete a file
	 */
	private ToolExecuteResult deleteFile(String filePath) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			if (!Files.exists(absolutePath)) {
				return new ToolExecuteResult("Error: File does not exist: " + filePath);
			}

			Files.delete(absolutePath);

			log.info("Deleted shared file: {}", absolutePath);
			return new ToolExecuteResult("File deleted successfully: " + filePath);
		}
		catch (IOException e) {
			log.error("Error deleting shared file: {}", filePath, e);
			return new ToolExecuteResult("Error deleting file: " + e.getMessage());
		}
	}

	/**
	 * List files in the shared directory only
	 */
	private ToolExecuteResult listFiles(String directoryPath) {
		try {
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				return new ToolExecuteResult("Error: rootPlanId is required for global file operations");
			}

			// Normalize the directory path to remove plan ID and shared directory
			// prefixes
			String normalizedPath = normalizeFilePath(directoryPath != null ? directoryPath : "");

			// Get the shared directory
			Path rootPlanDirectory = textFileService.getRootPlanDirectory(this.rootPlanId);
			Path sharedDirectory = rootPlanDirectory.resolve("shared");

			// If a subdirectory path is provided, resolve it within shared directory
			Path targetDirectory = sharedDirectory;
			if (normalizedPath != null && !normalizedPath.isEmpty()) {
				targetDirectory = sharedDirectory.resolve(normalizedPath).normalize();

				// Ensure the target directory stays within shared directory
				if (!targetDirectory.startsWith(sharedDirectory)) {
					return new ToolExecuteResult("Error: Directory path is invalid");
				}
			}

			// Check if directory exists - don't create it for list operation
			if (!Files.exists(targetDirectory)) {
				return new ToolExecuteResult("Error: Directory does not exist: "
						+ (normalizedPath != null && !normalizedPath.isEmpty() ? normalizedPath : "root"));
			}

			if (!Files.isDirectory(targetDirectory)) {
				return new ToolExecuteResult("Error: Path is not a directory: " + normalizedPath);
			}

			StringBuilder result = new StringBuilder();
			result.append("Files: \n");
			if (normalizedPath != null && !normalizedPath.isEmpty()) {
				result.append(normalizedPath).append("\n");
			}

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

			return new ToolExecuteResult(result.toString());
		}
		catch (IOException e) {
			String pathToLog = normalizeFilePath(directoryPath != null ? directoryPath : "");
			log.error("Error listing shared files: {}", pathToLog, e);
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
	 * Replace text in file
	 */
	private ToolExecuteResult replaceText(String filePath, String sourceText, String targetText) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new shared file automatically: {}", absolutePath);
			}

			String content = Files.readString(absolutePath);
			String newContent = content.replace(sourceText, targetText);
			Files.writeString(absolutePath, newContent);

			// Force flush to disk
			try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
				channel.force(true);
			}

			log.info("Text replaced in shared file: {}", absolutePath);
			return new ToolExecuteResult("Text replaced successfully in file: " + filePath);
		}
		catch (IOException e) {
			log.error("Error replacing text in shared file: {}", filePath, e);
			return new ToolExecuteResult("Error replacing text in file: " + e.getMessage());
		}
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

			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new shared file automatically: {}", absolutePath);
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
			log.error("Error retrieving text lines from shared file: {}", filePath, e);
			return new ToolExecuteResult("Error retrieving text lines from file: " + e.getMessage());
		}
	}

	/**
	 * Get all text from file
	 */
	private ToolExecuteResult getAllText(String filePath) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new shared file automatically: {}", absolutePath);
			}

			String content = Files.readString(absolutePath);

			// Force flush to disk to ensure data consistency
			try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.READ)) {
				channel.force(true);
			}

			// Use InnerStorageService to intelligently process content
			SmartContentSavingService.SmartProcessResult processedResult = innerStorageService
				.processContent(this.rootPlanId, content, "get_all_text_shared");

			return new ToolExecuteResult(processedResult.getSummary());
		}
		catch (IOException e) {
			log.error("Error retrieving all text from shared file: {}", filePath, e);
			return new ToolExecuteResult("Error retrieving all text from file: " + e.getMessage());
		}
	}

	/**
	 * Append content to file
	 */
	private ToolExecuteResult appendToFile(String filePath, String content) {
		try {
			if (content == null || content.isEmpty()) {
				return new ToolExecuteResult("Error: No content to append");
			}

			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
			}

			Files.writeString(absolutePath, "\n" + content, StandardOpenOption.APPEND, StandardOpenOption.CREATE);

			// Force flush to disk
			try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
				channel.force(true);
			}

			log.info("Content appended to shared file: {}", absolutePath);
			return new ToolExecuteResult("Content appended successfully to file: " + filePath);
		}
		catch (IOException e) {
			log.error("Error appending to shared file: {}", filePath, e);
			return new ToolExecuteResult("Error appending to file: " + e.getMessage());
		}
	}

	/**
	 * Count words in file
	 */
	private ToolExecuteResult countWords(String filePath) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new shared file automatically: {}", absolutePath);
			}

			String content = Files.readString(absolutePath);
			int wordCount = content.isEmpty() ? 0 : content.split("\\s+").length;

			return new ToolExecuteResult(String.format("Total word count in file: %d", wordCount));
		}
		catch (IOException e) {
			log.error("Error counting words in shared file: {}", filePath, e);
			return new ToolExecuteResult("Error counting words in file: " + e.getMessage());
		}
	}

	/**
	 * Search for text patterns in file (grep functionality)
	 */
	private ToolExecuteResult grepText(String filePath, String pattern, boolean caseSensitive, boolean wholeWord) {
		try {
			Path absolutePath = validateGlobalPath(filePath);

			// Create file if it doesn't exist
			if (!Files.exists(absolutePath)) {
				Files.createDirectories(absolutePath.getParent());
				Files.createFile(absolutePath);
				log.info("Created new shared file automatically: {}", absolutePath);
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
			log.error("Error performing grep search in shared file: {}", filePath, e);
			return new ToolExecuteResult("Error performing grep search in file: " + e.getMessage());
		}
	}

	@Override
	public String getCurrentToolStateString() {
		return "";
	}

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return """
				Perform various operations on text files. This operator provides access to files
				that can be accessed across all sub-plans within the same execution context.

				Keywords: global files, root directory, root folder, root plan directory,
				global file operations, root file access, cross-plan files.


				Supported operations:
				- delete: Delete an existing file, requires file_path parameter
				- list_files: List files and directories, optional file_path parameter (defaults to root)
				- replace: Replace specific text in file, requires source_text and target_text parameters
				- get_text: Get content from specified line range in file, requires start_line and end_line parameters
				  Limitation: Maximum 500 lines per call, use multiple calls for more content
				- get_all_text: Get all content from file
				  Note: If file content is too long, it will be automatically stored in temporary file and return file path
				- append: Append content to file, requires content parameter
				- count_words: Count words in file
				- grep: Search for text patterns in file, similar to Linux grep command
				  Parameters: pattern (required), case_sensitive (optional, default false), whole_word (optional, default false)

				Features:
				- Files created here are accessible by all sub-plans within the execution context

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
				                    "const": "delete"
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
				                    "const": "replace"
				                },
				                "file_path": {
				                    "type": "string",
				                    "description": "Relative file path (filename or relative path, e.g., 'file.txt' or 'subdir/file.txt')"
				                },
				                "source_text": {
				                    "type": "string",
				                    "description": "Text to be replaced"
				                },
				                "target_text": {
				                    "type": "string",
				                    "description": "Replacement text"
				                }
				            },
				            "required": ["action", "file_path", "source_text", "target_text"],
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
				                    "const": "append"
				                },
				                "file_path": {
				                    "type": "string",
				                    "description": "Relative file path (filename or relative path, e.g., 'file.txt' or 'subdir/file.txt')"
				                },
				                "content": {
				                    "type": "string",
				                    "description": "Content to append to the file"
				                }
				            },
				            "required": ["action", "file_path", "content"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": {
				                    "type": "string",
				                    "const": "count_words"
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
	public Class<GlobalFileInput> getInputType() {
		return GlobalFileInput.class;
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up shared file resources for plan: {}", planId);
			// Shared cleanup if needed - the TextFileService handles the main cleanup
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
