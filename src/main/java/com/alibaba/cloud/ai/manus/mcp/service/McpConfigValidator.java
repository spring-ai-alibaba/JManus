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
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.cloud.ai.manus.mcp.config.McpProperties;
import com.alibaba.cloud.ai.manus.mcp.model.po.McpConfigEntity;
import com.alibaba.cloud.ai.manus.mcp.model.po.McpConfigStatus;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpServerConfig;

/**
 * MCP configuration validator
 */
@Component
public class McpConfigValidator {

	private static final Logger logger = LoggerFactory.getLogger(McpConfigValidator.class);

	public McpConfigValidator(McpProperties mcpProperties) {
	}

	/**
	 * Validate MCP configuration entity
	 * @param mcpConfigEntity MCP configuration entity
	 * @throws IOException Thrown when validation fails
	 */
	public void validateMcpConfigEntity(McpConfigEntity mcpConfigEntity) throws IOException {
		String serverName = mcpConfigEntity.getMcpServerName();

		// Validate server name
		if (serverName == null || serverName.trim().isEmpty()) {
			throw new IOException("Server name is required");
		}

		// Validate connection type
		if (mcpConfigEntity.getConnectionType() == null) {
			throw new IOException("Connection type is required for server: " + serverName);
		}

		// Validate connection configuration
		if (mcpConfigEntity.getConnectionConfig() == null || mcpConfigEntity.getConnectionConfig().trim().isEmpty()) {
			throw new IOException("Connection config is required for server: " + serverName);
		}

		logger.debug("MCP config entity validation passed for server: {}", serverName);
	}

	/**
	 * Validate server configuration
	 * @param serverConfig Server configuration
	 * @param serverName Server name
	 * @throws IOException Thrown when validation fails
	 */
	public void validateServerConfig(McpServerConfig serverConfig, String serverName) throws IOException {
		if (serverConfig == null) {
			throw new IOException("Server config is null for server: " + serverName);
		}

		// Ensure env is never null
		if (serverConfig.getEnv() == null) {
			serverConfig.setEnv(new java.util.HashMap<>());
			logger.debug("Fixed null env field for server: {}", serverName);
		}

		// Validate required fields based on connection type
		if (serverConfig.getCommand() != null && !serverConfig.getCommand().trim().isEmpty()) {
			// STUDIO type: validate command
			validateCommand(serverConfig.getCommand(), serverName);

			// Validate args for STUDIO type
			if (serverConfig.getArgs() != null && !serverConfig.getArgs().isEmpty()) {
				validateArgs(serverConfig.getArgs(), serverName);
			}
		}
		else {
			// SSE/STREAMING type: validate URL
			validateUrl(serverConfig.getUrl(), serverName);
		}

		logger.debug("Server config validation passed for server: {}", serverName);
	}

	/**
	 * Validate command configuration
	 * @param command Command
	 * @param serverName Server name
	 * @throws IOException Thrown when validation fails
	 */
	public void validateCommand(String command, String serverName) throws IOException {
		if (command == null || command.trim().isEmpty()) {
			throw new IOException("Missing required 'command' field in server configuration for " + serverName);
		}

		// Validate that command is a proper executable
		String trimmedCommand = command.trim();
		if (trimmedCommand.contains(" ")) {
			throw new IOException("Command field should contain only the executable name, not arguments. "
					+ "Use 'args' field for arguments. Invalid command: '" + command + "' for server: " + serverName);
		}

		// Check for common MCP server commands
		if (trimmedCommand.equals("uvx") || trimmedCommand.equals("npx") || trimmedCommand.equals("node")) {
			logger.debug("Valid MCP command detected: {} for server: {}", trimmedCommand, serverName);
		}
	}

	/**
	 * Validate arguments configuration
	 * @param args Arguments list
	 * @param serverName Server name
	 * @throws IOException Thrown when validation fails
	 */
	public void validateArgs(List<String> args, String serverName) throws IOException {
		if (args == null) {
			return; // null args is acceptable
		}

		for (int i = 0; i < args.size(); i++) {
			String arg = args.get(i);
			if (arg == null) {
				throw new IOException("Argument at index " + i + " is null for server: " + serverName);
			}
			// Arguments can be empty strings, so we don't check for emptiness
		}

		logger.debug("Arguments validation passed for server: {} with {} args", serverName, args.size());
	}

	/**
	 * Validate URL configuration
	 * @param url URL
	 * @param serverName Server name
	 * @throws IOException Thrown when validation fails
	 */
	public void validateUrl(String url, String serverName) throws IOException {
		if (url == null || url.trim().isEmpty()) {
			throw new IOException("Invalid or missing MCP server URL for server: " + serverName);
		}

		try {
			URL parsedUrl = new URL(url.trim());

			// Validate URL format
			if (parsedUrl.getHost() == null || parsedUrl.getHost().trim().isEmpty()) {
				throw new IOException("Invalid URL host: " + url + " for server: " + serverName);
			}

			// Validate protocol
			String protocol = parsedUrl.getProtocol();
			if (!"http".equals(protocol) && !"https".equals(protocol)) {
				throw new IOException("Unsupported protocol '" + protocol + "' in URL: " + url + " for server: "
						+ serverName + ". Only HTTP and HTTPS are supported.");
			}

			// Pre-validate DNS resolution to avoid connection issues
			validateDnsResolution(parsedUrl.getHost(), serverName);
		}
		catch (MalformedURLException e) {
			throw new IOException("Invalid URL format: " + url + " for server: " + serverName, e);
		}
	}

	/**
	 * Validate SSE URL format
	 * @param url URL
	 * @param serverName Server name
	 * @throws IOException Thrown when validation fails
	 */
	public void validateSseUrl(String url, String serverName) throws IOException {
		validateUrl(url, serverName);

		try {
			URL parsedUrl = new URL(url.trim());
			String path = parsedUrl.getPath();

			// Check if path contains sse
			boolean pathContainsSse = path != null && path.toLowerCase().contains("sse");

			if (!pathContainsSse) {
				throw new IOException("URL must contain 'sse' in path for SSE connection. " + "Current URL: " + url
						+ " for server: " + serverName);
			}
		}
		catch (MalformedURLException e) {
			throw new IOException("Invalid URL format: " + url + " for server: " + serverName, e);
		}
	}

	/**
	 * Check if configuration is enabled
	 * @param mcpConfigEntity MCP configuration entity
	 * @return true if enabled, false if disabled
	 */
	public boolean isEnabled(McpConfigEntity mcpConfigEntity) {
		return mcpConfigEntity.getStatus() != null && mcpConfigEntity.getStatus() == McpConfigStatus.ENABLE;
	}

	/**
	 * Validate if server name already exists
	 * @param serverName Server name
	 * @param existingServer Existing server
	 * @throws IOException If server name already exists
	 */
	public void validateServerNameNotExists(String serverName, Object existingServer) throws IOException {
		if (existingServer != null) {
			throw new IOException("MCP server with name '" + serverName + "' already exists");
		}
	}

	/**
	 * Validate if server exists
	 * @param serverName Server name
	 * @param existingServer Existing server
	 * @throws IOException If server does not exist
	 */
	public void validateServerExists(String serverName, Object existingServer) throws IOException {
		if (existingServer == null) {
			throw new IOException("MCP server not found with name: " + serverName);
		}
	}

	/**
	 * Validate DNS resolution for the given hostname
	 * @param hostname Hostname to validate
	 * @param serverName Server name for error messages
	 * @throws IOException Thrown when DNS resolution fails
	 */
	private void validateDnsResolution(String hostname, String serverName) throws IOException {
		try {
			// Use CompletableFuture to add timeout to DNS resolution
			CompletableFuture<InetAddress> future = CompletableFuture.supplyAsync(() -> {
				try {
					return InetAddress.getByName(hostname);
				}
				catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
			});

			// Wait for DNS resolution with 10 second timeout
			future.get(10, TimeUnit.SECONDS);
			logger.debug("DNS resolution successful for hostname: {} (server: {})", hostname, serverName);
		}
		catch (Exception e) {
			String errorMessage = String.format(
					"DNS resolution failed for hostname '%s' (server: %s). "
							+ "Please verify the hostname is correct and accessible. " + "Error: %s",
					hostname, serverName, e.getMessage());
			logger.warn(errorMessage);
			throw new IOException(errorMessage, e);
		}
	}

}
