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
package com.alibaba.cloud.ai.lynxe.tool.browser.actions;

import com.alibaba.cloud.ai.lynxe.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.microsoft.playwright.Page;

public class ScrollAction extends BrowserAction {

	public ScrollAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Integer scrollAmount = request.getScrollAmount();
		String direction = request.getDirection();

		// If scrollAmount is not provided, use direction to determine scroll amount
		if (scrollAmount == null) {
			if (direction == null || direction.trim().isEmpty()) {
				return new ToolExecuteResult("Either 'scroll_amount' or 'direction' is required for 'scroll' action");
			}
			// Default scroll amount: 500 pixels
			int defaultScrollAmount = 500;
			if ("up".equalsIgnoreCase(direction)) {
				scrollAmount = -defaultScrollAmount;
			}
			else if ("down".equalsIgnoreCase(direction)) {
				scrollAmount = defaultScrollAmount;
			}
			else {
				return new ToolExecuteResult("Direction must be 'up' or 'down', got: " + direction);
			}
		}

		Page page = getCurrentPage(); // Get Playwright Page instance
		// Use arrow function to pass scrollAmount as parameter
		page.evaluate("(amount) => window.scrollBy(0, amount)", scrollAmount);

		String scrollDirection = scrollAmount > 0 ? "down" : "up";
		return new ToolExecuteResult("Scrolled " + scrollDirection + " by " + Math.abs(scrollAmount) + " pixels");
	}

}
