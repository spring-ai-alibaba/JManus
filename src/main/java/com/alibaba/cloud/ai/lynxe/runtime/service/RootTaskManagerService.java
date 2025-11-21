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
package com.alibaba.cloud.ai.lynxe.runtime.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.cloud.ai.lynxe.runtime.entity.po.RootTaskManagerEntity;
import com.alibaba.cloud.ai.lynxe.runtime.repository.RootTaskManagerRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing RootTaskManagerEntity operations
 */
@Service
@Transactional
public class RootTaskManagerService {

	private static final Logger logger = LoggerFactory.getLogger(RootTaskManagerService.class);

	@Autowired
	private RootTaskManagerRepository rootTaskManagerRepository;

	/**
	 * Create or update a root task manager entity
	 * @param rootPlanId The root plan ID
	 * @param desiredTaskState The desired task state
	 * @return The created or updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity createOrUpdateTask(String rootPlanId,
			RootTaskManagerEntity.DesiredTaskState desiredTaskState) {
		Optional<RootTaskManagerEntity> existingTask = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (existingTask.isPresent()) {
			RootTaskManagerEntity task = existingTask.get();
			task.setDesiredTaskState(desiredTaskState);
			task.setLastUpdated(LocalDateTime.now());

			// Set start time if transitioning to START
			if (desiredTaskState == RootTaskManagerEntity.DesiredTaskState.START && task.getStartTime() == null) {
				task.setStartTime(LocalDateTime.now());
			}

			// Set end time if transitioning to STOP, CANCEL, or PAUSE
			if ((desiredTaskState == RootTaskManagerEntity.DesiredTaskState.STOP
					|| desiredTaskState == RootTaskManagerEntity.DesiredTaskState.CANCEL
					|| desiredTaskState == RootTaskManagerEntity.DesiredTaskState.PAUSE) && task.getEndTime() == null) {
				task.setEndTime(LocalDateTime.now());
			}

			logger.info("Updated task state for planId {} to {}", rootPlanId, desiredTaskState);
			return rootTaskManagerRepository.save(task);
		}
		else {
			RootTaskManagerEntity newTask = new RootTaskManagerEntity(rootPlanId);
			newTask.setDesiredTaskState(desiredTaskState);

			// Set start time if creating with START state
			if (desiredTaskState == RootTaskManagerEntity.DesiredTaskState.START) {
				newTask.setStartTime(LocalDateTime.now());
			}

			logger.info("Created new task for planId {} with state {}", rootPlanId, desiredTaskState);
			return rootTaskManagerRepository.save(newTask);
		}
	}

	/**
	 * Get task by root plan ID
	 * @param rootPlanId The root plan ID
	 * @return Optional RootTaskManagerEntity
	 */
	@Transactional(readOnly = true)
	public Optional<RootTaskManagerEntity> getTaskByRootPlanId(String rootPlanId) {
		return rootTaskManagerRepository.findByRootPlanId(rootPlanId);
	}

	/**
	 * Check if task exists by root plan ID
	 * @param rootPlanId The root plan ID
	 * @return true if task exists, false otherwise
	 */
	@Transactional(readOnly = true)
	public boolean taskExists(String rootPlanId) {
		return rootTaskManagerRepository.existsByRootPlanId(rootPlanId);
	}

	/**
	 * Set task state to STOP
	 * @param rootPlanId The root plan ID
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity stopTask(String rootPlanId) {
		return createOrUpdateTask(rootPlanId, RootTaskManagerEntity.DesiredTaskState.STOP);
	}

	/**
	 * Set task state to CANCEL
	 * @param rootPlanId The root plan ID
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity cancelTask(String rootPlanId) {
		return createOrUpdateTask(rootPlanId, RootTaskManagerEntity.DesiredTaskState.CANCEL);
	}

	/**
	 * Set task state to PAUSE
	 * @param rootPlanId The root plan ID
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity pauseTask(String rootPlanId) {
		return createOrUpdateTask(rootPlanId, RootTaskManagerEntity.DesiredTaskState.PAUSE);
	}

	/**
	 * Set task state to RESUME
	 * @param rootPlanId The root plan ID
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity resumeTask(String rootPlanId) {
		return createOrUpdateTask(rootPlanId, RootTaskManagerEntity.DesiredTaskState.RESUME);
	}

	/**
	 * Delete task by root plan ID
	 * @param rootPlanId The root plan ID
	 */
	public void deleteTask(String rootPlanId) {
		rootTaskManagerRepository.deleteByRootPlanId(rootPlanId);
		logger.info("Deleted task for planId {}", rootPlanId);
	}

	/**
	 * Update task result
	 * @param rootPlanId The root plan ID
	 * @param taskResult The task result
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity updateTaskResult(String rootPlanId, String taskResult) {
		Optional<RootTaskManagerEntity> existingTask = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (existingTask.isPresent()) {
			RootTaskManagerEntity task = existingTask.get();
			task.setTaskResult(taskResult);
			task.setLastUpdated(LocalDateTime.now());
			logger.info("Updated task result for planId {}", rootPlanId);
			return rootTaskManagerRepository.save(task);
		}
		else {
			logger.warn("Task not found for planId {} when updating result", rootPlanId);
			return null;
		}
	}

	/**
	 * Complete a task by setting its result, state to STOP, and end time
	 * @param rootPlanId The root plan ID
	 * @param taskResult The task result
	 * @param isSuccess Whether the task completed successfully (true) or with failure
	 * (false)
	 * @return The updated RootTaskManagerEntity
	 */
	public RootTaskManagerEntity completeTask(String rootPlanId, String taskResult, boolean isSuccess) {
		Optional<RootTaskManagerEntity> existingTask = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (existingTask.isPresent()) {
			RootTaskManagerEntity task = existingTask.get();
			task.setTaskResult(taskResult);
			task.setDesiredTaskState(isSuccess ? RootTaskManagerEntity.DesiredTaskState.STOP
					: RootTaskManagerEntity.DesiredTaskState.CANCEL);
			task.setEndTime(LocalDateTime.now());
			task.setLastUpdated(LocalDateTime.now());
			logger.info("Completed task for planId {} with state {}", rootPlanId,
					isSuccess ? "STOP (success)" : "CANCEL (failed)");
			return rootTaskManagerRepository.save(task);
		}
		else {
			logger.warn("Task not found for planId {} when completing task", rootPlanId);
			return null;
		}
	}

}
