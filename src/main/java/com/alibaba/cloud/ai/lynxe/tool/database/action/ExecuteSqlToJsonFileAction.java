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

package com.alibaba.cloud.ai.lynxe.tool.database.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.database.DataSourceService;
import com.alibaba.cloud.ai.lynxe.tool.database.DatabaseRequest;
import com.alibaba.cloud.ai.lynxe.tool.textOperator.TextFileService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Action to execute SQL query and save results to JSON file
 */
public class ExecuteSqlToJsonFileAction extends AbstractDatabaseAction {

	private static final Logger log = LoggerFactory.getLogger(ExecuteSqlToJsonFileAction.class);

	private final TextFileService textFileService;

	private final ObjectMapper objectMapper;

	private final String rootPlanId;

	private final String currentPlanId;

	public ExecuteSqlToJsonFileAction(TextFileService textFileService, ObjectMapper objectMapper, String rootPlanId,
			String currentPlanId) {
		this.textFileService = textFileService;
		this.objectMapper = objectMapper;
		this.rootPlanId = rootPlanId;
		this.currentPlanId = currentPlanId;
	}

	@Override
	public ToolExecuteResult execute(DatabaseRequest request, DataSourceService dataSourceService) {
		String query = request.getQuery();
		String datasourceName = request.getDatasourceName();
		String fileName = request.getFileName();
		List<Object> parameters = request.getParameters();

		if (query == null || query.trim().isEmpty()) {
			log.warn("ExecuteSqlToJsonFileAction failed: missing query statement, datasourceName={}", datasourceName);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nError: Missing query statement");
		}

		if (fileName == null || fileName.trim().isEmpty()) {
			log.warn("ExecuteSqlToJsonFileAction failed: missing file name");
			return new ToolExecuteResult("Error: File name is required for saving SQL results to JSON file");
		}

		// Validate that it's a SELECT query
		if (!query.trim().toUpperCase().startsWith("SELECT")) {
			return new ToolExecuteResult("Only SELECT queries are allowed in read-only mode");
		}

		// Ensure file has .json extension
		if (!fileName.toLowerCase().endsWith(".json")) {
			fileName = fileName + ".json";
		}

		try {
			// Execute SQL and get results as JSON
			List<Map<String, Object>> jsonResults;
			if (parameters != null && !parameters.isEmpty()) {
				jsonResults = executePreparedStatement(query, parameters, datasourceName, dataSourceService);
			}
			else {
				jsonResults = executeRegularStatement(query, datasourceName, dataSourceService);
			}

			// Save to file
			return saveToFile(jsonResults, fileName, datasourceName);
		}
		catch (Exception e) {
			log.error("ExecuteSqlToJsonFileAction failed", e);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nError executing SQL and saving to file: " + e.getMessage());
		}
	}

	/**
	 * Execute SQL using prepared statements with parameters
	 */
	private List<Map<String, Object>> executePreparedStatement(String query, List<Object> parameters,
			String datasourceName, DataSourceService dataSourceService) throws SQLException {
		// Validate parameter count matches placeholder count
		int placeholderCount = countPlaceholders(query);
		if (placeholderCount != parameters.size()) {
			String errorMsg = String
				.format("Parameter count mismatch: SQL query has %d placeholder(s) (?), but %d parameter(s) provided. "
						+ "Query: %s", placeholderCount, parameters.size(), query);
			log.error("ExecuteSqlToJsonFileAction parameter validation failed: {}", errorMsg);
			throw new SQLException(errorMsg);
		}

		try (Connection conn = datasourceName != null && !datasourceName.trim().isEmpty()
				? dataSourceService.getConnection(datasourceName) : dataSourceService.getConnection();
				PreparedStatement pstmt = conn.prepareStatement(query)) {

			// Set parameters
			for (int i = 0; i < parameters.size(); i++) {
				Object param = parameters.get(i);
				if (param == null) {
					pstmt.setNull(i + 1, java.sql.Types.NULL);
				}
				else {
					pstmt.setObject(i + 1, param);
				}
			}

			log.info("Executing prepared statement with {} parameters", parameters.size());
			boolean hasResultSet = pstmt.execute();

			if (hasResultSet) {
				try (ResultSet rs = pstmt.getResultSet()) {
					return convertResultSetToJson(rs);
				}
			}
			else {
				// No result set, return empty list
				log.warn("Prepared statement execution returned no result set");
				return new ArrayList<>();
			}
		}
	}

	/**
	 * Execute SQL using regular statements
	 */
	private List<Map<String, Object>> executeRegularStatement(String query, String datasourceName,
			DataSourceService dataSourceService) throws SQLException {
		String[] statements = query.split(";");
		List<Map<String, Object>> allResults = new ArrayList<>();

		try (Connection conn = datasourceName != null && !datasourceName.trim().isEmpty()
				? dataSourceService.getConnection(datasourceName) : dataSourceService.getConnection();
				Statement stmt = conn.createStatement()) {
			for (String sql : statements) {
				sql = sql.trim();
				if (sql.isEmpty())
					continue;
				boolean hasResultSet = stmt.execute(sql);
				if (hasResultSet) {
					try (ResultSet rs = stmt.getResultSet()) {
						allResults.addAll(convertResultSetToJson(rs));
					}
				}
			}
		}

		return allResults;
	}

	/**
	 * Convert ResultSet to JSON format (List of Maps)
	 */
	private List<Map<String, Object>> convertResultSetToJson(ResultSet rs) throws SQLException {
		List<Map<String, Object>> results = new ArrayList<>();
		ResultSetMetaData metaData = rs.getMetaData();
		int columnCount = metaData.getColumnCount();

		// Get column names
		List<String> columnNames = new ArrayList<>();
		for (int i = 1; i <= columnCount; i++) {
			columnNames.add(metaData.getColumnName(i));
		}

		// Convert each row to a Map
		while (rs.next()) {
			Map<String, Object> row = new HashMap<>();
			for (int i = 1; i <= columnCount; i++) {
				Object value = rs.getObject(i);
				// Handle NULL values
				if (value == null) {
					row.put(columnNames.get(i - 1), null);
				}
				else {
					row.put(columnNames.get(i - 1), value);
				}
			}
			results.add(row);
		}

		return results;
	}

	/**
	 * Save JSON results to file
	 */
	private ToolExecuteResult saveToFile(List<Map<String, Object>> jsonResults, String fileName,
			String datasourceName) {
		try {
			// Get file path using TextFileService
			Path filePath = textFileService.getCreateFilePath(rootPlanId, fileName, currentPlanId);

			// Ensure parent directory exists
			Files.createDirectories(filePath.getParent());

			// Convert to JSON string with pretty printing
			String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonResults);

			// Write to file
			Files.writeString(filePath, jsonContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);

			log.info("Successfully saved SQL results to JSON file: {}, rows: {}", filePath, jsonResults.size());

			// Format return message consistent with other database tools
			String resultMessage = "Datasource: " + (datasourceName != null ? datasourceName : "default") + "\n"
					+ String.format("Successfully executed SQL query and saved %d row(s) to file: %s",
							jsonResults.size(), fileName);

			return new ToolExecuteResult(resultMessage);
		}
		catch (IOException e) {
			log.error("Error saving JSON to file: {}", fileName, e);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nError saving JSON to file: " + e.getMessage());
		}
		catch (Exception e) {
			log.error("Error converting results to JSON", e);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nError converting results to JSON: " + e.getMessage());
		}
	}

	/**
	 * Count the number of ? placeholders in SQL query
	 */
	private int countPlaceholders(String query) {
		if (query == null || query.trim().isEmpty()) {
			return 0;
		}
		int count = 0;
		for (int i = 0; i < query.length(); i++) {
			if (query.charAt(i) == '?') {
				count++;
			}
		}
		return count;
	}

}

