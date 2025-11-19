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
package com.alibaba.cloud.ai.manus.tool.database;

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
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.IExcelProcessingService;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Database Table to Excel Tool
 *
 * Converts database tables to Excel format. Supports exporting table data by table name
 * or custom SQL query.
 */
@Component
public class DatabaseTableToExcelTool extends AbstractBaseTool<DatabaseTableToExcelTool.TableToExcelRequest> {

	private static final Logger log = LoggerFactory.getLogger(DatabaseTableToExcelTool.class);

	private final DataSourceService dataSourceService;

	private final IExcelProcessingService excelProcessingService;

	private final UnifiedDirectoryManager directoryManager;

	public DatabaseTableToExcelTool(ManusProperties manusProperties, DataSourceService dataSourceService,
			IExcelProcessingService excelProcessingService, UnifiedDirectoryManager directoryManager) {
		this.dataSourceService = dataSourceService;
		this.excelProcessingService = excelProcessingService;
		this.directoryManager = directoryManager;
	}

	/**
	 * Request class for table to Excel conversion
	 */
	public static class TableToExcelRequest {

		@JsonProperty("tableName")
		private String tableName;

		@JsonProperty("query")
		private String query;

		@JsonProperty("datasourceName")
		private String datasourceName;

		@JsonProperty("filename")
		private String filename;

		@JsonProperty("worksheetName")
		private String worksheetName;

		@JsonProperty("parameters")
		private List<Object> parameters;

		public String getTableName() {
			return tableName;
		}

		public void setTableName(String tableName) {
			this.tableName = tableName;
		}

		public String getQuery() {
			return query;
		}

		public void setQuery(String query) {
			this.query = query;
		}

		public String getDatasourceName() {
			return datasourceName;
		}

		public void setDatasourceName(String datasourceName) {
			this.datasourceName = datasourceName;
		}

		public String getFilename() {
			return filename;
		}

		public void setFilename(String filename) {
			this.filename = filename;
		}

		public String getWorksheetName() {
			return worksheetName;
		}

		public void setWorksheetName(String worksheetName) {
			this.worksheetName = worksheetName;
		}

		public List<Object> getParameters() {
			return parameters;
		}

		public void setParameters(List<Object> parameters) {
			this.parameters = parameters;
		}

	}

	@Override
	public String getName() {
		return "database_table_to_excel";
	}

	@Override
	public String getDescription() {
		return """
				Convert database table data to Excel format.
				Supports exporting table data by table name or custom SQL query.
				The Excel file will be saved in the root plan shared directory.
				""";
	}

	@Override
	public String getParameters() {
		return """
				{
				    "type": "object",
				    "properties": {
				        "tableName": {
				            "type": "string",
				            "description": "Table name to export (if provided, will use SELECT * FROM tableName)"
				        },
				        "query": {
				            "type": "string",
				            "description": "Custom SQL SELECT query to execute (alternative to tableName)"
				        },
				        "datasourceName": {
				            "type": "string",
				            "description": "Data source name, optional (uses default if not specified)"
				        },
				        "filename": {
				            "type": "string",
				            "description": "Output Excel filename (e.g., 'table_export.xlsx'). If not provided, will generate from table name or query"
				        },
				        "worksheetName": {
				            "type": "string",
				            "description": "Worksheet name in Excel file (default: 'Sheet1')"
				        },
				        "parameters": {
				            "type": "array",
				            "description": "SQL parameters for prepared statements (when using query with ? placeholders)"
				        }
				    },
				    "required": []
				}
				""";
	}

	@Override
	public Class<TableToExcelRequest> getInputType() {
		return TableToExcelRequest.class;
	}

	@Override
	public ToolExecuteResult run(TableToExcelRequest request) {
		log.info("DatabaseTableToExcelTool request: tableName={}, query={}, datasourceName={}", request.getTableName(),
				request.getQuery(), request.getDatasourceName());

		try {
			// Validate input
			if (request.getTableName() == null && request.getQuery() == null) {
				return new ToolExecuteResult("Error: Either tableName or query must be provided");
			}

			// Build SQL query
			String sqlQuery = buildSqlQuery(request);
			if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
				return new ToolExecuteResult("Error: Failed to build SQL query");
			}

			// Validate SQL is SELECT only
			if (!sqlQuery.trim().toUpperCase().startsWith("SELECT")) {
				return new ToolExecuteResult("Error: Only SELECT queries are allowed");
			}

			// Generate filename if not provided
			String filename = generateFilename(request);
			if (filename == null) {
				return new ToolExecuteResult("Error: Failed to generate filename");
			}

			// Generate worksheet name if not provided
			String worksheetName = request.getWorksheetName();
			if (worksheetName == null || worksheetName.trim().isEmpty()) {
				worksheetName = "Sheet1";
			}

			// Get plan ID for file operations
			String planId = getRootPlanId();
			if (planId == null || planId.trim().isEmpty()) {
				planId = getCurrentPlanId();
			}
			if (planId == null || planId.trim().isEmpty()) {
				return new ToolExecuteResult("Error: Plan ID is required for file operations");
			}

			// Execute query and get data
			List<List<String>> data = executeQuery(sqlQuery, request.getDatasourceName(), request.getParameters());
			if (data == null || data.isEmpty()) {
				return new ToolExecuteResult("Error: No data returned from query or query execution failed");
			}

			// Extract headers (first row) and data rows
			List<String> headers = data.get(0);
			List<List<String>> dataRows = data.subList(1, data.size());

			// Create Excel file
			Map<String, List<String>> worksheets = new HashMap<>();
			worksheets.put(worksheetName, headers);

			String filePath = getFilePath(planId, filename);
			excelProcessingService.createExcelFile(planId, filePath, worksheets);
			excelProcessingService.writeExcelDataWithHeaders(planId, filePath, worksheetName, dataRows, null, false);

			log.info("Successfully converted table to Excel: {} -> {}", sqlQuery, filename);
			return new ToolExecuteResult(String.format(
					"Successfully converted database table to Excel\n\n" + "**Output File**: %s\n\n"
							+ "**Worksheet**: %s\n\n" + "**Rows Exported**: %d\n\n" + "**Columns**: %s",
					filename, worksheetName, dataRows.size(), String.join(", ", headers)));

		}
		catch (Exception e) {
			log.error("Database table to Excel conversion failed", e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * Build SQL query from request
	 */
	private String buildSqlQuery(TableToExcelRequest request) {
		if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
			return request.getQuery().trim();
		}
		if (request.getTableName() != null && !request.getTableName().trim().isEmpty()) {
			return "SELECT * FROM " + request.getTableName();
		}
		return null;
	}

	/**
	 * Generate filename from request
	 */
	private String generateFilename(TableToExcelRequest request) {
		if (request.getFilename() != null && !request.getFilename().trim().isEmpty()) {
			String filename = request.getFilename().trim();
			// Ensure .xlsx extension
			if (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls")) {
				filename += ".xlsx";
			}
			return filename;
		}

		// Generate from table name
		if (request.getTableName() != null && !request.getTableName().trim().isEmpty()) {
			return request.getTableName().trim() + ".xlsx";
		}

		// Generate from query (use first word or default)
		if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
			String query = request.getQuery().trim();
			// Try to extract table name from SELECT ... FROM table
			if (query.toUpperCase().contains("FROM")) {
				String[] parts = query.toUpperCase().split("FROM");
				if (parts.length > 1) {
					String tablePart = parts[1].trim().split("\\s+")[0];
					return tablePart + ".xlsx";
				}
			}
			return "query_result.xlsx";
		}

		return "table_export.xlsx";
	}

	/**
	 * Execute SQL query and return data as List of Lists
	 */
	private List<List<String>> executeQuery(String query, String datasourceName, List<Object> parameters)
			throws SQLException {
		List<List<String>> result = new ArrayList<>();

		try (Connection conn = datasourceName != null && !datasourceName.trim().isEmpty()
				? dataSourceService.getConnection(datasourceName) : dataSourceService.getConnection()) {

			if (parameters != null && !parameters.isEmpty()) {
				// Use prepared statement
				try (PreparedStatement pstmt = conn.prepareStatement(query)) {
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

					try (ResultSet rs = pstmt.executeQuery()) {
						result = convertResultSetToList(rs);
					}
				}
			}
			else {
				// Use regular statement
				try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
					result = convertResultSetToList(rs);
				}
			}
		}

		return result;
	}

	/**
	 * Convert ResultSet to List of Lists (first row is headers)
	 */
	private List<List<String>> convertResultSetToList(ResultSet rs) throws SQLException {
		List<List<String>> result = new ArrayList<>();
		ResultSetMetaData metaData = rs.getMetaData();
		int columnCount = metaData.getColumnCount();

		// Add header row
		List<String> headers = new ArrayList<>();
		for (int i = 1; i <= columnCount; i++) {
			headers.add(metaData.getColumnName(i));
		}
		result.add(headers);

		// Add data rows
		while (rs.next()) {
			List<String> row = new ArrayList<>();
			for (int i = 1; i <= columnCount; i++) {
				Object value = rs.getObject(i);
				String cellValue = (value == null) ? "" : value.toString();
				row.add(cellValue);
			}
			result.add(row);
		}

		return result;
	}

	/**
	 * Get file path in shared directory
	 */
	private String getFilePath(String planId, String filename) {
		try {
			java.nio.file.Path rootPlanDir = directoryManager.getRootPlanDirectory(planId);
			java.nio.file.Path sharedDir = rootPlanDir.resolve("shared");
			java.nio.file.Path filePath = sharedDir.resolve(filename).normalize();

			// Ensure the path stays within the shared directory
			if (!filePath.startsWith(sharedDir)) {
				log.warn("File path is outside shared directory: {}", filename);
				return "shared/" + filename;
			}

			// Return relative path from root plan directory
			return rootPlanDir.relativize(filePath).toString().replace("\\", "/");
		}
		catch (Exception e) {
			log.error("Error getting file path: {}", filename, e);
			return "shared/" + filename;
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up database table to Excel resources for plan: {}", planId);
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public String getServiceGroup() {
		return "database-service-group";
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			Map<String, String> datasourceInfo = dataSourceService.getAllDatasourceInfo();
			StringBuilder stateBuilder = new StringBuilder();
			stateBuilder.append("\n=== Database Table to Excel Tool Current State ===\n");

			if (datasourceInfo.isEmpty()) {
				stateBuilder.append("No datasources configured or available.\n");
			}
			else {
				stateBuilder.append("Available datasources:\n");
				for (Map.Entry<String, String> entry : datasourceInfo.entrySet()) {
					stateBuilder.append(String.format("  - %s (%s)\n", entry.getKey(), entry.getValue()));
				}
			}

			stateBuilder.append("\n=== End Database Table to Excel Tool State ===\n");
			return stateBuilder.toString();
		}
		catch (Exception e) {
			log.error("Failed to get database table to Excel tool state", e);
			return String.format("Database table to Excel tool state error: %s", e.getMessage());
		}
	}

	public static DatabaseTableToExcelTool getInstance(ManusProperties manusProperties,
			DataSourceService dataSourceService, IExcelProcessingService excelProcessingService,
			UnifiedDirectoryManager directoryManager) {
		return new DatabaseTableToExcelTool(manusProperties, dataSourceService, excelProcessingService,
				directoryManager);
	}

}
