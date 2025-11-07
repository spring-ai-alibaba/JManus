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

public class SwitchTabAction extends BrowserAction {

	public SwitchTabAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Integer tabId = request.getTabId();
		if (tabId == null || tabId < 0) {
			return new ToolExecuteResult("Tab ID is out of range for 'switch_tab' action");
		}

		Page page = getCurrentPage(); // Get Playwright Page instance
		java.util.List<Page> pages = page.context().pages();
		
		// Check if tabId is within valid range
		if (tabId >= pages.size()) {
			return new ToolExecuteResult("Tab ID " + tabId + " is out of range. Available tabs: 0 to " + (pages.size() - 1));
		}
		
		Page targetPage = pages.get(tabId); // Switch to specified tab
		if (targetPage == null) {
			return new ToolExecuteResult("Tab ID " + tabId + " does not exist");
		}

		// Update the current page in DriverWrapper
		getDriverWrapper().setCurrentPage(targetPage);

		return new ToolExecuteResult("Successfully switched to tab " + tabId);
	}

}
