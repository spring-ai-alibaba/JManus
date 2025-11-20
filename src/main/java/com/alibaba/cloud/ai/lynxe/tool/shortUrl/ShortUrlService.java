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
package com.alibaba.cloud.ai.lynxe.tool.shortUrl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Short URL service implementation for managing URL mappings Provides planId-level
 * isolation with in-memory storage only
 */
@Service
public class ShortUrlService {

	private static final Logger log = LoggerFactory.getLogger(ShortUrlService.class);

	/**
	 * Short URL prefix constant Public so other classes can use it to check if a URL is a
	 * short URL
	 */
	public static final String SHORT_URL_PREFIX = "http://s@Url.a/";

	// Map to store URL mappings per planId: planId -> (shortUrl -> realUrl)
	private final ConcurrentHashMap<String, Map<String, String>> planUrlMappings = new ConcurrentHashMap<>();

	// Map to store reverse mappings per planId: planId -> (realUrl -> shortUrl)
	private final ConcurrentHashMap<String, Map<String, String>> planReverseMappings = new ConcurrentHashMap<>();

	// Map to store counters per planId: planId -> counter
	private final ConcurrentHashMap<String, Integer> planCounters = new ConcurrentHashMap<>();

	// Locks per planId for thread safety
	private final ConcurrentHashMap<String, Lock> planLocks = new ConcurrentHashMap<>();

	/**
	 * Constructor
	 */
	public ShortUrlService() {
	}

	/**
	 * Get or create lock for a planId
	 */
	private Lock getLockForPlan(String planId) {
		return planLocks.computeIfAbsent(planId, k -> new ReentrantLock());
	}

	/**
	 * Get or create mappings map for a planId
	 */
	private Map<String, String> getMappingsForPlan(String planId) {
		return planUrlMappings.computeIfAbsent(planId, k -> new HashMap<>());
	}

	/**
	 * Get or create reverse mappings map for a planId
	 */
	private Map<String, String> getReverseMappingsForPlan(String planId) {
		return planReverseMappings.computeIfAbsent(planId, k -> new HashMap<>());
	}

	/**
	 * Get or create counter for a planId
	 */
	private int getNextCounter(String planId) {
		return planCounters.compute(planId, (k, v) -> (v == null) ? 1 : v + 1);
	}

	/**
	 * Create a short URL for the given real URL
	 * @param planId Plan ID for URL isolation
	 * @param realUrl The real URL to shorten
	 * @return The short URL (e.g., http://s@Url.a/1)
	 */
	public String createShortUrl(String planId, String realUrl) {
		if (planId == null || realUrl == null || realUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("planId and realUrl cannot be null or empty");
		}

		Lock lock = getLockForPlan(planId);
		lock.lock();
		try {
			// Check if URL already exists in reverse mappings
			Map<String, String> reverseMappings = getReverseMappingsForPlan(planId);
			String existingShortUrl = reverseMappings.get(realUrl);
			if (existingShortUrl != null) {
				log.debug("Short URL already exists for realUrl: {} -> {}", realUrl, existingShortUrl);
				return existingShortUrl;
			}

			// Create new short URL
			int counter = getNextCounter(planId);
			String shortUrl = SHORT_URL_PREFIX + counter;

			// Store mappings
			Map<String, String> mappings = getMappingsForPlan(planId);
			mappings.put(shortUrl, realUrl);
			reverseMappings.put(realUrl, shortUrl);

			log.debug("Created short URL mapping: {} -> {} for planId: {}", shortUrl, realUrl, planId);

			return shortUrl;
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Check if a URL is a short URL
	 * @param url The URL to check
	 * @return true if the URL is a short URL, false otherwise
	 */
	public static boolean isShortUrl(String url) {
		if (url == null || url.trim().isEmpty()) {
			return false;
		}
		return url.startsWith(SHORT_URL_PREFIX);
	}

	/**
	 * Get the real URL from a short URL
	 * @param planId Plan ID for URL isolation
	 * @param shortUrl The short URL (e.g., http://s@Url.a/1)
	 * @return The real URL, or null if not found
	 */
	public String getRealUrl(String planId, String shortUrl) {
		if (planId == null || shortUrl == null || shortUrl.trim().isEmpty()) {
			return null;
		}

		Map<String, String> mappings = planUrlMappings.get(planId);
		if (mappings != null) {
			return mappings.get(shortUrl);
		}

		return null;
	}

	/**
	 * Check if a short URL exists
	 * @param planId Plan ID for URL isolation
	 * @param shortUrl The short URL to check
	 * @return true if the short URL exists, false otherwise
	 */
	public boolean hasShortUrl(String planId, String shortUrl) {
		if (planId == null || shortUrl == null || shortUrl.trim().isEmpty()) {
			return false;
		}

		Map<String, String> mappings = planUrlMappings.get(planId);
		return mappings != null && mappings.containsKey(shortUrl);
	}

	/**
	 * Clear all URL mappings for a specific plan
	 * @param planId Plan ID
	 */
	public void clearMappings(String planId) {
		if (planId == null) {
			return;
		}

		Lock lock = getLockForPlan(planId);
		lock.lock();
		try {
			planUrlMappings.remove(planId);
			planReverseMappings.remove(planId);
			planCounters.remove(planId);
			planLocks.remove(planId);

			log.info("Cleared all URL mappings for planId: {}", planId);
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Get the short URL for a real URL if it already exists, otherwise create a new one
	 * @param planId Plan ID for URL isolation
	 * @param realUrl The real URL
	 * @return The short URL (existing or newly created)
	 */
	public String getOrCreateShortUrl(String planId, String realUrl) {
		if (planId == null || realUrl == null || realUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("planId and realUrl cannot be null or empty");
		}

		Lock lock = getLockForPlan(planId);
		lock.lock();
		try {
			// Check if URL already exists
			Map<String, String> reverseMappings = getReverseMappingsForPlan(planId);
			String existingShortUrl = reverseMappings.get(realUrl);
			if (existingShortUrl != null) {
				return existingShortUrl;
			}

			// Create new short URL
			return createShortUrl(planId, realUrl);
		}
		finally {
			lock.unlock();
		}
	}

	/**
	 * Get all mappings for a plan (for debugging/inspection)
	 * @param planId Plan ID
	 * @return Map of shortUrl -> realUrl, or null if planId is invalid
	 */
	public Map<String, String> getAllMappings(String planId) {
		if (planId == null) {
			return null;
		}

		Map<String, String> mappings = planUrlMappings.get(planId);
		return mappings != null ? new HashMap<>(mappings) : new HashMap<>();
	}

	/**
	 * Get the real URL from short URL (compatibility method for DriverWrapper) Always
	 * uses rootPlanId for short URL lifetime management
	 * @param rootPlanId Root plan ID (required)
	 * @param shortUrl The short URL (e.g., http://s@Url.a/1)
	 * @return The real URL, or null if not found
	 */
	public String getRealUrlFromShortUrl(String rootPlanId, String shortUrl) {
		if (rootPlanId == null) {
			log.warn("Cannot resolve short URL: rootPlanId is null");
			return null;
		}
		return getRealUrl(rootPlanId, shortUrl);
	}

	/**
	 * Add a URL mapping (shortUrl -> realUrl) (compatibility method for DriverWrapper)
	 * Always uses rootPlanId for short URL lifetime management
	 * @param rootPlanId Root plan ID (required)
	 * @param realUrl The real URL
	 * @return The short URL (e.g., http://s@Url.a/1)
	 */
	public synchronized String addUrlMapping(String rootPlanId, String realUrl) {
		if (rootPlanId == null) {
			throw new IllegalArgumentException("Cannot add URL mapping: rootPlanId is null");
		}
		return getOrCreateShortUrl(rootPlanId, realUrl);
	}

	/**
	 * Clear all URL mappings (compatibility method for DriverWrapper) Always uses
	 * rootPlanId for short URL lifetime management
	 * @param rootPlanId Root plan ID (required)
	 */
	public void clearUrlMappings(String rootPlanId) {
		if (rootPlanId != null) {
			clearMappings(rootPlanId);
		}
	}

}
