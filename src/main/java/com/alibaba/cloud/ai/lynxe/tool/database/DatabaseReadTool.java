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
package com.alibaba.cloud.ai.lynxe.tool.database;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.database.action.ExecuteSqlAction;
import com.alibaba.cloud.ai.lynxe.tool.database.action.GetTableNameAction;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DatabaseReadTool extends AbstractBaseTool<DatabaseRequest> {

	private static final Logger log = LoggerFactory.getLogger(DatabaseReadTool.class);

	private final DataSourceService dataSourceService;

	private final ObjectMapper objectMapper;

	public DatabaseReadTool(ManusProperties manusProperties, DataSourceService dataSourceService,
			ObjectMapper objectMapper) {
		this.dataSourceService = dataSourceService;
		this.objectMapper = objectMapper;
	}

	public DataSourceService getDataSourceService() {
		return dataSourceService;
	}

	private final String name = "database_read_use";

	@Override
	public String getServiceGroup() {
		return "database-service-group";
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return """
				Read and query database information, execute SELECT queries, and find table names.
				Use this tool when you need to:
				- 'execute_read_sql': Execute SELECT queries (read-only operations only)
				- 'get_table_name': Find table names based on table comments

				Important: When querying NULL values, use NULL keyword explicitly (e.g., WHERE email IS NULL).
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
				                "action": { "type": "string", "const": "execute_read_sql" },
				                "query": { "type": "string", "description": "SELECT query statement to execute (read-only)" },
				                "datasourceName": { "type": "string", "description": "Data source name, optional" }
				            },
				            "required": ["action", "query"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "get_table_name" },
				                "text": { "type": "string", "description": "Chinese table name or table description to search, supports single query only" },
				                "datasourceName": { "type": "string", "description": "Data source name, optional" }
				            },
				            "required": ["action", "text"],
				            "additionalProperties": false
				        }
				    ]
				}
				""";
	}

	@Override
	public Class<DatabaseRequest> getInputType() {
		return DatabaseRequest.class;
	}

	@Override
	public ToolExecuteResult run(DatabaseRequest request) {
		String action = request.getAction();
		log.info("DatabaseReadTool request: action={}", action);
		try {
			if (action == null) {
				return new ToolExecuteResult("Action parameter is required");
			}
			switch (action) {
				case "execute_read_sql":
					// Validate that it's a SELECT query
					String query = request.getQuery();
					if (query != null && !query.trim().toUpperCase().startsWith("SELECT")) {
						return new ToolExecuteResult("Only SELECT queries are allowed in read-only mode");
					}
					return new ExecuteSqlAction().execute(request, dataSourceService);
				case "get_table_name":
					return new GetTableNameAction(objectMapper).execute(request, dataSourceService);
				default:
					return new ToolExecuteResult("Unknown action: " + action);
			}
		}
		catch (Exception e) {
			log.error("Database read action '" + action + "' failed", e);
			return new ToolExecuteResult("Database read action '" + action + "' failed: " + e.getMessage());
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up database read resources for plan: {}", planId);
		}
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public String getCurrentToolStateString() {
		try {
			Map<String, String> datasourceInfo = dataSourceService.getAllDatasourceInfo();
			StringBuilder stateBuilder = new StringBuilder();
			stateBuilder.append("\n=== Database Read Tool Current State ===\n");

			if (datasourceInfo.isEmpty()) {
				stateBuilder.append("No datasources configured or available.\n");
			}
			else {
				stateBuilder.append("Available datasources:\n");
				for (Map.Entry<String, String> entry : datasourceInfo.entrySet()) {
					stateBuilder.append(String.format("  - %s (%s)\n", entry.getKey(), entry.getValue()));
				}
			}

			stateBuilder.append("\n=== End Database Read Tool State ===\n");
			return stateBuilder.toString();
		}
		catch (Exception e) {
			log.error("Failed to get database read tool state", e);
			return String.format("Database read tool state error: %s", e.getMessage());
		}
	}

	public static DatabaseReadTool getInstance(DataSourceService dataSourceService, ObjectMapper objectMapper) {
		return new DatabaseReadTool(null, dataSourceService, objectMapper);
	}

}
