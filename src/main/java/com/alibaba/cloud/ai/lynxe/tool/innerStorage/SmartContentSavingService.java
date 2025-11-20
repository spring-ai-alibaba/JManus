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
package com.alibaba.cloud.ai.lynxe.tool.innerStorage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;

/**
 * Internal file storage service for storing intermediate data in MapReduce processes
 */
@Service
public class SmartContentSavingService implements ISmartContentSavingService {

	private static final Logger log = LoggerFactory.getLogger(SmartContentSavingService.class);

	private final LynxeProperties lynxeProperties;

	public SmartContentSavingService(LynxeProperties lynxeProperties) {
		this.lynxeProperties = lynxeProperties;
	}

	public LynxeProperties getLynxeProperties() {
		return lynxeProperties;
	}

	/**
	 * Smart processing result class
	 */
	public static class SmartProcessResult {

		private final String fileName;

		private final String summary;

		public SmartProcessResult(String fileName, String summary) {
			this.fileName = fileName;
			this.summary = summary;
		}

		public String getFileName() {
			return fileName;
		}

		public String getSummary() {
			return summary;
		}

		@Override
		public String toString() {
			return String.format("SmartProcessResult{fileName='%s', summary='%s'}", fileName, summary);
		}

	}

	/**
	 * Intelligently process content, automatically store and return summary if content is
	 * too long
	 * @param planId Plan ID
	 * @param content Content
	 * @param callingMethod Calling method name
	 * @return Processing result containing filename and summary
	 */
	public SmartProcessResult processContent(String planId, String content, String callingMethod) {
		if (planId == null || content == null) {
			log.warn("processContent called with null parameters: planId={}, content={}, callingMethod={}", planId,
					content, callingMethod);
			return new SmartProcessResult(null, content != null ? content : "No content available");
		}

		// Check if content is empty
		if (content.trim().isEmpty()) {
			log.warn("processContent called with empty content: planId={}, callingMethod={}", planId, callingMethod);
			return new SmartProcessResult(null, "");
		}

		// Return content directly without smart processing
		log.debug("Returning content directly for plan {}", planId);
		return new SmartProcessResult(null, content != null && !content.trim().isEmpty() ? content : "");
	}

}
