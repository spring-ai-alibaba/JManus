package com.alibaba.cloud.ai.lynxe.tool.textOperator;
// /*
// * Copyright 2025 the original author or authors.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package com.alibaba.cloud.ai.lynxe.tool.textOperator;

// import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;

// import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
// import com.alibaba.cloud.ai.lynxe.tool.innerStorage.SmartContentSavingService;
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import java.io.IOException;
// import java.nio.channels.FileChannel;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.StandardOpenOption;
// import java.util.Map;

// public class TextFileOperator extends AbstractBaseTool<TextFileOperator.TextFileInput>
// {

// private static final Logger log = LoggerFactory.getLogger(TextFileOperator.class);

// /**
// * Internal input class for defining input parameters of text file operation tool
// */
// public static class TextFileInput {

// private String action;

// @com.fasterxml.jackson.annotation.JsonProperty("file_path")
// private String filePath;

// private String content;

// @com.fasterxml.jackson.annotation.JsonProperty("source_text")
// private String sourceText;

// @com.fasterxml.jackson.annotation.JsonProperty("target_text")
// private String targetText;

// @com.fasterxml.jackson.annotation.JsonProperty("start_line")
// private Integer startLine;

// @com.fasterxml.jackson.annotation.JsonProperty("end_line")
// private Integer endLine;

// @com.fasterxml.jackson.annotation.JsonProperty("pattern")
// private String pattern;

// @com.fasterxml.jackson.annotation.JsonProperty("case_sensitive")
// private Boolean caseSensitive;

// @com.fasterxml.jackson.annotation.JsonProperty("whole_word")
// private Boolean wholeWord;

// public TextFileInput() {
// }

// public String getAction() {
// return action;
// }

// public void setAction(String action) {
// this.action = action;
// }

// public String getFilePath() {
// return filePath;
// }

// public void setFilePath(String filePath) {
// this.filePath = filePath;
// }

// public String getContent() {
// return content;
// }

// public void setContent(String content) {
// this.content = content;
// }

// public String getSourceText() {
// return sourceText;
// }

// public void setSourceText(String sourceText) {
// this.sourceText = sourceText;
// }

// public String getTargetText() {
// return targetText;
// }

// public void setTargetText(String targetText) {
// this.targetText = targetText;
// }

// public Integer getStartLine() {
// return startLine;
// }

// public void setStartLine(Integer startLine) {
// this.startLine = startLine;
// }

// public Integer getEndLine() {
// return endLine;
// }

// public void setEndLine(Integer endLine) {
// this.endLine = endLine;
// }

// public String getPattern() {
// return pattern;
// }

// public void setPattern(String pattern) {
// this.pattern = pattern;
// }

// public Boolean getCaseSensitive() {
// return caseSensitive;
// }

// public void setCaseSensitive(Boolean caseSensitive) {
// this.caseSensitive = caseSensitive;
// }

// public Boolean getWholeWord() {
// return wholeWord;
// }

// public void setWholeWord(Boolean wholeWord) {
// this.wholeWord = wholeWord;
// }

// }

// private final TextFileService textFileService;

// private final SmartContentSavingService innerStorageService;

// private final ObjectMapper objectMapper;

// public TextFileOperator(TextFileService textFileService, SmartContentSavingService
// innerStorageService,
// ObjectMapper objectMapper) {
// this.textFileService = textFileService;
// this.innerStorageService = innerStorageService;
// this.objectMapper = objectMapper;
// }

// private static final String TOOL_NAME = "text_file_operator";

// public ToolExecuteResult run(String toolInput) {
// log.info("TextFileOperator toolInput:{}", toolInput);
// try {
// Map<String, Object> toolInputMap = objectMapper.readValue(toolInput,
// new TypeReference<Map<String, Object>>() {
// });
// String planId = this.currentPlanId;

// String action = (String) toolInputMap.get("action");
// String filePath = (String) toolInputMap.get("file_path");

// // Basic parameter validation
// if (action == null) {
// return new ToolExecuteResult("Error: action parameter is required");
// }
// if (filePath == null) {
// return new ToolExecuteResult("Error: file_path parameter is required");
// }

// return switch (action) {
// case "replace" -> {
// String sourceText = (String) toolInputMap.get("source_text");
// String targetText = (String) toolInputMap.get("target_text");

// if (sourceText == null || targetText == null) {
// yield new ToolExecuteResult(
// "Error: replace operation requires source_text and target_text parameters");
// }

// yield replaceText(filePath, sourceText, targetText);
// }
// case "get_text" -> {
// Integer startLine = (Integer) toolInputMap.get("start_line");
// Integer endLine = (Integer) toolInputMap.get("end_line");

// if (startLine == null || endLine == null) {
// yield new ToolExecuteResult(
// "Error: get_text operation requires start_line and end_line parameters");
// }

// yield getTextByLines(filePath, startLine, endLine);
// }
// case "get_all_text" -> getAllText(filePath);
// case "append" -> {
// String appendContent = (String) toolInputMap.get("content");

// if (appendContent == null) {
// yield new ToolExecuteResult("Error: append operation requires content parameter");
// }

// yield appendToFile(filePath, appendContent);
// }
// case "count_words" -> countWords(filePath);
// case "grep" -> {
// String pattern = (String) toolInputMap.get("pattern");
// Boolean caseSensitive = (Boolean) toolInputMap.get("case_sensitive");
// Boolean wholeWord = (Boolean) toolInputMap.get("whole_word");

// if (pattern == null) {
// yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
// }

// yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
// wholeWord != null ? wholeWord : false);
// }
// default -> {
// textFileService.updateFileState(planId, filePath, "Error: Unknown action");
// yield new ToolExecuteResult("Unknown operation: " + action
// + ". Supported operations: replace, get_text, get_all_text, append, count_words,
// grep");
// }
// };
// }
// catch (Exception e) {
// String planId = this.currentPlanId;
// textFileService.updateFileState(planId, textFileService.getCurrentFilePath(planId),
// "Error: " + e.getMessage());
// return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
// }
// }

// /**
// * Execute text file operations, accept strongly typed input object
// */
// @Override
// public ToolExecuteResult run(TextFileInput input) {
// log.info("TextFileOperator input: action={}, filePath={}", input.getAction(),
// input.getFilePath());
// try {
// String planId = this.currentPlanId;
// String action = input.getAction();
// String filePath = input.getFilePath();

// // Basic parameter validation
// if (action == null) {
// return new ToolExecuteResult("Error: action parameter is required");
// }
// if (filePath == null) {
// return new ToolExecuteResult("Error: file_path parameter is required");
// }

// return switch (action) {
// case "replace" -> {
// String sourceText = input.getSourceText();
// String targetText = input.getTargetText();

// if (sourceText == null || targetText == null) {
// yield new ToolExecuteResult(
// "Error: replace operation requires source_text and target_text parameters");
// }

// yield replaceText(filePath, sourceText, targetText);
// }
// case "get_text" -> {
// Integer startLine = input.getStartLine();
// Integer endLine = input.getEndLine();

// if (startLine == null || endLine == null) {
// yield new ToolExecuteResult(
// "Error: get_text operation requires start_line and end_line parameters");
// }

// yield getTextByLines(filePath, startLine, endLine);
// }
// case "get_all_text" -> getAllText(filePath);
// case "append" -> {
// String appendContent = input.getContent();

// if (appendContent == null) {
// yield new ToolExecuteResult("Error: append operation requires content parameter");
// }

// yield appendToFile(filePath, appendContent);
// }
// case "count_words" -> countWords(filePath);
// case "grep" -> {
// String pattern = input.getPattern();
// Boolean caseSensitive = input.getCaseSensitive();
// Boolean wholeWord = input.getWholeWord();

// if (pattern == null) {
// yield new ToolExecuteResult("Error: grep operation requires pattern parameter");
// }

// yield grepText(filePath, pattern, caseSensitive != null ? caseSensitive : false,
// wholeWord != null ? wholeWord : false);
// }
// default -> {
// textFileService.updateFileState(planId, filePath, "Error: Unknown action");
// yield new ToolExecuteResult("Unknown operation: " + action
// + ". Supported operations: replace, get_text, get_all_text, append, count_words,
// grep");
// }
// };
// }
// catch (Exception e) {
// String planId = this.currentPlanId;
// textFileService.updateFileState(planId, textFileService.getCurrentFilePath(planId),
// "Error: " + e.getMessage());
// return new ToolExecuteResult("Tool execution failed: " + e.getMessage());
// }
// }

// /**
// * Ensure file is opened, create if it doesn't exist Uses hierarchical file access:
// * sub-plans can read/write files from root folder, but new files are created in the
// * sub-plan directory
// */
// private ToolExecuteResult ensureFileOpen(String filePath) {
// try {
// // Check file type
// if (!textFileService.isSupportedFileType(filePath)) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: Unsupported file
// type");
// return new ToolExecuteResult("Unsupported file type. Only text-based files are
// supported.");
// }

// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Use TextFileService to get the absolute path with hierarchical access
// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);

// // If file doesn't exist, create it using the create file path method
// if (!Files.exists(absolutePath)) {
// try {
// // Use getCreateFilePath to ensure new files are created in the
// // appropriate directory
// Path createPath = textFileService.getCreateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// Files.createDirectories(createPath.getParent());
// Files.createFile(createPath);

// String location = (this.currentPlanId != null &&
// !this.currentPlanId.equals(this.rootPlanId))
// ? "sub-plan directory" : "root directory";

// textFileService.updateFileState(this.currentPlanId, filePath,
// "Success: New file created in " + location);
// return new ToolExecuteResult("New file created successfully in " + location + ": " +
// createPath);
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath,
// "Error: Failed to create file: " + e.getMessage());
// return new ToolExecuteResult("Failed to create file: " + e.getMessage());
// }
// }

// String location = (this.currentPlanId != null &&
// !this.currentPlanId.equals(this.rootPlanId))
// ? "sub-plan accessing root file" : "root directory";

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: File opened
// from " + location);
// return new ToolExecuteResult("File opened successfully from " + location + ": " +
// absolutePath);
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error opening file: " + e.getMessage());
// }
// }

// private ToolExecuteResult replaceText(String filePath, String sourceText, String
// targetText) {
// try {
// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// String content = Files.readString(absolutePath);
// String newContent = content.replace(sourceText, targetText);
// Files.writeString(absolutePath, newContent);

// // Automatically save file
// try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
// channel.force(true);
// }

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Text replaced
// and saved");
// return new ToolExecuteResult("Text replaced and saved successfully");
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error replacing text: " + e.getMessage());
// }
// }

// private ToolExecuteResult getTextByLines(String filePath, Integer startLine, Integer
// endLine) {
// try {
// // Parameter validation
// if (startLine < 1 || endLine < 1) {
// return new ToolExecuteResult("Error: Line numbers must start from 1");
// }
// if (startLine > endLine) {
// return new ToolExecuteResult("Error: Start line number cannot be greater than end line
// number");
// }

// // Check 500-line limit
// int requestedLines = endLine - startLine + 1;
// if (requestedLines > 500) {
// return new ToolExecuteResult(
// "Error: Maximum 500 lines per request. Please adjust line range or make multiple calls.
// Current requested lines: "
// + requestedLines);
// }

// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// java.util.List<String> lines = Files.readAllLines(absolutePath);

// if (lines.isEmpty()) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Success: File is
// empty");
// return new ToolExecuteResult("File is empty");
// }

// // Validate line number range
// if (startLine > lines.size()) {
// return new ToolExecuteResult(
// "Error: Start line number exceeds file range (file has " + lines.size() + " lines)");
// }

// // Adjust end line number (not exceeding total file lines)
// int actualEndLine = Math.min(endLine, lines.size());

// StringBuilder result = new StringBuilder();
// result.append(String.format("File: %s (Lines %d-%d, Total %d lines)\n", filePath,
// startLine, actualEndLine,
// lines.size()));
// result.append("=".repeat(50)).append("\n");

// for (int i = startLine - 1; i < actualEndLine; i++) {
// result.append(String.format("%4d: %s\n", i + 1, lines.get(i)));
// }

// // If file has more content, prompt user
// if (actualEndLine < lines.size()) {
// result.append("\nNote: File has more content (lines ")
// .append(actualEndLine + 1)
// .append("-")
// .append(lines.size())
// .append("), you can continue calling get_text to retrieve.");
// }

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Retrieved text
// lines");
// return new ToolExecuteResult(result.toString());
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error retrieving text lines: " + e.getMessage());
// }
// }

// private ToolExecuteResult getAllText(String filePath) {
// try {
// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// // Read file content
// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// String content = Files.readString(absolutePath);

// // Force flush to disk to ensure data consistency
// try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.READ)) {
// channel.force(true);
// }

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Retrieved all
// text");

// // Use InnerStorageService to intelligently process content
// SmartContentSavingService.SmartProcessResult processedResult = innerStorageService
// .processContent(this.currentPlanId, content, "get_all_text");

// return new ToolExecuteResult(processedResult.getSummary());
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error retrieving all text: " + e.getMessage());
// }
// }

// private ToolExecuteResult appendToFile(String filePath, String content) {
// try {
// if (content == null || content.isEmpty()) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: No content to
// append");
// return new ToolExecuteResult("Error: No content to append");
// }

// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// Files.writeString(absolutePath, "\n" + content, StandardOpenOption.APPEND,
// StandardOpenOption.CREATE);

// // Automatically save file
// try (FileChannel channel = FileChannel.open(absolutePath, StandardOpenOption.WRITE)) {
// channel.force(true);
// }

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Content
// appended and saved");
// return new ToolExecuteResult("Content appended and saved successfully");
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error appending to file: " + e.getMessage());
// }
// }

// private ToolExecuteResult countWords(String filePath) {
// try {
// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// String content = Files.readString(absolutePath);
// int wordCount = content.isEmpty() ? 0 : content.split("\\s+").length;

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Counted
// words");
// return new ToolExecuteResult(String.format("Total word count (including Markdown
// symbols): %d", wordCount));
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error counting words: " + e.getMessage());
// }
// }

// private ToolExecuteResult grepText(String filePath, String pattern, boolean
// caseSensitive, boolean wholeWord) {
// try {
// // Validate rootPlanId is available for directory operations
// if (this.rootPlanId == null || this.rootPlanId.isEmpty()) {
// String errorMsg = "Error: rootPlanId is required for file operations but is null or
// empty";
// log.error(errorMsg);
// textFileService.updateFileState(this.currentPlanId, filePath, errorMsg);
// return new ToolExecuteResult(errorMsg);
// }

// log.debug("Using rootPlanId: {} for directory operations (currentPlanId: {})",
// this.rootPlanId,
// this.currentPlanId);

// // Automatically open file
// ToolExecuteResult openResult = ensureFileOpen(filePath);
// if (!openResult.getOutput().toLowerCase().contains("success")) {
// return openResult;
// }

// Path absolutePath = textFileService.validateFilePath(this.rootPlanId, filePath,
// this.currentPlanId);
// java.util.List<String> lines = Files.readAllLines(absolutePath);

// if (lines.isEmpty()) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Success: File is
// empty");
// return new ToolExecuteResult("File is empty");
// }

// // Prepare pattern for matching
// String searchPattern = pattern;
// if (!caseSensitive) {
// searchPattern = pattern.toLowerCase();
// }
// if (wholeWord) {
// searchPattern = "\\b" + java.util.regex.Pattern.quote(searchPattern) + "\\b";
// }

// java.util.regex.Pattern regexPattern;
// if (wholeWord) {
// regexPattern = caseSensitive ? java.util.regex.Pattern.compile(searchPattern)
// : java.util.regex.Pattern.compile(searchPattern,
// java.util.regex.Pattern.CASE_INSENSITIVE);
// }
// else {
// regexPattern = caseSensitive
// ? java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(searchPattern))
// : java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(searchPattern),
// java.util.regex.Pattern.CASE_INSENSITIVE);
// }

// StringBuilder result = new StringBuilder();
// result.append(String.format("Grep results for pattern '%s' in file: %s\n", pattern,
// filePath));
// result.append("=".repeat(60)).append("\n");

// int matchCount = 0;
// for (int i = 0; i < lines.size(); i++) {
// String line = lines.get(i);
// String searchLine = caseSensitive ? line : line.toLowerCase();

// if (wholeWord) {
// if (regexPattern.matcher(line).find()) {
// result.append(String.format("%4d: %s\n", i + 1, line));
// matchCount++;
// }
// }
// else {
// if (searchLine.contains(searchPattern)) {
// result.append(String.format("%4d: %s\n", i + 1, line));
// matchCount++;
// }
// }
// }

// if (matchCount == 0) {
// result.append("No matches found.\n");
// }
// else {
// result.append(String.format("\nTotal matches found: %d\n", matchCount));
// }

// textFileService.updateFileState(this.currentPlanId, filePath, "Success: Grep search
// completed");
// return new ToolExecuteResult(result.toString());
// }
// catch (IOException e) {
// textFileService.updateFileState(this.currentPlanId, filePath, "Error: " +
// e.getMessage());
// return new ToolExecuteResult("Error performing grep search: " + e.getMessage());
// }
// }

// @Override
// public String getCurrentToolStateString() {
// String planId = this.currentPlanId;
// try {
// // Get the root plan directory instead of using empty filePath
// Path workingDir = textFileService.getRootPlanDirectory(planId);
// return String.format(
// """
// Current Text File Operation State:
// - working Directory:
// %s

// - Operations are automatically handled (no manual file opening/closing required)
// - All file operations (open, save) are performed automatically
// - Supported file types: txt, md, html, css, java, py, js, ts, xml, json, yaml,
// properties, sh, bat, log, etc.
// - Available operations: replace, get_text, get_all_text, append, count_words, grep

// - Last Operation Result:
// %s
// """,
// workingDir.toString(), textFileService.getLastOperationResult(planId).isEmpty()
// ? "No operation performed yet" : textFileService.getLastOperationResult(planId));
// }
// catch (Exception e) {
// return String.format("""
// Current Text File Operation State:
// - Error getting working directory: %s
// - Available operations: replace, get_text, get_all_text, append, count_words, grep

// - Last Operation Result:
// %s
// """, e.getMessage(), textFileService.getLastOperationResult(planId).isEmpty()
// ? "No operation performed yet" : textFileService.getLastOperationResult(planId));
// }
// }

// @Override
// public String getName() {
// return TOOL_NAME;
// }

// @Override
// public String getDescription() {
// return """
// Perform various operations on text files (including md, html, css, java, etc.).

// Supported operations:
// - replace: Replace specific text in file, requires source_text and target_text
// parameters
// - get_text: Get content from specified line range in file, requires start_line and
// end_line parameters
// Limitation: Maximum 500 lines per call, use multiple calls for more content
// - get_all_text: Get all content from file
// Note: If file content is too long, it will be automatically stored in temporary file
// and return file path
// - append: Append content to file, requires content parameter
// - count_words: Count words in current file
// - grep: Search for text patterns in file, similar to Linux grep command
// Parameters: pattern (required), case_sensitive (optional, default false), whole_word
// (optional, default false)

// Supported file types include:
// - Text files (.txt)
// - Markdown files (.md, .markdown)
// - Web files (.html, .css, .scss, .sass, .less)
// - Programming files (.java, .py, .js, .ts, .cpp, .c, .h, .go, .rs, .php, .rb, .swift,
// .kt, .scala)
// - Configuration files (.json, .xml, .yaml, .yml, .toml, .ini, .conf)
// - Documentation files (.rst, .adoc)
// """;
// }

// @Override
// public String getParameters() {
// return """
// {
// "type": "object",
// "oneOf": [
// {
// "type": "object",
// "properties": {
// "action": {
// "type": "string",
// "const": "replace"
// },
// "file_path": {
// "type": "string",
// "description": "File path to operate on"
// },
// "source_text": {
// "type": "string",
// "description": "Text to be replaced"
// },
// "target_text": {
// "type": "string",
// "description": "Replacement text"
// }
// },
// "required": ["action", "file_path", "source_text", "target_text"],
// "additionalProperties": false
// },
// {
// "type": "object",
// "properties": {
// "action": {
// "type": "string",
// "const": "get_text"
// },
// "file_path": {
// "type": "string",
// "description": "File path to read"
// },
// "start_line": {
// "type": "integer",
// "description": "Starting line number (starts from 1)"
// },
// "end_line": {
// "type": "integer",
// "description": "Ending line number (inclusive). Note: Maximum 500 lines per call, use
// multiple calls for more content"
// }
// },
// "required": ["action", "file_path", "start_line", "end_line"],
// "additionalProperties": false
// },
// {
// "type": "object",
// "properties": {
// "action": {
// "type": "string",
// "const": "get_all_text"
// },
// "file_path": {
// "type": "string",
// "description": "File path to read all content. Note: If file is too long, content will
// be stored in temporary file and return file path"
// }
// },
// "required": ["action", "file_path"],
// "additionalProperties": false
// },
// {
// "type": "object",
// "properties": {
// "action": {
// "type": "string",
// "const": "append"
// },
// "file_path": {
// "type": "string",
// "description": "File path to operate on"
// },
// "content": {
// "type": "string",
// "description": "Content to append to the file"
// }
// },
// "required": ["action", "file_path", "content"],
// "additionalProperties": false
// },
// {
// "type": "object",
// "properties": {
// "action": {
// "type": "string",
// "const": "grep"
// },
// "file_path": {
// "type": "string",
// "description": "File path to search in"
// },
// "pattern": {
// "type": "string",
// "description": "Text pattern to search for"
// },
// "case_sensitive": {
// "type": "boolean",
// "description": "Whether to perform case-sensitive search (default: false)"
// },
// "whole_word": {
// "type": "boolean",
// "description": "Whether to match whole words only (default: false)"
// }
// },
// "required": ["action", "file_path", "pattern"],
// "additionalProperties": false
// }
// ]
// }
// """;
// }

// @Override
// public Class<TextFileInput> getInputType() {
// return TextFileInput.class;
// }

// @Override
// public void cleanup(String planId) {
// if (planId != null) {
// log.info("Cleaning up text file resources for plan: {}", planId);
// textFileService.cleanupPlanDirectory(planId);
// }
// }

// // @Override
// // public FileState getInstance(String planId) {
// // if (planId == null) {
// // throw new IllegalArgumentException("planId cannot be null");
// // }
// // return textFileService.getFileState(planId);
// // }

// @Override
// public String getServiceGroup() {
// return "default-service-group";
// }

// @Override
// public boolean isSelectable() {
// return true;
// }

// }
