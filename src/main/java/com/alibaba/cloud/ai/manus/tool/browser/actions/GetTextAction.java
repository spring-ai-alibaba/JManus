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

public class GetTextAction extends BrowserAction {

	private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GetTextAction.class);

	private static String READABILITY_JS;

	private static String TURNDOWNSERVICE_JS;

	// Removed the static initialization block, directly using string constants

	private static final String CONVERSE_FRAME_TO_MARKDOWN_JS = """
			    (() => {
			        try {
			            const documentClone = window.document.cloneNode(true);
			            const reader = new Readability(documentClone);
			            const article = reader.parse();

			            if (article && article.content) {
			                const html = article.content;
			                const turndownService = new TurndownService({
			                    headingStyle: 'atx',
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
		ClassPathResource readabilityResource = new ClassPathResource("tool/Readability.js");
		try (InputStream is = readabilityResource.getInputStream()) {
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			READABILITY_JS = new String(bytes);
		}
		catch (IOException e) {
		}
		ClassPathResource turndownResource = new ClassPathResource("tool/turndown.js");
		try (InputStream is = turndownResource.getInputStream()) {
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			TURNDOWNSERVICE_JS = new String(bytes);
		}
		catch (IOException e) {
		}
	}

	public GetTextAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Page page = getCurrentPage(); // Get Playwright Page instance
		StringBuilder allText = new StringBuilder();
		for (com.microsoft.playwright.Frame frame : page.frames()) {
			try {
				// frame.evaluate(READABILITY_JS);
				// frame.evaluate(TURNDOWNSERVICE_JS);
				String text = frame.innerText("html");
				// String textMd = (String) frame.evaluate(CONVERSE_FRAME_TO_MARKDOWN_JS);
				if (text != null && !text.isEmpty()) {
					allText.append("<inner_text>\n");
					allText.append(text).append("\\n");
					allText.append("</inner_text>\n");
					// allText.append("<inner_text_md>\n");
					// allText.append(textMd).append("\\n");
					// allText.append("</inner_text_md>\n");
				}
			}
			catch (Exception e) {
				// Ignore frames without body
			}
		}
		String result = allText.toString().trim();

		// Log only first 10 lines for brevity
		String[] lines = result.split("\n");
		String logPreview = lines.length > 10 ? String.join("\n", java.util.Arrays.copyOf(lines, 10)) + "\n... (total "
				+ lines.length + " lines, showing first 10)" : result;
		log.info("get_text all frames body is {}", logPreview);
		log.debug("get_text all frames body is {}", result);
		return new ToolExecuteResult(result);
	}

}
