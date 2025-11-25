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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.alibaba.cloud.ai.lynxe.mcp.config.McpProperties;

/**
 * Test class for McpConfigValidator
 */
@ExtendWith(MockitoExtension.class)
class McpConfigValidatorTest {

	@Mock
	private McpProperties mcpProperties;

	private McpConfigValidator validator;

	@BeforeEach
	void setUp() {
		validator = new McpConfigValidator(mcpProperties);
	}

	@Test
	void testValidateUrl_ValidUrl() throws IOException {
		// Test with a valid, resolvable URL
		assertDoesNotThrow(() -> validator.validateUrl("https://www.google.com", "test-server"));
	}

	@Test
	void testValidateUrl_InvalidHostname() {
		// Test with an invalid hostname that should fail DNS resolution
		IOException exception = assertThrows(IOException.class,
				() -> validator.validateUrl("https://invalid-hostname-that-does-not-exist.com", "test-server"));

		assertTrue(exception.getMessage().contains("DNS resolution failed"));
		assertTrue(exception.getMessage().contains("test-server"));
	}

	@Test
	void testValidateUrl_EmptyUrl() {
		IOException exception = assertThrows(IOException.class, () -> validator.validateUrl("", "test-server"));

		assertTrue(exception.getMessage().contains("Invalid or missing MCP server URL"));
	}

	@Test
	void testValidateUrl_NullUrl() {
		IOException exception = assertThrows(IOException.class, () -> validator.validateUrl(null, "test-server"));

		assertTrue(exception.getMessage().contains("Invalid or missing MCP server URL"));
	}

	@Test
	void testValidateUrl_InvalidProtocol() {
		IOException exception = assertThrows(IOException.class,
				() -> validator.validateUrl("ftp://example.com", "test-server"));

		assertTrue(exception.getMessage().contains("Unsupported protocol"));
	}

	@Test
	void testValidateUrl_MalformedUrl() {
		IOException exception = assertThrows(IOException.class,
				() -> validator.validateUrl("not-a-valid-url", "test-server"));

		assertTrue(exception.getMessage().contains("Invalid URL format"));
	}

}
