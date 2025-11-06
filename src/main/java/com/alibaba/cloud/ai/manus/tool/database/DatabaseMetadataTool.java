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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.database.action.GetDatasourceInfoAction;
import com.alibaba.cloud.ai.manus.tool.database.action.GetTableIndexAction;
import com.alibaba.cloud.ai.manus.tool.database.action.GetTableMetaAction;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DatabaseMetadataTool extends AbstractBaseTool<DatabaseRequest> {

	private static final Logger log = LoggerFactory.getLogger(DatabaseMetadataTool.class);

	private final DataSourceService dataSourceService;

	private final ObjectMapper objectMapper;

	public DatabaseMetadataTool(ManusProperties manusProperties, DataSourceService dataSourceService,
			ObjectMapper objectMapper) {
		this.dataSourceService = dataSourceService;
		this.objectMapper = objectMapper;
	}

	public DataSourceService getDataSourceService() {
		return dataSourceService;
	}

	private final String name = "database_metadata_use";

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
				Get database metadata information including table structures, indexes, and datasource information.
				Use this tool when you need to:
				- 'get_table_meta': Get complete metadata of table structure, fields, indexes
				- 'get_table_index': Get table index information
				- 'get_datasource_info': Get data source information
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
				                "action": { "type": "string", "const": "get_table_meta" },
				                "text": { "type": "string", "description": "Fuzzy search table description, leave empty to get all tables" },
				                "datasourceName": { "type": "string", "description": "Data source name, optional" }
				            },
				            "required": ["action"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "get_table_index" },
				                "text": { "type": "string", "description": "Table name to search" },
				                "datasourceName": { "type": "string", "description": "Data source name, optional" }
				            },
				            "required": ["action", "text"],
				            "additionalProperties": false
				        },
				        {
				            "type": "object",
				            "properties": {
				                "action": { "type": "string", "const": "get_datasource_info" },
				                "datasourceName": { "type": "string", "description": "Data source name, leave empty to get all available data sources" }
				            },
				            "required": ["action"],
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
		log.info("DatabaseMetadataTool request: action={}", action);
		try {
			if (action == null) {
				return new ToolExecuteResult("Action parameter is required");
			}
			switch (action) {
				case "get_table_meta": {
					// First search with text, if not found then search all
					GetTableMetaAction metaAction = new GetTableMetaAction(objectMapper);
					ToolExecuteResult result = metaAction.execute(request, dataSourceService);
					if (result == null || result.getOutput() == null || result.getOutput().trim().isEmpty()
							|| result.getOutput().equals("[]")
							|| result.getOutput().contains("No matching tables found")) {
						DatabaseRequest allReq = new DatabaseRequest();
						allReq.setAction("get_table_meta");
						allReq.setText(null);
						result = metaAction.execute(allReq, dataSourceService);
					}
					return result;
				}
				case "get_table_index":
					return new GetTableIndexAction(objectMapper).execute(request, dataSourceService);
				case "get_datasource_info":
					return new GetDatasourceInfoAction(objectMapper).execute(request, dataSourceService);
				default:
					return new ToolExecuteResult("Unknown action: " + action);
			}
		}
		catch (Exception e) {
			log.error("Database metadata action '" + action + "' failed", e);
			return new ToolExecuteResult("Database metadata action '" + action + "' failed: " + e.getMessage());
		}
	}

	@Override
	public void cleanup(String planId) {
		if (planId != null) {
			log.info("Cleaning up database metadata resources for plan: {}", planId);
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
			stateBuilder.append("\n=== Database Metadata Tool Current State ===\n");

			if (datasourceInfo.isEmpty()) {
				stateBuilder.append("No datasources configured or available.\n");
			}
			else {
				stateBuilder.append("Available datasources:\n");
				for (Map.Entry<String, String> entry : datasourceInfo.entrySet()) {
					String datasourceName = entry.getKey();
					String datasourceType = entry.getValue();
					stateBuilder.append(String.format("  - %s (%s)\n", datasourceName, datasourceType));
				}

				// Get default data source information
				try {
					String defaultType = dataSourceService.getDataSourceType();
					stateBuilder.append(String.format("\nDefault datasource type: %s\n", defaultType));
				}
				catch (Exception e) {
					stateBuilder.append("\nDefault datasource: Not available\n");
				}

				// Test connection status
				stateBuilder.append("\nConnection status:\n");
				for (String datasourceName : datasourceInfo.keySet()) {
					try {
						dataSourceService.getConnection(datasourceName);
						stateBuilder.append(String.format("  - %s: Connected ✓\n", datasourceName));
					}
					catch (Exception e) {
						stateBuilder.append(
								String.format("  - %s: Connection failed ✗ (%s)\n", datasourceName, e.getMessage()));
					}
				}
			}

			stateBuilder.append("\n=== End Database Metadata Tool State ===\n");
			return stateBuilder.toString();
		}
		catch (Exception e) {
			log.error("Failed to get database metadata tool state", e);
			return String.format("Database metadata tool state error: %s", e.getMessage());
		}
	}

	public static DatabaseMetadataTool getInstance(DataSourceService dataSourceService, ObjectMapper objectMapper) {
		return new DatabaseMetadataTool(null, dataSourceService, objectMapper);
	}

}
