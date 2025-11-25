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
package com.alibaba.cloud.ai.lynxe.runtime.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.alibaba.cloud.ai.lynxe.runtime.entity.po.RootTaskManagerEntity;

import java.util.Optional;

/**
 * Repository for managing RootTaskManagerEntity operations
 */
@Repository
public interface RootTaskManagerRepository extends JpaRepository<RootTaskManagerEntity, Long> {

	/**
	 * Find RootTaskManagerEntity by root plan ID
	 * @param rootPlanId The root plan ID
	 * @return Optional RootTaskManagerEntity
	 */
	Optional<RootTaskManagerEntity> findByRootPlanId(String rootPlanId);

	/**
	 * Check if a task exists by root plan ID
	 * @param rootPlanId The root plan ID
	 * @return true if task exists, false otherwise
	 */
	boolean existsByRootPlanId(String rootPlanId);

	/**
	 * Delete task by root plan ID
	 * @param rootPlanId The root plan ID
	 */
	void deleteByRootPlanId(String rootPlanId);

	/**
	 * Find tasks by desired task state
	 * @param desiredTaskState The desired task state
	 * @return List of RootTaskManagerEntity
	 */
	@Query("SELECT r FROM RootTaskManagerEntity r WHERE r.desiredTaskState = :desiredTaskState")
	java.util.List<RootTaskManagerEntity> findByDesiredTaskState(
			@Param("desiredTaskState") RootTaskManagerEntity.DesiredTaskState desiredTaskState);

}
