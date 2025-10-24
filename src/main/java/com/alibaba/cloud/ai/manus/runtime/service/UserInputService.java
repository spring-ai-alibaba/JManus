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
package com.alibaba.cloud.ai.manus.runtime.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.manus.runtime.entity.vo.UserInputWaitState;
import com.alibaba.cloud.ai.manus.tool.FormInputTool;

@Service
public class UserInputService implements IUserInputService {

	private static final Logger log = LoggerFactory.getLogger(UserInputService.class);

	private final ConcurrentHashMap<String, FormInputTool> formInputToolMap = new ConcurrentHashMap<>();

	// Lock for exclusive form storage per root plan
	private final ReentrantLock formStorageLock = new ReentrantLock();

	/**
	 * Store a form input tool with exclusive access per root plan. Only one form can be
	 * stored at a time per root plan. If another form is already stored, this method will
	 * wait using spin lock.
	 * @param planId The root plan ID
	 * @param tool The form input tool to store
	 * @param requesterPlanId The sub-plan ID requesting to store the tool
	 * @return true if successfully stored, false if interrupted or timeout
	 */
	public boolean storeFormInputToolExclusive(String planId, FormInputTool tool, String requesterPlanId) {
		log.info("Sub-plan {} attempting to store form for root plan {}", requesterPlanId, planId);

		// Try to acquire lock with timeout
		try {
			if (formStorageLock.tryLock(5, TimeUnit.SECONDS)) {
				try {
					// Check if there's already a form for this root plan
					FormInputTool existingTool = formInputToolMap.get(planId);
					if (existingTool != null
							&& existingTool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) {
						String existingOwner = getFormOwner(existingTool);
						if (existingOwner != null && !existingOwner.equals(requesterPlanId)) {
							log.info("Root plan {} already has form from sub-plan {}. Sub-plan {} will wait.", planId,
									existingOwner, requesterPlanId);

							// Wait for existing form to complete using spin lock
							waitForFormCompletion(existingTool, requesterPlanId);

							// After waiting, remove the completed form
							formInputToolMap.remove(planId);
							log.info("Removed completed form from root plan {}", planId);
						}
					}

					// Now store our form
					formInputToolMap.put(planId, tool);
					log.info("Successfully stored form for root plan {} from sub-plan {}", planId, requesterPlanId);
					return true;

				}
				finally {
					formStorageLock.unlock();
				}
			}
			else {
				log.warn("Failed to acquire lock for storing form. Sub-plan {} timed out.", requesterPlanId);
				return false;
			}
		}
		catch (InterruptedException e) {
			log.warn("Interrupted while waiting for lock. Sub-plan {} interrupted.", requesterPlanId);
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Wait for a form to complete using spin lock mechanism
	 * @param existingTool The existing form tool to wait for
	 * @param requesterPlanId The sub-plan ID waiting for completion
	 */
	private void waitForFormCompletion(FormInputTool existingTool, String requesterPlanId) {
		log.info("Sub-plan {} entering spin lock to wait for form completion", requesterPlanId);
		long startTime = System.currentTimeMillis();
		long timeoutMs = 300000; // 5 minutes timeout
		long checkIntervalMs = 100; // Check every 100ms

		while (existingTool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) {
			long currentTime = System.currentTimeMillis();
			if (currentTime - startTime > timeoutMs) {
				log.warn("Timeout waiting for form completion. Sub-plan {} giving up.", requesterPlanId);
				break;
			}

			try {
				TimeUnit.MILLISECONDS.sleep(checkIntervalMs);
			}
			catch (InterruptedException e) {
				log.warn("Interrupted while waiting for form completion. Sub-plan {} interrupted.", requesterPlanId);
				Thread.currentThread().interrupt();
				break;
			}
		}

		log.info("Sub-plan {} finished waiting for form completion. Final state: {}", requesterPlanId,
				existingTool.getInputState());
	}

	/**
	 * Get the owner (sub-plan ID) of a form input tool
	 * @param formInputTool The form input tool to check
	 * @return The plan ID that owns this form, or null if not found
	 */
	private String getFormOwner(FormInputTool formInputTool) {
		if (formInputTool == null) {
			return null;
		}

		// Use the currentPlanId from AbstractBaseTool superclass
		return formInputTool.getCurrentPlanId();
	}

	public FormInputTool getFormInputTool(String planId) {
		return formInputToolMap.get(planId);
	}

	public void removeFormInputTool(String planId) {
		formInputToolMap.remove(planId);
	}

	public UserInputWaitState createUserInputWaitState(String planId, String title, FormInputTool formInputTool) {
		UserInputWaitState waitState = new UserInputWaitState(planId, title, true);
		if (formInputTool != null) {
			// Assume FormInputTool has methods getFormDescription() and getFormInputs()
			// to get form information
			// This requires FormInputTool class to support these methods, or other ways
			// to get this information
			// This is indicative code, specific implementation depends on the actual
			// structure of FormInputTool
			FormInputTool.UserFormInput latestFormInput = formInputTool.getLatestUserFormInput();
			if (latestFormInput != null) {
				// Use title from form input if available, otherwise use the provided
				// title
				if (latestFormInput.getTitle() != null && !latestFormInput.getTitle().isEmpty()) {
					waitState.setTitle(latestFormInput.getTitle());
				}
				waitState.setFormDescription(latestFormInput.getDescription());
				if (latestFormInput.getInputs() != null) {
					List<Map<String, String>> formInputsForState = latestFormInput.getInputs()
						.stream()
						.map(inputItem -> {
							Map<String, String> inputMap = new HashMap<>();
							inputMap.put("label", inputItem.getLabel());
							inputMap.put("value", inputItem.getValue() != null ? inputItem.getValue() : "");
							if (inputItem.getName() != null) {
								inputMap.put("name", inputItem.getName());
							}
							if (inputItem.getType() != null) {
								inputMap.put("type", inputItem.getType().getValue());
							}
							if (inputItem.getPlaceholder() != null) {
								inputMap.put("placeholder", inputItem.getPlaceholder());
							}
							if (inputItem.getRequired() != null) {
								inputMap.put("required", inputItem.getRequired().toString());
							}
							if (inputItem.getOptions() != null && !inputItem.getOptions().isEmpty()) {
								inputMap.put("options", String.join(",", inputItem.getOptions()));
							}

							return inputMap;
						})
						.collect(Collectors.toList());
					waitState.setFormInputs(formInputsForState);
				}
			}
		}
		return waitState;
	}

	public UserInputWaitState getWaitState(String planId) {
		FormInputTool tool = getFormInputTool(planId);
		if (tool != null && tool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) { // Corrected
			// to
			// use
			// getInputState
			// and
			// InputState
			// Assuming a default title or retrieve from tool if available
			return createUserInputWaitState(planId, "Awaiting user input.", tool);
		}
		return null; // Or a UserInputWaitState with waiting=false
	}

	public boolean submitUserInputs(String planId, Map<String, String> inputs) { // Changed
		// to
		// return
		// boolean
		FormInputTool formInputTool = getFormInputTool(planId);
		if (formInputTool != null && formInputTool.getInputState() == FormInputTool.InputState.AWAITING_USER_INPUT) { // Corrected
			// to
			// use
			// getInputState
			// and
			// InputState
			List<FormInputTool.InputItem> inputItems = inputs.entrySet().stream().map(entry -> {
				return new FormInputTool.InputItem(entry.getKey(), entry.getValue());
			}).collect(Collectors.toList());

			formInputTool.setUserFormInputValues(inputItems);
			formInputTool.markUserInputReceived();
			return true;
		}
		else {
			if (formInputTool == null) {
				throw new IllegalArgumentException("FormInputTool not found for planId: " + planId);
			}
			// If tool exists but not awaiting input
			return false;
		}
	}

}
