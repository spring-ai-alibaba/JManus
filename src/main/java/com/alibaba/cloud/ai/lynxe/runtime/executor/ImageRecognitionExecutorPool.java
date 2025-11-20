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
package com.alibaba.cloud.ai.lynxe.runtime.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.lynxe.config.LynxeProperties;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated executor pool for image recognition and OCR processing tasks. This pool is
 * specifically designed for handling image processing workloads with configurable thread
 * pool size and retry mechanisms.
 */
public class ImageRecognitionExecutorPool {

	private static final Logger log = LoggerFactory.getLogger(ImageRecognitionExecutorPool.class);

	private final LynxeProperties lynxeProperties;

	private volatile ExecutorService executorService;

	private volatile int currentPoolSize;

	private volatile long lastConfigCheckTime;

	/**
	 * Configuration check interval in milliseconds (10 seconds)
	 */
	private static final long CONFIG_CHECK_INTERVAL_MILLIS = 10_000;

	public ImageRecognitionExecutorPool(LynxeProperties lynxeProperties) {
		this.lynxeProperties = lynxeProperties;

		// Initialize thread pool with current configuration
		this.currentPoolSize = getConfiguredPoolSize();
		this.executorService = createExecutorService(currentPoolSize);
		this.lastConfigCheckTime = System.currentTimeMillis();

		log.info("ImageRecognitionExecutorPool initialized with thread pool size: {}", currentPoolSize);
	}

	/**
	 * Get or create an executor service for image recognition tasks
	 * @return ExecutorService for image recognition processing
	 */
	public ExecutorService getExecutorService() {
		return getUpdatedExecutorService();
	}

	/**
	 * Submit a task to the image recognition executor pool
	 * @param task The task to execute
	 * @param <T> The type of the task result
	 * @return CompletableFuture representing the task execution
	 */
	public <T> CompletableFuture<T> submitTask(Callable<T> task) {
		ExecutorService executor = getUpdatedExecutorService();
		return CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			}
			catch (Exception e) {
				throw new RuntimeException("Image recognition task failed", e);
			}
		}, executor);
	}

	/**
	 * Submit a runnable task to the image recognition executor pool
	 * @param task The task to execute
	 * @return CompletableFuture representing the task execution
	 */
	public CompletableFuture<Void> submitTask(Runnable task) {
		ExecutorService executor = getUpdatedExecutorService();
		return CompletableFuture.runAsync(task, executor);
	}

	/**
	 * Get the configured pool size from LynxeProperties
	 * @return configured pool size or default value if not configured
	 */
	private int getConfiguredPoolSize() {
		if (lynxeProperties != null) {
			Integer configuredPoolSize = lynxeProperties.getImageRecognitionPoolSize();
			if (configuredPoolSize != null && configuredPoolSize > 0) {
				log.debug("Using configured image recognition pool size: {}", configuredPoolSize);
				return configuredPoolSize;
			}
		}

		log.debug("Using default image recognition pool size: 4");
		return 4;
	}

	/**
	 * Create a new executor service with the specified pool size
	 * @param poolSize The size of the thread pool
	 * @return New ExecutorService instance
	 */
	private ExecutorService createExecutorService(int poolSize) {
		String poolName = "image-recognition-executor-" + System.currentTimeMillis();

		ThreadPoolExecutor executor = new ThreadPoolExecutor(poolSize, poolSize, // Core
																					// and
																					// max
																					// pool
																					// size
																					// are
																					// the
																					// same
																					// for
																					// fixed
																					// pool
				60L, TimeUnit.SECONDS, // Keep-alive time
				new LinkedBlockingQueue<>(poolSize * 2), // Queue capacity
				new ThreadFactory() {
					private final AtomicInteger threadCounter = new AtomicInteger(1);

					@Override
					public Thread newThread(Runnable r) {
						Thread thread = new Thread(r, poolName + "-thread-" + threadCounter.getAndIncrement());
						thread.setDaemon(false);
						return thread;
					}
				}, new ThreadPoolExecutor.CallerRunsPolicy() // Rejection policy
		);

		log.info("Created image recognition executor pool: {} (size: {})", poolName, poolSize);
		return executor;
	}

	/**
	 * Check and update thread pool configuration if needed. This method is called before
	 * each executor service usage to ensure configuration changes are picked up without
	 * using timers or background threads.
	 * @return the current (potentially updated) executor service
	 */
	private ExecutorService getUpdatedExecutorService() {
		long currentTime = System.currentTimeMillis();

		// Check if enough time has passed since last configuration check
		if (currentTime - lastConfigCheckTime >= CONFIG_CHECK_INTERVAL_MILLIS) {
			lastConfigCheckTime = currentTime;

			// Get current configuration
			int newPoolSize = getConfiguredPoolSize();

			// Check if configuration has changed
			if (newPoolSize != currentPoolSize) {
				log.info("Image recognition pool size configuration changed from {} to {}, rebuilding thread pool",
						currentPoolSize, newPoolSize);

				// Gracefully shutdown old executor service
				ExecutorService oldExecutorService = executorService;

				// Create new executor service with updated configuration
				ExecutorService newExecutorService = createExecutorService(newPoolSize);

				// Update current state atomically
				this.executorService = newExecutorService;
				this.currentPoolSize = newPoolSize;

				// Gracefully shutdown old executor service in background
				// This ensures existing tasks can complete
				shutdownExecutorGracefully(oldExecutorService);

				log.info("Image recognition thread pool successfully updated to size: {}", newPoolSize);
			}
		}

		return executorService;
	}

	/**
	 * Gracefully shutdown an executor service
	 * @param executor the executor service to shutdown
	 */
	private void shutdownExecutorGracefully(ExecutorService executor) {
		if (executor != null && !executor.isShutdown()) {
			try {
				// Initiate graceful shutdown
				executor.shutdown();
				log.debug("Old image recognition thread pool shutdown initiated");
			}
			catch (Exception e) {
				log.warn("Error during graceful shutdown of old image recognition thread pool", e);
				// Force shutdown if graceful shutdown fails
				executor.shutdownNow();
			}
		}
	}

	/**
	 * Get the current pool size
	 * @return Current pool size
	 */
	public int getCurrentPoolSize() {
		return currentPoolSize;
	}

	/**
	 * Get the current pool status
	 * @return Status string describing current pool configuration
	 */
	public String getPoolStatus() {
		return String.format("Image Recognition Executor Pool Status: Active (size: %d, configured: %d)",
				currentPoolSize, getConfiguredPoolSize());
	}

	/**
	 * Shutdown the executor pool gracefully
	 */
	public void shutdown() {
		if (executorService != null && !executorService.isShutdown()) {
			executorService.shutdown();
			try {
				if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
					executorService.shutdownNow();
					if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
						log.error("Image recognition executor pool did not terminate");
					}
				}
			}
			catch (InterruptedException e) {
				executorService.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

}
