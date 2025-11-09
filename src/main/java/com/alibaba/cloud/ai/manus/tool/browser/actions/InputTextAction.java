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

import com.alibaba.cloud.ai.manus.tool.browser.AriaElementHolder;
import com.alibaba.cloud.ai.manus.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.microsoft.playwright.Locator;

public class InputTextAction extends BrowserAction {

	public InputTextAction(BrowserUseTool browserUseTool) {
		super(browserUseTool);
	}

	@Override
	public ToolExecuteResult execute(BrowserRequestVO request) throws Exception {
		Integer index = request.getIndex();
		String text = request.getText();

		if (index == null || text == null) {
			return new ToolExecuteResult("Index and text are required for 'input_text' action");
		}

		// Get ARIA node to check role
		AriaElementHolder.AriaNode node = getAriaNodeByIdx(index);
		if (node == null) {
			return new ToolExecuteResult("Element with index " + index + " not found in ARIA snapshot");
		}

		// Check if element is a text input element
		if (!"textbox".equals(node.role) && !"combobox".equals(node.role)) {
			return new ToolExecuteResult(
					"Element at index " + index + " is not an input element (role: " + node.role + ")");
		}

		// Get element locator
		Locator elementLocator = getLocatorByIdx(index);
		if (elementLocator == null) {
			return new ToolExecuteResult("Failed to create locator for element with index " + index);
		}

		// Set timeout for element operations to prevent hanging
		Integer timeoutMs = getElementTimeoutMs();

		// Try fill with timeout
		try {
			Locator.FillOptions fillOptions = new Locator.FillOptions().setTimeout(timeoutMs);
			elementLocator.fill("", fillOptions); // Clear first
			// Set character input delay to 100ms, adjustable as needed
			Locator.PressSequentiallyOptions options = new Locator.PressSequentiallyOptions().setDelay(100)
				.setTimeout(timeoutMs);
			elementLocator.pressSequentially(text, options);
		}
		catch (Exception e) {
			// If fill fails, try direct fill
			try {
				Locator.FillOptions fillOptions = new Locator.FillOptions().setTimeout(timeoutMs);
				elementLocator.fill("", fillOptions); // Clear again
				elementLocator.fill(text, fillOptions); // Direct fill
			}
			catch (Exception e2) {
				// If still fails, use JS assignment and trigger input event
				try {
					elementLocator.evaluate(
							"(el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })); }",
							text);
				}
				catch (Exception e3) {
					return new ToolExecuteResult("Input failed: " + e3.getMessage());
				}
			}
		}

		// Wait 500ms after input to allow page to update and JavaScript events to process
		try {
			Thread.sleep(500);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			// Continue execution even if interrupted
		}

		return new ToolExecuteResult(
				"Successfully input: '" + text + "' to the specified element with index: " + index);
	}

}
