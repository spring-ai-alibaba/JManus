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
package com.alibaba.cloud.ai.lynxe.planning.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.alibaba.cloud.ai.lynxe.planning.model.po.FuncAgentToolEntity;

/**
 * Coordinator Tool Data Access Layer
 */
@Repository
public interface FuncAgentToolRepository extends JpaRepository<FuncAgentToolEntity, Long> {

	/**
	 * Find by plan template ID
	 */
	List<FuncAgentToolEntity> findByPlanTemplateId(String planTemplateId);

	/**
	 * Find by tool name
	 */
	List<FuncAgentToolEntity> findByToolName(String toolName);

	/**
	 * Find by service group and tool name (respects unique constraint)
	 */
	Optional<FuncAgentToolEntity> findByServiceGroupAndToolName(String serviceGroup, String toolName);

	/**
	 * Delete by plan template ID
	 */
	void deleteByPlanTemplateId(String planTemplateId);

	/**
	 * Delete by tool name
	 */
	void deleteByToolName(String toolName);

}
