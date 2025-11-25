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
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.lynxe.runtime.entity.po.RootTaskManagerEntity;

/**
 * Utility component for integrating task interruption checking into agent execution This
 * component provides convenient methods for agents to check interruption signals during
 * their execution loops
 */
@Component
public class AgentInterruptionHelper {

	private static final Logger logger = LoggerFactory.getLogger(AgentInterruptionHelper.class);

	@Autowired
	private TaskInterruptionCheckerService interruptionCheckerService;

	/**
	 * Check if execution should be interrupted and handle it appropriately This method
	 * should be called at key points during agent execution
	 * @param rootPlanId The root plan ID
	 * @return true if execution should continue, false if interrupted
	 */
	public boolean checkInterruptionAndContinue(String rootPlanId) {
		try {
			interruptionCheckerService.checkAndThrowIfInterrupted(rootPlanId);
			return true; // Continue execution
		}
		catch (TaskInterruptionCheckerService.TaskInterruptedException e) {
			logger.info("Agent execution interrupted for planId: {} - {}", rootPlanId, e.getMessage());
			return false; // Stop execution
		}
	}

	/**
	 * Check if execution should be interrupted and throw exception if so This method can
	 * be used when you want to immediately stop execution
	 * @param rootPlanId The root plan ID
	 * @throws TaskInterruptionCheckerService.TaskInterruptedException if interrupted
	 */
	public void checkInterruptionAndThrow(String rootPlanId)
			throws TaskInterruptionCheckerService.TaskInterruptedException {
		interruptionCheckerService.checkAndThrowIfInterrupted(rootPlanId);
	}

	/**
	 * Check interruption status without throwing exception
	 * @param rootPlanId The root plan ID
	 * @return true if should interrupt, false otherwise
	 */
	public boolean isInterruptionRequested(String rootPlanId) {
		return interruptionCheckerService.shouldInterruptExecution(rootPlanId);
	}

	/**
	 * Get the current interruption status
	 * @param rootPlanId The root plan ID
	 * @return The desired task state, or null if not found
	 */
	public RootTaskManagerEntity.DesiredTaskState getInterruptionStatus(String rootPlanId) {
		return interruptionCheckerService.getInterruptionStatus(rootPlanId);
	}

}
