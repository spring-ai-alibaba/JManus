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

package com.alibaba.cloud.ai.manus.tool.database.action;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.database.DataSourceService;
import com.alibaba.cloud.ai.manus.tool.database.DatabaseRequest;

public class ExecuteSqlAction extends AbstractDatabaseAction {

	private static final Logger log = LoggerFactory.getLogger(ExecuteSqlAction.class);

	@Override
	public ToolExecuteResult execute(DatabaseRequest request, DataSourceService dataSourceService) {
		String query = request.getQuery();
		String datasourceName = request.getDatasourceName();

		if (query == null || query.trim().isEmpty()) {
			log.warn("ExecuteSqlAction failed: missing query statement, datasourceName={}", datasourceName);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nError: Missing query statement");
		}
		String[] statements = query.split(";");
		List<String> results = new ArrayList<>();
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
						results.add(formatResultSet(rs));
					}
				}
				else {
					int updateCount = stmt.getUpdateCount();
					results.add("Execution successful. Affected rows: " + updateCount);
				}
			}
			log.info("ExecuteSqlAction completed successfully, datasourceName={}, statements={}", datasourceName,
					statements.length);
			String resultContent = "Datasource: " + (datasourceName != null ? datasourceName : "default") + "\n"
					+ String.join("\n---\n", results);
			return new ToolExecuteResult(resultContent);
		}
		catch (SQLException e) {
			log.error("ExecuteSqlAction failed with SQLException, datasourceName={}, error={}", datasourceName,
					e.getMessage(), e);
			return new ToolExecuteResult("Datasource: " + (datasourceName != null ? datasourceName : "default")
					+ "\nSQL execution failed: " + e.getMessage());
		}
	}

	private String formatResultSet(ResultSet rs) throws SQLException {
		int columnCount = rs.getMetaData().getColumnCount();
		// Collect column names
		List<String> columnNames = new ArrayList<>();
		for (int i = 1; i <= columnCount; i++) {
			columnNames.add(rs.getMetaData().getColumnName(i));
		}
		// Check if we have data and collect rows
		boolean hasData = false;
		List<List<String>> rows = new ArrayList<>();
		while (rs.next()) {
			hasData = true;
			List<String> row = new ArrayList<>();
			for (int i = 1; i <= columnCount; i++) {
				Object val = rs.getObject(i);
				String cellValue = (val == null) ? "NULL" : val.toString();
				row.add(escapeMarkdownTableCell(cellValue));
			}
			rows.add(row);
		}
		// Format output based on whether we have data
		if (hasData) {
			// Build markdown table
			StringBuilder table = new StringBuilder();
			// Header row
			table.append("| ");
			for (int i = 0; i < columnNames.size(); i++) {
				table.append(escapeMarkdownTableCell(columnNames.get(i)));
				if (i < columnNames.size() - 1) {
					table.append(" | ");
				}
			}
			table.append(" |\n");
			// Separator row
			table.append("| ");
			for (int i = 0; i < columnNames.size(); i++) {
				table.append("---");
				if (i < columnNames.size() - 1) {
					table.append(" | ");
				}
			}
			table.append(" |\n");
			// Data rows
			for (List<String> row : rows) {
				table.append("| ");
				for (int i = 0; i < row.size(); i++) {
					table.append(row.get(i));
					if (i < row.size() - 1) {
						table.append(" | ");
					}
				}
				table.append(" |\n");
			}
			return table.toString();
		}
		else {
			// Empty result set: return specific format
			String columnNamesStr = String.join(",", columnNames);
			return "returnColumn: " + columnNamesStr + " \nresultSet: No rows found.";
		}
	}

	private String escapeMarkdownTableCell(String cell) {
		if (cell == null) {
			return "";
		}
		// Replace pipe characters and newlines to prevent markdown table breakage
		return cell.replace("|", "\\|").replace("\n", "\\n").replace("\r", "\\r");
	}

}
