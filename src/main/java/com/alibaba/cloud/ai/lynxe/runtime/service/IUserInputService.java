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

import java.util.Map;

import com.alibaba.cloud.ai.lynxe.runtime.entity.vo.UserInputWaitState;
import com.alibaba.cloud.ai.lynxe.tool.FormInputTool;

/**
 * User input service interface managing user input related functions
 */
public interface IUserInputService {

	/**
	 * Store a form input tool with exclusive access per root plan. Only one form can be
	 * stored at a time per root plan. If another form is already stored, this method will
	 * wait using spin lock.
	 * @param planId The root plan ID
	 * @param tool The form input tool to store
	 * @param requesterPlanId The sub-plan ID requesting to store the tool
	 * @return true if successfully stored, false if interrupted or timeout
	 */
	boolean storeFormInputToolExclusive(String planId, FormInputTool tool, String requesterPlanId);

	/**
	 * Get form input tool
	 * @param planId Plan ID
	 * @return Form input tool
	 */
	FormInputTool getFormInputTool(String planId);

	/**
	 * Remove form input tool
	 * @param planId Plan ID
	 */
	void removeFormInputTool(String planId);

	/**
	 * Create user input waiting state
	 * @param planId Plan ID
	 * @param title Title
	 * @param formInputTool Form input tool
	 * @return User input waiting state
	 */
	UserInputWaitState createUserInputWaitState(String planId, String title, FormInputTool formInputTool);

	/**
	 * Get waiting state
	 * @param planId Plan ID
	 * @return User input waiting state
	 */
	UserInputWaitState getWaitState(String planId);

	/**
	 * Submit user input
	 * @param planId Plan ID
	 * @param inputs Input data
	 * @return Whether submission was successful
	 */
	boolean submitUserInputs(String planId, Map<String, String> inputs);

}
