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

import com.alibaba.cloud.ai.manus.tool.DocLoaderTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * PDF to Markdown Processor
 *
 * Converts PDF files to Markdown format Uses DocLoaderTool to extract text content and
 * formats it as Markdown
 */
public class PdfToMarkdownProcessor {

	private static final Logger log = LoggerFactory.getLogger(PdfToMarkdownProcessor.class);

	private final UnifiedDirectoryManager directoryManager;

	public PdfToMarkdownProcessor(UnifiedDirectoryManager directoryManager, ApplicationContext applicationContext) {
		this.directoryManager = directoryManager;
	}

	/**
	 * Convert PDF file to Markdown
	 * @param sourceFile The source PDF file
	 * @param additionalRequirement Optional additional requirements for conversion
	 * @param currentPlanId Current plan ID for file operations
	 * @return ToolExecuteResult with conversion status
	 */
	public ToolExecuteResult convertToMarkdown(Path sourceFile, String additionalRequirement, String currentPlanId) {
		try {
			log.info("Converting PDF file to Markdown: {}", sourceFile.getFileName());

			// Step 0: Check if content.md already exists
			String originalFilename = sourceFile.getFileName().toString();
			String markdownFilename = generateMarkdownFilename(originalFilename);
			if (markdownFileExists(currentPlanId, markdownFilename)) {
				log.info("Markdown file already exists, skipping conversion: {}", markdownFilename);
				return new ToolExecuteResult(
						"Skipped conversion - content.md file already exists: " + markdownFilename);
			}

			// Step 1: Read the PDF using DocLoaderTool
			String content = extractPdfContent(sourceFile, currentPlanId);
			if (content == null || content.trim().isEmpty()) {
				return new ToolExecuteResult("Error: Could not extract content from PDF file");
			}

			// Step 2: Convert content to Markdown format
			String markdownContent = convertToMarkdownFormat(content, additionalRequirement);

			// Step 3: Generate output filename (already declared above)
			markdownFilename = generateMarkdownFilename(originalFilename);

			// Step 4: Save Markdown file
			Path outputFile = saveMarkdownFile(markdownContent, markdownFilename, currentPlanId);
			if (outputFile == null) {
				return new ToolExecuteResult("Error: Failed to save Markdown file");
			}

			// Step 5: Return success result
			String result = String.format("Successfully converted PDF file to Markdown\n\n" + "**Output File**: %s\n\n",
					markdownFilename);

			// Add content if less than 1000 characters
			if (markdownContent.length() < 1000) {
				result += "**Content**:\n\n" + markdownContent;
			}

			log.info("PDF to Markdown conversion completed: {} -> {}", originalFilename, markdownFilename);
			return new ToolExecuteResult(result);

		}
		catch (Exception e) {
			log.error("Error converting PDF file to Markdown: {}", sourceFile.getFileName(), e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * Check if markdown file already exists
	 */
	private boolean markdownFileExists(String currentPlanId, String markdownFilename) {
		try {
			Path currentPlanDir = directoryManager.getRootPlanDirectory(currentPlanId);
			Path markdownFile = currentPlanDir.resolve(markdownFilename);
			return Files.exists(markdownFile);
		}
		catch (Exception e) {
			log.error("Error checking if markdown file exists: {}", markdownFilename, e);
			return false;
		}
	}

	/**
	 * Extract content from PDF using DocLoaderTool
	 */
	private String extractPdfContent(Path sourceFile, String currentPlanId) {
		try {
			DocLoaderTool docTool = new DocLoaderTool();
			DocLoaderTool.DocLoaderInput docInput = new DocLoaderTool.DocLoaderInput();
			docInput.setFileType("pdf");
			docInput.setFilePath(sourceFile.toAbsolutePath().toString());

			docTool.setCurrentPlanId(currentPlanId);
			docTool.setRootPlanId(currentPlanId);

			ToolExecuteResult result = docTool.run(docInput);
			if (result != null && result.getOutput() != null) {
				return result.getOutput();
			}

			return null;
		}
		catch (Exception e) {
			log.error("Error extracting content from PDF: {}", sourceFile.getFileName(), e);
			return null;
		}
	}

	/**
	 * Convert extracted content to Markdown format
	 */
	private String convertToMarkdownFormat(String content, String additionalRequirement) {
		StringBuilder markdown = new StringBuilder();

		// Add header
		markdown.append("# PDF Document Conversion\n\n");

		if (additionalRequirement != null && !additionalRequirement.trim().isEmpty()) {
			markdown.append("**Additional Requirements**: ").append(additionalRequirement).append("\n\n");
		}

		markdown.append("---\n\n");

		// Process content line by line
		String[] lines = content.split("\n");
		boolean inList = false;
		boolean inCodeBlock = false;

		for (String line : lines) {
			String trimmedLine = line.trim();

			if (trimmedLine.isEmpty()) {
				markdown.append("\n");
				inList = false;
				continue;
			}

			// Detect code blocks
			if (trimmedLine.startsWith("```") || trimmedLine.startsWith("`")) {
				inCodeBlock = !inCodeBlock;
				markdown.append(line).append("\n");
				continue;
			}

			if (inCodeBlock) {
				markdown.append(line).append("\n");
				continue;
			}

			// Detect headers (lines that look like titles)
			if (isHeader(trimmedLine)) {
				markdown.append("## ").append(trimmedLine).append("\n\n");
				inList = false;
				continue;
			}

			// Detect lists
			if (isListItem(trimmedLine)) {
				if (!inList) {
					markdown.append("\n");
				}
				markdown.append("- ").append(trimmedLine.replaceFirst("^[\\-\\*\\+]\\s*", "")).append("\n");
				inList = true;
				continue;
			}

			// Detect numbered lists
			if (isNumberedListItem(trimmedLine)) {
				if (!inList) {
					markdown.append("\n");
				}
				markdown.append(trimmedLine).append("\n");
				inList = true;
				continue;
			}

			// Regular paragraph
			if (inList) {
				markdown.append("\n");
				inList = false;
			}
			markdown.append(trimmedLine).append("\n\n");
		}

		markdown.append("\n---\n\n");
		markdown.append("*This document was automatically converted from PDF to Markdown format.*\n");

		return markdown.toString();
	}

	/**
	 * Check if line looks like a header
	 */
	private boolean isHeader(String line) {
		return line.length() > 3 && line.length() < 100 && !line.startsWith("-") && !line.startsWith("*")
				&& !line.startsWith("+") && !line.matches("^\\d+\\.") && Character.isUpperCase(line.charAt(0));
	}

	/**
	 * Check if line is a list item
	 */
	private boolean isListItem(String line) {
		return line.matches("^[\\-\\*\\+]\\s+.+");
	}

	/**
	 * Check if line is a numbered list item
	 */
	private boolean isNumberedListItem(String line) {
		return line.matches("^\\d+\\.\\s+.+");
	}

	/**
	 * Generate markdown filename by replacing extension with .md
	 */
	private String generateMarkdownFilename(String originalFilename) {
		int lastDotIndex = originalFilename.lastIndexOf('.');
		if (lastDotIndex > 0) {
			return originalFilename.substring(0, lastDotIndex) + ".md";
		}
		return originalFilename + ".md";
	}

	/**
	 * Save Markdown content to file
	 */
	private Path saveMarkdownFile(String content, String filename, String currentPlanId) {
		try {
			Path currentPlanDir = directoryManager.getRootPlanDirectory(currentPlanId);
			Path outputFile = currentPlanDir.resolve(filename);

			Files.write(outputFile, content.getBytes("UTF-8"), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING);

			log.info("Markdown file saved: {}", outputFile);
			return outputFile;
		}
		catch (IOException e) {
			log.error("Error saving Markdown file: {}", filename, e);
			return null;
		}
	}

}
