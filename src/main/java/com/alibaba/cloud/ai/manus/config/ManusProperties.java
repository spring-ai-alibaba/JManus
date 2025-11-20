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

package com.alibaba.cloud.ai.manus.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.config.entity.ConfigInputType;

@Component
@ConfigurationProperties(prefix = "manus")
public class ManusProperties implements IManusProperties {

	@Lazy
	@Autowired
	private IConfigService configService;

	// Browser Settings
	// Begin-------------------------------------------------------------------------------------------

	@ConfigProperty(group = "manus", subGroup = "browser", key = "headless", path = "manus.browser.headless",
			description = "manus.browser.headless.description", defaultValue = "false",
			inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.browser.headless.option.true"),
					@ConfigOption(value = "false", label = "manus.browser.headless.option.false") })
	private volatile Boolean browserHeadless;

	public Boolean getBrowserHeadless() {
		String configPath = "manus.browser.headless";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			browserHeadless = Boolean.valueOf(value);
		}
		return browserHeadless;
	}

	public void setBrowserHeadless(Boolean browserHeadless) {
		this.browserHeadless = browserHeadless;
	}

	@ConfigProperty(group = "manus", subGroup = "browser", key = "requestTimeout",
			path = "manus.browser.requestTimeout", description = "manus.browser.requestTimeout.description",
			defaultValue = "180", inputType = ConfigInputType.NUMBER)
	private volatile Integer browserRequestTimeout;

	public Integer getBrowserRequestTimeout() {
		String configPath = "manus.browser.requestTimeout";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			browserRequestTimeout = Integer.valueOf(value);
		}
		return browserRequestTimeout;
	}

	public void setBrowserRequestTimeout(Integer browserRequestTimeout) {
		this.browserRequestTimeout = browserRequestTimeout;
	}

	@ConfigProperty(group = "manus", subGroup = "general", key = "debugDetail", path = "manus.general.debugDetail",
			description = "manus.general.debugDetail.description", defaultValue = "false",
			inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.general.debugDetail.option.true"),
					@ConfigOption(value = "false", label = "manus.general.debugDetail.option.false") })
	private volatile Boolean debugDetail;

	public Boolean getDebugDetail() {
		String configPath = "manus.general.debugDetail";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			debugDetail = Boolean.valueOf(value);
		}
		return debugDetail;
	}

	public void setDebugDetail(Boolean debugDetail) {
		this.debugDetail = debugDetail;
	}

	// Browser Settings
	// End---------------------------------------------------------------------------------------------

	// General Settings
	// Begin---------------------------------------------------------------------------------------
	@ConfigProperty(group = "manus", subGroup = "general", key = "openBrowser", path = "manus.general.openBrowser",
			description = "manus.general.openBrowser.description", defaultValue = "true",
			inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.general.openBrowser.option.true"),
					@ConfigOption(value = "false", label = "manus.general.openBrowser.option.false") })
	private volatile Boolean openBrowserAuto;

	public Boolean getOpenBrowserAuto() {
		String configPath = "manus.general.openBrowser";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			openBrowserAuto = Boolean.valueOf(value);
		}
		// Default to true if not configured
		if (openBrowserAuto == null) {
			openBrowserAuto = true;
		}
		return openBrowserAuto;
	}

	public void setOpenBrowserAuto(Boolean openBrowserAuto) {
		this.openBrowserAuto = openBrowserAuto;
	}

	@ConfigProperty(group = "manus", subGroup = "browser", key = "enableShortUrl",
			path = "manus.browser.enableShortUrl", description = "manus.browser.enableShortUrl.description",
			defaultValue = "true", inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.browser.enableShortUrl.option.true"),
					@ConfigOption(value = "false", label = "manus.browser.enableShortUrl.option.false") })
	private volatile Boolean enableShortUrl;

	public Boolean getEnableShortUrl() {
		String configPath = "manus.browser.enableShortUrl";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			enableShortUrl = Boolean.valueOf(value);
		}
		// Default to true if not configured
		if (enableShortUrl == null) {
			enableShortUrl = true;
		}
		return enableShortUrl;
	}

	public void setEnableShortUrl(Boolean enableShortUrl) {
		this.enableShortUrl = enableShortUrl;
	}

	// General Settings
	// End-----------------------------------------------------------------------------------------

	// Agent Settings
	// Begin---------------------------------------------------------------------------------------------

	@ConfigProperty(group = "manus", subGroup = "agent", key = "maxSteps", path = "manus.maxSteps",
			description = "manus.agent.maxSteps.description", defaultValue = "200", inputType = ConfigInputType.NUMBER)
	private volatile Integer maxSteps;

	public Integer getMaxSteps() {
		String configPath = "manus.maxSteps";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			maxSteps = Integer.valueOf(value);
		}
		return maxSteps;
	}

	public void setMaxSteps(Integer maxSteps) {
		this.maxSteps = maxSteps;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "userInputTimeout",
			path = "manus.agent.userInputTimeout", description = "manus.agent.userInputTimeout.description",
			defaultValue = "300", inputType = ConfigInputType.NUMBER)
	private volatile Integer userInputTimeout;

	public Integer getUserInputTimeout() {
		String configPath = "manus.agent.userInputTimeout";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			userInputTimeout = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (userInputTimeout == null) {
			// Attempt to parse the default value specified in the annotation,
			// or use a hardcoded default if parsing fails or is complex to retrieve here.
			// For simplicity, directly using the intended default.
			userInputTimeout = 300;
		}
		return userInputTimeout;
	}

	public void setUserInputTimeout(Integer userInputTimeout) {
		this.userInputTimeout = userInputTimeout;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "maxMemory", path = "manus.agent.maxMemory",
			description = "manus.agent.maxMemory.description", defaultValue = "1000",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer maxMemory;

	public Integer getMaxMemory() {
		String configPath = "manus.agent.maxMemory";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			maxMemory = Integer.valueOf(value);
		}
		if (maxMemory == null) {
			maxMemory = 1000;
		}
		return maxMemory;
	}

	public void setMaxMemory(Integer maxMemory) {
		this.maxMemory = maxMemory;
	}

	@ConfigProperty(group = "manus", subGroup = "general", key = "enableConversationMemory",
			path = "manus.general.enableConversationMemory",
			description = "manus.general.enableConversationMemory.description", defaultValue = "true",
			inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.general.enableConversationMemory.option.true"),
					@ConfigOption(value = "false", label = "manus.general.enableConversationMemory.option.false") })
	private volatile Boolean enableConversationMemory;

	public Boolean getEnableConversationMemory() {
		String configPath = "manus.general.enableConversationMemory";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			enableConversationMemory = Boolean.valueOf(value);
		}
		// Default to true if not configured
		if (enableConversationMemory == null) {
			enableConversationMemory = true;
		}
		return enableConversationMemory;
	}

	public void setEnableConversationMemory(Boolean enableConversationMemory) {
		this.enableConversationMemory = enableConversationMemory;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "conversationMemoryMaxChars",
			path = "manus.agent.conversationMemoryMaxChars",
			description = "manus.agent.conversationMemoryMaxChars.description", defaultValue = "30000",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer conversationMemoryMaxChars;

	public Integer getConversationMemoryMaxChars() {
		String configPath = "manus.agent.conversationMemoryMaxChars";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			conversationMemoryMaxChars = Integer.valueOf(value);
		}
		if (conversationMemoryMaxChars == null) {
			conversationMemoryMaxChars = 30000;
		}
		return conversationMemoryMaxChars;
	}

	public void setConversationMemoryMaxChars(Integer conversationMemoryMaxChars) {
		this.conversationMemoryMaxChars = conversationMemoryMaxChars;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "parallelToolCalls",
			path = "manus.agent.parallelToolCalls", description = "manus.agent.parallelToolCalls.description",
			defaultValue = "false", inputType = ConfigInputType.CHECKBOX,
			options = { @ConfigOption(value = "true", label = "manus.agent.parallelToolCalls.option.true"),
					@ConfigOption(value = "false", label = "manus.agent.parallelToolCalls.option.false") })
	private volatile Boolean parallelToolCalls;

	public Boolean getParallelToolCalls() {
		String configPath = "manus.agent.parallelToolCalls";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			parallelToolCalls = Boolean.valueOf(value);
		}
		if (value == null) {
			parallelToolCalls = false;
		}
		return parallelToolCalls;
	}

	public void setParallelToolCalls(Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "executorPoolSize",
			path = "manus.agent.executorPoolSize", description = "manus.agent.executorPoolSize.description",
			defaultValue = "5", inputType = ConfigInputType.NUMBER)
	private volatile Integer executorPoolSize;

	public Integer getExecutorPoolSize() {
		String configPath = "manus.agent.executorPoolSize";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			executorPoolSize = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (executorPoolSize == null) {
			executorPoolSize = 5;
		}
		return executorPoolSize;
	}

	public void setExecutorPoolSize(Integer executorPoolSize) {
		this.executorPoolSize = executorPoolSize;
	}

	@ConfigProperty(group = "manus", subGroup = "agent", key = "llmReadTimeout", path = "manus.agent.llmReadTimeout",
			description = "manus.agent.llmReadTimeout.description", defaultValue = "120",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer llmReadTimeout;

	public Integer getLlmReadTimeout() {
		String configPath = "manus.agent.llmReadTimeout";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			llmReadTimeout = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (llmReadTimeout == null) {
			llmReadTimeout = 120; // Default 120 seconds (2 minutes)
		}
		return llmReadTimeout;
	}

	public void setLlmReadTimeout(Integer llmReadTimeout) {
		this.llmReadTimeout = llmReadTimeout;
	}

	// Agent Settings
	// End-----------------------------------------------------------------------------------------------

	// Normal Settings
	// Begin--------------------------------------------------------------------------------------------

	// Normal Settings
	// End----------------------------------------------------------------------------------------------

	// File System Security SubGroup
	@ConfigProperty(group = "manus", subGroup = "general", key = "externalLinkedFolder",
			path = "manus.general.externalLinkedFolder", description = "manus.general.externalLinkedFolder.description",
			defaultValue = "", inputType = ConfigInputType.TEXT)
	private volatile String externalLinkedFolder = "";

	public String getExternalLinkedFolder() {
		String configPath = "manus.general.externalLinkedFolder";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			externalLinkedFolder = value;
		}
		return externalLinkedFolder;
	}

	public void setExternalLinkedFolder(String externalLinkedFolder) {
		this.externalLinkedFolder = externalLinkedFolder;
	}

	// MCP Service Loader Settings
	// Begin--------------------------------------------------------------------------------------------

	@ConfigProperty(group = "manus", subGroup = "mcpServiceLoader", key = "connectionTimeoutSeconds",
			path = "manus.mcpServiceLoader.connectionTimeoutSeconds",
			description = "manus.mcpServiceLoader.connectionTimeoutSeconds.description", defaultValue = "20",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer mcpConnectionTimeoutSeconds;

	public Integer getMcpConnectionTimeoutSeconds() {
		String configPath = "manus.mcpServiceLoader.connectionTimeoutSeconds";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			mcpConnectionTimeoutSeconds = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (mcpConnectionTimeoutSeconds == null) {
			mcpConnectionTimeoutSeconds = 3;
		}
		return mcpConnectionTimeoutSeconds;
	}

	public void setMcpConnectionTimeoutSeconds(Integer mcpConnectionTimeoutSeconds) {
		this.mcpConnectionTimeoutSeconds = mcpConnectionTimeoutSeconds;
	}

	@ConfigProperty(group = "manus", subGroup = "mcpServiceLoader", key = "maxRetryCount",
			path = "manus.mcpServiceLoader.maxRetryCount",
			description = "manus.mcpServiceLoader.maxRetryCount.description", defaultValue = "3",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer mcpMaxRetryCount;

	public Integer getMcpMaxRetryCount() {
		String configPath = "manus.mcpServiceLoader.maxRetryCount";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			mcpMaxRetryCount = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (mcpMaxRetryCount == null) {
			mcpMaxRetryCount = 1;
		}
		return mcpMaxRetryCount;
	}

	public void setMcpMaxRetryCount(Integer mcpMaxRetryCount) {
		this.mcpMaxRetryCount = mcpMaxRetryCount;
	}

	@ConfigProperty(group = "manus", subGroup = "mcpServiceLoader", key = "maxConcurrentConnections",
			path = "manus.mcpServiceLoader.maxConcurrentConnections",
			description = "manus.mcpServiceLoader.maxConcurrentConnections.description", defaultValue = "10",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer mcpMaxConcurrentConnections;

	public Integer getMcpMaxConcurrentConnections() {
		String configPath = "manus.mcpServiceLoader.maxConcurrentConnections";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			mcpMaxConcurrentConnections = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (mcpMaxConcurrentConnections == null) {
			mcpMaxConcurrentConnections = 10;
		}
		return mcpMaxConcurrentConnections;
	}

	public void setMcpMaxConcurrentConnections(Integer mcpMaxConcurrentConnections) {
		this.mcpMaxConcurrentConnections = mcpMaxConcurrentConnections;
	}

	// MCP Service Loader Settings
	// End----------------------------------------------------------------------------------------------

	// Image Recognition Settings
	// Begin--------------------------------------------------------------------------------------------

	@ConfigProperty(group = "manus", subGroup = "imageRecognition", key = "poolSize",
			path = "manus.imageRecognition.poolSize", description = "manus.imageRecognition.poolSize.description",
			defaultValue = "4", inputType = ConfigInputType.NUMBER)
	private volatile Integer imageRecognitionPoolSize;

	public Integer getImageRecognitionPoolSize() {
		String configPath = "manus.imageRecognition.poolSize";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			imageRecognitionPoolSize = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (imageRecognitionPoolSize == null) {
			imageRecognitionPoolSize = 4;
		}
		return imageRecognitionPoolSize;
	}

	public void setImageRecognitionPoolSize(Integer imageRecognitionPoolSize) {
		this.imageRecognitionPoolSize = imageRecognitionPoolSize;
	}

	@ConfigProperty(group = "manus", subGroup = "imageRecognition", key = "modelName",
			path = "manus.imageRecognition.modelName", description = "manus.imageRecognition.modelName.description",
			defaultValue = "qwen-vl-ocr-latest", inputType = ConfigInputType.TEXT)
	private volatile String imageRecognitionModelName;

	public String getImageRecognitionModelName() {
		String configPath = "manus.imageRecognition.modelName";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			imageRecognitionModelName = value;
		}
		// Ensure a default value if not configured and not set
		if (imageRecognitionModelName == null) {
			imageRecognitionModelName = "qwen-vl-ocr-latest";
		}
		return imageRecognitionModelName;
	}

	public void setImageRecognitionModelName(String imageRecognitionModelName) {
		this.imageRecognitionModelName = imageRecognitionModelName;
	}

	@ConfigProperty(group = "manus", subGroup = "imageRecognition", key = "dpi", path = "manus.imageRecognition.dpi",
			description = "manus.imageRecognition.dpi.description", defaultValue = "120.0",
			inputType = ConfigInputType.NUMBER)
	private volatile Float imageRecognitionDpi;

	public Float getImageRecognitionDpi() {
		String configPath = "manus.imageRecognition.dpi";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			imageRecognitionDpi = Float.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (imageRecognitionDpi == null) {
			imageRecognitionDpi = 120.0f;
		}
		return imageRecognitionDpi;
	}

	public void setImageRecognitionDpi(Float imageRecognitionDpi) {
		this.imageRecognitionDpi = imageRecognitionDpi;
	}

	@ConfigProperty(group = "manus", subGroup = "imageRecognition", key = "imageType",
			path = "manus.imageRecognition.imageType", description = "manus.imageRecognition.imageType.description",
			defaultValue = "RGB", inputType = ConfigInputType.TEXT)
	private volatile String imageRecognitionImageType;

	public String getImageRecognitionImageType() {
		String configPath = "manus.imageRecognition.imageType";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			imageRecognitionImageType = value;
		}
		// Ensure a default value if not configured and not set
		if (imageRecognitionImageType == null) {
			imageRecognitionImageType = "RGB";
		}
		return imageRecognitionImageType;
	}

	public void setImageRecognitionImageType(String imageRecognitionImageType) {
		this.imageRecognitionImageType = imageRecognitionImageType;
	}

	@ConfigProperty(group = "manus", subGroup = "imageRecognition", key = "maxRetryAttempts",
			path = "manus.imageRecognition.maxRetryAttempts",
			description = "manus.imageRecognition.maxRetryAttempts.description", defaultValue = "3",
			inputType = ConfigInputType.NUMBER)
	private volatile Integer imageRecognitionMaxRetryAttempts;

	public Integer getImageRecognitionMaxRetryAttempts() {
		String configPath = "manus.imageRecognition.maxRetryAttempts";
		String value = configService.getConfigValue(configPath);
		if (value != null) {
			imageRecognitionMaxRetryAttempts = Integer.valueOf(value);
		}
		// Ensure a default value if not configured and not set
		if (imageRecognitionMaxRetryAttempts == null) {
			imageRecognitionMaxRetryAttempts = 3;
		}
		return imageRecognitionMaxRetryAttempts;
	}

	public void setImageRecognitionMaxRetryAttempts(Integer imageRecognitionMaxRetryAttempts) {
		this.imageRecognitionMaxRetryAttempts = imageRecognitionMaxRetryAttempts;
	}

	// Image Recognition Settings
	// End----------------------------------------------------------------------------------------------

}
