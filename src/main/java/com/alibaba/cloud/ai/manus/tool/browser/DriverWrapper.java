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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.SameSiteAttribute; // Import for SameSiteAttribute

public class DriverWrapper {

	private final ObjectMapper objectMapper;

	private static final Logger log = LoggerFactory.getLogger(DriverWrapper.class);

	private Playwright playwright;

	private Page currentPage;

	private Browser browser;

	private final Path cookiePath;

	// Mixin class for Playwright Cookie deserialization
	abstract static class PlaywrightCookieMixin {

		@JsonCreator
		PlaywrightCookieMixin(@JsonProperty("name") String name, @JsonProperty("value") String value) {
		}

		@JsonProperty("domain")
		abstract Cookie setDomain(String domain);

		@JsonProperty("path")
		abstract Cookie setPath(String path);

		@JsonProperty("expires")
		abstract Cookie setExpires(Number expires);

		@JsonProperty("httpOnly")
		abstract Cookie setHttpOnly(boolean httpOnly);

		@JsonProperty("secure")
		abstract Cookie setSecure(boolean secure);

		@JsonProperty("sameSite")
		abstract Cookie setSameSite(SameSiteAttribute sameSite);

	}

	// Move ObjectMapper configuration to constructor

	public DriverWrapper(Playwright playwright, Browser browser, Page currentPage, String cookieDir,
			ObjectMapper objectMapper) {
		this.playwright = playwright;
		this.currentPage = currentPage;
		this.browser = browser;
	
		this.objectMapper = objectMapper;
		// Configure ObjectMapper
		this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		this.objectMapper.addMixIn(Cookie.class, PlaywrightCookieMixin.class);

		if (cookieDir == null || cookieDir.trim().isEmpty()) {
			this.cookiePath = Paths.get("playwright-cookies-default.json");
			log.warn("Warning: cookieDir was not provided or was empty. Using default cookie path: {}",
					this.cookiePath.toAbsolutePath());
		}
		else {
			this.cookiePath = Paths.get(cookieDir, "playwright-cookies.json");
		}
		loadCookies();
	}

	private void loadCookies() {
		if (this.currentPage == null) {
			log.info("Cannot load cookies: currentPage is null.");
			return;
		}
		if (!Files.exists(this.cookiePath)) {
			log.info("Cookie file not found, skipping cookie loading: {}", this.cookiePath.toAbsolutePath());
			return;
		}
		try {
			byte[] jsonData = Files.readAllBytes(this.cookiePath);
			if (jsonData.length == 0) {
				log.info("Cookie file is empty, skipping cookie loading: {}", this.cookiePath.toAbsolutePath());
				return;
			}
			List<Cookie> cookies = objectMapper.readValue(jsonData, new TypeReference<List<Cookie>>() {
			});
			if (cookies != null && !cookies.isEmpty()) {
				// Filter out expired cookies before loading
				List<Cookie> validCookies = filterExpiredCookies(cookies);
				if (!validCookies.isEmpty()) {
					this.currentPage.context().addCookies(validCookies);
					log.info("Loaded {} valid cookies (filtered {} expired) from: {}", validCookies.size(),
							cookies.size() - validCookies.size(), this.cookiePath.toAbsolutePath());
				}
				else {
					log.info("All cookies in file are expired, skipping cookie loading: {}",
							this.cookiePath.toAbsolutePath());
				}
				// Filter out expired cookies before loading
				List<Cookie> validCookies = filterExpiredCookies(cookies);
				if (!validCookies.isEmpty()) {
					this.currentPage.context().addCookies(validCookies);
					log.info("Loaded {} valid cookies (filtered {} expired) from: {}", validCookies.size(),
							cookies.size() - validCookies.size(), this.cookiePath.toAbsolutePath());
				}
				else {
					log.info("All cookies in file are expired, skipping cookie loading: {}",
							this.cookiePath.toAbsolutePath());
				}
			}
			else {
				log.info("No cookies found in file or cookies list was empty: {}", this.cookiePath.toAbsolutePath());
			}
		}
		catch (IOException e) {
			log.info("Failed to load cookies from {}: {}", this.cookiePath.toAbsolutePath(), e.getMessage());
		}
		catch (Exception e) {
			log.info("An unexpected error occurred while loading cookies from {}: {}", this.cookiePath.toAbsolutePath(),
					e.getMessage());
		}
	}

	/**
	 * Filter out expired cookies
	 * @param cookies List of cookies to filter
	 * @return List of valid (non-expired) cookies
	 */
	private List<Cookie> filterExpiredCookies(List<Cookie> cookies) {
		if (cookies == null || cookies.isEmpty()) {
			return Collections.emptyList();
		}

		long currentTimeSeconds = System.currentTimeMillis() / 1000;

		return cookies.stream().filter(cookie -> {
			// Cookies without expiration (session cookies) are always valid
			if (cookie.expires == null || cookie.expires == -1) {
				return true;
			}

			// Check if cookie expiration time is in the future
			// expires is in seconds since epoch
			double expiresValue = cookie.expires.doubleValue();
			return expiresValue > currentTimeSeconds;
		}).toList();
	}

	private void saveCookies() {
		if (this.currentPage == null) {
			log.info("Cannot save cookies: currentPage is null.");
			return;
		}
		try {
			List<Cookie> cookies = this.currentPage.context().cookies();
			if (cookies == null) {
				cookies = Collections.emptyList();
			}

			// Filter out expired cookies before saving
			List<Cookie> validCookies = filterExpiredCookies(cookies);

			Path parentDir = this.cookiePath.getParent();
			if (parentDir != null && !Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}

			byte[] jsonData = objectMapper.writeValueAsBytes(validCookies);
			Files.write(this.cookiePath, jsonData);
			log.info("Saved {} valid cookies (filtered {} expired) to: {}", validCookies.size(),
					cookies.size() - validCookies.size(), this.cookiePath.toAbsolutePath());
		}
		catch (IOException e) {
			log.info("Failed to save cookies to {}: {}", this.cookiePath.toAbsolutePath(), e.getMessage());
		}
		catch (Exception e) {
			log.info("An unexpected error occurred while saving cookies to {}: {}", this.cookiePath.toAbsolutePath(),
					e.getMessage());
		}
	}

	/**
	 * Public method to save cookies (can be called after operations)
	 */
	public void persistCookies() {
		saveCookies();
		// Also save storage state for better persistence (includes cookies, localStorage,
		// etc.)
		saveStorageState();
	}

	/**
	 * Save browser context storage state (cookies, localStorage, sessionStorage, etc.)
	 * This provides better persistence than just saving cookies
	 */
	private void saveStorageState() {
		if (this.currentPage == null) {
			log.debug("Cannot save storage state: currentPage is null.");
			return;
		}
		try {
			com.microsoft.playwright.BrowserContext context = this.currentPage.context();
			if (context == null) {
				log.debug("Cannot save storage state: browser context is null.");
				return;
			}

			// Save storage state to file (includes cookies, localStorage, sessionStorage)
			java.nio.file.Path storageStatePath = this.cookiePath.getParent().resolve("storage-state.json");
			// Playwright storageState method uses StorageStateOptions
			context.storageState(
					new com.microsoft.playwright.BrowserContext.StorageStateOptions().setPath(storageStatePath));
			log.debug("Storage state saved successfully to: {}", storageStatePath.toAbsolutePath());
		}
		catch (Exception e) {
			log.debug("Failed to save storage state: {}", e.getMessage());
		}
	}

	/**
	 * Get AriaElementHolder, refreshing from current page if needed
	 * @return AriaElementHolder instance
	 */
	public AriaElementHolder getAriaElementHolder() {
		if (ariaElementHolder == null && currentPage != null) {
			refreshAriaElementHolder();
		}
		return ariaElementHolder;
	}

	/**
	 * Save browser context storage state (cookies, localStorage, sessionStorage, etc.)
	 * This provides better persistence than just saving cookies
	 */
	private void saveStorageState() {
		if (this.currentPage == null) {
			log.debug("Cannot save storage state: currentPage is null.");
			return;
		}
		try {
			com.microsoft.playwright.BrowserContext context = this.currentPage.context();
			if (context == null) {
				log.debug("Cannot save storage state: browser context is null.");
				return;
			}

			// Save storage state to file (includes cookies, localStorage, sessionStorage)
			java.nio.file.Path storageStatePath = this.cookiePath.getParent().resolve("storage-state.json");
			// Playwright storageState method uses StorageStateOptions
			context.storageState(
					new com.microsoft.playwright.BrowserContext.StorageStateOptions().setPath(storageStatePath));
			log.debug("Storage state saved successfully to: {}", storageStatePath.toAbsolutePath());
		}
		catch (Exception e) {
			log.debug("Failed to save storage state: {}", e.getMessage());
		}
	}

	

	public Playwright getPlaywright() {
		return playwright;
	}

	public void setPlaywright(Playwright playwright) {
		this.playwright = playwright;
	}

	public Page getCurrentPage() {
		return currentPage;
	}

	public void setCurrentPage(Page currentPage) {
		this.currentPage = currentPage;
		// Refresh ARIA element holder when page changes
		// Potentially load cookies if page context changes and it's desired
		// loadCookies();
	}

	public Browser getBrowser() {
		return browser;
	}

	public void setBrowser(Browser browser) {
		this.browser = browser;
	}

	public void close() {
		log.info("Closing DriverWrapper and all associated resources");

		// Save cookies first, before closing anything
		try {
			saveCookies();
		}
		catch (Exception e) {
			log.warn("Failed to save cookies during close: {}", e.getMessage());
		}

		// Close current page first
		if (this.currentPage != null) {
			try {
				if (!this.currentPage.isClosed()) {
					this.currentPage.close();
					log.debug("Successfully closed current page");
				}
				else {
					log.debug("Current page was already closed");
				}
			}
			catch (Exception e) {
				log.warn("Error closing current page: {}", e.getMessage());
			}
			finally {
				this.currentPage = null;
			}
		}

		// Close browser context (if using browser.newContext())
		// Get context from page if available, or close all contexts from browser
		if (this.browser != null) {
			try {
				// If we have a page, try to get its context and close it
				// Otherwise, close all contexts from the browser
				if (this.currentPage != null && !this.currentPage.isClosed()) {
					try {
						com.microsoft.playwright.BrowserContext context = this.currentPage.context();
						if (context != null) {
							context.close();
							log.debug("Successfully closed browser context");
						}
					}
					catch (Exception e) {
						log.warn("Error closing browser context from page: {}", e.getMessage());
					}
				}

				// Close browser if still connected
				if (this.browser.isConnected()) {
					this.browser.close();
					log.debug("Successfully closed browser");
				}
				else {
					log.debug("Browser was already disconnected");
				}
			}
			catch (Exception e) {
				log.warn("Error closing browser: {}", e.getMessage());
			}
			finally {
				this.browser = null;
			}
		}

		log.info("DriverWrapper close operation completed");

		// Note: Playwright instance itself is usually managed by the service that created
		// it.
		// Closing it here might be premature if other DriverWrappers use the same
		// Playwright instance, so we leave it to the service to manage.
	}

}
