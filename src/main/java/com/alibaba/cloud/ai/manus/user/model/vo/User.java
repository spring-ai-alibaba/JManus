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
package com.alibaba.cloud.ai.manus.user.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * User value object representing basic user information
 * Simple structure without complex user management features
 */
public class User {

	private Long id;

	private String username;

	private String email;

	@JsonProperty("display_name")
	private String displayName;

	@JsonProperty("created_at")
	private LocalDateTime createdAt;

	@JsonProperty("last_login")
	private LocalDateTime lastLogin;

	private String status;

	private List<String> preferences;

	@JsonProperty("current_conversation_id")
	private String currentConversationId;

	// Constructors
	public User() {
	}

	public User(String username, String email, String displayName) {
		this.username = username;
		this.email = email;
		this.displayName = displayName;
		this.status = "active";
		this.createdAt = LocalDateTime.now();
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getLastLogin() {
		return lastLogin;
	}

	public void setLastLogin(LocalDateTime lastLogin) {
		this.lastLogin = lastLogin;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<String> getPreferences() {
		return preferences;
	}

	public void setPreferences(List<String> preferences) {
		this.preferences = preferences;
	}

	@Override
	public String toString() {
		return "User{" + "id=" + id + ", username='" + username + '\'' + ", email='" + email + '\''
				+ ", displayName='" + displayName + '\'' + ", status='" + status + '\'' + '}';
	}

}
