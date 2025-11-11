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
package com.alibaba.cloud.ai.manus.tool.browser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.playwright.Page;

public class AriaElementHelper {

	private static final Logger log = LoggerFactory.getLogger(AriaElementHelper.class);



	/**
	 * Replace aria-id-num patterns with [idx=num] in ARIA snapshot text
	 * This method processes the snapshot text and replaces patterns like:
	 * - button "aria-id-1" -> button [idx=1]
	 * - link "aria-id-5" [checked] -> link [idx=5] [checked]
	 * @param snapshot The original ARIA snapshot text
	 * @return The snapshot text with aria-id-num replaced by [idx=num]
	 */
	public static String replaceAriaIdWithIdx(String snapshot) {
		if (snapshot == null || snapshot.isEmpty()) {
			return snapshot;
		}

		// Pattern to match: role "aria-id-N" followed by optional attributes
		// This will match lines like:
		// - button "aria-id-1"
		// - link "aria-id-5" [checked]
		// - textbox "aria-id-10" [disabled] [required]
		// The pattern captures: role, number, and any trailing attributes
		Pattern pattern = Pattern.compile("(\\w+)\\s+\"aria-id-(\\d+)\"((?:\\s+\\[[^\\]]+\\])*)");
		Matcher matcher = pattern.matcher(snapshot);

		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String role = matcher.group(1);
			String num = matcher.group(2);
			String attributes = matcher.group(3); // May be null or empty
			// Replace "aria-id-N" with [idx=N], preserving any existing attributes
			String replacement = role + " [idx=" + num + "]" + (attributes != null ? attributes : "");
			matcher.appendReplacement(result, replacement);
		}
		matcher.appendTail(result);

		return result.toString();
	}
	public static String parsePageAndAssignRefs(Page page)
	{
		return parsePageAndAssignRefs(page, null);
	}


	/**
	 * Parse page, replace aria-id-num with [idx=num], and return processed snapshot
	 * This method encapsulates the common pattern of parsing a page and preparing it for use
	 * @param page The page to parse
	 * @param options Snapshot options (if null, default options will be used)
	 * @return Processed YAML snapshot string with aria-id-num replaced by [idx=num], or null if parsing failed
	 */
	public static String parsePageAndAssignRefs(Page page, AriaSnapshotOptions options) {
		if (page == null) {
			log.warn("Cannot parse null page");
			return null;
		}

		try {
			// Use default options if none provided
			if (options == null) {
				options = new AriaSnapshotOptions().setSelector("body").setTimeout(30000);
			}

			// Generate ARIA snapshot
			String snapshot = AriaSnapshot.ariaSnapshot(page, options);
			if (snapshot != null && !snapshot.isEmpty()) {
				// Replace aria-id-num with [idx=num] in the snapshot text
				String processedSnapshot = replaceAriaIdWithIdx(snapshot);
				log.debug("Replaced aria-id-num patterns with [idx=num] in snapshot");

				// Return the processed snapshot text
				return processedSnapshot;
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse page and assign refs: {}", e.getMessage());
		}

		return null;
	}

}
