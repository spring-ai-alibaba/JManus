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
package com.alibaba.cloud.ai.lynxe.user.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.lynxe.user.service.UserService;

/**
 * Language Controller for handling language preference REST endpoints
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class LanguageController {

	private static final Logger logger = LoggerFactory.getLogger(LanguageController.class);

	private final UserService userService;

	public LanguageController(UserService userService) {
		this.userService = userService;
	}

	/**
	 * Get current language preference
	 * @return Current language preference ("zh" or "en")
	 */
	@GetMapping("/language")
	public ResponseEntity<Map<String, Object>> getLanguage() {
		try {
			logger.debug("Getting current language preference");
			String language = userService.getLanguage();
			return ResponseEntity.ok(Map.of("language", language));
		}
		catch (Exception e) {
			logger.error("Error getting language preference", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "Failed to get language preference: " + e.getMessage()));
		}
	}

	/**
	 * Set language preference
	 * @param request Request body containing language ("zh" or "en")
	 * @return Success response with updated language
	 */
	@PostMapping("/language")
	public ResponseEntity<Map<String, Object>> setLanguage(@RequestBody Map<String, String> request) {
		try {
			String language = request.get("language");
			if (language == null || language.trim().isEmpty()) {
				return ResponseEntity.badRequest()
					.body(Map.of("success", false, "error", "Language parameter is required"));
			}
			logger.info("Setting language preference to: {}", language);
			boolean success = userService.setLanguage(language);
			if (success) {
				return ResponseEntity.ok(Map.of("success", true, "language", language));
			}
			else {
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("success", false, "error", "Failed to set language preference"));
			}
		}
		catch (Exception e) {
			logger.error("Error setting language preference", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("success", false, "error", "Failed to set language preference: " + e.getMessage()));
		}
	}

}

