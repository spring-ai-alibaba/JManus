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
package com.alibaba.cloud.ai.manus.planning.initializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.planning.service.PlanTemplateInitializationService;

/**
 * Startup initializer for plan templates from startup-plans directory
 */
@Component
public class PlanTemplateStartupInitializer {

	private static final Logger log = LoggerFactory.getLogger(PlanTemplateStartupInitializer.class);

	@Autowired
	private PlanTemplateInitializationService planTemplateInitializationService;

	@Value("${namespace.value}")
	private String namespace;

	/**
	 * Initialize startup plan templates when application is ready
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void initializeStartupPlanTemplates() {
		log.info("Starting startup plan templates initialization for namespace: {}", namespace);

		try {
			// Initialize plan templates for the current namespace
			planTemplateInitializationService.initializePlanTemplatesForNamespace(namespace);
			log.info("Successfully initialized startup plan templates for namespace: {}", namespace);
		}
		catch (Exception e) {
			log.error("Failed to initialize startup plan templates for namespace: {}", namespace, e);
		}
	}

}
