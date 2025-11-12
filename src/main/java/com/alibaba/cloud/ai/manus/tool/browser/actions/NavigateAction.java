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
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.WaitForLoadStateOptions;
import com.microsoft.playwright.options.LoadState;

public class NavigateAction extends BrowserAction {

	public NavigateAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {

		String url = request.getUrl();
		Integer timeoutMs = getBrowserTimeoutMs();

		if (url == null) {
			return new ToolExecuteResult("URL is required for 'navigate' action");
		}

		// Check if URL is a short URL (http://sUrl.a/...)
		if (url.startsWith("http://sUrl.a/") || url.startsWith("https://sUrl.a/")) {
			String realUrl = getShortUrlService().getRealUrlFromShortUrl(getRootPlanId(), url);
			if (realUrl == null) {
				return new ToolExecuteResult("Short URL not found in mapping: " + url);
			}
			url = realUrl;
			org.slf4j.LoggerFactory.getLogger(NavigateAction.class)
					.debug("Resolved short URL {} to real URL {}", request.getUrl(), url);
		}

		// Auto-complete the URL prefix
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			url = "https://" + url;
		}
		Page page = getCurrentPage(); // Get the Playwright Page instance
		page.navigate(url, new Page.NavigateOptions().setTimeout(timeoutMs));

		// Before calling page.content(), ensure the page is fully loaded
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new WaitForLoadStateOptions().setTimeout(timeoutMs));

		// Save cookies after navigation to persist them
		try {
			getBrowserUseTool().getDriver().persistCookies();
		}
		catch (Exception e) {
			// Log but don't fail the navigation if cookie saving fails
			org.slf4j.LoggerFactory.getLogger(NavigateAction.class)
				.debug("Failed to save cookies after navigation: {}", e.getMessage());
		}

		return new ToolExecuteResult("successfully navigated to " + url);
	}

}
