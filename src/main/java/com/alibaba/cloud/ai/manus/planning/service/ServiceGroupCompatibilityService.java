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
package com.alibaba.cloud.ai.manus.planning.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;

/**
 * Compatibility service to migrate service_group from coordinator_tools to plan_template
 * This service runs on application startup to ensure backward compatibility
 */
@Service
public class ServiceGroupCompatibilityService {

	private static final Logger log = LoggerFactory.getLogger(ServiceGroupCompatibilityService.class);

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlanTemplateRepository planTemplateRepository;

	/**
	 * Migrate service_group from coordinator_tools to plan_template on application startup
	 */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void migrateServiceGroupOnStartup() {
		log.info("Starting service group compatibility migration...");

		try {
			// Check if service_group column exists in coordinator_tools table
			if (!columnExists("coordinator_tools", "service_group")) {
				log.info("Column 'service_group' does not exist in 'coordinator_tools' table. Skipping migration.");
				return;
			}

			// Migrate service_group values from coordinator_tools to plan_template
			int migratedCount = migrateServiceGroupFromCoordinatorTools();
			log.info("Service group compatibility migration completed. Migrated {} plan templates.", migratedCount);
		}
		catch (Exception e) {
			log.error("Failed to migrate service_group from coordinator_tools to plan_template", e);
		}
	}

	/**
	 * Check if a column exists in a table
	 * @param tableName Table name
	 * @param columnName Column name
	 * @return true if column exists, false otherwise
	 */
	private boolean columnExists(String tableName, String columnName) {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metaData = connection.getMetaData();
			String catalog = connection.getCatalog();
			String schema = connection.getSchema();

			try (ResultSet columns = metaData.getColumns(catalog, schema, tableName, columnName)) {
				return columns.next();
			}
		}
		catch (Exception e) {
			log.warn("Failed to check if column '{}' exists in table '{}': {}", columnName, tableName, e.getMessage());
			return false;
		}
	}

	/**
	 * Migrate service_group values from coordinator_tools to plan_template
	 * Only updates plan_template if its service_group is empty/null and coordinator_tools has a non-empty value
	 * @return Number of plan templates updated
	 */
	private int migrateServiceGroupFromCoordinatorTools() {
		// Query to get all coordinator tools with non-empty service_group
		String query = "SELECT plan_template_id, service_group FROM coordinator_tools "
				+ "WHERE service_group IS NOT NULL AND service_group != '' AND TRIM(service_group) != ''";

		List<ServiceGroupMigration> migrations = jdbcTemplate.query(query, (rs, rowNum) -> {
			ServiceGroupMigration migration = new ServiceGroupMigration();
			migration.planTemplateId = rs.getString("plan_template_id");
			migration.serviceGroup = rs.getString("service_group");
			return migration;
		});

		if (migrations.isEmpty()) {
			log.info("No coordinator tools with service_group found. Nothing to migrate.");
			return 0;
		}

		log.info("Found {} coordinator tools with service_group. Starting migration...", migrations.size());

		int updatedCount = 0;
		for (ServiceGroupMigration migration : migrations) {
			try {
				Optional<PlanTemplate> planTemplateOpt = planTemplateRepository
						.findByPlanTemplateId(migration.planTemplateId);

				if (planTemplateOpt.isPresent()) {
					PlanTemplate planTemplate = planTemplateOpt.get();
					String currentServiceGroup = planTemplate.getServiceGroup();

					// Only update if plan_template.service_group is empty/null and coordinator_tools has a value
					if (isNullOrEmpty(currentServiceGroup) && !isNullOrEmpty(migration.serviceGroup)) {
						planTemplate.setServiceGroup(migration.serviceGroup.trim());
						planTemplate.setUpdateTime(java.time.LocalDateTime.now());
						planTemplateRepository.save(planTemplate);

						// Clear service_group in coordinator_tools after successful migration
						clearServiceGroupInCoordinatorTools(migration.planTemplateId);
						updatedCount++;
						log.debug("Migrated service_group '{}' to plan_template '{}' and cleared from coordinator_tools",
								migration.serviceGroup, migration.planTemplateId);
					}
					else {
						log.debug("Skipped plan_template '{}': service_group already set to '{}'",
								migration.planTemplateId, currentServiceGroup);
					}
				}
				else {
					log.warn("Plan template not found for planTemplateId: {}. Skipping migration.",
							migration.planTemplateId);
				}
			}
			catch (Exception e) {
				log.error("Failed to migrate service_group for plan_template_id '{}': {}", migration.planTemplateId,
						e.getMessage(), e);
			}
		}

		return updatedCount;
	}

	/**
	 * Clear service_group column in coordinator_tools table after migration
	 * @param planTemplateId Plan template ID to identify the coordinator tool
	 */
	private void clearServiceGroupInCoordinatorTools(String planTemplateId) {
		try {
			String updateSql = "UPDATE coordinator_tools SET service_group = NULL WHERE plan_template_id = ?";
			int rowsAffected = jdbcTemplate.update(updateSql, planTemplateId);
			if (rowsAffected > 0) {
				log.debug("Cleared service_group in coordinator_tools for plan_template_id: {}", planTemplateId);
			}
			else {
				log.warn("No rows updated when clearing service_group for plan_template_id: {}", planTemplateId);
			}
		}
		catch (Exception e) {
			log.error("Failed to clear service_group in coordinator_tools for plan_template_id '{}': {}",
					planTemplateId, e.getMessage(), e);
		}
	}

	/**
	 * Check if a string is null or empty (including whitespace)
	 */
	private boolean isNullOrEmpty(String str) {
		return str == null || str.trim().isEmpty();
	}

	/**
	 * Internal class to hold migration data
	 */
	private static class ServiceGroupMigration {

		String planTemplateId;

		String serviceGroup;

	}

}

