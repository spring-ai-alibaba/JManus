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
package com.alibaba.cloud.ai.manus.tool.browser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Cookie;

/**
 * Implementation of browser context management for the Manus browser tool system.
 * This class wraps Microsoft Playwright's BrowserContext and provides additional
 * functionality for managing browser sessions, cookies, and page lifecycle.
 */
public class BrowserContextImpl {

	private static final Logger logger = LoggerFactory.getLogger(BrowserContextImpl.class);

	private final BrowserContext playwrightContext;

	private final Map<String, Object> contextData;

	private final List<Page> managedPages;

	private boolean closed = false;

	private String closeReason;

	/**
	 * Constructor for BrowserContextImpl
	 * @param playwrightContext The underlying Playwright BrowserContext
	 */
	public BrowserContextImpl(BrowserContext playwrightContext) {
		this.playwrightContext = playwrightContext;
		this.contextData = new ConcurrentHashMap<>();
		this.managedPages = new CopyOnWriteArrayList<>();
		
		// Set up event listeners for the Playwright context
		setupEventListeners();
	}

	/**
	 * Set up event listeners for the Playwright browser context
	 */
	private void setupEventListeners() {
		try {
			// Listen for new pages
			playwrightContext.onPage(page -> {
				managedPages.add(page);
				logger.debug("New page created in context: {}", page.url());
			});

			// Listen for context close events
			playwrightContext.onClose(context -> {
				logger.info("Browser context closed");
				handleContextClose();
			});

		}
		catch (Exception e) {
			logger.warn("Failed to set up event listeners for browser context: {}", e.getMessage());
		}
	}

	/**
	 * Handle context close event
	 */
	private void handleContextClose() {
		closed = true;
		managedPages.clear();
		contextData.clear();
	}

	/**
	 * Create a new page in this context
	 * @return The newly created page
	 */
	public Page newPage() {
		if (closed) {
			throw new IllegalStateException("Cannot create new page: browser context is closed");
		}

		try {
			Page page = playwrightContext.newPage();
			managedPages.add(page);
			logger.debug("Created new page: {}", page.url());
			return page;
		}
		catch (Exception e) {
			logger.error("Failed to create new page: {}", e.getMessage());
			throw new RuntimeException("Failed to create new page", e);
		}
	}

	/**
	 * Get all pages in this context
	 * @return List of pages
	 */
	public List<Page> pages() {
		return List.copyOf(managedPages);
	}

	/**
	 * Get the underlying Playwright BrowserContext
	 * @return The Playwright BrowserContext
	 */
	public BrowserContext getPlaywrightContext() {
		return playwrightContext;
	}

	/**
	 * Close the browser context
	 */
	public void close() {
		close(null);
	}

	/**
	 * Close the browser context with a reason
	 * @param reason The reason for closing
	 */
	public void close(String reason) {
		if (closed) {
			logger.debug("Browser context already closed");
			return;
		}

		this.closeReason = reason;

		try {
			// Close all managed pages first
			for (Page page : managedPages) {
				try {
					if (!page.isClosed()) {
						page.close();
					}
				}
				catch (Exception e) {
					logger.warn("Failed to close page: {}", e.getMessage());
				}
			}

			// Close the context
			playwrightContext.close();
			logger.info("Browser context closed successfully. Reason: {}", reason != null ? reason : "Not specified");

		}
		catch (Exception e) {
			logger.error("Error closing browser context: {}", e.getMessage());
			throw new RuntimeException("Failed to close browser context", e);
		}
		finally {
			handleContextClose();
		}
	}

	/**
	 * Check if the context is closed
	 * @return true if closed, false otherwise
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Get the close reason
	 * @return The reason for closing, or null if not closed or no reason provided
	 */
	public String getCloseReason() {
		return closeReason;
	}

	/**
	 * Add cookies to the context
	 * @param cookies List of cookies to add
	 */
	public void addCookies(List<Cookie> cookies) {
		if (closed) {
			throw new IllegalStateException("Cannot add cookies: browser context is closed");
		}

		try {
			playwrightContext.addCookies(cookies);
			logger.debug("Added {} cookies to context", cookies.size());
		}
		catch (Exception e) {
			logger.error("Failed to add cookies: {}", e.getMessage());
			throw new RuntimeException("Failed to add cookies", e);
		}
	}

	/**
	 * Get cookies from the context
	 * @return List of cookies
	 */
	public List<Cookie> cookies() {
		if (closed) {
			throw new IllegalStateException("Cannot get cookies: browser context is closed");
		}

		try {
			return playwrightContext.cookies();
		}
		catch (Exception e) {
			logger.error("Failed to get cookies: {}", e.getMessage());
			throw new RuntimeException("Failed to get cookies", e);
		}
	}

	/**
	 * Clear cookies from the context
	 */
	public void clearCookies() {
		if (closed) {
			throw new IllegalStateException("Cannot clear cookies: browser context is closed");
		}

		try {
			playwrightContext.clearCookies();
			logger.debug("Cleared cookies from context");
		}
		catch (Exception e) {
			logger.error("Failed to clear cookies: {}", e.getMessage());
			throw new RuntimeException("Failed to clear cookies", e);
		}
	}

	/**
	 * Set extra HTTP headers for all requests in this context
	 * @param headers Map of header name to header value
	 */
	public void setExtraHTTPHeaders(Map<String, String> headers) {
		if (closed) {
			throw new IllegalStateException("Cannot set headers: browser context is closed");
		}

		try {
			playwrightContext.setExtraHTTPHeaders(headers);
			logger.debug("Set {} extra HTTP headers", headers.size());
		}
		catch (Exception e) {
			logger.error("Failed to set extra HTTP headers: {}", e.getMessage());
			throw new RuntimeException("Failed to set extra HTTP headers", e);
		}
	}

	/**
	 * Set the context to offline mode
	 * @param offline true to enable offline mode, false to disable
	 */
	public void setOffline(boolean offline) {
		if (closed) {
			throw new IllegalStateException("Cannot set offline mode: browser context is closed");
		}

		try {
			playwrightContext.setOffline(offline);
			logger.debug("Set offline mode to: {}", offline);
		}
		catch (Exception e) {
			logger.error("Failed to set offline mode: {}", e.getMessage());
			throw new RuntimeException("Failed to set offline mode", e);
		}
	}

	/**
	 * Store custom data in the context
	 * @param key The key for the data
	 * @param value The value to store
	 */
	public void setContextData(String key, Object value) {
		contextData.put(key, value);
	}

	/**
	 * Retrieve custom data from the context
	 * @param key The key for the data
	 * @return The stored value, or null if not found
	 */
	public Object getContextData(String key) {
		return contextData.get(key);
	}

	/**
	 * Remove custom data from the context
	 * @param key The key for the data to remove
	 * @return The removed value, or null if not found
	 */
	public Object removeContextData(String key) {
		return contextData.remove(key);
	}

	/**
	 * Get all context data keys
	 * @return Set of all keys
	 */
	public java.util.Set<String> getContextDataKeys() {
		return contextData.keySet();
	}

	/**
	 * Wait for a page with specific conditions
	 * @param timeout Timeout in milliseconds
	 * @return The page that matches the conditions
	 */
	public Page waitForPage(double timeout) {
		if (closed) {
			throw new IllegalStateException("Cannot wait for page: browser context is closed");
		}

		try {
			return playwrightContext.waitForPage(() -> {
				// This is a placeholder - in practice, you might want to add specific conditions
			});
		}
		catch (Exception e) {
			logger.error("Failed to wait for page: {}", e.getMessage());
			throw new RuntimeException("Failed to wait for page", e);
		}
	}

	@Override
	public String toString() {
		return String.format("BrowserContextImpl{closed=%s, pagesCount=%d, closeReason='%s'}", 
				closed, managedPages.size(), closeReason);
	}
}
