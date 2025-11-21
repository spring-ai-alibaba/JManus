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
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.alibaba.cloud.ai.manus.mcp.config.McpProperties;
import com.alibaba.cloud.ai.manus.mcp.model.po.McpConfigType;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;

/**
 * MCP transport builder
 */
@Component
public class McpTransportBuilder {

	private static final Logger logger = LoggerFactory.getLogger(McpTransportBuilder.class);

	private final McpConfigValidator configValidator;

	private final McpProperties mcpProperties;

	private final ObjectMapper objectMapper;

	public McpTransportBuilder(McpConfigValidator configValidator, McpProperties mcpProperties,
			ObjectMapper objectMapper) {
		this.configValidator = configValidator;
		this.mcpProperties = mcpProperties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Build MCP transport
	 * @param configType Configuration type
	 * @param serverConfig Server configuration
	 * @param serverName Server name
	 * @return MCP client transport
	 * @throws IOException Thrown when build fails
	 */
	public McpClientTransport buildTransport(McpConfigType configType, McpServerConfig serverConfig, String serverName)
			throws IOException {
		// Validate server configuration
		configValidator.validateServerConfig(serverConfig, serverName);

		switch (configType) {
			case SSE -> {
				return buildSseTransport(serverConfig, serverName);
			}
			case STUDIO -> {
				return buildStudioTransport(serverConfig, serverName);
			}
			case STREAMING -> {
				return buildStreamingTransport(serverConfig, serverName);
			}
			default -> {
				throw new IOException("Unsupported connection type: " + configType + " for server: " + serverName);
			}
		}
	}

	/**
	 * Build SSE transport
	 * @param serverConfig Server configuration
	 * @param serverName Server name
	 * @return SSE transport
	 * @throws IOException Thrown when build fails
	 */
	private McpClientTransport buildSseTransport(McpServerConfig serverConfig, String serverName) throws IOException {
		String url = serverConfig.getUrl().trim();
		configValidator.validateSseUrl(url, serverName);

		URL parsedUrl = new URL(url);
		String baseUrl = parsedUrl.getProtocol() + "://" + parsedUrl.getHost()
				+ (parsedUrl.getPort() == -1 ? "" : ":" + parsedUrl.getPort());

		String path = parsedUrl.getPath();
		String sseEndpoint = path;

		// Remove leading slash
		if (sseEndpoint.startsWith("/")) {
			sseEndpoint = sseEndpoint.substring(1);
		}

		// Set to null if empty
		if (sseEndpoint.isEmpty()) {
			sseEndpoint = null;
		}

		logger.info("Building SSE transport for server: {} with baseUrl: {}, endpoint: {}", serverName, baseUrl,
				sseEndpoint);

		Map<String, String> headers = serverConfig.getHeaders();
		WebClient.Builder webClientBuilder = createWebClientBuilder(baseUrl, headers);

		JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

		if (sseEndpoint != null && !sseEndpoint.isEmpty()) {
			return new WebFluxSseClientTransport(webClientBuilder, jsonMapper, sseEndpoint);
		}
		else {
			return new WebFluxSseClientTransport(webClientBuilder, jsonMapper);
		}
	}

	/**
	 * Build STUDIO transport (STDIO). Sets up error handler for server stderr output to
	 * improve debugging and monitoring.
	 * @param serverConfig Server configuration
	 * @param serverName Server name
	 * @return STUDIO transport
	 * @throws IOException Thrown when build fails
	 */
	private McpClientTransport buildStudioTransport(McpServerConfig serverConfig, String serverName)
			throws IOException {
		String command = serverConfig.getCommand().trim();
		List<String> args = serverConfig.getArgs();
		Map<String, String> env = serverConfig.getEnv();

		logger.debug("Building STUDIO transport for server: {} with command: {}", serverName, command);

		ServerParameters.Builder builder = ServerParameters.builder(command);

		// Add parameters
		if (args != null && !args.isEmpty()) {
			builder.args(args);
			logger.debug("Added {} arguments for server: {}", args.size(), serverName);
		}

		// Add environment variables
		if (env != null && !env.isEmpty()) {
			builder.env(env);
			logger.debug("Added {} environment variables for server: {}", env.size(), serverName);
		}

		ServerParameters serverParameters = builder.build();
		JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
		StdioClientTransport transport = new StdioClientTransport(serverParameters, jsonMapper);

		// Set up error handler for server stderr output
		// This helps with debugging and monitoring server errors
		transport.setStdErrorHandler(error -> {
			if (error != null && !error.trim().isEmpty()) {
				// Log server stderr output for debugging
				// Filter out common non-error messages if needed
				if (error.contains("ERROR") || error.contains("FATAL") || error.contains("Exception")) {
					logger.error("MCP server stderr [{}]: {}", serverName, error);
				}
				else if (error.contains("WARN") || error.contains("WARNING")) {
					logger.warn("MCP server stderr [{}]: {}", serverName, error);
				}
				else {
					logger.debug("MCP server stderr [{}]: {}", serverName, error);
				}
			}
		});

		return transport;
	}

	/**
	 * Build STREAMING transport
	 * @param serverConfig Server configuration
	 * @param serverName Server name
	 * @return STREAMING transport
	 * @throws IOException Thrown when build fails
	 */
	private McpClientTransport buildStreamingTransport(McpServerConfig serverConfig, String serverName)
			throws IOException {
		String url = serverConfig.getUrl().trim();
		configValidator.validateUrl(url, serverName);

		URL parsedUrl = new URL(url);
		String baseUrl = parsedUrl.getProtocol() + "://" + parsedUrl.getHost()
				+ (parsedUrl.getPort() == -1 ? "" : ":" + parsedUrl.getPort());

		String streamEndpoint = parsedUrl.getPath();

		// Remove leading slash
		if (streamEndpoint.startsWith("/")) {
			streamEndpoint = streamEndpoint.substring(1);
		}

		// Set to null if empty
		if (streamEndpoint.isEmpty()) {
			streamEndpoint = null;
		}

		logger.info("Building Streamable HTTP transport for server: {} with Url: {} and Endpoint: {}", serverName,
				baseUrl, streamEndpoint);

		Map<String, String> headers = serverConfig.getHeaders();
		WebClient.Builder webClientBuilder = createWebClientBuilder(baseUrl, headers);

		logger.debug("Using WebClientStreamableHttpTransport with endpoint: {} for STREAMING mode", streamEndpoint);
		JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);
		return WebClientStreamableHttpTransport.builder(webClientBuilder)
			.jsonMapper(jsonMapper)
			.endpoint(streamEndpoint)
			.resumableStreams(true)
			.openConnectionOnStartup(false)
			.build();

	}

	/**
	 * Create WebClient builder (with baseUrl)
	 * @param baseUrl Base URL
	 * @param customHeaders Custom headers from configuration (can be null or empty)
	 * @return WebClient builder
	 */
	private WebClient.Builder createWebClientBuilder(String baseUrl, Map<String, String> customHeaders) {
		WebClient.Builder builder = WebClient.builder()
			.baseUrl(baseUrl)
			.defaultHeader("Accept", "text/event-stream")
			.defaultHeader("Content-Type", "application/json")
			.defaultHeader("User-Agent", mcpProperties.getUserAgent())
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 10))
			// Add timeout to prevent hanging connections
			.filter((request,
					next) -> next.exchange(request).timeout(java.time.Duration.ofSeconds(30)).onErrorMap(ex -> {
						if (ex.getMessage() != null && ex.getMessage().contains("Failed to resolve")) {
							return new IOException("DNS resolution failed for URL: " + baseUrl + ". "
									+ "Please verify the hostname is correct and accessible.", ex);
						}
						return ex;
					}));

		// Add custom headers from configuration
		if (customHeaders != null && !customHeaders.isEmpty()) {
			customHeaders.forEach((name, value) -> {
				if (name != null && value != null) {
					builder.defaultHeader(name, value);
					logger.debug("Added custom header: {} for baseUrl: {}", name, baseUrl);
				}
			});
		}

		return builder;
	}

}
