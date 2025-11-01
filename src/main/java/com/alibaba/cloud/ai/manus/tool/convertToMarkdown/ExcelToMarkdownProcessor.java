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

import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.ExcelProcessorTool;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.IExcelProcessingService;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Excel to Markdown Processor
 *
 * Converts Excel files (.xlsx, .xls) to Markdown format Uses ExcelProcessorTool to
 * extract data and formats it as Markdown tables
 */
public class ExcelToMarkdownProcessor {

	private static final Logger log = LoggerFactory.getLogger(ExcelToMarkdownProcessor.class);

	private final UnifiedDirectoryManager directoryManager;

	@Autowired
	private IExcelProcessingService excelProcessingService;

	public ExcelToMarkdownProcessor(UnifiedDirectoryManager directoryManager) {
		this.directoryManager = directoryManager;
	}

	/**
	 * Convert Excel file to Markdown
	 * @param sourceFile The source Excel file
	 * @param additionalRequirement Optional additional requirements for conversion
	 * @param currentPlanId Current plan ID for file operations
	 * @return ToolExecuteResult with conversion status
	 */
	public ToolExecuteResult convertToMarkdown(Path sourceFile, String additionalRequirement, String currentPlanId) {
		try {
			log.info("Converting Excel file to Markdown: {}", sourceFile.getFileName());

			// Step 0: Check if content.md already exists
			String originalFilename = sourceFile.getFileName().toString();
			String markdownFilename = generateMarkdownFilename(originalFilename);
			if (markdownFileExists(currentPlanId, markdownFilename)) {
				log.info("Markdown file already exists, skipping conversion: {}", markdownFilename);
				return new ToolExecuteResult(
						"Skipped conversion - content.md file already exists: " + markdownFilename);
			}

			// Step 1: Get Excel structure and data
			String structureInfo = getExcelStructure(sourceFile, currentPlanId);
			String dataContent = getExcelData(sourceFile, currentPlanId);

			if (structureInfo == null && dataContent == null) {
				return new ToolExecuteResult("Error: Could not extract content from Excel file");
			}

			// Step 2: Convert to Markdown format
			String markdownContent = convertToMarkdownFormat(structureInfo, dataContent, additionalRequirement);

			// Step 3: Generate output filename (already declared above)
			markdownFilename = generateMarkdownFilename(originalFilename);

			// Step 4: Save Markdown file
			Path outputFile = saveMarkdownFile(markdownContent, markdownFilename, currentPlanId);
			if (outputFile == null) {
				return new ToolExecuteResult("Error: Failed to save Markdown file");
			}

			// Step 5: Return success result
			String result = String.format("""
					Successfully converted Excel file to Markdown

					**Output File**: %s

					""", markdownFilename);

			// Add content if less than 1000 characters
			if (markdownContent.length() < 1000) {
				result += "**Content**:\n\n" + markdownContent;
			}

			log.info("Excel to Markdown conversion completed: {} -> {}", originalFilename, markdownFilename);
			return new ToolExecuteResult(result);

		}
		catch (Exception e) {
			log.error("Error converting Excel file to Markdown: {}", sourceFile.getFileName(), e);
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
	 * Get Excel file structure information
	 */
	private String getExcelStructure(Path sourceFile, String currentPlanId) {
		try {
			IExcelProcessingService excelService = excelProcessingService;
			ExcelProcessorTool excelTool = new ExcelProcessorTool(excelService);
			ExcelProcessorTool.ExcelInput input = new ExcelProcessorTool.ExcelInput();
			input.setAction("get_structure");
			input.setFilePath(sourceFile.toString());

			excelTool.setCurrentPlanId(currentPlanId);
			excelTool.setRootPlanId(currentPlanId);

			ToolExecuteResult result = excelTool.run(input);
			return result != null ? result.getOutput() : null;
		}
		catch (Exception e) {
			log.warn("Could not get Excel structure: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Get Excel file data content
	 */
	private String getExcelData(Path sourceFile, String currentPlanId) {
		try {
			IExcelProcessingService excelService = excelProcessingService;
			ExcelProcessorTool excelTool = new ExcelProcessorTool(excelService);
			ExcelProcessorTool.ExcelInput input = new ExcelProcessorTool.ExcelInput();
			input.setAction("read_data");
			input.setFilePath(sourceFile.toString());
			input.setWorksheetName("Sheet1"); // Try default worksheet first

			excelTool.setCurrentPlanId(currentPlanId);
			excelTool.setRootPlanId(currentPlanId);

			ToolExecuteResult result = excelTool.run(input);
			return result != null ? result.getOutput() : null;
		}
		catch (Exception e) {
			log.warn("Could not get Excel data: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Convert Excel content to Markdown format
	 */
	private String convertToMarkdownFormat(String structureInfo, String dataContent, String additionalRequirement) {
		StringBuilder markdown = new StringBuilder();

		// Add header
		markdown.append("# Excel Data Conversion\n\n");

		if (additionalRequirement != null && !additionalRequirement.trim().isEmpty()) {
			markdown.append("**Additional Requirements**: ").append(additionalRequirement).append("\n\n");
		}

		// Add structure information if available
		if (structureInfo != null && !structureInfo.trim().isEmpty()) {
			markdown.append("## File Structure\n\n");
			markdown.append(convertStructureToMarkdown(structureInfo));
			markdown.append("\n");
		}

		// Add data content if available
		if (dataContent != null && !dataContent.trim().isEmpty()) {
			markdown.append("## Data Content\n\n");
			markdown.append(convertDataToMarkdown(dataContent));
			markdown.append("\n");
		}

		// Add footer
		markdown.append("---\n\n");
		markdown.append("*This document was automatically converted from Excel to Markdown format.*\n");

		return markdown.toString();
	}

	/**
	 * Convert structure information to Markdown
	 */
	private String convertStructureToMarkdown(String structureInfo) {
		StringBuilder markdown = new StringBuilder();
		String[] lines = structureInfo.split("\n");

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				markdown.append("\n");
				continue;
			}

			// Convert structure info to readable format
			if (trimmedLine.contains("worksheets") || trimmedLine.contains("sheets")) {
				markdown.append("### ").append(trimmedLine).append("\n\n");
			}
			else if (trimmedLine.contains("rows") || trimmedLine.contains("columns")) {
				markdown.append("- ").append(trimmedLine).append("\n");
			}
			else {
				markdown.append(trimmedLine).append("\n");
			}
		}

		return markdown.toString();
	}

	/**
	 * Convert data content to Markdown tables
	 */
	private String convertDataToMarkdown(String dataContent) {
		StringBuilder markdown = new StringBuilder();
		String[] lines = dataContent.split("\n");
		boolean inTable = false;
		int tableRowCount = 0;

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				if (inTable) {
					markdown.append("\n");
					inTable = false;
					tableRowCount = 0;
				}
				continue;
			}

			// Check if this looks like table data
			if (isTableRow(trimmedLine)) {
				if (!inTable) {
					markdown.append("### Data Table\n\n");
					inTable = true;
					tableRowCount = 0;
				}

				// Convert to Markdown table format
				String[] cells = splitTableRow(trimmedLine);
				if (cells.length > 0) {
					if (tableRowCount == 0) {
						// Header row
						markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
						markdown.append("| ").append("--- |".repeat(cells.length)).append("\n");
					}
					else {
						// Data row
						markdown.append("| ").append(String.join(" | ", cells)).append(" |\n");
					}
					tableRowCount++;
				}
			}
			else {
				if (inTable) {
					markdown.append("\n");
					inTable = false;
					tableRowCount = 0;
				}
				markdown.append(trimmedLine).append("\n");
			}
		}

		if (inTable) {
			markdown.append("\n");
		}

		return markdown.toString();
	}

	/**
	 * Check if line looks like a table row
	 */
	private boolean isTableRow(String line) {
		// Simple heuristic: contains multiple separators (tabs, commas, or multiple
		// spaces)
		return line.contains("\t") || (line.contains(",") && line.split(",").length > 2)
				|| (line.contains("  ") && line.split("  ").length > 2);
	}

	/**
	 * Split table row into cells
	 */
	private String[] splitTableRow(String line) {
		// Try different separators
		if (line.contains("\t")) {
			return line.split("\t");
		}
		else if (line.contains(",")) {
			return line.split(",");
		}
		else if (line.contains("  ")) {
			return line.split("\\s{2,}"); // Split on 2 or more spaces
		}
		else {
			return new String[] { line };
		}
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

			Files.writeString(outputFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

			log.info("Markdown file saved: {}", outputFile);
			return outputFile;
		}
		catch (IOException e) {
			log.error("Error saving Markdown file: {}", filename, e);
			return null;
		}
	}

}
