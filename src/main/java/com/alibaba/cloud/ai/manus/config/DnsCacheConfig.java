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

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * DNS缓存和网络配置 解决VPN环境下的DNS解析超时问题
 */
@Configuration
public class DnsCacheConfig {

	private static final Logger log = LoggerFactory.getLogger(DnsCacheConfig.class);

	/**
	 * 配置带有DNS缓存的WebClient
	 */
	@Bean
	public WebClient webClientWithDnsCache() {
		log.info("Configuring WebClient with DNS cache and extended timeouts");

		// 创建连接提供者，增加连接池大小和超时时间
		ConnectionProvider connectionProvider = ConnectionProvider.builder("dns-cache-pool")
			.maxConnections(100)
			.maxIdleTime(Duration.ofMinutes(5))
			.maxLifeTime(Duration.ofMinutes(10))
			.pendingAcquireTimeout(Duration.ofSeconds(30))
			.evictInBackground(Duration.ofSeconds(120))
			.build();

		// 配置HttpClient with DNS缓存和超时设置
		HttpClient httpClient = HttpClient.create(connectionProvider)
			// 使用默认地址解析器组（包含DNS缓存）
			.resolver(DefaultAddressResolverGroup.INSTANCE)
			// 设置连接超时
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30秒
			// 设置读取超时
			.doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
				.addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)))
			// 启用TCP保活
			.option(ChannelOption.SO_KEEPALIVE, true)
			// 设置TCP_NODELAY
			.option(ChannelOption.TCP_NODELAY, true);

		return WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
			.build();
	}

	/**
	 * 配置系统属性以启用DNS缓存
	 */
	@Bean
	public DnsCacheInitializer dnsCacheInitializer() {
		return new DnsCacheInitializer();
	}

	/**
	 * DNS缓存初始化器
	 */
	public static class DnsCacheInitializer {

		static {
			log.info("Initializing DNS cache settings");

			// 启用DNS缓存
			System.setProperty("java.net.useSystemProxies", "true");
			System.setProperty("networkaddress.cache.ttl", "300"); // 5分钟缓存
			System.setProperty("networkaddress.cache.negative.ttl", "60"); // 1分钟负缓存

			// Netty DNS设置
			System.setProperty("io.netty.resolver.dns.cache.ttl", "300"); // 5分钟
			System.setProperty("io.netty.resolver.dns.cache.negative.ttl", "60"); // 1分钟
			System.setProperty("io.netty.resolver.dns.queryTimeoutMillis", "10000"); // 10秒超时

			// 启用Netty DNS缓存
			System.setProperty("io.netty.resolver.dns.cache.enabled", "true");
			System.setProperty("io.netty.resolver.dns.cache.maxTtl", "300");
			System.setProperty("io.netty.resolver.dns.cache.minTtl", "60");

			log.info("DNS cache settings initialized successfully");
		}

	}

}
