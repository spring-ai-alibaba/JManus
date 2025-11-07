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

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.manus.tool.browser.AriaElementHolder;
import com.alibaba.cloud.ai.manus.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.manus.tool.browser.DriverWrapper;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;

public abstract class BrowserAction {

	private final static Logger log = LoggerFactory.getLogger(BrowserAction.class);

	public abstract ToolExecuteResult execute(BrowserRequestVO request) throws Exception;

	private final BrowserUseTool browserUseTool;

	public BrowserAction(BrowserUseTool browserUseTool) {
		this.browserUseTool = browserUseTool;
	}

	public BrowserUseTool getBrowserUseTool() {
		return browserUseTool;
	}

	/**
	 * Get browser operation timeout configuration
	 * @return Timeout in milliseconds, returns default value of 30 seconds if not
	 * configured
	 */
	protected Integer getBrowserTimeoutMs() {
		Integer timeout = getBrowserUseTool().getManusProperties().getBrowserRequestTimeout();
		return (timeout != null ? timeout : 30) * 1000; // Convert to milliseconds
	}

	/**
	 * Get browser operation timeout configuration
	 * @return Timeout in seconds, returns default value of 30 seconds if not configured
	 */
	protected Integer getBrowserTimeoutSec() {
		Integer timeout = getBrowserUseTool().getManusProperties().getBrowserRequestTimeout();
		return timeout != null ? timeout : 30; // Default timeout is 30 seconds
	}

	/**
	 * Get reasonable timeout for element operations (capped at 10 seconds) This prevents
	 * long waits when elements are not found or not ready
	 * @return Timeout in milliseconds, capped at 10 seconds
	 */
	protected Integer getElementTimeoutMs() {
		return Math.min(getBrowserTimeoutMs(), 10000); // Max 10 seconds for element
														// operations
	}

	/**
	 * Simulate human behavior
	 * @param element Playwright ElementHandle instance
	 */
	protected void simulateHumanBehavior(ElementHandle element) {
		try {
			// Add random delay
			Thread.sleep(new Random().nextInt(500) + 200);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Get DriverWrapper instance
	 * @return DriverWrapper
	 */
	protected DriverWrapper getDriverWrapper() {

		return browserUseTool.getDriver();
	}

	/**
	 * Get current page Page instance
	 * @return Current Playwright Page instance
	 */
	protected Page getCurrentPage() {
		DriverWrapper driverWrapper = getDriverWrapper();
		return driverWrapper.getCurrentPage();
	}

	/**
	 * Get locator for element by idx (from ARIA snapshot) Uses ARIA node properties
	 * (role, name) to create proper Playwright locators
	 * @param idx Element idx (from ARIA snapshot)
	 * @return Locator for the element, or null if not found
	 */
	protected Locator getLocatorByIdx(int idx) {
		Page page = getCurrentPage();
		if (page == null) {
			return null;
		}

		AriaElementHolder.AriaNode node = getAriaNodeByIdx(idx);
		if (node == null) {
			return null;
		}

		// Create locator based on ARIA role and name
		return createLocatorFromAriaNode(page, node);
	}

	/**
	 * Create Playwright locator from ARIA node properties
	 * @param page The page to create locator on
	 * @param node The ARIA node
	 * @return Locator for the element
	 */
	private Locator createLocatorFromAriaNode(Page page, AriaElementHolder.AriaNode node) {
		if (node.role == null) {
			return null;
		}

		String role = node.role;
		String name = node.name;

		try {
			// First, try to use id from props if available (most specific selector)
			String id = node.props != null ? node.props.get("id") : null;
			if (id != null && !id.isEmpty()) {
				try {
					Locator idLocator = page.locator("#" + id);
					// Verify the element exists and has the correct role
					if (idLocator.count() > 0) {
						return idLocator;
					}
				}
				catch (Exception e) {
					log.debug("Failed to use id selector for element: {}", e.getMessage());
				}
			}

			// Use getByRole with name if available
			if (name != null && !name.isEmpty()) {
				try {
					Locator locator = null;
					switch (role) {
						case "button":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
									new Page.GetByRoleOptions().setName(name));
							break;
						case "link":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
									new Page.GetByRoleOptions().setName(name));
							break;
						case "textbox":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX,
									new Page.GetByRoleOptions().setName(name));
							break;
						case "checkbox":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.CHECKBOX,
									new Page.GetByRoleOptions().setName(name));
							break;
						case "radio":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.RADIO,
									new Page.GetByRoleOptions().setName(name));
							break;
						case "combobox":
							locator = page.getByRole(com.microsoft.playwright.options.AriaRole.COMBOBOX,
									new Page.GetByRoleOptions().setName(name));
							break;
						default:
							// Fallback: try to get by role without name
							return getByRoleWithoutName(page, role);
					}
					// If we get here, locator was created successfully (no strict mode violation)
					return locator;
				}
				catch (Exception e) {
					// Strict mode violation or other error - try to handle multiple matches
					if (e.getMessage() != null && e.getMessage().contains("strict mode violation")) {
						log.debug("Strict mode violation for role={}, name={}, trying alternative selector", role, name);
						return handleMultipleMatches(page, node, role, name);
					}
					throw e;
				}
			}
			else {
				// No name, try to get by role only
				return getByRoleWithoutName(page, role);
			}
		}
		catch (Exception e) {
			log.warn("Failed to create locator from ARIA node (role: {}, name: {}): {}", role, name, e.getMessage());
			return null;
		}
	}

	/**
	 * Handle case when multiple elements match the same role and name
	 * @param page The page
	 * @param node The ARIA node we're looking for
	 * @param role The role
	 * @param name The name
	 * @return A more specific locator
	 */
	private Locator handleMultipleMatches(Page page, AriaElementHolder.AriaNode node, String role, String name) {
		try {
			// Try to use placeholder with CSS selector to narrow down
			String placeholder = node.props != null ? node.props.get("placeholder") : null;
			if (placeholder != null && !placeholder.isEmpty()) {
				try {
					// Use CSS selector with placeholder as additional filter
					Locator placeholderLocator = page.locator(
							"input[placeholder*='" + placeholder.replace("'", "\\'") + "']");
					if (placeholderLocator.count() == 1) {
						return placeholderLocator;
					}
				}
				catch (Exception e) {
					log.debug("Failed to use placeholder CSS selector: {}", e.getMessage());
				}
			}

			// Get all elements with same role and name, then select by position
			// We'll use the index from the ARIA snapshot to determine which one
			DriverWrapper driverWrapper = getDriverWrapper();
			AriaElementHolder holder = driverWrapper.getAriaElementHolder();
			int nodeIndex = -1;
			if (holder != null) {
				// Get all nodes with same role and name
				List<AriaElementHolder.AriaNode> matchingNodes = holder.getByRoleAndName(role, name);
				if (matchingNodes.size() > 1) {
					// Find the index of our node in the list
					for (int i = 0; i < matchingNodes.size(); i++) {
						if (matchingNodes.get(i).ref != null && matchingNodes.get(i).ref.equals(node.ref)) {
							nodeIndex = i;
							break;
						}
					}
				}
			}

			// Last resort: use getByRole without name, then filter or select by index
			// We can't use getByRole with name because it throws strict mode violation
			try {
				com.microsoft.playwright.options.AriaRole ariaRole = mapRoleToAriaRole(role);
				if (ariaRole != null) {
					// Get all elements with this role (without name filter to avoid strict mode)
					Locator allRoleElements = page.getByRole(ariaRole);
					// Note: count() doesn't have timeout option, but it should be fast
					// If it hangs, it's likely a page loading issue
					int totalCount = allRoleElements.count();
					
					// If we found the index and it's valid, use nth()
					if (nodeIndex >= 0 && nodeIndex < totalCount) {
						log.debug("Using nth({}) for role={}, name={}", nodeIndex, role, name);
						return allRoleElements.nth(nodeIndex);
					}
					// Fallback: use first() if we can't determine the index
					log.warn("Multiple elements match role={}, name={}, using first() as fallback", role, name);
					return allRoleElements.first();
				}
			}
			catch (Exception e) {
				log.debug("Failed to use role-only selector with index: {}", e.getMessage());
			}

			// Final fallback: use role without name
			log.warn("Multiple elements match role={}, name={}, using role-only selector as fallback", role, name);
			return getByRoleWithoutName(page, role);
		}
		catch (Exception e) {
			log.warn("Failed to handle multiple matches for role={}, name={}: {}", role, name, e.getMessage());
			return getByRoleWithoutName(page, role);
		}
	}

	/**
	 * Get locator by role without name (uses nth() to select by index if needed)
	 */
	private Locator getByRoleWithoutName(Page page, String role) {
		try {
			com.microsoft.playwright.options.AriaRole ariaRole = mapRoleToAriaRole(role);
			if (ariaRole != null) {
				// Get all elements with this role and use first() as fallback
				// Note: This is less precise, but better than failing
				return page.getByRole(ariaRole).first();
			}
		}
		catch (Exception e) {
			log.debug("Failed to map role {} to AriaRole: {}", role, e.getMessage());
		}
		return null;
	}

	/**
	 * Map string role to Playwright AriaRole enum
	 */
	private com.microsoft.playwright.options.AriaRole mapRoleToAriaRole(String role) {
		if (role == null) {
			return null;
		}
		try {
			return com.microsoft.playwright.options.AriaRole.valueOf(role.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			// Handle common role mappings
			switch (role.toLowerCase()) {
				case "textbox":
					return com.microsoft.playwright.options.AriaRole.TEXTBOX;
				case "button":
					return com.microsoft.playwright.options.AriaRole.BUTTON;
				case "link":
					return com.microsoft.playwright.options.AriaRole.LINK;
				case "checkbox":
					return com.microsoft.playwright.options.AriaRole.CHECKBOX;
				case "radio":
					return com.microsoft.playwright.options.AriaRole.RADIO;
				case "combobox":
					return com.microsoft.playwright.options.AriaRole.COMBOBOX;
				default:
					return null;
			}
		}
	}

	/**
	 * Get ARIA node by idx
	 * @param idx Element idx (from ARIA snapshot)
	 * @return AriaNode, or null if not found
	 */
	protected AriaElementHolder.AriaNode getAriaNodeByIdx(int idx) {
		DriverWrapper driverWrapper = getDriverWrapper();
		AriaElementHolder holder = driverWrapper.getAriaElementHolder();
		if (holder == null) {
			return null;
		}
		return holder.getByRefId(String.valueOf(idx));
	}

	/**
	 * Get element name from ARIA node by idx
	 * @param idx Element idx (from ARIA snapshot)
	 * @return Element name, or empty string if not found
	 */
	protected String getElementNameByIdx(int idx) {
		AriaElementHolder.AriaNode node = getAriaNodeByIdx(idx);
		if (node != null) {
			if (node.name != null && !node.name.isEmpty()) {
				return node.name;
			}
			// If no name, return role as fallback
			return node.role != null ? node.role : "element";
		}
		return "";
	}

	/**
	 * Check if element exists by idx
	 * @param idx Element idx (from ARIA snapshot)
	 * @return true if element exists, false otherwise
	 */
	protected boolean elementExistsByIdx(int idx) {
		return getAriaNodeByIdx(idx) != null;
	}

	protected String clickAndSwitchToNewTabIfOpened(Page pageToClickOn, Runnable clickLambda) {
		Page newPageFromPopup = null;
		String originalPageUrl = pageToClickOn.url();
		BrowserContext context = pageToClickOn.context();
		List<Page> pagesBeforeClick = context.pages();
		Set<String> urlsBeforeClick = pagesBeforeClick.stream().map(Page::url).collect(Collectors.toSet());

		try {
			Integer timeout = getBrowserTimeoutMs();
			// Use the minimum of configured timeout and 2 seconds for popup detection
			int popupTimeout = Math.min(timeout, 2000);
			Page.WaitForPopupOptions popupOptions = new Page.WaitForPopupOptions().setTimeout(popupTimeout);

			log.debug("Using popup timeout: {}ms, browser timeout: {}ms", popupTimeout, timeout);

			newPageFromPopup = pageToClickOn.waitForPopup(popupOptions, clickLambda);

			if (newPageFromPopup != null) {
				log.info("waitForPopup detected new page: {}", newPageFromPopup.url());
				if (getDriverWrapper().getCurrentPage() != newPageFromPopup) {
					getDriverWrapper().setCurrentPage(newPageFromPopup);
				}
				return "and opened in new tab: " + newPageFromPopup.url();
			}

			// Fallback if newPageFromPopup is null but no exception (unlikely for
			// waitForPopup)
			if (!pageToClickOn.isClosed() && !pageToClickOn.url().equals(originalPageUrl)) {
				log.info("Page navigated in the same tab (fallback check): {}", pageToClickOn.url());
				return "and navigated in the same tab to: " + pageToClickOn.url();
			}
			return "successfully.";

		}
		catch (TimeoutError e) {
			log.warn(
					"No popup detected by waitForPopup within timeout. Click action was performed. Checking page states...");

			List<Page> pagesAfterTimeout = context.pages();
			List<Page> newPagesByDiff = pagesAfterTimeout.stream()
				.filter(p -> !urlsBeforeClick.contains(p.url()))
				.collect(Collectors.toList());

			if (!newPagesByDiff.isEmpty()) {
				Page newlyFoundPage = newPagesByDiff.get(0);
				log.info("New tab found by diffing URLs after waitForPopup timeout: {}", newlyFoundPage.url());
				getDriverWrapper().setCurrentPage(newlyFoundPage);
				return "and opened in new tab: " + newlyFoundPage.url();
			}

			if (!pageToClickOn.isClosed() && !pageToClickOn.url().equals(originalPageUrl)) {
				if (getDriverWrapper().getCurrentPage() != pageToClickOn) {
					getDriverWrapper().setCurrentPage(pageToClickOn);
				}
				log.info("Page navigated in the same tab after timeout: {}", pageToClickOn.url());
				return "and navigated in the same tab to: " + pageToClickOn.url();
			}

			Page currentPageInWrapper = getDriverWrapper().getCurrentPage();
			if (pageToClickOn.isClosed() && currentPageInWrapper != null && !currentPageInWrapper.isClosed()
					&& !urlsBeforeClick.contains(currentPageInWrapper.url())) {
				log.info("Original page closed, current page is now: {}", currentPageInWrapper.url());
				return "and current page changed to: " + currentPageInWrapper.url();
			}
			log.info("No new tab or significant navigation detected after timeout.");
			return "successfully, but no new tab was detected by waitForPopup or URL diff.";
		}
		catch (Exception e) {
			log.error("Exception during click or popup handling: {}", e.getMessage(), e);

			List<Page> pagesAfterError = context.pages();
			List<Page> newPagesByDiffAfterError = pagesAfterError.stream()
				.filter(p -> !urlsBeforeClick.contains(p.url()))
				.collect(Collectors.toList());
			if (!newPagesByDiffAfterError.isEmpty()) {
				Page newlyFoundPage = newPagesByDiffAfterError.get(0);
				log.info("New tab found by diffing URLs after an error: {}", newlyFoundPage.url());
				getDriverWrapper().setCurrentPage(newlyFoundPage);
				return "with error '" + e.getMessage() + "' but opened new tab: " + newlyFoundPage.url();
			}
			return "with error: " + e.getMessage();
		}
	}

}
