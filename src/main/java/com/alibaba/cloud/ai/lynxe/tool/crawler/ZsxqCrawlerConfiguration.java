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
package com.alibaba.cloud.ai.lynxe.tool.crawler;

import com.alibaba.cloud.ai.lynxe.tool.browser.ChromeDriverService;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 知识星球爬虫工具配置
 */
@Configuration
@ConditionalOnProperty(name = "lynxe.tools.zsxq-crawler.enabled", havingValue = "true", matchIfMissing = true)
public class ZsxqCrawlerConfiguration {

	@Bean
	public ZsxqCrawlerTool zsxqCrawlerTool(ChromeDriverService chromeDriverService,
			UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper,
			ToolI18nService toolI18nService) {
		return new ZsxqCrawlerTool(chromeDriverService, unifiedDirectoryManager, objectMapper, toolI18nService);
	}

}
