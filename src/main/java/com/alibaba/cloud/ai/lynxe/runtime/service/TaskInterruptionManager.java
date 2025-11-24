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
import java.util.List;
import java.util.Optional;

/**
 * Service for managing task interruption using database-driven coordination This approach
 * works in distributed/multi-machine environments where agents run on stateless machines
 */
@Service
@Transactional
public class TaskInterruptionManager {

	private static final Logger logger = LoggerFactory.getLogger(TaskInterruptionManager.class);

	@Autowired
	private RootTaskManagerRepository rootTaskManagerRepository;

	/**
	 * Check if a task should be interrupted based on database state
	 * @param rootPlanId The root plan ID
	 * @return true if task should be interrupted, false otherwise
	 */
	@Transactional(readOnly = true)
	public boolean shouldInterruptTask(String rootPlanId) {
		Optional<RootTaskManagerEntity> taskEntity = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (taskEntity.isPresent()) {
			RootTaskManagerEntity.DesiredTaskState desiredState = taskEntity.get().getDesiredTaskState();
			boolean shouldInterrupt = (desiredState == RootTaskManagerEntity.DesiredTaskState.STOP
					|| desiredState == RootTaskManagerEntity.DesiredTaskState.CANCEL
					|| desiredState == RootTaskManagerEntity.DesiredTaskState.PAUSE);

			if (shouldInterrupt) {
				logger.debug("Task {} should be interrupted due to desired state: {}", rootPlanId, desiredState);
			}
			return shouldInterrupt;
		}

		logger.debug("No task entity found for planId: {}, assuming no interruption needed", rootPlanId);
		return false;
	}

	/**
	 * Check if a task is currently running (exists in database with START state)
	 * @param rootPlanId The root plan ID
	 * @return true if task is running, false otherwise
	 */
	@Transactional(readOnly = true)
	public boolean isTaskRunning(String rootPlanId) {
		Optional<RootTaskManagerEntity> taskEntity = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (taskEntity.isPresent()) {
			RootTaskManagerEntity.DesiredTaskState desiredState = taskEntity.get().getDesiredTaskState();
			boolean isRunning = (desiredState == RootTaskManagerEntity.DesiredTaskState.START
					|| desiredState == RootTaskManagerEntity.DesiredTaskState.RESUME);

			logger.debug("Task {} running status: {} (desired state: {})", rootPlanId, isRunning, desiredState);
			return isRunning;
		}

		logger.debug("No task entity found for planId: {}, assuming not running", rootPlanId);
		return false;
	}

	/**
	 * Mark a task for interruption by updating its desired state
	 * @param rootPlanId The root plan ID
	 * @param desiredState The desired interruption state (STOP, CANCEL, PAUSE)
	 * @return true if task was marked for interruption, false if task not found
	 */
	public boolean markTaskForInterruption(String rootPlanId, RootTaskManagerEntity.DesiredTaskState desiredState) {
		Optional<RootTaskManagerEntity> taskEntity = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (taskEntity.isPresent()) {
			RootTaskManagerEntity task = taskEntity.get();
			task.setDesiredTaskState(desiredState);
			task.setLastUpdated(LocalDateTime.now());

			// Set end time if transitioning to STOP, CANCEL, or PAUSE
			if ((desiredState == RootTaskManagerEntity.DesiredTaskState.STOP
					|| desiredState == RootTaskManagerEntity.DesiredTaskState.CANCEL
					|| desiredState == RootTaskManagerEntity.DesiredTaskState.PAUSE) && task.getEndTime() == null) {
				task.setEndTime(LocalDateTime.now());
			}

			rootTaskManagerRepository.save(task);
			logger.info("Marked task {} for interruption with state: {}", rootPlanId, desiredState);
			return true;
		}
		else {
			logger.warn("Task not found for planId: {} when marking for interruption", rootPlanId);
			return false;
		}
	}

	/**
	 * Stop a task by marking it for interruption
	 * @param rootPlanId The root plan ID
	 * @return true if task was marked for stop, false if task not found
	 */
	public boolean stopTask(String rootPlanId) {
		return markTaskForInterruption(rootPlanId, RootTaskManagerEntity.DesiredTaskState.STOP);
	}

	/**
	 * Cancel a task by marking it for interruption
	 * @param rootPlanId The root plan ID
	 * @return true if task was marked for cancellation, false if task not found
	 */
	public boolean cancelTask(String rootPlanId) {
		return markTaskForInterruption(rootPlanId, RootTaskManagerEntity.DesiredTaskState.CANCEL);
	}

	/**
	 * Pause a task by marking it for interruption
	 * @param rootPlanId The root plan ID
	 * @return true if task was marked for pause, false if task not found
	 */
	public boolean pauseTask(String rootPlanId) {
		return markTaskForInterruption(rootPlanId, RootTaskManagerEntity.DesiredTaskState.PAUSE);
	}

	/**
	 * Resume a task by updating its desired state
	 * @param rootPlanId The root plan ID
	 * @return true if task was marked for resume, false if task not found
	 */
	public boolean resumeTask(String rootPlanId) {
		Optional<RootTaskManagerEntity> taskEntity = rootTaskManagerRepository.findByRootPlanId(rootPlanId);

		if (taskEntity.isPresent()) {
			RootTaskManagerEntity task = taskEntity.get();
			task.setDesiredTaskState(RootTaskManagerEntity.DesiredTaskState.RESUME);
			task.setLastUpdated(LocalDateTime.now());

			// Clear end time when resuming
			task.setEndTime(null);

			rootTaskManagerRepository.save(task);
			logger.info("Marked task {} for resume", rootPlanId);
			return true;
		}
		else {
			logger.warn("Task not found for planId: {} when marking for resume", rootPlanId);
			return false;
		}
	}

	/**
	 * Get the number of currently running tasks
	 * @return Number of running tasks
	 */
	@Transactional(readOnly = true)
	public int getRunningTaskCount() {
		List<RootTaskManagerEntity> runningTasks = rootTaskManagerRepository
			.findByDesiredTaskState(RootTaskManagerEntity.DesiredTaskState.START);
		return runningTasks.size();
	}

	/**
	 * Get all running task IDs
	 * @return List of root plan IDs that have running tasks
	 */
	@Transactional(readOnly = true)
	public List<String> getRunningTaskIds() {
		List<RootTaskManagerEntity> runningTasks = rootTaskManagerRepository
			.findByDesiredTaskState(RootTaskManagerEntity.DesiredTaskState.START);
		return runningTasks.stream().map(RootTaskManagerEntity::getRootPlanId).toList();
	}

	/**
	 * Get task interruption status
	 * @param rootPlanId The root plan ID
	 * @return The desired task state, or null if task not found
	 */
	@Transactional(readOnly = true)
	public RootTaskManagerEntity.DesiredTaskState getTaskInterruptionStatus(String rootPlanId) {
		Optional<RootTaskManagerEntity> taskEntity = rootTaskManagerRepository.findByRootPlanId(rootPlanId);
		return taskEntity.map(RootTaskManagerEntity::getDesiredTaskState).orElse(null);
	}

	/**
	 * Clean up completed tasks (tasks with end time set) This method can be called
	 * periodically to clean up old completed tasks
	 * @param olderThanDays Number of days to keep completed tasks
	 * @return Number of tasks cleaned up
	 */
	@Transactional
	public int cleanupCompletedTasks(int olderThanDays) {
		LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);

		List<RootTaskManagerEntity> completedTasks = rootTaskManagerRepository.findAll()
			.stream()
			.filter(task -> task.getEndTime() != null && task.getEndTime().isBefore(cutoffDate))
			.toList();

		if (!completedTasks.isEmpty()) {
			rootTaskManagerRepository.deleteAll(completedTasks);
			logger.info("Cleaned up {} completed tasks older than {} days", completedTasks.size(), olderThanDays);
		}

		return completedTasks.size();
	}

}
