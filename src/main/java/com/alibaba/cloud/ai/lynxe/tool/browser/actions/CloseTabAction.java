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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;

public class CloseTabAction extends BrowserAction {

	private static final Logger logger = LoggerFactory.getLogger(CloseTabAction.class);

	public CloseTabAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Page currentPage = getCurrentPage();

		// Validate current page
		if (currentPage == null) {
			return new ToolExecuteResult("Error: No current page available");
		}

		// Check if page is already closed
		if (currentPage.isClosed()) {
			logger.debug("Current page is already closed");
			// Try to find another open page
			Page newCurrentPage = findFirstOpenPage(currentPage);
			if (newCurrentPage != null) {
				getDriverWrapper().setCurrentPage(newCurrentPage);
				return new ToolExecuteResult("Current page was already closed. Switched to another open tab");
			}
			return new ToolExecuteResult("Current page is already closed and no other pages are available");
		}

		// Get all pages in the context
		List<Page> allPages = currentPage.context().pages();

		// Filter out closed pages to get only open pages
		List<Page> openPages = allPages.stream().filter(p -> {
			try {
				return !p.isClosed();
			}
			catch (Exception e) {
				logger.debug("Error checking if page is closed: {}", e.getMessage());
				return false;
			}
		}).toList();

		// Determine which page to close
		Page pageToClose = currentPage;
		Integer tabId = request.getTabId();

		if (tabId != null && tabId >= 0) {
			// Close specific tab by ID
			if (tabId >= openPages.size()) {
				return new ToolExecuteResult(
						"Error: Tab ID " + tabId + " is out of range. Available tabs: 0 to " + (openPages.size() - 1));
			}

			Page targetPage = openPages.get(tabId);
			if (targetPage == null || targetPage.isClosed()) {
				return new ToolExecuteResult("Error: Tab ID " + tabId + " does not exist or is already closed");
			}

			pageToClose = targetPage;
		}

		// Check if this is the last remaining page
		boolean isLastPage = (openPages.size() == 1);

		// If closing the last remaining page, navigate to about:blank instead of closing
		if (isLastPage && pageToClose == currentPage) {
			try {
				// Navigate to about:blank directly (better than closing and creating new page)
				pageToClose.navigate("about:blank");
				logger.debug("Navigated last tab to about:blank instead of closing");
				return new ToolExecuteResult("Successfully navigated to blank page (last tab preserved)");
			}
			catch (PlaywrightException e) {
				logger.error("Failed to navigate to about:blank: {}", e.getMessage(), e);
				return new ToolExecuteResult("Error: Failed to navigate to blank page: " + e.getMessage());
			}
		}

		// If closing current page and it's not the last page, find another open page to switch to
		Page newCurrentPage = null;
		if (pageToClose == currentPage && !isLastPage) {
			newCurrentPage = findFirstOpenPageExcept(currentPage, openPages);
			if (newCurrentPage == null) {
				return new ToolExecuteResult("Error: No other open pages available to switch to");
			}
		}

		// Safely close the page (only for non-last pages)
		try {
			// Close the page (default: no beforeunload handlers, waits for close)
			pageToClose.close();

			// Verify the page is closed
			if (!pageToClose.isClosed()) {
				logger.warn("Page close() was called but page is not yet closed");
			}

			// If we closed the current page (but not the last one), switch to another page
			if (newCurrentPage != null) {
				getDriverWrapper().setCurrentPage(newCurrentPage);
				logger.debug("Switched to page: {}", newCurrentPage.url());
				return new ToolExecuteResult(
						"Successfully closed current tab and switched to tab " + getTabIndex(newCurrentPage, openPages));
			}
			else {
				// Closed a different tab, current page remains
				return new ToolExecuteResult("Successfully closed tab " + getTabIndex(pageToClose, openPages));
			}
		}
		catch (PlaywrightException e) {
			// Handle specific Playwright exceptions
			if (e.getMessage() != null && (e.getMessage().contains("Target page, context or browser has been closed")
					|| e.getMessage().contains("Browser has been closed")
					|| e.getMessage().contains("Context has been closed"))) {
				logger.warn("Page was already closed or context/browser was closed: {}", e.getMessage());

				// Try to find another open page
				Page fallbackPage = findFirstOpenPage(currentPage);
				if (fallbackPage != null) {
					getDriverWrapper().setCurrentPage(fallbackPage);
					return new ToolExecuteResult("Page was already closed. Switched to another open tab");
				}
				return new ToolExecuteResult("Error: Page was already closed and no other pages are available");
			}
			throw e; // Re-throw unexpected exceptions
		}
	}

	/**
	 * Find the first open page in the context
	 * @param referencePage Reference page to get context from
	 * @return First open page, or null if none found
	 */
	private Page findFirstOpenPage(Page referencePage) {
		try {
			if (referencePage == null || referencePage.isClosed()) {
				return null;
			}

			List<Page> allPages = referencePage.context().pages();
			return allPages.stream().filter(p -> {
				try {
					return !p.isClosed();
				}
				catch (Exception e) {
					logger.debug("Error checking if page is closed: {}", e.getMessage());
					return false;
				}
			}).findFirst().orElse(null);
		}
		catch (Exception e) {
			logger.warn("Error finding open page: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Find the first open page except the specified page
	 * @param excludePage Page to exclude from search
	 * @param openPages List of open pages
	 * @return First open page that is not the excludePage, or null if none found
	 */
	private Page findFirstOpenPageExcept(Page excludePage, List<Page> openPages) {
		return openPages.stream().filter(p -> p != excludePage && !p.isClosed()).findFirst().orElse(null);
	}

	/**
	 * Get the index of a page in the list of open pages
	 * @param page Page to find index for
	 * @param openPages List of open pages
	 * @return Index of the page, or -1 if not found
	 */
	private int getTabIndex(Page page, List<Page> openPages) {
		for (int i = 0; i < openPages.size(); i++) {
			if (openPages.get(i) == page) {
				return i;
			}
		}
		return -1;
	}

}
