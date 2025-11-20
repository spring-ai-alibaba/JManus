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
package com.alibaba.cloud.ai.lynxe.mcp.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.mcp.config.McpProperties;
import com.alibaba.cloud.ai.lynxe.mcp.model.po.McpConfigEntity;
import com.alibaba.cloud.ai.lynxe.mcp.model.po.McpConfigStatus;
import com.alibaba.cloud.ai.lynxe.mcp.model.vo.McpServiceEntity;
import com.alibaba.cloud.ai.lynxe.mcp.repository.McpConfigRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * MCP Cache Manager - supports seamless cache updates
 */
@Component
public class McpCacheManager {

	private static final Logger logger = LoggerFactory.getLogger(McpCacheManager.class);

	/**
	 * MCP connection result wrapper class
	 */
	private static class McpConnectionResult {

		private final boolean success;

		private final McpServiceEntity serviceEntity;

		private final String serverName;

		private final String errorMessage;

		private final long connectionTime;

		private final int retryCount;

		private final String connectionType;

		public McpConnectionResult(boolean success, McpServiceEntity serviceEntity, String serverName,
				String errorMessage, long connectionTime, int retryCount, String connectionType) {
			this.success = success;
			this.serviceEntity = serviceEntity;
			this.serverName = serverName;
			this.errorMessage = errorMessage;
			this.connectionTime = connectionTime;
			this.retryCount = retryCount;
			this.connectionType = connectionType;
		}

		public boolean isSuccess() {
			return success;
		}

		public McpServiceEntity getServiceEntity() {
			return serviceEntity;
		}

		public String getServerName() {
			return serverName;
		}

		public String getErrorMessage() {
			return errorMessage;
		}

		public long getConnectionTime() {
			return connectionTime;
		}

		public int getRetryCount() {
			return retryCount;
		}

		public String getConnectionType() {
			return connectionType;
		}

	}

	/**
	 * Double cache wrapper - implements seamless updates
	 */
	private class DoubleCacheWrapper {

		private volatile Map<String, McpServiceEntity> activeCache = new ConcurrentHashMap<>();

		private volatile Map<String, McpServiceEntity> backgroundCache = new ConcurrentHashMap<>();

		private final Object switchLock = new Object();

		/**
		 * Atomically switch cache and close old clients to prevent resource leaks
		 */
		public void switchCache() {
			synchronized (switchLock) {
				// Close all clients in the old active cache before switching
				closeAllClients(activeCache);

				Map<String, McpServiceEntity> temp = activeCache;
				activeCache = backgroundCache;
				backgroundCache = temp;
			}
		}

		/**
		 * Get current active cache
		 */
		public Map<String, McpServiceEntity> getActiveCache() {
			return activeCache;
		}

		/**
		 * Get background cache (for cleanup purposes)
		 */
		public Map<String, McpServiceEntity> getBackgroundCache() {
			return backgroundCache;
		}

		/**
		 * Update background cache
		 */
		public void updateBackgroundCache(Map<String, McpServiceEntity> newCache) {
			// Close old background cache clients before replacing
			closeAllClients(backgroundCache);
			backgroundCache = new ConcurrentHashMap<>(newCache);
		}

	}

	private final McpConnectionFactory connectionFactory;

	private final McpConfigRepository mcpConfigRepository;

	private final ManusProperties manusProperties;

	// Double cache wrapper
	private final DoubleCacheWrapper doubleCache = new DoubleCacheWrapper();

	/**
	 * Close all MCP clients in a cache map to prevent resource leaks. This method handles
	 * all transport types (SSE, STDIO, STREAMING) and ensures proper cleanup of:
	 * <ul>
	 * <li>SSE: HttpClient instances and SelectorManager threads</li>
	 * <li>STDIO: Child processes and their resources</li>
	 * <li>STREAMING: HTTP connections and streams</li>
	 * </ul>
	 */
	private void closeAllClients(Map<String, McpServiceEntity> cache) {
		if (cache == null || cache.isEmpty()) {
			return;
		}

		for (Map.Entry<String, McpServiceEntity> entry : cache.entrySet()) {
			McpServiceEntity serviceEntity = entry.getValue();
			if (serviceEntity != null && serviceEntity.getMcpAsyncClient() != null) {
				closeClientSafely(serviceEntity, entry.getKey());
			}
		}
	}

	// Thread pool management
	private final AtomicReference<ExecutorService> connectionExecutorRef = new AtomicReference<>();

	// Scheduled task executor
	private final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "McpCacheUpdateTask");
		t.setDaemon(true);
		return t;
	});

	private ScheduledFuture<?> updateTask;

	private volatile int lastConfigHash = 0;

	// Cache update interval (10 minutes)
	private static final long CACHE_UPDATE_INTERVAL_MINUTES = 10;

	public McpCacheManager(McpConnectionFactory connectionFactory, McpConfigRepository mcpConfigRepository,
			McpProperties mcpProperties, ManusProperties manusProperties) {
		this.connectionFactory = connectionFactory;
		this.mcpConfigRepository = mcpConfigRepository;
		this.manusProperties = manusProperties;

		// Initialize thread pool
		updateConnectionExecutor();
	}

	/**
	 * Automatically load cache on startup
	 */
	@PostConstruct
	public void initializeCache() {
		logger.info("Initializing MCP cache manager with double buffer mechanism");

		try {
			// Load initial cache on startup
			Map<String, McpServiceEntity> initialCache = loadMcpServices(
					mcpConfigRepository.findByStatus(McpConfigStatus.ENABLE));

			// Set both active cache and background cache
			doubleCache.updateBackgroundCache(initialCache);
			doubleCache.switchCache(); // Switch to initial cache

			logger.info("Initial cache loaded successfully with {} services", initialCache.size());

			// Start scheduled update task
			startScheduledUpdate();

		}
		catch (Exception e) {
			logger.error("Failed to initialize cache", e);
		}
	}

	/**
	 * Start scheduled update task
	 */
	private void startScheduledUpdate() {
		if (updateTask != null && !updateTask.isCancelled()) {
			updateTask.cancel(false);
		}

		updateTask = scheduledExecutor.scheduleAtFixedRate(this::updateCacheTask, CACHE_UPDATE_INTERVAL_MINUTES,
				CACHE_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES);

		logger.info("Scheduled cache update task started, interval: {} minutes", CACHE_UPDATE_INTERVAL_MINUTES);
	}

	/**
	 * Scheduled cache update task. Closes old clients before creating new ones to prevent
	 * resource leaks (HttpClient SelectorManager threads).
	 */
	private void updateCacheTask() {
		try {
			logger.debug("Starting scheduled cache update task");

			// Query all enabled configurations
			List<McpConfigEntity> configs = mcpConfigRepository.findByStatus(McpConfigStatus.ENABLE);

			// Build new data in background cache
			// Note: This creates new McpAsyncClient instances with new HttpClient
			// connections
			Map<String, McpServiceEntity> newCache = loadMcpServices(configs);

			// Update background cache (this will close old background cache clients)
			doubleCache.updateBackgroundCache(newCache);

			// Atomically switch cache (this will close old active cache clients)
			// Old clients are closed before switching to prevent resource leaks
			doubleCache.switchCache();

			logger.info(
					"Cache updated successfully via scheduled task, services count: {}. Old clients have been closed.",
					newCache.size());

		}
		catch (Exception e) {
			logger.error("Failed to update cache via scheduled task", e);
		}
	}

	/**
	 * Update connection thread pool (supports dynamic configuration adjustment)
	 */
	private void updateConnectionExecutor() {
		int currentConfigHash = calculateConfigHash();

		// Check if configuration has changed
		if (currentConfigHash != lastConfigHash) {
			logger.info("MCP service loader configuration changed, updating thread pool");

			// Close old thread pool
			ExecutorService oldExecutor = connectionExecutorRef.get();
			if (oldExecutor != null && !oldExecutor.isShutdown()) {
				shutdownExecutor(oldExecutor);
			}

			// Create new thread pool
			int maxConcurrentConnections = manusProperties.getMcpMaxConcurrentConnections();
			ExecutorService newExecutor = Executors.newFixedThreadPool(maxConcurrentConnections);
			connectionExecutorRef.set(newExecutor);

			lastConfigHash = currentConfigHash;
			logger.info("Updated MCP service loader thread pool with max {} concurrent connections",
					maxConcurrentConnections);
		}
	}

	/**
	 * Calculate configuration hash value for detecting configuration changes
	 */
	private int calculateConfigHash() {
		int hash = 17;
		hash = 31 * hash + manusProperties.getMcpConnectionTimeoutSeconds();
		hash = 31 * hash + manusProperties.getMcpMaxRetryCount();
		hash = 31 * hash + manusProperties.getMcpMaxConcurrentConnections();
		return hash;
	}

	/**
	 * Safely shutdown thread pool
	 */
	private void shutdownExecutor(ExecutorService executor) {
		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
					executor.shutdownNow();
					if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
						logger.warn("Thread pool did not terminate");
					}
				}
			}
			catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	/**
	 * Get current connection thread pool
	 */
	private ExecutorService getConnectionExecutor() {
		// Check if configuration needs to be updated
		updateConnectionExecutor();
		return connectionExecutorRef.get();
	}

	/**
	 * Load MCP services (parallel processing version)
	 * @param mcpConfigEntities MCP configuration entity list
	 * @return MCP service entity mapping
	 * @throws IOException Thrown when loading fails
	 */
	private Map<String, McpServiceEntity> loadMcpServices(List<McpConfigEntity> mcpConfigEntities) throws IOException {
		Map<String, McpServiceEntity> toolCallbackMap = new ConcurrentHashMap<>();

		if (mcpConfigEntities == null || mcpConfigEntities.isEmpty()) {
			logger.info("No MCP server configurations found");
			return toolCallbackMap;
		}

		// Record main thread start time
		long mainStartTime = System.currentTimeMillis();
		logger.info("Loading {} MCP server configurations in parallel", mcpConfigEntities.size());

		// Get current configured thread pool
		ExecutorService executor = getConnectionExecutor();

		// Create connections in parallel
		List<CompletableFuture<McpConnectionResult>> futures = mcpConfigEntities.stream()
			.map(config -> CompletableFuture.supplyAsync(() -> createConnectionWithRetry(config), executor))
			.collect(Collectors.toList());

		// Wait for all tasks to complete, set timeout
		CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

		try {
			// Set overall timeout (using current configuration)
			allFutures.get(manusProperties.getMcpConnectionTimeoutSeconds(), TimeUnit.SECONDS);

			// Collect results
			for (int i = 0; i < mcpConfigEntities.size(); i++) {
				try {
					McpConnectionResult result = futures.get(i).get();
					if (result.isSuccess()) {
						toolCallbackMap.put(result.getServerName(), result.getServiceEntity());
					}
				}
				catch (Exception e) {
					String serverName = mcpConfigEntities.get(i).getMcpServerName();
					logger.error("Failed to get result for MCP server: {}", serverName, e);
				}
			}
		}
		catch (Exception e) {
			logger.error("Timeout or error occurred during parallel MCP connection creation", e);
			// Try to get completed results
			for (int i = 0; i < futures.size(); i++) {
				if (futures.get(i).isDone()) {
					try {
						McpConnectionResult result = futures.get(i).get();
						if (result.isSuccess()) {
							toolCallbackMap.put(result.getServerName(), result.getServiceEntity());
						}
					}
					catch (Exception ex) {
						logger.debug("Failed to get completed result for index: {}", i, ex);
					}
				}
			}
		}

		// Calculate main thread total time
		long mainEndTime = System.currentTimeMillis();
		long mainTotalTime = mainEndTime - mainStartTime;

		// Collect all results for detailed log output
		List<McpConnectionResult> allResults = new ArrayList<>();
		for (int i = 0; i < mcpConfigEntities.size(); i++) {
			try {
				if (futures.get(i).isDone()) {
					allResults.add(futures.get(i).get());
				}
			}
			catch (Exception e) {
				// If getting result fails, create a failed result record
				String serverName = mcpConfigEntities.get(i).getMcpServerName();
				allResults.add(new McpConnectionResult(false, null, serverName, "Failed to get result", 0, 0, "N/A"));
			}
		}

		// Output detailed execution log
		logger.info("\n"
				+ "╔══════════════════════════════════════════════════════════════════════════════════════════════════════╗\n"
				+ "║                                    MCP Service Loader Execution Report                                ║\n"
				+ "╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n"
				+ "║  Main Thread: Started at {}, Completed at {}, Total Time: {}ms                                      ║\n"
				+ "║  Configuration: Timeout={}s, MaxRetry={}, MaxConcurrent={}                                           ║\n"
				+ "║  Summary: {}/{} servers loaded successfully                                                         ║\n"
				+ "╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n"
				+ "║  Individual Server Results:                                                                          ║\n"
				+ "{}"
				+ "╚══════════════════════════════════════════════════════════════════════════════════════════════════════╝",
				formatTime(mainStartTime), formatTime(mainEndTime), mainTotalTime,
				manusProperties.getMcpConnectionTimeoutSeconds(), manusProperties.getMcpMaxRetryCount(),
				manusProperties.getMcpMaxConcurrentConnections(), toolCallbackMap.size(), mcpConfigEntities.size(),
				formatIndividualResults(allResults));

		return toolCallbackMap;
	}

	/**
	 * Connection creation method with retry
	 * @param config MCP configuration entity
	 * @return Connection result
	 */
	private McpConnectionResult createConnectionWithRetry(McpConfigEntity config) {
		String serverName = config.getMcpServerName();
		String connectionType = config.getConnectionType().toString();
		long startTime = System.currentTimeMillis();
		int retryCount = 0;

		// Try to connect, retry at most MAX_RETRY_COUNT times
		for (int attempt = 0; attempt <= manusProperties.getMcpMaxRetryCount(); attempt++) {
			try {
				McpServiceEntity serviceEntity = connectionFactory.createConnection(config);

				if (serviceEntity != null) {
					long connectionTime = System.currentTimeMillis() - startTime;
					return new McpConnectionResult(true, serviceEntity, serverName, null, connectionTime, retryCount,
							connectionType);
				}
				else {
					if (attempt == manusProperties.getMcpMaxRetryCount()) {
						long connectionTime = System.currentTimeMillis() - startTime;
						return new McpConnectionResult(false, null, serverName, "Service entity is null",
								connectionTime, retryCount, connectionType);
					}
					logger.debug("Attempt {} failed for server: {}, retrying...", attempt + 1, serverName);
					retryCount++;
				}
			}
			catch (Exception e) {
				if (attempt == manusProperties.getMcpMaxRetryCount()) {
					long connectionTime = System.currentTimeMillis() - startTime;
					return new McpConnectionResult(false, null, serverName, e.getMessage(), connectionTime, retryCount,
							connectionType);
				}
				logger.debug("Attempt {} failed for server: {}, error: {}, retrying...", attempt + 1, serverName,
						e.getMessage());
				retryCount++;
			}
		}

		// This line should theoretically never be reached, but for compilation safety
		long connectionTime = System.currentTimeMillis() - startTime;
		return new McpConnectionResult(false, null, serverName, "Max retry attempts exceeded", connectionTime,
				retryCount, connectionType);
	}

	/**
	 * Get MCP services (uniformly use default cache)
	 * @param planId Plan ID (use default if null)
	 * @return MCP service entity mapping
	 */
	public Map<String, McpServiceEntity> getOrLoadServices(String planId) {
		try {
			// planId is not used.
			// Directly read active cache, no locking needed, ensures seamless operation
			Map<String, McpServiceEntity> activeCache = doubleCache.getActiveCache();

			return new ConcurrentHashMap<>(activeCache);
		}
		catch (Exception e) {
			logger.error("Failed to get MCP services for plan: {}", planId, e);
			return new ConcurrentHashMap<>();
		}
	}

	/**
	 * Get MCP service entity list
	 * @param planId Plan ID
	 * @return MCP service entity list
	 */
	public List<McpServiceEntity> getServiceEntities(String planId) {
		try {
			return new ArrayList<>(getOrLoadServices(planId).values());
		}
		catch (Exception e) {
			logger.error("Failed to get MCP service entities for plan: {}", planId, e);
			return new ArrayList<>();
		}
	}

	/**
	 * Manually trigger cache reload. Closes old clients before creating new ones to
	 * prevent resource leaks.
	 */
	public void triggerCacheReload() {
		try {
			logger.info("Manually triggering cache reload");

			// Query all enabled configurations
			List<McpConfigEntity> configs = mcpConfigRepository.findByStatus(McpConfigStatus.ENABLE);

			// Build new data in background cache
			// Note: This creates new McpAsyncClient instances with new HttpClient
			// connections
			Map<String, McpServiceEntity> newCache = loadMcpServices(configs);

			// Update background cache (this will close old background cache clients)
			doubleCache.updateBackgroundCache(newCache);

			// Atomically switch cache (this will close old active cache clients)
			// Old clients are closed before switching to prevent resource leaks
			doubleCache.switchCache();

			logger.info("Manual cache reload completed, services count: {}. Old clients have been closed.",
					newCache.size());

		}
		catch (Exception e) {
			logger.error("Failed to manually reload cache", e);
		}
	}

	/**
	 * Clear cache (compatibility method, actually uses double cache mechanism)
	 * @param planId Plan ID
	 */
	public void invalidateCache(String planId) {
		logger.info("Cache invalidation requested for plan: {}, but using double buffer mechanism - no action needed",
				planId);
		// Under double cache mechanism, no need to manually clear cache, will auto-update
	}

	/**
	 * Clear all cache (compatibility method, actually uses double cache mechanism)
	 */
	public void invalidateAllCache() {
		logger.info("All cache invalidation requested, but using double buffer mechanism - triggering reload instead");
		// Trigger reload instead of clearing
		triggerCacheReload();
	}

	/**
	 * Refresh cache (compatibility method, actually uses double cache mechanism)
	 * @param planId Plan ID
	 */
	public void refreshCache(String planId) {
		logger.info("Cache refresh requested for plan: {}, triggering reload", planId);
		triggerCacheReload();
	}

	/**
	 * Get cache statistics
	 * @return Cache statistics
	 */
	public String getCacheStats() {
		Map<String, McpServiceEntity> activeCache = doubleCache.getActiveCache();
		return String.format("Double Buffer Cache Stats - Active Services: %d, Last Update: %s", activeCache.size(),
				formatTime(System.currentTimeMillis()));
	}

	/**
	 * Manually update connection configuration (supports runtime dynamic adjustment)
	 */
	public void updateConnectionConfiguration() {
		logger.info("Manually updating MCP service loader configuration");
		updateConnectionExecutor();
	}

	/**
	 * Get current connection configuration information
	 * @return Configuration information string
	 */
	public String getConnectionConfigurationInfo() {
		return String.format("MCP Service Loader Config - Timeout: %ds, MaxRetry: %d, MaxConcurrent: %d",
				manusProperties.getMcpConnectionTimeoutSeconds(), manusProperties.getMcpMaxRetryCount(),
				manusProperties.getMcpMaxConcurrentConnections());
	}

	/**
	 * Get cache update configuration information
	 * @return Cache update configuration information
	 */
	public String getCacheUpdateConfigurationInfo() {
		return String.format("Cache Update Config - Interval: %d minutes, Double Buffer: enabled",
				CACHE_UPDATE_INTERVAL_MINUTES);
	}

	/**
	 * Close resources (called when application shuts down). Closes all MCP clients from
	 * both active and background caches to prevent resource leaks.
	 */
	@PreDestroy
	public void shutdown() {
		logger.info("Shutting down MCP cache manager");

		// Stop scheduled task
		if (updateTask != null && !updateTask.isCancelled()) {
			updateTask.cancel(false);
		}

		// Close scheduled executor
		if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
			scheduledExecutor.shutdown();
			try {
				if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
					scheduledExecutor.shutdownNow();
				}
			}
			catch (InterruptedException e) {
				scheduledExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}

		// Close connection thread pool
		ExecutorService executor = connectionExecutorRef.get();
		if (executor != null) {
			shutdownExecutor(executor);
		}

		// Close all MCP client connections from both active and background caches
		// This ensures all resources are properly closed:
		// - SSE: HttpClient instances and SelectorManager threads
		// - STDIO: Child processes and their resources
		// - STREAMING: HTTP connections and streams
		Map<String, McpServiceEntity> activeCache = doubleCache.getActiveCache();
		Map<String, McpServiceEntity> backgroundCache = doubleCache.getBackgroundCache();

		int closedCount = 0;

		// Close active cache clients
		for (Map.Entry<String, McpServiceEntity> entry : activeCache.entrySet()) {
			if (closeClientSafely(entry.getValue(), entry.getKey())) {
				closedCount++;
			}
		}

		// Close background cache clients
		for (Map.Entry<String, McpServiceEntity> entry : backgroundCache.entrySet()) {
			if (closeClientSafely(entry.getValue(), entry.getKey())) {
				closedCount++;
			}
		}

		logger.info("MCP cache manager shutdown completed. Closed {} MCP clients (including STDIO processes).",
				closedCount);
	}

	/**
	 * Safely close a single MCP client. This method handles all transport types:
	 * <ul>
	 * <li>SSE: Closes HttpClient and releases SelectorManager threads</li>
	 * <li>STDIO: Closes child process gracefully and releases process resources</li>
	 * <li>STREAMING: Closes HTTP connections and streams</li>
	 * </ul>
	 * For STDIO transport, this method uses graceful shutdown to allow the child process
	 * to terminate cleanly. The underlying transport (including STDIO child processes) is
	 * automatically closed when the client is closed.
	 * @param serviceEntity Service entity containing the client
	 * @param serverName Server name for logging (can be null, will use serviceGroup)
	 * @return true if client was closed successfully, false otherwise
	 */
	private boolean closeClientSafely(McpServiceEntity serviceEntity, String serverName) {
		if (serviceEntity == null) {
			return false;
		}

		io.modelcontextprotocol.client.McpAsyncClient client = serviceEntity.getMcpAsyncClient();
		if (client == null) {
			return false;
		}

		String logName = serverName != null ? serverName : serviceEntity.getServiceGroup();

		try {
			logger.debug("Closing MCP client for server: {} (this will close transport and any child processes)",
					logName);

			// Step 1: Try graceful shutdown first (especially important for STDIO
			// processes)
			// For async client, closeGracefully() returns a Mono<Void>
			try {
				client.closeGracefully()
					.timeout(java.time.Duration.ofSeconds(5))
					.doOnSuccess(v -> logger.debug("MCP client closed gracefully for server: {}", logName))
					.doOnError(e -> logger.warn("Error during graceful close for server: {}, will force close", logName,
							e))
					.block(); // Block to wait for graceful shutdown

				// Step 2: Additional wait for STDIO process cleanup
				// The process.destroy() in closeGracefully() sends TERM signal
				// but process might need a moment to clean up
				Thread.sleep(200);

				logger.debug("Successfully closed MCP client for server: {}", logName);
				return true;
			}
			catch (Exception gracefulEx) {
				logger.warn("Graceful shutdown failed or timed out for server: {}, forcing close", logName, gracefulEx);
				// Step 3: Fallback to force close
				client.close();
				// Still wait a bit for cleanup
				Thread.sleep(100);
				return true;
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted during client shutdown for server: {}, forcing close", logName);
			try {
				client.close();
			}
			catch (Exception ex) {
				logger.error("Error during force close after interruption for server: {}", logName, ex);
			}
			return false;
		}
		catch (Exception e) {
			logger.warn("Error closing MCP client for server: {} (transport and processes may not be fully cleaned up)",
					logName, e);
			// Last resort: try force close
			try {
				client.close();
			}
			catch (Exception ex) {
				logger.error("Error during final force close for server: {}", logName, ex);
			}
			return false;
		}
	}

	private String formatTime(long time) {
		return String.format("%tF %tT", time, time);
	}

	private String formatIndividualResults(List<McpConnectionResult> results) {
		StringBuilder sb = new StringBuilder();
		for (McpConnectionResult result : results) {
			String status = result.isSuccess() ? "✅ Success" : "❌ Failed";
			String errorInfo = result.getErrorMessage() != null ? (result.getErrorMessage().length() > 15
					? result.getErrorMessage().substring(0, 12) + "..." : result.getErrorMessage()) : "N/A";

			sb.append(String.format("║  %-20s | %-12s | Type: %-8s | Time: %-6dms | Retry: %-2d | Error: %-15s ║\n",
					result.getServerName(), status, result.getConnectionType(), result.getConnectionTime(),
					result.getRetryCount(), errorInfo));
		}
		return sb.toString();
	}

}
