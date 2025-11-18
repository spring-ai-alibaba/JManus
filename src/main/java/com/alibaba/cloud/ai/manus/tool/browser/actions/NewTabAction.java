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
package com.alibaba.cloud.ai.manus.tool.browser.actions;

import com.alibaba.cloud.ai.manus.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.shortUrl.ShortUrlService;
import com.microsoft.playwright.Page;

public class NewTabAction extends BrowserAction {

	public NewTabAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		String url = request.getUrl();
		if (url == null || url.trim().isEmpty()) {
			return new ToolExecuteResult("URL is required for 'new_tab' action");
		}

		// Check if URL is a short URL
		if (ShortUrlService.isShortUrl(url)) {
			String realUrl = getShortUrlService().getRealUrlFromShortUrl(getRootPlanId(), url);
			if (realUrl == null) {
				return new ToolExecuteResult("Short URL not found in mapping: " + url);
			}
			url = realUrl;
			org.slf4j.LoggerFactory.getLogger(NewTabAction.class)
				.debug("Resolved short URL {} to real URL {}", request.getUrl(), url);
		}

		// Auto-complete the URL prefix
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			url = "https://" + url;
		}

		// Get current page to access browser context
		Page currentPage = getCurrentPage();

		// Create a new page (new tab) in the same browser context
		Page newPage = currentPage.context().newPage();

		// Navigate the new page to the specified URL
		newPage.navigate(url);

		// Set the new page as the current page in DriverWrapper
		getDriverWrapper().setCurrentPage(newPage);

		return new ToolExecuteResult("Opened new tab with URL " + url);
	}

}
