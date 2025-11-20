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
package com.alibaba.cloud.ai.lynxe.tool.textOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.alibaba.cloud.ai.lynxe.config.ManusProperties;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;

/**
 * Simple test class for enhanced hierarchical file access functionality Tests the new
 * behavior where sub-plans can read/write files from root folder, but create new files in
 * their own directory
 */
public class SimpleHierarchicalFileAccessTest {

	@TempDir
	Path tempDir;

	private TextFileService textFileService;

	private UnifiedDirectoryManager directoryManager;

	private ManusProperties manusProperties;

	@BeforeEach
	void setUp() throws IOException {
		// Create a simple test properties object that doesn't require config service
		manusProperties = new TestManusProperties();

		// Initialize services
		directoryManager = new UnifiedDirectoryManager(manusProperties);
		textFileService = new TextFileService();

		// Use reflection to inject dependencies for testing
		try {
			java.lang.reflect.Field manusPropertiesField = TextFileService.class.getDeclaredField("manusProperties");
			manusPropertiesField.setAccessible(true);
			manusPropertiesField.set(textFileService, manusProperties);

			java.lang.reflect.Field directoryManagerField = TextFileService.class
				.getDeclaredField("unifiedDirectoryManager");
			directoryManagerField.setAccessible(true);
			directoryManagerField.set(textFileService, directoryManager);
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to inject dependencies", e);
		}
	}

	@AfterEach
	void tearDown() {
		// Clean up test directories
		try {
			Files.walk(tempDir).sorted((path1, path2) -> path2.compareTo(path1)).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException e) {
					// Ignore cleanup errors
				}
			});
		}
		catch (IOException e) {
			// Ignore cleanup errors
		}
	}

	@Test
	void testSubPlanCanReadExistingRootFile() throws IOException {
		String rootPlanId = "root-plan-123";
		String subPlanId = "sub-plan-456";
		String fileName = "test.md";
		String content = "# Test Content\nThis is a test file.";

		// Create root plan directory and file
		Path rootDir = directoryManager.getRootPlanDirectory(rootPlanId);
		Files.createDirectories(rootDir);
		Path rootFile = rootDir.resolve(fileName);
		Files.writeString(rootFile, content);

		// Test that sub-plan can read the file from root directory
		Path resolvedPath = textFileService.getAbsolutePath(rootPlanId, fileName, subPlanId);

		assertTrue(Files.exists(resolvedPath), "Sub-plan should be able to access root file");
		assertEquals(rootFile, resolvedPath, "Should resolve to root file path");
		assertEquals(content, Files.readString(resolvedPath), "Should read correct content");
	}

	@Test
	void testSubPlanCreatesNewFileInSubPlanDirectory() throws IOException {
		String rootPlanId = "root-plan-123";
		String subPlanId = "sub-plan-456";
		String fileName = "new-file.md";

		// Test that new file creation uses sub-plan directory
		Path createPath = textFileService.getCreateFilePath(rootPlanId, fileName, subPlanId);

		// Verify the path points to sub-plan directory
		Path expectedSubPlanDir = directoryManager.getSubTaskDirectory(rootPlanId, subPlanId);
		assertTrue(createPath.startsWith(expectedSubPlanDir), "New file should be created in sub-plan directory");

		// Create the file
		Files.createDirectories(createPath.getParent());
		Files.writeString(createPath, "# New File\nCreated by sub-plan.");

		// Verify file exists in sub-plan directory
		assertTrue(Files.exists(createPath), "File should exist in sub-plan directory");
	}

	@Test
	void testSubPlanWritesToExistingRootFile() throws IOException {
		String rootPlanId = "root-plan-123";
		String subPlanId = "sub-plan-456";
		String fileName = "shared.md";
		String originalContent = "# Original Content\nThis is the original content.";
		String newContent = "# Updated Content\nThis content was updated by sub-plan.";

		// Create root plan directory and file
		Path rootDir = directoryManager.getRootPlanDirectory(rootPlanId);
		Files.createDirectories(rootDir);
		Path rootFile = rootDir.resolve(fileName);
		Files.writeString(rootFile, originalContent);

		// Test that sub-plan writes to the existing root file
		Path resolvedPath = textFileService.getAbsolutePath(rootPlanId, fileName, subPlanId);

		assertTrue(Files.exists(resolvedPath), "Sub-plan should access existing root file");
		assertEquals(rootFile, resolvedPath, "Should resolve to root file path");

		// Write new content
		Files.writeString(resolvedPath, newContent);

		// Verify content was written to root file
		assertEquals(newContent, Files.readString(rootFile), "Root file should contain updated content");
	}

	@Test
	void testRootPlanBehaviorUnchanged() throws IOException {
		String rootPlanId = "root-plan-123";
		String fileName = "root-file.md";
		String content = "# Root File\nThis is a root plan file.";

		// Test root plan file operations (should work as before)
		Path createPath = textFileService.getCreateFilePath(rootPlanId, fileName, rootPlanId);
		Path expectedRootDir = directoryManager.getRootPlanDirectory(rootPlanId);

		assertTrue(createPath.startsWith(expectedRootDir), "Root plan should create files in root directory");

		// Create and verify file
		Files.createDirectories(createPath.getParent());
		Files.writeString(createPath, content);

		assertTrue(Files.exists(createPath), "Root file should exist");
		assertEquals(content, Files.readString(createPath), "Should read correct content");
	}

	@Test
	void testFileNotFoundInRootCreatesInSubPlan() throws IOException {
		String rootPlanId = "root-plan-123";
		String subPlanId = "sub-plan-456";
		String fileName = "non-existent.md";

		// Test that when file doesn't exist in root, sub-plan creates it in sub-plan
		// directory
		Path resolvedPath = textFileService.getAbsolutePath(rootPlanId, fileName, subPlanId);

		// Should resolve to sub-plan directory since file doesn't exist in root
		Path expectedSubPlanDir = directoryManager.getSubTaskDirectory(rootPlanId, subPlanId);
		assertTrue(resolvedPath.startsWith(expectedSubPlanDir),
				"Should resolve to sub-plan directory when file not in root");

		// Create the file
		Files.createDirectories(resolvedPath.getParent());
		Files.writeString(resolvedPath, "# Non-existent File\nCreated in sub-plan directory.");

		assertTrue(Files.exists(resolvedPath), "File should exist in sub-plan directory");
	}

	/**
	 * Test implementation of ManusProperties that doesn't require IConfigService
	 */
	private static class TestManusProperties extends ManusProperties {

	}

}
