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

/**
 * Service for checking task interruption signals during agent execution This service is
 * used by DynamicAgent instances running on different machines to check if they should
 * interrupt their execution based on database state
 */
@Service
@Transactional(readOnly = true)
public class TaskInterruptionCheckerService {

	private static final Logger logger = LoggerFactory.getLogger(TaskInterruptionCheckerService.class);

	@Autowired
	private TaskInterruptionManager taskInterruptionManager;

	/**
	 * Check if the current task should be interrupted This method should be called
	 * periodically during agent execution
	 * @param rootPlanId The root plan ID
	 * @return true if task should be interrupted, false otherwise
	 */
	public boolean shouldInterruptExecution(String rootPlanId) {
		if (rootPlanId == null || rootPlanId.trim().isEmpty()) {
			logger.debug("Root plan ID is null or empty, no interruption check needed");
			return false;
		}

		try {
			boolean shouldInterrupt = taskInterruptionManager.shouldInterruptTask(rootPlanId);

			if (shouldInterrupt) {
				RootTaskManagerEntity.DesiredTaskState desiredState = taskInterruptionManager
					.getTaskInterruptionStatus(rootPlanId);
				logger.info("Task {} should be interrupted due to desired state: {}", rootPlanId, desiredState);
			}

			return shouldInterrupt;
		}
		catch (Exception e) {
			logger.error("Error checking interruption status for planId: {}", rootPlanId, e);
			// In case of error, don't interrupt to avoid breaking execution
			return false;
		}
	}

	/**
	 * Check if the current task should be interrupted and throw an exception if so This
	 * method can be used to immediately stop execution when interruption is detected
	 * @param rootPlanId The root plan ID
	 * @throws TaskInterruptedException if task should be interrupted
	 */
	public void checkAndThrowIfInterrupted(String rootPlanId) throws TaskInterruptedException {
		if (shouldInterruptExecution(rootPlanId)) {
			RootTaskManagerEntity.DesiredTaskState desiredState = taskInterruptionManager
				.getTaskInterruptionStatus(rootPlanId);
			throw new TaskInterruptedException("Task " + rootPlanId + " was interrupted with state: " + desiredState);
		}
	}

	/**
	 * Get the current interruption status for a task
	 * @param rootPlanId The root plan ID
	 * @return The desired task state, or null if task not found
	 */
	public RootTaskManagerEntity.DesiredTaskState getInterruptionStatus(String rootPlanId) {
		try {
			return taskInterruptionManager.getTaskInterruptionStatus(rootPlanId);
		}
		catch (Exception e) {
			logger.error("Error getting interruption status for planId: {}", rootPlanId, e);
			return null;
		}
	}

	/**
	 * Custom exception for task interruption
	 */
	public static class TaskInterruptedException extends RuntimeException {

		public TaskInterruptedException(String message) {
			super(message);
		}

		public TaskInterruptedException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
