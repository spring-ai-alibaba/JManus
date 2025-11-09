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

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.alibaba.cloud.ai.manus.config.IManusProperties;
import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.manus.tool.innerStorage.SmartContentSavingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;

import jakarta.annotation.PreDestroy;

@Service
@Primary
public class ChromeDriverService implements IChromeDriverService {

	private static final Logger log = LoggerFactory.getLogger(ChromeDriverService.class);

	private final ConcurrentHashMap<String, DriverWrapper> drivers = new ConcurrentHashMap<>();

	private final Lock driverLock = new ReentrantLock();

	private ManusProperties manusProperties;

	private SmartContentSavingService innerStorageService;

	private UnifiedDirectoryManager unifiedDirectoryManager;

	private final ObjectMapper objectMapper;

	@Autowired(required = false)
	private SpringBootPlaywrightInitializer playwrightInitializer;

	/**
	 * Shared directory for storing cookies
	 */
	/**
	 * Shared directory for storing cookies
	 */
	private String sharedDir;

	/**
	 * Get current shared directory
	 */
	public String getSharedDir() {
		return sharedDir;
	}

	/**
	 * Save all cookies from drivers to global shared directory (cookies.json)
	 */
	public void saveCookiesToSharedDir() {
		// Get the first available driver
		DriverWrapper driver = drivers.values().stream().findFirst().orElse(null);
		if (driver == null) {
			log.warn("No driver found for saving cookies");
			return;
		}
		try {
			List<com.microsoft.playwright.options.Cookie> cookies = driver.getCurrentPage().context().cookies();
			String cookieFile = sharedDir + "/cookies.json";
			try (java.io.FileWriter writer = new java.io.FileWriter(cookieFile)) {
				writer.write(objectMapper.writeValueAsString(cookies));
			}
			log.info("Cookies saved to {}", cookieFile);
		}
		catch (Exception e) {
			log.error("Failed to save cookies", e);
		}
	}

	/**
	 * Load cookies from global shared directory to all drivers
	 */
	public void loadCookiesFromSharedDir() {
		String cookieFile = sharedDir + "/cookies.json";
		java.io.File file = new java.io.File(cookieFile);
		if (!file.exists()) {
			log.warn("Cookie file does not exist: {}", cookieFile);
			return;
		}
		try (java.io.FileReader reader = new java.io.FileReader(cookieFile)) {
			// Replace FastJSON's JSON.parseArray with Jackson's objectMapper.readValue
			List<com.microsoft.playwright.options.Cookie> cookies = objectMapper.readValue(reader,
					new TypeReference<List<com.microsoft.playwright.options.Cookie>>() {
					});
			for (DriverWrapper driver : drivers.values()) {
				driver.getCurrentPage().context().addCookies(cookies);
			}
			log.info("Cookies loaded from {} to all drivers", cookieFile);
		}
		catch (Exception e) {
			log.error("Failed to load cookies for all drivers", e);
		}
	}

	public ChromeDriverService(ManusProperties manusProperties, SmartContentSavingService innerStorageService,
			UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper) {
		this.manusProperties = manusProperties;
		this.innerStorageService = innerStorageService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.objectMapper = objectMapper;
		// Use UnifiedDirectoryManager to get the shared directory for playwright
		try {
			java.nio.file.Path playwrightDir = unifiedDirectoryManager.getWorkingDirectory().resolve("playwright");
			unifiedDirectoryManager.ensureDirectoryExists(playwrightDir);
			this.sharedDir = playwrightDir.toString();
		}
		catch (java.io.IOException e) {
			log.error("Failed to create playwright directory", e);
			this.sharedDir = unifiedDirectoryManager.getWorkingDirectory().resolve("playwright").toString();
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			log.info("JVM shutting down - cleaning up Playwright processes");
			cleanupAllPlaywrightProcesses();
		}));
	}

	public DriverWrapper getDriver(String planId) {
		if (planId == null) {
			throw new IllegalArgumentException("planId cannot be null");
		}

		DriverWrapper currentDriver = drivers.get(planId);
		if (currentDriver != null) {
			// Check if the existing driver is still healthy
			if (isDriverHealthy(currentDriver)) {
				return currentDriver;
			}
			else {
				log.warn("Existing driver for planId {} is unhealthy, recreating", planId);
				closeDriverForPlan(planId);
				currentDriver = null;
			}
		}

		try {
			if (!driverLock.tryLock(30, TimeUnit.SECONDS)) {
				throw new RuntimeException("Failed to acquire driver lock within 30 seconds for planId: " + planId);
			}
			try {
				currentDriver = drivers.get(planId);
				if (currentDriver != null && isDriverHealthy(currentDriver)) {
					return currentDriver;
				}
				log.info("Creating new Playwright Browser instance for planId: {}", planId);
				currentDriver = createNewDriverWithRetry(planId);
				if (currentDriver != null) {
					drivers.put(planId, currentDriver);
					log.info("Successfully created and cached new driver for planId: {}", planId);
				}
				else {
					log.error("Failed to create new driver for planId: {}. All retry attempts failed.", planId);
					throw new RuntimeException("Failed to create new driver for planId: " + planId);
				}
			}
			finally {
				driverLock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for driver lock for planId: " + planId, e);
		}
		catch (Exception e) {
			log.error("Unexpected error while getting driver for planId: {}", planId, e);
			throw new RuntimeException("Failed to get driver for planId: " + planId, e);
		}

		return currentDriver;
	}

	private void cleanupAllPlaywrightProcesses() {
		try {
			drivers.clear();
			log.info("Successfully cleaned up all Playwright processes	");
		}
		catch (Exception e) {
			log.error("Error cleaning up Browser processes", e);
		}
	}

	public void closeDriverForPlan(String planId) {
		DriverWrapper driver = drivers.remove(planId);
		if (driver != null) {
			driver.close();
		}
	}

	/**
	 * Create new driver with retry mechanism
	 */
	private DriverWrapper createNewDriverWithRetry(String planId) {
		int maxRetries = 3;
		int retryDelay = 2000; // 2 seconds

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			try {
				log.info("Creating new browser driver for planId: {} (attempt {}/{})", planId, attempt, maxRetries);
				DriverWrapper driver = createDriverInstance();
				if (driver != null && isDriverHealthy(driver)) {
					log.info("Successfully created healthy driver for planId: {} on attempt {}", planId, attempt);
					return driver;
				}
				else {
					log.warn("Created driver for planId: {} is not healthy on attempt {}", planId, attempt);
					if (driver != null) {
						try {
							driver.close();
						}
						catch (Exception e) {
							log.warn("Error closing unhealthy driver: {}", e.getMessage());
						}
					}
				}
			}
			catch (PlaywrightException e) {
				log.error("Playwright error on attempt {} for planId: {}: {}", attempt, planId, e.getMessage());
			}
			catch (Exception e) {
				log.error("Unexpected error on attempt {} for planId: {}: {}", attempt, planId, e.getMessage(), e);
			}

			if (attempt < maxRetries) {
				try {
					log.info("Waiting {} ms before retry attempt {} for planId: {}", retryDelay, attempt + 1, planId);
					Thread.sleep(retryDelay);
					retryDelay *= 2; // Exponential backoff
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					log.error("Interrupted during retry delay for planId: {}", planId);
					break;
				}
			}
		}

		log.error("Failed to create driver for planId: {} after {} attempts", planId, maxRetries);
		return null;
	}

	/**
	 * Check if driver is healthy and responsive
	 */
	private boolean isDriverHealthy(DriverWrapper driver) {
		if (driver == null) {
			return false;
		}

		try {
			// Check if browser is connected
			Browser browser = driver.getBrowser();
			if (browser == null || !browser.isConnected()) {
				log.debug("Driver health check failed: browser not connected");
				return false;
			}

			// Check if current page is accessible
			Page page = driver.getCurrentPage();
			if (page == null || page.isClosed()) {
				log.debug("Driver health check failed: page is null or closed");
				return false;
			}

			// Try a simple operation with timeout
			try {
				page.evaluate("() => document.readyState");
				return true;
			}
			catch (TimeoutError e) {
				log.debug("Driver health check failed: page evaluation timeout");
				return false;
			}
			catch (PlaywrightException e) {
				log.debug("Driver health check failed: playwright exception: {}", e.getMessage());
				return false;
			}

		}
		catch (Exception e) {
			log.debug("Driver health check failed with exception: {}", e.getMessage());
			return false;
		}
	}

	/**
	 * Create browser driver instance with comprehensive error handling Uses
	 * browser.newContext() for better isolation and resource management
	 */
	private DriverWrapper createDriverInstance() {
		Playwright playwright = null;
		Browser browser = null;
		BrowserContext browserContext = null;
		Page page = null;

		try {
			// Set system properties for Playwright configuration
			System.setProperty("playwright.browsers.path", System.getProperty("user.home") + "/.cache/ms-playwright");
			System.setProperty("playwright.driver.tmpdir", System.getProperty("java.io.tmpdir"));
			System.setProperty("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");

			// Create Playwright instance with error handling
			try {
				if (playwrightInitializer != null && playwrightInitializer.canInitialize()) {
					log.info("Using SpringBootPlaywrightInitializer");
					playwright = playwrightInitializer.createPlaywright();
				}
				else {
					log.info("Using standard Playwright initialization");
					playwright = Playwright.create();
				}
				log.info("Successfully created Playwright instance");
			}
			catch (PlaywrightException e) {
				log.error("Playwright initialization failed: {}", e.getMessage(), e);
				throw new RuntimeException("Failed to initialize Playwright: " + e.getMessage(), e);
			}
			catch (Exception e) {
				log.error("Unexpected error during Playwright initialization: {}", e.getMessage(), e);
				throw new RuntimeException("Unexpected error during Playwright initialization", e);
			}

			// Get browser type with error handling
			BrowserType browserType;
			try {
				browserType = getBrowserTypeFromEnv(playwright);
				log.info("Using browser type: {}", browserType.name());
			}
			catch (Exception e) {
				log.error("Failed to get browser type: {}", e.getMessage(), e);
				throw new RuntimeException("Failed to get browser type", e);
			}

			// Configure browser launch options
			BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
			try {
				// Basic configuration with error handling for user agent
				String userAgent;
				try {
					userAgent = getRandomUserAgent();
				}
				catch (Exception e) {
					log.warn("Failed to get random user agent, using default: {}", e.getMessage());
					userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
				}

				launchOptions
					.setArgs(Arrays.asList("--remote-allow-origins=*", "--disable-blink-features=AutomationControlled",
							"--disable-infobars", "--disable-notifications", "--disable-dev-shm-usage", "--no-sandbox", // Add
																														// for
																														// better
																														// stability
							"--disable-gpu", // Add for headless stability
							"--lang=zh-CN,zh,en-US,en", "--user-agent=" + userAgent, "--window-size=1920,1080"));

				// Set headless mode based on configuration
				if (manusProperties.getBrowserHeadless()) {
					log.info("Enable Playwright headless mode");
					launchOptions.setHeadless(true);
				}
				else {
					log.info("Enable Playwright non-headless mode");
					launchOptions.setHeadless(false);
				}

				// Set timeout for browser launch
				launchOptions.setTimeout(60000); // 60 seconds timeout for browser launch

			}
			catch (Exception e) {
				log.error("Failed to configure browser launch options: {}", e.getMessage(), e);
				throw new RuntimeException("Failed to configure browser launch options", e);
			}

			// Launch browser with error handling
			try {
				browser = browserType.launch(launchOptions);
				log.info("Successfully launched Playwright Browser instance");

				// Verify browser connection
				if (!browser.isConnected()) {
					throw new RuntimeException("Browser launched but is not connected");
				}

			}
			catch (PlaywrightException e) {
				log.error("Failed to launch browser: {}", e.getMessage(), e);
				throw new RuntimeException("Failed to launch browser: " + e.getMessage(), e);
			}
			catch (Exception e) {
				log.error("Unexpected error during browser launch: {}", e.getMessage(), e);
				throw new RuntimeException("Unexpected error during browser launch", e);
			}

			// Create browser context with error handling
			// Using browser.newContext() provides better isolation and resource
			// management
			try {
				// Check browser is still connected before creating context
				if (!browser.isConnected()) {
					throw new RuntimeException("Browser is not connected, cannot create context");
				}

				// Configure context options
				Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();

				// Set viewport size
				contextOptions.setViewportSize(1920, 1080);

				// Set user agent
				try {
					String userAgent = getRandomUserAgent();
					contextOptions.setUserAgent(userAgent);
				}
				catch (Exception e) {
					log.warn("Failed to set user agent in context options: {}", e.getMessage());
				}

				// Set locale
				contextOptions.setLocale("zh-CN");

				// Set timezone if needed
				// contextOptions.setTimezoneId("Asia/Shanghai");

				// Try to load storage state (cookies, localStorage, etc.) for persistence
				// This provides better cookie persistence than manual cookie loading
				try {
					java.nio.file.Path storageStatePath = java.nio.file.Paths.get(sharedDir, "storage-state.json");
					if (java.nio.file.Files.exists(storageStatePath)) {
						contextOptions.setStorageStatePath(storageStatePath);
						log.info("Loading browser storage state from: {}", storageStatePath);
					}
					else {
						log.debug("Storage state file not found, creating new context without storage state: {}",
								storageStatePath);
					}
				}
				catch (Exception e) {
					log.warn("Failed to set storage state path, continuing without it: {}", e.getMessage());
				}

				// Create context with timeout
				browserContext = browser.newContext(contextOptions);
				log.info("Successfully created browser context");

				// Verify context is valid
				if (browserContext == null) {
					throw new RuntimeException("Browser context was created but is null");
				}

			}
			catch (PlaywrightException e) {
				log.error("Failed to create browser context: {}", e.getMessage(), e);
				// Check if it's a connection issue
				if (e.getMessage() != null && (e.getMessage().contains("Target closed")
						|| e.getMessage().contains("Browser has been closed")
						|| e.getMessage().contains("Connection closed"))) {
					throw new RuntimeException("Browser connection lost while creating context: " + e.getMessage(), e);
				}
				throw new RuntimeException("Failed to create browser context: " + e.getMessage(), e);
			}
			catch (Exception e) {
				log.error("Unexpected error during browser context creation: {}", e.getMessage(), e);
				throw new RuntimeException("Unexpected error during browser context creation", e);
			}

			// Create new page from context with error handling
			try {
				// browserContext is guaranteed to be non-null here due to previous
				// validation
				
				// Close any pages restored from storage state before creating new page
				// Storage state may restore previous tabs, which we don't want to keep
				try {
					List<Page> existingPages = browserContext.pages();
					if (!existingPages.isEmpty()) {
						log.info("Found {} existing page(s) from storage state, closing them", existingPages.size());
						for (Page existingPage : existingPages) {
							try {
								if (!existingPage.isClosed()) {
									existingPage.close();
									log.debug("Closed existing page from storage state: {}", existingPage.url());
								}
							}
							catch (Exception e) {
								log.warn("Failed to close existing page: {}", e.getMessage());
							}
						}
					}
				}
				catch (Exception e) {
					log.warn("Failed to check/close existing pages from storage state: {}", e.getMessage());
				}
				
				page = browserContext.newPage();
				log.info("Successfully created new page from context");

				// Verify page is not closed
				if (page.isClosed()) {
					throw new RuntimeException("Page was created but is already closed");
				}

			}
			catch (PlaywrightException e) {
				log.error("Failed to create new page from context: {}", e.getMessage(), e);
				// Check if context was closed
				if (e.getMessage() != null && (e.getMessage().contains("Target closed")
						|| e.getMessage().contains("Context has been closed"))) {
					throw new RuntimeException("Browser context was closed while creating page: " + e.getMessage(), e);
				}
				throw new RuntimeException("Failed to create new page: " + e.getMessage(), e);
			}
			catch (Exception e) {
				log.error("Unexpected error during page creation: {}", e.getMessage(), e);
				throw new RuntimeException("Unexpected error during page creation", e);
			}

			// Configure page timeouts with error handling
			try {
				Integer timeout = manusProperties.getBrowserRequestTimeout();
				if (timeout != null && timeout > 0) {
					log.info("Setting browser page timeout to {} seconds", timeout);
					page.setDefaultTimeout(timeout * 1000); // Convert to milliseconds
					page.setDefaultNavigationTimeout(timeout * 1000);
					// Also set context-level timeout
					browserContext.setDefaultTimeout(timeout * 1000);
					browserContext.setDefaultNavigationTimeout(timeout * 1000);
				}
				else {
					// Set reasonable default timeouts
					log.info("Setting default browser timeouts (30 seconds)");
					page.setDefaultTimeout(30000);
					page.setDefaultNavigationTimeout(30000);
					browserContext.setDefaultTimeout(30000);
					browserContext.setDefaultNavigationTimeout(30000);
				}
			}
			catch (Exception e) {
				log.warn("Failed to set page/context timeouts, continuing with defaults: {}", e.getMessage());
			}

			// Create and return DriverWrapper with error handling
			try {
				DriverWrapper wrapper = new DriverWrapper(playwright, browser, page, this.sharedDir, objectMapper);
				log.info("Successfully created DriverWrapper instance with browser context");
				return wrapper;
			}
			catch (Exception e) {
				log.error("Failed to create DriverWrapper: {}", e.getMessage(), e);
				throw new RuntimeException("Failed to create DriverWrapper", e);
			}

		}
		catch (Exception e) {
			// Comprehensive cleanup on any error
			log.error("Driver creation failed, performing cleanup: {}", e.getMessage(), e);

			// Close page if created
			if (page != null && !page.isClosed()) {
				try {
					page.close();
					log.debug("Cleaned up page after error");
				}
				catch (Exception ex) {
					log.warn("Failed to close page during cleanup: {}", ex.getMessage());
				}
			}

			// Close browser context if created
			if (browserContext != null) {
				try {
					browserContext.close();
					log.debug("Cleaned up browser context after error");
				}
				catch (Exception ex) {
					log.warn("Failed to close browser context during cleanup: {}", ex.getMessage());
				}
			}

			// Close browser if created
			if (browser != null && browser.isConnected()) {
				try {
					browser.close();
					log.debug("Cleaned up browser after error");
				}
				catch (Exception ex) {
					log.warn("Failed to close browser during cleanup: {}", ex.getMessage());
				}
			}

			// Close playwright if created
			if (playwright != null) {
				try {
					playwright.close();
					log.debug("Cleaned up playwright after error");
				}
				catch (Exception ex) {
					log.warn("Failed to close playwright during cleanup: {}", ex.getMessage());
				}
			}

			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			}
			else {
				throw new RuntimeException("Failed to initialize Playwright Browser", e);
			}
		}
	}

	/**
	 * Get browser type, supports environment variable configuration
	 */
	private BrowserType getBrowserTypeFromEnv(Playwright playwright) {
		String browserName = System.getenv("BROWSER");
		if (browserName == null) {
			browserName = "chromium";
		}

		switch (browserName.toLowerCase()) {
			case "webkit":
				return playwright.webkit();
			case "firefox":
				return playwright.firefox();
			case "chromium":
			default:
				return playwright.chromium();
		}
	}

	private String getRandomUserAgent() {
		List<String> userAgents = Arrays.asList(
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
				"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0");
		return userAgents.get(new Random().nextInt(userAgents.size()));
	}

	@PreDestroy
	public void cleanup() {
		log.info("Spring container shutting down - cleaning up Browser resources");
		cleanupAllPlaywrightProcesses();
	}

	public void setManusProperties(IManusProperties manusProperties) {
		this.manusProperties = (ManusProperties) manusProperties;
	}

	public IManusProperties getManusProperties() {
		return manusProperties;
	}

	public SmartContentSavingService getInnerStorageService() {
		return innerStorageService;
	}

	public UnifiedDirectoryManager getUnifiedDirectoryManager() {
		return unifiedDirectoryManager;
	}

}
