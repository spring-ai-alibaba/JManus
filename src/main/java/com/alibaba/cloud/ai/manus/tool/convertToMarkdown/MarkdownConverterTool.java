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
package com.alibaba.cloud.ai.manus.tool.convertToMarkdown;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Markdown Converter Tool - Converts various file types to Markdown format
 *
 * Features: - Supports Word documents (.doc, .docx) - Supports Excel files (.xlsx, .xls)
 * - Converts content to clean Markdown format - Saves converted files with .md extension
 * - Handles additional requirements for conversion
 */
public class MarkdownConverterTool extends AbstractBaseTool<MarkdownConverterTool.MarkdownConverterInput> {

	private static final Logger log = LoggerFactory.getLogger(MarkdownConverterTool.class);

	private static final String TOOL_NAME = "markdown_converter";

	private final UnifiedDirectoryManager directoryManager;

	private final PdfOcrProcessor ocrProcessor;

	private final ImageOcrProcessor imageOcrProcessor;

	public MarkdownConverterTool(UnifiedDirectoryManager directoryManager, PdfOcrProcessor ocrProcessor,
			ImageOcrProcessor imageOcrProcessor) {
		this.directoryManager = directoryManager;
		this.ocrProcessor = ocrProcessor;
		this.imageOcrProcessor = imageOcrProcessor;
	}

	/**
	 * Input class for markdown conversion operations
	 */
	public static class MarkdownConverterInput {

		@JsonProperty("filename")
		private String filename;

		@JsonProperty("additionalRequirement")
		private String additionalRequirement;

		// Getters and setters
		public String getFilename() {
			return filename;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public String getAdditionalRequirement() {
			return additionalRequirement;
		}

		public void setAdditionalRequirement(String additionalRequirement) {
			this.additionalRequirement = additionalRequirement;
		}

	}

	@Override
	public ToolExecuteResult run(MarkdownConverterInput input) {
		String filename = input.getFilename();
		String additionalRequirement = input.getAdditionalRequirement();

		log.info("MarkdownConverterTool processing file: {} with additional requirement: {}", filename,
				additionalRequirement);

		try {
			// Step 1: Validate input
			if (filename == null || filename.trim().isEmpty()) {
				return new ToolExecuteResult("Error: filename is required");
			}

			// Step 2: Get file extension
			String extension = getFileExtension(filename);
			if (extension.isEmpty()) {
				return new ToolExecuteResult("Error: File has no extension: " + filename);
			}

			// Step 3: Find the file in root plan shared directory
			Path sourceFile = findFileInRootPlan(filename);
			if (sourceFile == null || !Files.exists(sourceFile)) {
				return new ToolExecuteResult("Error: File not found: " + filename
						+ ". Please ensure the file exists in the root plan shared directory (rootPlanId/shared/).");
			}

			// Step 4: Dispatch to appropriate processor
			String ext = extension.toLowerCase().substring(1);
			return switch (ext) {
				case "doc", "docx" -> processWordToMarkdown(sourceFile, additionalRequirement);
				case "xlsx", "xls" -> processExcelToMarkdown(sourceFile, additionalRequirement);
				case "pdf" -> processPdfToMarkdown(sourceFile, additionalRequirement);
				case "jpg", "jpeg", "png", "gif" -> processImageToMarkdown(sourceFile, additionalRequirement);
				case "txt", "md", "json", "xml", "yaml", "yml", "log", "java", "py", "js", "html", "css" ->
					processTextToMarkdown(sourceFile, additionalRequirement);
				default -> new ToolExecuteResult("Error: Unsupported file type: " + extension
						+ ". Supported types: .doc, .docx, .xlsx, .xls, .pdf, .jpg, .jpeg, .png, .gif, .txt, .md, .json, .xml, .yaml, .yml, .log, .java, .py, .js, .html, .css");
			};

		}
		catch (Exception e) {
			log.error("Error converting file to markdown: {}", filename, e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * Process Word documents to Markdown
	 */
	private ToolExecuteResult processWordToMarkdown(Path sourceFile, String additionalRequirement) {
		try {
			WordToMarkdownProcessor processor = new WordToMarkdownProcessor(directoryManager);
			return processor.convertToMarkdown(sourceFile, additionalRequirement, rootPlanId);
		}
		catch (Exception e) {
			log.error("Word to Markdown conversion failed: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Word conversion error: " + e.getMessage());
		}
	}

	/**
	 * Process Excel files to Markdown
	 */
	private ToolExecuteResult processExcelToMarkdown(Path sourceFile, String additionalRequirement) {
		try {
			ExcelToMarkdownProcessor processor = new ExcelToMarkdownProcessor(directoryManager);
			return processor.convertToMarkdown(sourceFile, additionalRequirement, rootPlanId);
		}
		catch (Exception e) {
			log.error("Excel to Markdown conversion failed: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Excel conversion error: " + e.getMessage());
		}
	}

	/**
	 * Process PDF files to Markdown
	 */
	private ToolExecuteResult processPdfToMarkdown(Path sourceFile, String additionalRequirement) {
		try {
			PdfToMarkdownProcessor processor = new PdfToMarkdownProcessor(directoryManager, ocrProcessor);
			return processor.convertToMarkdown(sourceFile, additionalRequirement, rootPlanId);
		}
		catch (Exception e) {
			log.error("PDF to Markdown conversion failed: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("PDF conversion error: " + e.getMessage());
		}
	}

	/**
	 * Process image files to Markdown using OCR
	 */
	private ToolExecuteResult processImageToMarkdown(Path sourceFile, String additionalRequirement) {
		try {
			if (imageOcrProcessor == null) {
				return new ToolExecuteResult("Error: Image OCR processor is not available");
			}

			// Generate markdown filename
			String markdownFilename = generateMarkdownFilename(sourceFile.getFileName().toString());
			return imageOcrProcessor.convertImageToTextWithOcr(sourceFile, additionalRequirement, rootPlanId,
					markdownFilename);
		}
		catch (Exception e) {
			log.error("Image to Markdown conversion failed: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Image conversion error: " + e.getMessage());
		}
	}

	/**
	 * Process text files to Markdown
	 */
	private ToolExecuteResult processTextToMarkdown(Path sourceFile, String additionalRequirement) {
		try {
			TextToMarkdownProcessor processor = new TextToMarkdownProcessor(directoryManager);
			return processor.convertToMarkdown(sourceFile, additionalRequirement, rootPlanId);
		}
		catch (Exception e) {
			log.error("Text to Markdown conversion failed: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Text conversion error: " + e.getMessage());
		}
	}

	/**
	 * Find file in root plan shared directory (same as GlobalFileOperator)
	 */
	private Path findFileInRootPlan(String filename) {
		try {
			if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
				log.error("rootPlanId is required for file operations but is null or empty");
				return null;
			}

			// Get the root plan directory and resolve to shared subdirectory (same as GlobalFileOperator)
			Path rootPlanDirectory = directoryManager.getRootPlanDirectory(rootPlanId);
			Path sharedDirectory = rootPlanDirectory.resolve("shared");

			// Resolve file path within the shared directory
			Path filePath = sharedDirectory.resolve(filename).normalize();

			// Ensure the path stays within the shared directory
			if (!filePath.startsWith(sharedDirectory)) {
				log.warn("File path is outside shared directory: {}", filename);
				return null;
			}

			if (Files.exists(filePath)) {
				log.info("Found file in root plan shared directory: {}", filename);
				return filePath;
			}

			log.warn("File not found in root plan shared directory: {}", filename);
			return null;
		}
		catch (Exception e) {
			log.error("Error finding file: {}", filename, e);
			return null;
		}
	}

	/**
	 * Get file extension including the dot
	 */
	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
	}

	/**
	 * Generate markdown filename by replacing extension with .md
	 */
	protected String generateMarkdownFilename(String originalFilename) {
		int lastDotIndex = originalFilename.lastIndexOf('.');
		if (lastDotIndex > 0) {
			return originalFilename.substring(0, lastDotIndex) + ".md";
		}
		return originalFilename + ".md";
	}

	// ===== Tool Interface Implementation =====

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return "Converts various file types to Markdown format with intelligent processing. "
				+ "**Core Strategy**: For Word documents (.doc, .docx), Excel files (.xlsx, .xls), and PDF files, "
				+ "strictly follows a Markdown-first approach - first converts to Markdown format, "
				+ "then processes the content for optimal readability and structure. "
				+ "Supports image files (.jpg, .jpeg, .png, .gif) using OCR processing to extract text content. "
				+ "Supports text files (.txt, .md, .json, .xml, .yaml, .yml, .log, .java, .py, .js, .html, .css) "
				+ "with type-specific formatting. "
				+ "The converted file will be saved with .md extension in the root plan shared directory (rootPlanId/shared/). "
				+ "Additional requirements can be specified for custom conversion needs. "
				+ "**Best Practice**: Always convert complex documents to Markdown first for better content analysis and processing.";
	}

	@Override
	public String getParameters() {
		return "{\"type\":\"object\"," + "\"properties\":{" + "\"filename\":{\"type\":\"string\","
				+ "\"description\":\"Name of the file to convert to Markdown (must exist in root plan shared directory, rootPlanId/shared/)\"},"
				+ "\"additionalRequirement\":{\"type\":\"string\","
				+ "\"description\":\"Optional additional requirements for conversion (e.g., specific formatting, structure)\"}"
				+ "}," + "\"required\":[\"filename\"]}";
	}

	@Override
	public Class<MarkdownConverterInput> getInputType() {
		return MarkdownConverterInput.class;
	}

	@Override
	public void cleanup(String planId) {
		log.info("MarkdownConverterTool cleanup for planId: {}", planId);
	}

	@Override
	public String getCurrentToolStateString() {
		return String.format(
				"MarkdownConverterTool State:\n- Root Plan ID: %s\n- Supported File Types: .doc, .docx, .xlsx, .xls, .pdf, .jpg, .jpeg, .png, .gif, .txt, .md, .json, .xml, .yaml, .yml, .log, .java, .py, .js, .html, .css",
				rootPlanId);
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
