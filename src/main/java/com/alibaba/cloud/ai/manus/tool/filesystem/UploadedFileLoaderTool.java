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
package com.alibaba.cloud.ai.manus.tool.filesystem;

import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.DocLoaderTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.ExcelProcessorTool;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.IExcelProcessingService;
import com.alibaba.cloud.ai.manus.tool.innerStorage.SmartContentSavingService;
import com.alibaba.cloud.ai.manus.tool.tableProcessor.TableProcessingService;
import com.alibaba.cloud.ai.manus.tool.tableProcessor.TableProcessorTool;
import com.alibaba.cloud.ai.manus.tool.textOperator.TextFileOperator;
import com.alibaba.cloud.ai.manus.tool.textOperator.TextFileService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent File Processing Tool - jmanus style: Simple, Direct, Smart
 *
 * True jmanus principles: 1. Tool is simple class with clear purpose 2. Tools create
 * other tools on demand (following existing pattern) 3. Smart function-based tool
 * selection (the real "function as first-class citizen") 4. Minimal dependencies, maximum
 * effectiveness
 */
public class UploadedFileLoaderTool extends AbstractBaseTool<UploadedFileLoaderTool.UploadedFileInput> {

	private static final Logger log = LoggerFactory.getLogger(UploadedFileLoaderTool.class);

	private static final String TOOL_NAME = "uploaded_file_loader";

	private static final String UPLOADED_FILES_DIR = "uploaded_files";

	// Simple jmanus dependencies
	private final UnifiedDirectoryManager directoryManager;

	private final ApplicationContext applicationContext;

	public UploadedFileLoaderTool(UnifiedDirectoryManager directoryManager, ApplicationContext applicationContext) {
		this.directoryManager = directoryManager;
		this.applicationContext = applicationContext;
	}

	/**
	 * Input class for uploaded file operations
	 */
	public static class UploadedFileInput {

		@JsonProperty("action")
		private String action;

		@JsonProperty("file_name")
		private String fileName;

		// Getters and setters
		public String getAction() {
			return action;
		}

		public void setAction(String action) {
			this.action = action;
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

	}

	@Override
	public ToolExecuteResult run(UploadedFileInput input) {
		String action = input.getAction();
		log.info("UploadedFileLoaderTool action: {}", action);

		try {
			return switch (action.toLowerCase()) {
				case "list_files" -> listUploadedFiles();
				case "process_file" -> processFile(input);
				default -> new ToolExecuteResult(
						"Unsupported action: " + action + ". Supported actions: list_files, process_file");
			};
		}
		catch (Exception e) {
			log.error("Error executing UploadedFileLoaderTool action: {}", action, e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * List uploaded files - jmanus style: simple and direct
	 */
	private ToolExecuteResult listUploadedFiles() throws IOException {
		List<String> fileList = new ArrayList<>();

		// 1. Prioritize checking current plan files
		Path currentUploadDir = directoryManager.getRootPlanDirectory(currentPlanId).resolve(UPLOADED_FILES_DIR);
		if (Files.exists(currentUploadDir)) {
			addFilesFromDirectory(currentUploadDir, fileList, "Current Plan");
		}

		// 2. If current plan has files, prioritize using current plan files
		if (!fileList.isEmpty()) {
			log.info("Using files from current plan only: {}", currentPlanId);
		}
		else {
			log.info("No files in current plan, recommending upload to current plan...");
			return new ToolExecuteResult("""
					❌ No uploaded files found in current plan

					🎯 **Suggested Solution**:
					1. Please upload files to be analyzed in the current plan
					2. This avoids file ownership confusion
					3. Ensures analysis of correct and latest files

					💡 **Why do this**:
					- Files and analysis in the same plan, clearer logic
					- Avoid processing irrelevant historical files
					- Improve system performance and accuracy

					📁 Please upload files and then re-execute the analysis task.
					""");
		}

		StringBuilder result = new StringBuilder("📁 Available uploaded files:\n\n");
		for (int i = 0; i < fileList.size(); i++) {
			result.append(String.format("%d. %s\n", i + 1, fileList.get(i)));
		}

		// Extract pure file name list for convenient agent use
		result.append("\n📋 File names for processing:\n");
		for (String fileInfo : fileList) {
			String fileName = extractFileName(fileInfo);
			result.append("   - " + fileName + "\n");
		}

		result.append("\n💡 **Next Step Guide**:\n");
		result.append("   - To process file: {\"action\": \"process_file\", \"file_name\": \"exact_file_name\"}\n");
		result.append("   - ⚠️ **Important**: Don't call list_files repeatedly, all available files are shown\n");
		result
			.append("   - 📋 Suggestion: After processing needed files, use terminate tool to complete task summary\n");
		result.append("\n📋 Supported file types: All common file types are automatically detected and processed");

		log.info("Listed {} files for planId: {}", fileList.size(), currentPlanId);
		return new ToolExecuteResult(result.toString());
	}

	/**
	 * Process specified file - Smart tool selection (jmanus style)
	 */
	private ToolExecuteResult processFile(UploadedFileInput input) {
		String fileName = input.getFileName();
		if (fileName == null || fileName.trim().isEmpty()) {
			return new ToolExecuteResult("❌ Error: file_name is required for process_file action");
		}

		// 1. Find file and location information
		FileLocation fileLocation = findUploadedFileLocation(fileName);
		if (fileLocation == null || !Files.exists(fileLocation.filePath)) {
			return new ToolExecuteResult(
					"❌ File not found: " + fileName + ". Use 'list_files' action to see available files.");
		}

		// 2. Smart tool selection (jmanus approach: function-based intelligence)
		String extension = getFileExtension(fileName);
		if (extension.isEmpty()) {
			return new ToolExecuteResult("❌ File has no extension: " + fileName + ". Cannot determine file type.");
		}

		// Log processing information
		log.info("Processing file: {} (type: {}) from plan: {}", fileName, extension, fileLocation.planId);

		// 3. Smart tool selection based on file type (the real "function as first-class
		// citizen")
		return selectAndExecuteTool(fileLocation, extension);
	}

	/**
	 * Smart tool selection - Function as first-class citizen concept Each file type maps
	 * to a processing function
	 */
	private ToolExecuteResult selectAndExecuteTool(FileLocation fileLocation, String extension) {
		String ext = extension.toLowerCase().substring(1);

		// Function-based dispatch - each case represents a "processing function"
		return switch (ext) {
			case "pdf" -> processPdf(fileLocation);
			case "xlsx", "xls" -> processExcel(fileLocation, ext);
			case "csv" -> processCsv(fileLocation);
			case "txt", "md", "json", "xml", "yaml", "yml", "log", "java", "py", "js", "html", "css" ->
				processText(fileLocation);
			case "tsv" -> processTable(fileLocation);
			default -> processFallback(fileLocation);
		};
	}

	// ===== Processing Functions (jmanus style: simple tool creation) =====

	/**
	 * Process PDF files
	 */
	private ToolExecuteResult processPdf(FileLocation fileLocation) {
		try {
			DocLoaderTool docTool = new DocLoaderTool();
			DocLoaderTool.DocLoaderInput docInput = new DocLoaderTool.DocLoaderInput();
			docInput.setFileType("pdf");
			docInput.setFilePath(fileLocation.filePath.toAbsolutePath().toString());

			docTool.setCurrentPlanId(fileLocation.planId);
			docTool.setRootPlanId(fileLocation.planId);

			log.info("✅ Processing with DocLoaderTool: {}", fileLocation.filePath.getFileName());
			return docTool.run(docInput);
		}
		catch (Exception e) {
			log.warn("DocLoaderTool failed: {}", e.getMessage());
			return new ToolExecuteResult("❌ PDF processing error: " + e.getMessage());
		}
	}

	/**
	 * Process Excel files with enhanced structure + data reading
	 */
	private ToolExecuteResult processExcel(FileLocation fileLocation, String ext) {
		try {
			IExcelProcessingService excelService = applicationContext.getBean(IExcelProcessingService.class);

			// Step 1: Get Excel structure
			ExcelProcessorTool structureTool = new ExcelProcessorTool(excelService);
			ExcelProcessorTool.ExcelInput structureInput = new ExcelProcessorTool.ExcelInput();
			structureInput.setAction("get_structure");
			structureInput.setFilePath(fileLocation.filePath.toString());

			structureTool.setCurrentPlanId(fileLocation.planId);
			structureTool.setRootPlanId(fileLocation.planId);

			ToolExecuteResult structureResult = structureTool.run(structureInput);

			// Step 2: Try to read data from default worksheet
			if (structureResult != null && structureResult.getOutput().contains("worksheets")) {
				try {
					ExcelProcessorTool dataTool = new ExcelProcessorTool(excelService);
					ExcelProcessorTool.ExcelInput dataInput = new ExcelProcessorTool.ExcelInput();
					dataInput.setAction("read_data");
					dataInput.setFilePath(fileLocation.filePath.toString());
					dataInput.setWorksheetName("Sheet1");

					dataTool.setCurrentPlanId(fileLocation.planId);
					dataTool.setRootPlanId(fileLocation.planId);

					ToolExecuteResult dataResult = dataTool.run(dataInput);

					// Combine structure and data
					if (dataResult != null && !dataResult.getOutput().contains("Error")) {
						return new ToolExecuteResult(
								structureResult.getOutput() + "\n\n📊 Data from Sheet1:\n" + dataResult.getOutput());
					}
				}
				catch (Exception dataEx) {
					log.debug("Could not read default worksheet, returning structure only: {}", dataEx.getMessage());
				}
			}

			log.info("✅ Processing with ExcelProcessorTool: {}", fileLocation.filePath.getFileName());
			return structureResult;
		}
		catch (Exception e) {
			log.warn("ExcelProcessorTool failed: {}", e.getMessage());
			return new ToolExecuteResult("❌ Excel processing error: " + e.getMessage());
		}
	}

	/**
	 * Process CSV files
	 */
	private ToolExecuteResult processCsv(FileLocation fileLocation) {
		try {
			IExcelProcessingService excelService = applicationContext.getBean(IExcelProcessingService.class);

			ExcelProcessorTool excelTool = new ExcelProcessorTool(excelService);
			ExcelProcessorTool.ExcelInput excelInput = new ExcelProcessorTool.ExcelInput();
			excelInput.setAction("read_csv");
			excelInput.setFilePath(fileLocation.filePath.toString());

			excelTool.setCurrentPlanId(fileLocation.planId);
			excelTool.setRootPlanId(fileLocation.planId);

			log.info("✅ Processing with ExcelProcessorTool (CSV): {}", fileLocation.filePath.getFileName());
			return excelTool.run(excelInput);
		}
		catch (Exception e) {
			log.warn("CSV processing failed: {}", e.getMessage());
			return new ToolExecuteResult("❌ CSV processing error: " + e.getMessage());
		}
	}

	/**
	 * Process text files
	 */
	private ToolExecuteResult processText(FileLocation fileLocation) {
		try {
			TextFileService textService = applicationContext.getBean(TextFileService.class);
			SmartContentSavingService storageService = applicationContext.getBean(SmartContentSavingService.class);
			ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);

			TextFileOperator textOperator = new TextFileOperator(textService, storageService, objectMapper);
			TextFileOperator.TextFileInput textInput = new TextFileOperator.TextFileInput();
			textInput.setAction("read");
			textInput.setFilePath(fileLocation.filePath.toString());

			textOperator.setCurrentPlanId(fileLocation.planId);
			textOperator.setRootPlanId(fileLocation.planId);

			log.info("✅ Processing with TextFileOperator: {}", fileLocation.filePath.getFileName());
			return textOperator.run(textInput);
		}
		catch (Exception e) {
			log.warn("Text processing failed: {}", e.getMessage());
			return new ToolExecuteResult("❌ Text processing error: " + e.getMessage());
		}
	}

	/**
	 * Process table files
	 */
	private ToolExecuteResult processTable(FileLocation fileLocation) {
		try {
			TableProcessingService tableService = applicationContext.getBean(TableProcessingService.class);

			TableProcessorTool tableTool = new TableProcessorTool(tableService);
			TableProcessorTool.TableInput tableInput = new TableProcessorTool.TableInput();
			tableInput.setAction("read_table");
			tableInput.setFilePath(fileLocation.filePath.toString());

			tableTool.setCurrentPlanId(fileLocation.planId);
			tableTool.setRootPlanId(fileLocation.planId);

			log.info("✅ Processing with TableProcessorTool: {}", fileLocation.filePath.getFileName());
			return tableTool.run(tableInput);
		}
		catch (Exception e) {
			log.warn("Table processing failed: {}", e.getMessage());
			return new ToolExecuteResult("❌ Table processing error: " + e.getMessage());
		}
	}

	/**
	 * Fallback processing: simple text reading
	 */
	private ToolExecuteResult processFallback(FileLocation fileLocation) {
		try {
			String content = Files.readString(fileLocation.filePath);
			log.info("✅ Fallback text reading: {}", fileLocation.filePath.getFileName());
			return new ToolExecuteResult("File content (from plan " + fileLocation.planId + "):\n" + content);
		}
		catch (Exception e) {
			log.error("All processing methods failed for file: {}", fileLocation.filePath.getFileName());
			return new ToolExecuteResult("❌ Unable to process file: " + e.getMessage());
		}
	}

	// ===== Helper Methods (jmanus style: simple utilities) =====

	private void addFilesFromDirectory(Path uploadDir, List<String> fileList, String source) throws IOException {
		Files.list(uploadDir).filter(Files::isRegularFile).forEach(filePath -> {
			try {
				String fileName = filePath.getFileName().toString();
				long size = Files.size(filePath);
				String sizeStr = formatFileSize(size);
				fileList.add(String.format("%s (%s) - from %s", fileName, sizeStr, source));
				log.info("Found file: {} ({}) in {}", fileName, sizeStr, source);
			}
			catch (IOException e) {
				log.warn("Error reading file info: {}", filePath, e);
			}
		});
	}

	private String extractFileName(String fileInfo) {
		try {
			int sizeIndex = fileInfo.indexOf(" (");
			return sizeIndex > 0 ? fileInfo.substring(0, sizeIndex) : fileInfo;
		}
		catch (Exception e) {
			log.warn("Error extracting filename from: {}", fileInfo, e);
			return fileInfo;
		}
	}

	private String formatFileSize(long size) {
		if (size < 1024)
			return size + " B";
		else if (size < 1024 * 1024)
			return String.format("%.1f KB", size / 1024.0);
		else
			return String.format("%.1f MB", size / (1024.0 * 1024.0));
	}

	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		return lastDotIndex > 0 ? fileName.substring(lastDotIndex) : "";
	}

	/**
	 * Simple file location data class - jmanus style
	 */
	private static class FileLocation {

		final Path filePath;

		final String planId;

		final boolean isCurrentPlan;

		FileLocation(Path filePath, String planId, boolean isCurrentPlan) {
			this.filePath = filePath;
			this.planId = planId;
			this.isCurrentPlan = isCurrentPlan;
		}

	}

	/**
	 * Find uploaded file location
	 */
	private FileLocation findUploadedFileLocation(String fileName) {
		// 1. Check current plan
		Path currentFile = directoryManager.getRootPlanDirectory(currentPlanId)
			.resolve(UPLOADED_FILES_DIR)
			.resolve(fileName);

		if (Files.exists(currentFile)) {
			log.info("Found file in current plan: {}", fileName);
			return new FileLocation(currentFile, currentPlanId, true);
		}

		// 2. Check recent plans
		try {
			Path innerStorageRoot = directoryManager.getInnerStorageRoot();
			if (!Files.exists(innerStorageRoot)) {
				return null;
			}

			List<Path> recentPlans = Files.list(innerStorageRoot)
				.filter(Files::isDirectory)
				.filter(path -> path.getFileName().toString().startsWith("plan-"))
				.filter(path -> !path.getFileName().toString().equals(currentPlanId))
				.sorted((p1, p2) -> {
					try {
						return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
					}
					catch (IOException e) {
						return 0;
					}
				})
				.limit(10)
				.toList();

			for (Path planDir : recentPlans) {
				Path candidate = planDir.resolve(UPLOADED_FILES_DIR).resolve(fileName);
				if (Files.exists(candidate)) {
					String planId = planDir.getFileName().toString();
					log.info("Found file {} in recent plan: {}", fileName, planId);
					return new FileLocation(candidate, planId, false);
				}
			}
		}
		catch (IOException e) {
			log.warn("Error searching recent plans", e);
		}

		return null;
	}

	// ===== Tool Interface Implementation =====

	@Override
	public String getName() {
		return TOOL_NAME;
	}

	@Override
	public String getDescription() {
		return "Intelligent file processing tool - Automatically recognizes and processes various file types. "
				+ "Workflow: 1.Use list_files to view available files (only call once) "
				+ "2.Use process_file to process needed files one by one "
				+ "3.After processing all files use other tools to complete final task. "
				+ "Important: list_files only needs to be called once, don't call repeatedly, "
				+ "if needed files are processed should proceed to next step. "
				+ "Best practice: Upload and analyze files in current plan, prioritize processing files in current plan. "
				+ "Supported file types: PDF documents automatic text extraction, Excel/CSV automatic data reading, "
				+ "text files automatic content reading, other formats intelligent selection processing. "
				+ "Note: Prioritize using files in current plan to ensure analyzing most relevant content.";
	}

	@Override
	public String getParameters() {
		return "{\"type\":\"object\"," + "\"properties\":{" + "\"action\":{\"type\":\"string\","
				+ "\"description\":\"Operation type: list_files (list files) or process_file (intelligent file processing)\","
				+ "\"enum\":[\"list_files\",\"process_file\"]}," + "\"file_name\":{\"type\":\"string\","
				+ "\"description\":\"Filename to process (required for process_file). Must be the exact filename shown in list_files results\"}"
				+ "}," + "\"required\":[\"action\"]}";
	}

	@Override
	public Class<UploadedFileInput> getInputType() {
		return UploadedFileInput.class;
	}

	@Override
	public void cleanup(String planId) {
		// File processing tool doesn't need special cleanup logic
		log.info("UploadedFileLoaderTool cleanup for planId: {}", planId);
	}

	@Override
	public String getCurrentToolStateString() {
		return String.format(
				"UploadedFileLoaderTool State:\n- Current Plan ID: %s\n- Supported File Types: pdf, xlsx, xls, csv, txt, md, json, xml, yaml, yml, log, java, py, js, html, css",
				currentPlanId);
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
