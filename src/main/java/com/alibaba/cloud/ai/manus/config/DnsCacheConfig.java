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
package com.alibaba.cloud.ai.manus.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * DNS cache and network configuration to resolve DNS resolution timeout issues in VPN
 * environments
 */
@Configuration
public class DnsCacheConfig {

	private static final Logger log = LoggerFactory.getLogger(DnsCacheConfig.class);

	@Lazy
	@Autowired(required = false)
	private ManusProperties manusProperties;

	/**
	 * Get configured LLM read timeout from ManusProperties, defaulting to 120 seconds if not configured
	 */
	private int getLlmReadTimeoutSeconds() {
		if (manusProperties != null && manusProperties.getLlmReadTimeout() != null) {
			return manusProperties.getLlmReadTimeout();
		}
		return 120; // Default 120 seconds (2 minutes)
	}

	/**
	 * Configure WebClient with DNS cache
	 */
	@Bean
	public WebClient webClientWithDnsCache() {
		int readTimeoutSeconds = getLlmReadTimeoutSeconds();
		log.info("Configuring WebClient with DNS cache and extended timeouts (read timeout: {} seconds)",
				readTimeoutSeconds);

		// Create connection provider with increased connection pool size and timeout
		ConnectionProvider connectionProvider = ConnectionProvider.builder("dns-cache-pool")
			.maxConnections(100)
			.maxIdleTime(Duration.ofMinutes(5))
			.maxLifeTime(Duration.ofMinutes(10))
			.pendingAcquireTimeout(Duration.ofSeconds(30))
			.evictInBackground(Duration.ofSeconds(120))
			.build();

		// Configure HttpClient with DNS cache and timeout settings
		HttpClient httpClient = HttpClient.create(connectionProvider)
			// Use default address resolver group (includes DNS cache)
			.resolver(DefaultAddressResolverGroup.INSTANCE)
			// Set connection timeout
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30 seconds
			// Set read and write timeout from configuration
			.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS))
				.addHandlerLast(new WriteTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS)))
			// Enable TCP keep-alive
			.option(ChannelOption.SO_KEEPALIVE, true)
			// Set TCP_NODELAY
			.option(ChannelOption.TCP_NODELAY, true);

		return WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
			.build();
	}

	/**
	 * Configure system properties to enable DNS cache
	 */
	@Bean
	public DnsCacheInitializer dnsCacheInitializer() {
		return new DnsCacheInitializer();
	}

	/**
	 * DNS cache initializer
	 */
	public static class DnsCacheInitializer {

		static {
			log.info("Initializing DNS cache settings");

			// Enable DNS cache
			System.setProperty("java.net.useSystemProxies", "true");
			System.setProperty("networkaddress.cache.ttl", "300"); // 5 minutes cache
			System.setProperty("networkaddress.cache.negative.ttl", "60"); // 1 minute
																			// negative
																			// cache

			// Netty DNS settings
			System.setProperty("io.netty.resolver.dns.cache.ttl", "300"); // 5 minutes
			System.setProperty("io.netty.resolver.dns.cache.negative.ttl", "60"); // 1
																					// minute
			System.setProperty("io.netty.resolver.dns.queryTimeoutMillis", "10000"); // 10
																						// seconds
																						// timeout

			// Enable Netty DNS cache
			System.setProperty("io.netty.resolver.dns.cache.enabled", "true");
			System.setProperty("io.netty.resolver.dns.cache.maxTtl", "300");
			System.setProperty("io.netty.resolver.dns.cache.minTtl", "60");

			log.info("DNS cache settings initialized successfully");
		}

	}

}
