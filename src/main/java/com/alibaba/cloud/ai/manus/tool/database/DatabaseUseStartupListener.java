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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.tool.database.model.po.DatasourceConfigEntity;
import com.alibaba.cloud.ai.manus.tool.database.service.DatasourceConfigService;

@Component
public class DatabaseUseStartupListener implements ApplicationListener<ApplicationStartedEvent> {

	private static final Logger log = LoggerFactory.getLogger(DatabaseUseStartupListener.class);

	@Autowired
	private DataSourceService dataSourceService;

	@Autowired
	private DatasourceConfigService datasourceConfigService;

	@Override
	public void onApplicationEvent(ApplicationStartedEvent event) {
		initializeDatabaseConfigs();
	}

	private void initializeDatabaseConfigs() {
		try {
			log.info("Starting to initialize database configurations from database...");

			// Get all enabled datasource configurations from database
			List<DatasourceConfigEntity> configs = datasourceConfigService.getEnabledEntities();

			if (configs.isEmpty()) {
				log.warn("No enabled database configurations found. This is normal if no datasources are configured.");
				printDatasourceSummary(configs);
				return;
			}

			// Iterate and initialize each data source
			for (DatasourceConfigEntity config : configs) {
				initializeDatasource(config);
			}

			log.info("Database configurations initialized with {} datasources", dataSourceService.getDataSourceCount());

			// Output data source list
			printDatasourceSummary(configs);

		}
		catch (Exception e) {
			log.error("Failed to initialize database configurations", e);
		}
	}

	private void initializeDatasource(DatasourceConfigEntity config) {
		try {
			String datasourceName = config.getName();
			String type = config.getType();
			Boolean enable = config.getEnable();
			String url = config.getUrl();
			String driverClassName = config.getDriverClassName();
			String username = config.getUsername();
			String password = config.getPassword();

			if (type == null || url == null || driverClassName == null) {
				log.warn("Incomplete configuration for datasource '{}'", datasourceName);
				return;
			}

			if (enable == null || !enable) {
				log.info("Datasource '{}' is disabled", datasourceName);
				return;
			}

			// Create data source
			dataSourceService.addDataSource(datasourceName, url, username, password, driverClassName, type);
			log.info("Initialized datasource '{}' (type: {})", datasourceName, type);

		}
		catch (Exception e) {
			log.error("Failed to initialize datasource '{}'", config.getName(), e);
		}
	}

	private void printDatasourceSummary(List<DatasourceConfigEntity> configs) {
		StringBuilder summary = new StringBuilder();
		summary.append("\n");
		summary.append("=".repeat(100)).append("\n");
		summary.append("DATABASE DATASOURCE SUMMARY").append("\n");
		summary.append("=".repeat(100)).append("\n");

		int totalConfigs = configs.size();
		int initializedCount = 0;
		int disabledCount = 0;

		for (DatasourceConfigEntity config : configs) {
			String datasourceName = config.getName();
			String type = config.getType();
			Boolean enable = config.getEnable();
			String url = config.getUrl();

			boolean isEnabled = enable != null && enable;
			boolean isInitialized = dataSourceService.hasDataSource(datasourceName);

			String status = isEnabled && isInitialized ? "✓ INSTANTIATED"
					: isEnabled && !isInitialized ? "✗ FAILED" : "○ DISABLED";

			summary.append(String.format("│ Datasource: %-12s │ Type: %-8s │ Status: %-12s │ URL: %-35s │\n",
					datasourceName, type != null ? type : "N/A", status, url != null ? url : "N/A"));

			if (isEnabled && isInitialized) {
				initializedCount++;
			}
			else if (!isEnabled) {
				disabledCount++;
			}
		}

		summary.append("-".repeat(100)).append("\n");
		summary.append(String.format("│ SUMMARY: Total=%d, Instantiated=%d, Disabled=%d, Failed=%d │\n", totalConfigs,
				initializedCount, disabledCount, totalConfigs - initializedCount - disabledCount));
		summary.append("=".repeat(100)).append("\n");
		summary.append("\n");

		log.info(summary.toString());
	}

}
