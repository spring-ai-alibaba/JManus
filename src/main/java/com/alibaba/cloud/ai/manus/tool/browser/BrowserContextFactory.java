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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;

/**
 * Factory class for creating and managing BrowserContextImpl instances.
 * This class provides centralized management of browser contexts and ensures
 * proper resource cleanup.
 */
@Component
public class BrowserContextFactory {

	private static final Logger logger = LoggerFactory.getLogger(BrowserContextFactory.class);

	private final Map<String, BrowserContextImpl> activeContexts = new ConcurrentHashMap<>();

	/**
	 * Create a new browser context
	 * @param browser The browser instance to create the context from
	 * @param contextId Unique identifier for the context
	 * @return The created BrowserContextImpl
	 */
	public BrowserContextImpl createContext(Browser browser, String contextId) {
		return createContext(browser, contextId, null);
	}

	/**
	 * Create a new browser context with options
	 * @param browser The browser instance to create the context from
	 * @param contextId Unique identifier for the context
	 * @param options Browser context options (can be null)
	 * @return The created BrowserContextImpl
	 */
	public BrowserContextImpl createContext(Browser browser, String contextId, 
			Browser.NewContextOptions options) {
		
		if (activeContexts.containsKey(contextId)) {
			logger.warn("Context with ID {} already exists, closing existing context", contextId);
			closeContext(contextId);
		}

		try {
			BrowserContext playwrightContext;
			if (options != null) {
				playwrightContext = browser.newContext(options);
			} else {
				playwrightContext = browser.newContext();
			}

			BrowserContextImpl contextImpl = new BrowserContextImpl(playwrightContext);
			activeContexts.put(contextId, contextImpl);

			logger.info("Created new browser context with ID: {}", contextId);
			return contextImpl;

		} catch (Exception e) {
			logger.error("Failed to create browser context with ID {}: {}", contextId, e.getMessage());
			throw new RuntimeException("Failed to create browser context", e);
		}
	}

	/**
	 * Get an existing browser context by ID
	 * @param contextId The context ID
	 * @return The BrowserContextImpl, or null if not found
	 */
	public BrowserContextImpl getContext(String contextId) {
		BrowserContextImpl context = activeContexts.get(contextId);
		if (context != null && context.isClosed()) {
			// Remove closed contexts from our tracking
			activeContexts.remove(contextId);
			return null;
		}
		return context;
	}

	/**
	 * Close a specific browser context
	 * @param contextId The context ID to close
	 * @return true if the context was found and closed, false otherwise
	 */
	public boolean closeContext(String contextId) {
		return closeContext(contextId, null);
	}

	/**
	 * Close a specific browser context with a reason
	 * @param contextId The context ID to close
	 * @param reason The reason for closing
	 * @return true if the context was found and closed, false otherwise
	 */
	public boolean closeContext(String contextId, String reason) {
		BrowserContextImpl context = activeContexts.remove(contextId);
		if (context != null) {
			try {
				context.close(reason);
				logger.info("Closed browser context with ID: {} (reason: {})", 
						contextId, reason != null ? reason : "Not specified");
				return true;
			} catch (Exception e) {
				logger.error("Error closing browser context with ID {}: {}", contextId, e.getMessage());
				throw new RuntimeException("Failed to close browser context", e);
			}
		}
		return false;
	}

	/**
	 * Close all active browser contexts
	 */
	public void closeAllContexts() {
		closeAllContexts(null);
	}

	/**
	 * Close all active browser contexts with a reason
	 * @param reason The reason for closing all contexts
	 */
	public void closeAllContexts(String reason) {
		logger.info("Closing {} active browser contexts", activeContexts.size());
		
		for (Map.Entry<String, BrowserContextImpl> entry : activeContexts.entrySet()) {
			try {
				entry.getValue().close(reason);
				logger.debug("Closed context: {}", entry.getKey());
			} catch (Exception e) {
				logger.error("Error closing context {}: {}", entry.getKey(), e.getMessage());
			}
		}
		
		activeContexts.clear();
		logger.info("All browser contexts closed");
	}

	/**
	 * Get the number of active contexts
	 * @return The number of active contexts
	 */
	public int getActiveContextCount() {
		// Clean up any closed contexts first
		activeContexts.entrySet().removeIf(entry -> entry.getValue().isClosed());
		return activeContexts.size();
	}

	/**
	 * Check if a context with the given ID exists and is active
	 * @param contextId The context ID to check
	 * @return true if the context exists and is not closed, false otherwise
	 */
	public boolean hasActiveContext(String contextId) {
		BrowserContextImpl context = activeContexts.get(contextId);
		if (context != null && context.isClosed()) {
			activeContexts.remove(contextId);
			return false;
		}
		return context != null;
	}

	/**
	 * Get all active context IDs
	 * @return Set of active context IDs
	 */
	public java.util.Set<String> getActiveContextIds() {
		// Clean up closed contexts first
		activeContexts.entrySet().removeIf(entry -> entry.getValue().isClosed());
		return java.util.Set.copyOf(activeContexts.keySet());
	}
}
