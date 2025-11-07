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
				// For links, try using href from props first (more specific)
				if ("link".equals(role)) {
					String href = node.props != null ? node.props.get("url") : null;
					if (href != null && !href.isEmpty()) {
						try {
							// Use CSS selector with href attribute for more specific matching
							Locator hrefLocator = page.locator("a[href*='" + href.replace("'", "\\'") + "']");
							if (hrefLocator.count() == 1) {
								return hrefLocator;
							}
							// If multiple or none, fall through to role-based selector
						}
						catch (Exception e) {
							log.debug("Failed to use href CSS selector: {}", e.getMessage());
						}
					}
				}
				
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
					
					// Check if locator matches multiple elements (proactive check)
					// This helps avoid strict mode violations during waitFor/click operations
					try {
						int count = locator.count();
						if (count > 1) {
							log.debug("Locator for role={}, name={} matches {} elements, using handleMultipleMatches", role, name, count);
							return handleMultipleMatches(page, node, role, name);
						}
						// If count is 1 or 0, the locator is fine (0 will fail later, but that's expected)
						return locator;
					}
					catch (Exception countException) {
						// If count() fails, try to use the locator anyway
						// It might work if the exception is due to timing
						log.debug("Failed to get count for locator, using it anyway: {}", countException.getMessage());
						return locator;
					}
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
			List<AriaElementHolder.AriaNode> matchingNodes = new java.util.ArrayList<>();
			if (holder != null && node.ref != null) {
				// Get all nodes with same role and name
				matchingNodes = holder.getByRoleAndName(role, name);
				if (matchingNodes.size() > 1) {
					// Find the index of our node in the list (sorted by document order)
					for (int i = 0; i < matchingNodes.size(); i++) {
						if (matchingNodes.get(i).ref != null && matchingNodes.get(i).ref.equals(node.ref)) {
							nodeIndex = i;
							log.debug("Found node index {} for ref {} in matching nodes", i, node.ref);
							break;
						}
					}
				}
			}

			// Try to use getByRole with name and filter by index
			// We need to use a workaround since getByRole with name throws strict mode violation
			try {
				com.microsoft.playwright.options.AriaRole ariaRole = mapRoleToAriaRole(role);
				if (ariaRole != null) {
					// Get all elements with this role and name using filter
					// First, get all elements with the role
					Locator allRoleElements = page.getByRole(ariaRole);
					
					// Filter by name using a filter function
					// This avoids strict mode violation by not using getByRole with name directly
					Locator filteredByName = allRoleElements.filter(new Locator.FilterOptions().setHasText(name));
					
					int filteredCount = filteredByName.count();
					log.debug("Found {} elements with role={} and name={}", filteredCount, role, name);
					
					// If we found the index and it's valid, use nth()
					if (nodeIndex >= 0 && nodeIndex < filteredCount) {
						log.debug("Using nth({}) for role={}, name={} (ref={})", nodeIndex, role, name, node.ref);
						return filteredByName.nth(nodeIndex);
					}
					
					// If filtered count matches expected, use the filtered locator
					if (filteredCount > 0 && filteredCount <= matchingNodes.size()) {
						// Use first() as fallback if we can't determine exact index
						log.debug("Using first() from {} filtered elements for role={}, name={}", filteredCount, role, name);
						return filteredByName.first();
					}
					
					// Fallback: use all role elements without name filter
					int totalCount = allRoleElements.count();
					if (nodeIndex >= 0 && nodeIndex < totalCount) {
						log.debug("Using nth({}) from all {} role elements for role={}, name={}", nodeIndex, totalCount, role, name);
						return allRoleElements.nth(nodeIndex);
					}
					
					// Final fallback: use first() if we can't determine the index
					log.warn("Multiple elements match role={}, name={}, using first() as fallback", role, name);
					return allRoleElements.first();
				}
			}
			catch (Exception e) {
				log.debug("Failed to use role selector with filter: {}", e.getMessage());
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
