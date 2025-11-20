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
package com.alibaba.cloud.ai.manus.mcp.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.mcp.config.McpProperties;
import com.alibaba.cloud.ai.manus.mcp.model.po.McpConfigEntity;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpServerConfig;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpServiceEntity;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP connection factory
 */
@Component
public class McpConnectionFactory {

	private static final Logger logger = LoggerFactory.getLogger(McpConnectionFactory.class);

	private final McpTransportBuilder transportBuilder;

	private final McpConfigValidator configValidator;

	private final McpProperties mcpProperties;

	private final ObjectMapper objectMapper;

	public McpConnectionFactory(McpTransportBuilder transportBuilder, McpConfigValidator configValidator,
			McpProperties mcpProperties, ObjectMapper objectMapper) {
		this.transportBuilder = transportBuilder;
		this.configValidator = configValidator;
		this.mcpProperties = mcpProperties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Create MCP connection
	 * @param mcpConfigEntity MCP configuration entity
	 * @return MCP service entity
	 * @throws IOException Thrown when creation fails
	 */
	public McpServiceEntity createConnection(McpConfigEntity mcpConfigEntity) throws IOException {
		String serverName = mcpConfigEntity.getMcpServerName();

		// Validate configuration entity
		configValidator.validateMcpConfigEntity(mcpConfigEntity);

		// Check if enabled
		if (!configValidator.isEnabled(mcpConfigEntity)) {
			logger.info("Skipping disabled MCP server: {}", serverName);
			return null;
		}

		// Parse server configuration
		McpServerConfig serverConfig = parseServerConfig(mcpConfigEntity.getConnectionConfig(), serverName);

		// Configure MCP transport with retry mechanism
		return configureMcpTransportWithRetry(serverName, mcpConfigEntity.getConnectionType(), serverConfig);
	}

	/**
	 * Parse server configuration
	 * @param connectionConfig Connection configuration JSON
	 * @param serverName Server name
	 * @return Server configuration object
	 * @throws IOException Thrown when parsing fails
	 */
	private McpServerConfig parseServerConfig(String connectionConfig, String serverName) throws IOException {
		try (JsonParser jsonParser = objectMapper.createParser(connectionConfig)) {
			return jsonParser.readValueAs(McpServerConfig.class);
		}
		catch (Exception e) {
			logger.error("Failed to parse server config for server: {}", serverName, e);
			throw new IOException("Failed to parse server config for server: " + serverName, e);
		}
	}

	/**
	 * Configure MCP transport with retry mechanism that creates fresh transport for each
	 * attempt
	 * @param mcpServerName MCP server name
	 * @param connectionType Connection type
	 * @param serverConfig Server configuration
	 * @return MCP service entity
	 * @throws IOException Thrown when configuration fails
	 */
	private McpServiceEntity configureMcpTransportWithRetry(String mcpServerName,
			com.alibaba.cloud.ai.manus.mcp.model.po.McpConfigType connectionType, McpServerConfig serverConfig)
			throws IOException {

		int maxRetries = mcpProperties.getMaxRetries();
		Exception lastException = null;

		for (int attempt = 1; attempt <= maxRetries; attempt++) {
			McpAsyncClient mcpAsyncClient = null;
			McpClientTransport transport = null;
			try {
				// Create fresh transport for each attempt to avoid unicast sink reuse
				transport = transportBuilder.buildTransport(connectionType, serverConfig, mcpServerName);
				if (transport == null) {
					throw new IOException("Failed to build transport for server: " + mcpServerName);
				}

				// Create new client with fresh transport
				mcpAsyncClient = McpClient.async(transport)
					.requestTimeout(mcpProperties.getTimeout())
					.clientInfo(new McpSchema.Implementation(mcpServerName, "1.0.0"))
					.build();

				logger.debug("Attempting to initialize MCP transport for: {} (attempt {}/{})", mcpServerName, attempt,
						maxRetries);

				mcpAsyncClient.initialize()
					.timeout(mcpProperties.getTimeout())
					.doOnSuccess(result -> logger.info("MCP client initialized successfully for {}", mcpServerName))
					.doOnError(error -> logger.error("Failed to initialize MCP client for {}: {}", mcpServerName,
							error.getMessage()))
					.block();

				logger.info("MCP transport configured successfully for: {} (attempt {})", mcpServerName, attempt);

				AsyncMcpToolCallbackProvider callbackProvider = new AsyncMcpToolCallbackProvider(mcpAsyncClient);
				return new McpServiceEntity(mcpAsyncClient, callbackProvider, mcpServerName);
			}
			catch (Exception e) {
				lastException = e;

				// Check if this is a DNS-related error that shouldn't be retried
				if (isDnsRelatedError(e)) {
					logger.error("DNS resolution failed for MCP server '{}'. Skipping retries. Error: {}",
							mcpServerName, e.getMessage());
					cleanupClient(mcpAsyncClient, mcpServerName);
					cleanupTransport(transport, mcpServerName);
					throw new IOException(
							"DNS resolution failed for MCP server '" + mcpServerName + "': " + e.getMessage(), e);
				}

				logger.warn("Failed to initialize MCP transport for {} on attempt {}/{}: {}", mcpServerName, attempt,
						maxRetries, e.getMessage());

				// Clean up the failed client and transport
				cleanupClient(mcpAsyncClient, mcpServerName);
				cleanupTransport(transport, mcpServerName);

				if (attempt < maxRetries) {
					try {
						// Incremental wait time
						Thread.sleep(1000L * mcpProperties.getRetryWaitMultiplier() * attempt);
					}
					catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						logger.warn("Retry wait interrupted for server: {}", mcpServerName);
						break;
					}
				}
			}
		}

		logger.error("Failed to initialize MCP transport for {} after {} attempts", mcpServerName, maxRetries,
				lastException);
		return null;
	}

	/**
	 * Check if the exception is DNS-related and shouldn't be retried
	 * @param e Exception to check
	 * @return true if DNS-related, false otherwise
	 */
	private boolean isDnsRelatedError(Exception e) {
		if (e == null)
			return false;

		String message = e.getMessage();
		if (message == null)
			return false;

		return message.contains("Failed to resolve") || message.contains("DnsNameResolverTimeoutException")
				|| message.contains("SearchDomainUnknownHostException") || message.contains("UnknownHostException")
				|| message.toLowerCase().contains("dns");
	}

	/**
	 * Clean up MCP client resources. Uses graceful shutdown first, then falls back to
	 * force close. This is especially important for STDIO transport to allow child
	 * processes to terminate cleanly.
	 * @param mcpAsyncClient Client to clean up
	 * @param mcpServerName Server name for logging
	 */
	private void cleanupClient(McpAsyncClient mcpAsyncClient, String mcpServerName) {
		if (mcpAsyncClient != null) {
			try {
				logger.debug("Cleaning up MCP client for server: {}", mcpServerName);
				// Try graceful shutdown first (important for STDIO processes)
				try {
					mcpAsyncClient.closeGracefully().timeout(java.time.Duration.ofSeconds(3)).block();
					// Wait a bit for process cleanup (especially for STDIO)
					Thread.sleep(100);
				}
				catch (Exception gracefulEx) {
					logger.debug("Graceful shutdown failed for server: {}, forcing close: {}", mcpServerName,
							gracefulEx.getMessage());
					mcpAsyncClient.close();
				}
			}
			catch (Exception closeEx) {
				logger.debug("Failed to close MCP client during cleanup for server: {}: {}", mcpServerName,
						closeEx.getMessage());
			}
		}
	}

	/**
	 * Clean up MCP transport resources
	 * @param transport Transport to clean up
	 * @param mcpServerName Server name for logging
	 */
	private void cleanupTransport(McpClientTransport transport, String mcpServerName) {
		if (transport != null) {
			try {
				logger.debug("Cleaning up MCP transport for server: {}", mcpServerName);
				transport.close();
			}
			catch (Exception closeEx) {
				logger.debug("Failed to close MCP transport during cleanup for server: {}: {}", mcpServerName,
						closeEx.getMessage());
			}
		}
	}

}
