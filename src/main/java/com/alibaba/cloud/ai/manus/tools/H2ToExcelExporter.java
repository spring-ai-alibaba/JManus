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
package com.alibaba.cloud.ai.manus.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone tool to export H2 database data to Excel file.
 * Usage: java -cp ... H2ToExcelExporter [dbPath] [outputPath]
 */
public class H2ToExcelExporter {

	private static final Logger log = LoggerFactory.getLogger(H2ToExcelExporter.class);

	// Default H2 database configuration
	private static final String DEFAULT_USERNAME = "sa";

	private static final String DEFAULT_PASSWORD = "$FSD#@!@#!#$!12341234";

	private static final String DRIVER_CLASS = "org.h2.Driver";

    private static final String targetTable = "reddit_research";
	/**
	 * Main method to run the export tool
	 * @param args Command line arguments: [dbPath] [outputPath]
	 *             - dbPath: Path to H2 database file (optional, defaults to
	 *             ./h2-data/openmanus_db)
	 *             - outputPath: Output Excel file path (optional, defaults to
	 *             h2-export-{timestamp}.xlsx)
	 */
	public static void main(String[] args) {
		String dbPath = args.length > 0 ? args[0] : "./h2-data/openmanus_db";
		String outputPath = args.length > 1 ? args[1] : generateDefaultOutputPath();

		log.info("Starting H2 to Excel export tool");
		log.info("Database path: {}", dbPath);
		log.info("Output file: {}", outputPath);

		H2ToExcelExporter exporter = new H2ToExcelExporter();
		try {
			exporter.exportToExcel(dbPath, outputPath);
			log.info("Export completed successfully!");
			System.out.println("Export completed successfully! Output file: " + outputPath);
		}
		catch (Exception e) {
			log.error("Export failed", e);
			System.err.println("Export failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Generate default output path with timestamp
	 */
	private static String generateDefaultOutputPath() {
		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		return "h2-export-" + timestamp + ".xlsx";
	}

	/**
	 * Export all tables from H2 database to Excel
	 * @param dbPath Path to H2 database file
	 * @param outputPath Output Excel file path
	 */
	public void exportToExcel(String dbPath, String outputPath) throws SQLException, IOException {
		String dbUrl = "jdbc:h2:file:" + dbPath + ";MODE=MYSQL;DATABASE_TO_LOWER=TRUE";

		try {
			// Load H2 driver
			Class.forName(DRIVER_CLASS);
		}
		catch (ClassNotFoundException e) {
			throw new SQLException("H2 driver not found. Make sure h2.jar is in the classpath.", e);
		}

		try (Connection conn = DriverManager.getConnection(dbUrl, DEFAULT_USERNAME, DEFAULT_PASSWORD);
				Workbook workbook = new XSSFWorkbook()) {

			log.info("Connected to H2 database: {}", dbPath);

			// Get all table names
			List<String> tableNames = getTableNames(conn);
			log.info("Found {} tables to export", tableNames.size());

			if (tableNames.isEmpty()) {
				log.warn("No tables found in database");
				// Create a sheet with message
				Sheet sheet = workbook.createSheet("No Tables");
				Row row = sheet.createRow(0);
				Cell cell = row.createCell(0);
				cell.setCellValue("No tables found in database");
			}
			else {
				// Export each table to a separate sheet
				for (String tableName : tableNames) {
					log.info("Exporting table: {}", tableName);
                    if(targetTable.equals(tableName)){
                        exportTable(conn, tableName, workbook);
                    }
					
				}
			}

			// Write workbook to file
			try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
				workbook.write(fileOut);
			}

			log.info("Excel file created: {}", outputPath);
		}
	}

	/**
	 * Get all table names from the database
	 */
	private List<String> getTableNames(Connection conn) throws SQLException {
		List<String> tableNames = new ArrayList<>();
		DatabaseMetaData metaData = conn.getMetaData();

		// Get all tables (excluding system tables)
		try (ResultSet rs = metaData.getTables(null, null, null, new String[] { "TABLE" })) {
			while (rs.next()) {
				String tableName = rs.getString("TABLE_NAME");
				// Filter out H2 system tables
				if (!tableName.startsWith("INFORMATION_SCHEMA") && !tableName.startsWith("SYSTEM_")) {
					tableNames.add(tableName);
				}
			}
		}

		return tableNames;
	}

	/**
	 * Export a single table to a sheet in the workbook
	 */
	private void exportTable(Connection conn, String tableName, Workbook workbook) throws SQLException {
		// Create sheet with table name (Excel sheet names have limitations)
		String sheetName = sanitizeSheetName(tableName);
		Sheet sheet = workbook.createSheet(sheetName);

		// Create styles
		CellStyle headerStyle = createHeaderStyle(workbook);
		CellStyle dataStyle = createDataStyle(workbook);

		// Query table data
		String query = "SELECT * FROM " + tableName;
		try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {

			ResultSetMetaData metaData = rs.getMetaData();
			int columnCount = metaData.getColumnCount();

			// Create header row
			Row headerRow = sheet.createRow(0);
			for (int i = 1; i <= columnCount; i++) {
				Cell cell = headerRow.createCell(i - 1);
				String columnName = metaData.getColumnName(i);
				cell.setCellValue(columnName);
				cell.setCellStyle(headerStyle);
			}

			// Create data rows
			int rowNum = 1;
			while (rs.next()) {
				Row row = sheet.createRow(rowNum);
				for (int i = 1; i <= columnCount; i++) {
					Cell cell = row.createCell(i - 1);
					Object value = rs.getObject(i);
					setCellValue(cell, value);
					cell.setCellStyle(dataStyle);
				}
				rowNum++;
			}

			// Auto-size columns
			for (int i = 0; i < columnCount; i++) {
				sheet.autoSizeColumn(i);
				// Set minimum width
				int currentWidth = sheet.getColumnWidth(i);
				if (currentWidth < 2000) {
					sheet.setColumnWidth(i, 2000);
				}
			}

			log.info("Exported {} rows from table {}", rowNum - 1, tableName);
		}
	}

	/**
	 * Sanitize table name for Excel sheet name (Excel has limitations on sheet names)
	 */
	private String sanitizeSheetName(String tableName) {
		// Excel sheet name limitations:
		// - Max 31 characters
		// - Cannot contain: / \ ? * [ ]
		String sanitized = tableName.replaceAll("[\\\\/:?*\\[\\]]", "_");
		if (sanitized.length() > 31) {
			sanitized = sanitized.substring(0, 31);
		}
		return sanitized;
	}

	/**
	 * Set cell value based on object type
	 */
	private void setCellValue(Cell cell, Object value) {
		if (value == null) {
			cell.setCellValue("");
		}
		else if (value instanceof Number) {
			cell.setCellValue(((Number) value).doubleValue());
		}
		else if (value instanceof Boolean) {
			cell.setCellValue((Boolean) value);
		}
		else if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp
				|| value instanceof java.time.LocalDate || value instanceof java.time.LocalDateTime) {
			cell.setCellValue(value.toString());
		}
		else {
			// Convert string value and replace literal \n with actual newlines
			String stringValue = value.toString();
			stringValue = normalizeNewlines(stringValue);
			cell.setCellValue(stringValue);
		}
	}

	/**
	 * Normalize newline characters in string values
	 * Replaces literal \n with actual newline characters for proper Excel formatting
	 */
	private String normalizeNewlines(String value) {
		if (value == null) {
			return "";
		}
		// Replace literal \n (backslash + n) with actual newline character
		// In Java strings, literal backslash+n is represented as "\\n"
		// We need to replace the two-character sequence backslash+n with actual newline
		return value.replace("\\n", "\n");
	}

	/**
	 * Create header cell style
	 */
	private CellStyle createHeaderStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.WHITE.getIndex());
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		return style;
	}

	/**
	 * Create data cell style
	 */
	private CellStyle createDataStyle(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		style.setWrapText(true);
		return style;
	}

}

