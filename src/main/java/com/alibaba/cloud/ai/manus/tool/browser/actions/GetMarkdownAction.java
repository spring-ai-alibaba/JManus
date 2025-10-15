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
package com.alibaba.cloud.ai.manus.tool.browser.actions;

import com.microsoft.playwright.Page;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.ClassPathResource;

import com.alibaba.cloud.ai.manus.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;

public class GetMarkdownAction extends BrowserAction {

	private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GetMarkdownAction.class);

	private static String READABILITY_JS;

	private static String TURNDOWNSERVICE_JS;

	// JavaScript code to convert frame content to markdown
	private static final String CONVERT_FRAME_TO_MARKDOWN_JS = """
				(() => {
					try {
						const documentClone = window.document.cloneNode(true);
						const reader = new Readability(documentClone);
						const article = reader.parse();

						if (article && article.content) {
							const html = article.content;
							const turndownService = new TurndownService({
								headingStyle: 'atx',
								codeBlockStyle: 'fenced',
								bulletListMarker: '-',
								linkStyle: 'inlined',
								linkReferenceStyle: 'full'
							});
							return turndownService.turndown(html);
						}
						return "";
					} catch (error) {
						console.error('Error converting frame to markdown:', error);
						return "";
					}
				})
			""";

	static {
		// Load Readability.js library
		ClassPathResource readabilityResource = new ClassPathResource("tool/Readability.js");
		try (InputStream is = readabilityResource.getInputStream()) {
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			READABILITY_JS = new String(bytes);
		}
		catch (IOException e) {
			log.warn("Failed to load Readability.js: {}", e.getMessage());
		}

		// Load TurndownService.js library
		ClassPathResource turndownResource = new ClassPathResource("tool/turndown.js");
		try (InputStream is = turndownResource.getInputStream()) {
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			TURNDOWNSERVICE_JS = new String(bytes);
		}
		catch (IOException e) {
			log.warn("Failed to load turndown.js: {}", e.getMessage());
		}
	}

	public GetMarkdownAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Page page = getCurrentPage(); // Get Playwright Page instance
		StringBuilder allMarkdown = new StringBuilder();

		for (com.microsoft.playwright.Frame frame : page.frames()) {
			try {
				// Inject required JavaScript libraries into the frame
				if (READABILITY_JS != null && !READABILITY_JS.isEmpty()) {
					frame.evaluate(READABILITY_JS);
				}
				if (TURNDOWNSERVICE_JS != null && !TURNDOWNSERVICE_JS.isEmpty()) {
					frame.evaluate(TURNDOWNSERVICE_JS);
				}

				// Convert frame content to markdown
				String markdown = (String) frame.evaluate(CONVERT_FRAME_TO_MARKDOWN_JS);

				if (markdown != null && !markdown.trim().isEmpty()) {
					allMarkdown.append("<markdown_content>\n");
					allMarkdown.append(markdown).append("\n");
					allMarkdown.append("</markdown_content>\n");
				}
			}
			catch (Exception e) {
				log.debug("Failed to convert frame to markdown: {}", e.getMessage());
				// Fallback to innerText if markdown conversion fails
				try {
					String text = frame.innerText("html");
					if (text != null && !text.trim().isEmpty()) {
						allMarkdown.append("<fallback_text>\n");
						allMarkdown.append(text).append("\n");
						allMarkdown.append("</fallback_text>\n");
					}
				}
				catch (Exception fallbackException) {
					log.debug("Fallback text extraction also failed: {}", fallbackException.getMessage());
				}
			}
		}

		String result = allMarkdown.toString().trim();

		// Log only first 10 lines for brevity
		String[] lines = result.split("\n");
		String logPreview = lines.length > 10 ? String.join("\n", java.util.Arrays.copyOf(lines, 10)) + "\n... (total "
				+ lines.length + " lines, showing first 10)" : result;
		log.info("get_markdown all frames content is {}", logPreview);
		log.debug("get_markdown all frames content is {}", result);

		return new ToolExecuteResult(result);
	}

}
