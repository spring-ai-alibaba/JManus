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
package com.alibaba.cloud.ai.manus.user.service;

import com.alibaba.cloud.ai.manus.user.model.po.UserEntity;
import com.alibaba.cloud.ai.manus.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * User data initializer to create default users Follows the same pattern as
 * PromptDataInitializer
 */
@Component
public class UserDataInitializer implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(UserDataInitializer.class);

	private final UserRepository userRepository;

	public UserDataInitializer(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public void run(String... args) throws Exception {
		initializeDefaultUsers();
	}

	/**
	 * Initialize default users if they don't exist
	 */
	private void initializeDefaultUsers() {
		logger.info("Starting user data initialization");

		// Check if default user exists
		if (!userRepository.existsByUsername("jmanus_user")) {
			UserEntity defaultUser = new UserEntity("jmanus_user", "user@jmanus.ai", "JManus User");
			defaultUser.setStatus("active");
			defaultUser.setCreatedAt(LocalDateTime.now().minusDays(30));
			defaultUser.setLastLogin(LocalDateTime.now().minusHours(2));
			defaultUser.setPreferences(Arrays.asList("dark_mode", "notifications_enabled", "auto_save"));

			userRepository.save(defaultUser);
			logger.info("Created default user: {}", defaultUser.getUsername());
		}
		else {
			logger.info("Default user already exists, skipping creation");
		}

		long userCount = userRepository.count();
		logger.info("User data initialization completed. Total users: {}", userCount);
	}

}
