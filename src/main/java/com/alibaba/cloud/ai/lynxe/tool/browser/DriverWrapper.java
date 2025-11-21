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
package com.alibaba.cloud.ai.lynxe.tool.browser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Wrapper for Playwright browser resources following best practices.
 * Uses Storage State for persistence (cookies, localStorage, sessionStorage).
 * Ensures proper cleanup order: BrowserContext -> Browser -> Playwright
 */
public class DriverWrapper {

	private static final Logger log = LoggerFactory.getLogger(DriverWrapper.class);

	private final Playwright playwright;

	private final Browser browser;

	private final BrowserContext browserContext;

	private Page currentPage;

	private final Path storageStatePath;

	/**
	 * Create a new DriverWrapper with Playwright resources.
	 * Following best practices: Browser -> BrowserContext -> Page
	 *
	 * @param playwright Playwright instance
	 * @param browser Browser instance
	 * @param browserContext Browser context instance
	 * @param currentPage Current page instance
	 * @param storageStateDir Directory for storing storage state
	 */
	public DriverWrapper(Playwright playwright, Browser browser, BrowserContext browserContext, Page currentPage,
			String storageStateDir) {
		this.playwright = playwright;
		this.browser = browser;
		this.browserContext = browserContext;
		this.currentPage = currentPage;

		// Set storage state path
		if (storageStateDir == null || storageStateDir.trim().isEmpty()) {
			this.storageStatePath = Paths.get("storage-state.json");
			log.warn("Storage state directory not provided, using default: {}", this.storageStatePath.toAbsolutePath());
		}
		else {
			this.storageStatePath = Paths.get(storageStateDir, "storage-state.json");
		}

		log.info("DriverWrapper created with storage state path: {}", this.storageStatePath.toAbsolutePath());
	}

	/**
	 * Get the current page
	 */
	public Page getCurrentPage() {
		return currentPage;
	}

	/**
	 * Set the current page
	 */
	public void setCurrentPage(Page currentPage) {
		this.currentPage = currentPage;
	}

	/**
	 * Get the browser context
	 */
	public BrowserContext getBrowserContext() {
		return browserContext;
	}

	/**
	 * Get the browser
	 */
	public Browser getBrowser() {
		return browser;
	}

	/**
	 * Get the Playwright instance
	 */
	public Playwright getPlaywright() {
		return playwright;
	}

	/**
	 * Get the storage state path
	 */
	public Path getStorageStatePath() {
		return storageStatePath;
	}

	/**
	 * Save storage state (cookies, localStorage, sessionStorage) asynchronously with timeout.
	 * This is the recommended way to persist browser state according to Playwright best practices.
	 */
	public void saveStorageState() {
		if (browserContext == null) {
			log.debug("Cannot save storage state: browser context is null");
			return;
		}

		try {
			// Save storage state asynchronously with timeout to prevent blocking during shutdown
			CompletableFuture<Void> saveFuture = CompletableFuture.runAsync(() -> {
				try {
					browserContext.storageState(
							new BrowserContext.StorageStateOptions().setPath(storageStatePath));
					log.info("Storage state saved successfully to: {}", storageStatePath.toAbsolutePath());
				}
				catch (Exception e) {
					log.warn("Failed to save storage state: {}", e.getMessage());
				}
			});

			// Wait up to 5 seconds for storage state to save
			try {
				saveFuture.get(5, TimeUnit.SECONDS);
			}
			catch (TimeoutException e) {
				log.warn("Storage state save timed out after 5 seconds, continuing with shutdown");
				saveFuture.cancel(true);
			}
			catch (Exception e) {
				log.warn("Error waiting for storage state save: {}", e.getMessage());
			}
		}
		catch (Exception e) {
			log.warn("Failed to save storage state: {}", e.getMessage());
		}
	}

	/**
	 * Close all resources following Playwright best practices.
	 * 
	 * <p>According to Playwright documentation, BrowserContext should be explicitly closed
	 * before calling Browser.close() to ensure graceful cleanup and proper event handling.
	 * 
	 * <p>Cleanup order:
	 * <ol>
	 *   <li>Save storage state (preserves cookies, localStorage, sessionStorage)</li>
	 *   <li>Close BrowserContext (closes all pages and ensures artifacts are flushed)</li>
	 *   <li>Close Browser (after all contexts are closed)</li>
	 *   <li>Close Playwright (terminates I/O threads)</li>
	 * </ol>
	 * 
	 * <p>Reference: {@link com.microsoft.playwright.Browser#newContext() Browser.newContext()}
	 * documentation recommends: "explicitly close the returned context via 
	 * BrowserContext.close() when your code is done with the BrowserContext, and before 
	 * calling Browser.close()"
	 */
	public void close() {
		log.info("Closing DriverWrapper and all associated resources");

		// Step 1: Save storage state before closing anything
		// This preserves cookies, localStorage, sessionStorage, and other browser state
		try {
			saveStorageState();
		}
		catch (Exception e) {
			log.warn("Failed to save storage state during close: {}", e.getMessage());
		}

		// Step 2: Close BrowserContext first (best practice)
		// This will close all pages in the context and ensure proper event handling
		if (browserContext != null) {
			try {
				if (!browserContext.browser().isConnected()) {
					log.debug("Browser already disconnected, skipping context close");
				}
				else {
					browserContext.close();
					log.debug("Successfully closed browser context");
				}
			}
			catch (Exception e) {
				log.warn("Error closing browser context: {}", e.getMessage());
			}
		}

		// Step 3: Close Browser (after contexts are closed)
		if (browser != null) {
			try {
				if (browser.isConnected()) {
					browser.close();
					log.debug("Successfully closed browser");
				}
				else {
					log.debug("Browser was already disconnected");
				}
			}
			catch (Exception e) {
				log.warn("Error closing browser: {}", e.getMessage());
			}
		}

		// Step 4: Close Playwright instance (terminates I/O threads)
		if (playwright != null) {
			try {
				playwright.close();
				log.debug("Successfully closed Playwright instance");
			}
			catch (Exception e) {
				log.warn("Error closing Playwright instance: {}", e.getMessage());
			}
		}

		// Clear current page reference
		currentPage = null;

		log.info("DriverWrapper close operation completed");
	}

}
