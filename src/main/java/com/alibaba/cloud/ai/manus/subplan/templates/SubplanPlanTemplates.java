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
package com.alibaba.cloud.ai.manus.subplan.templates;

import java.util.HashMap;
import java.util.Map;

/**
 * Predefined plan templates for subplan tools
 *
 * Contains all the plan templates that will be automatically created when the application
 * starts
 */
public class SubplanPlanTemplates {

	/**
	 * Get all predefined plan templates
	 * @return Map of template ID to template content
	 */
	public static Map<String, String> getAllPlanTemplates() {
		Map<String, String> templates = new HashMap<>();

		// Content extraction templates
		// templates.put("extract_relevant_content_template",
		// getExtractRelevantContentTemplate());
		// templates.put("extract_relevant_content_template",
		// getExtractRelevantContentTemplateWithDynamicAgent());

		return templates;
	}

}
