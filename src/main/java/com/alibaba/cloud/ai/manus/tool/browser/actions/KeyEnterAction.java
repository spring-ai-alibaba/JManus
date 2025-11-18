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
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class KeyEnterAction extends BrowserAction {

	public KeyEnterAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Integer index = request.getIndex();
		if (index == null) {
			return new ToolExecuteResult("Index is required for 'key_enter' action");
		}

		Locator locator = getLocatorByIdx(index);
		if (locator == null) {
			return new ToolExecuteResult("Element with index " + index + " not found in ARIA snapshot");
		}

		// Execute the enter operation with timeout handling
		try {
			// Check if element is visible and enabled
			if (!locator.isVisible()) {
				return new ToolExecuteResult("Element at index " + index + " is not visible");
			}

			// Press Enter with explicit timeout
			locator.press("Enter", new Locator.PressOptions().setTimeout(getBrowserTimeoutMs()));

			// Wait for page to process the action and update content
			// This is especially important for search actions that trigger AJAX requests
			Page page = getCurrentPage();
			try {
				// Wait for network idle to ensure search requests complete
				page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
						new com.microsoft.playwright.Page.WaitForLoadStateOptions().setTimeout(3000));
			}
			catch (com.microsoft.playwright.TimeoutError e) {
				// If network idle timeout, wait a bit more for content to update
				Thread.sleep(1000);
			}
			catch (Exception e) {
				// If any error, wait a short time anyway
				Thread.sleep(500);
			}

		}
		catch (com.microsoft.playwright.TimeoutError e) {
			return new ToolExecuteResult("Timeout waiting for element at index " + index
					+ " to be ready for Enter key press. " + e.getMessage());
		}
		catch (Exception e) {
			return new ToolExecuteResult("Failed to press Enter on element at index " + index + ": " + e.getMessage());
		}
		return new ToolExecuteResult("Successfully pressed Enter key at index " + index);
	}

}
