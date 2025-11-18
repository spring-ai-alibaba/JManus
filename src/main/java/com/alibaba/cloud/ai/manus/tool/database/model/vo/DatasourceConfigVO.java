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
package com.alibaba.cloud.ai.manus.tool.database.model.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

/**
 * Datasource configuration value object for API responses
 */
public class DatasourceConfigVO {

	private Long id;

	private String name;

	private String type;

	private Boolean enable;

	private String url;

	@JsonProperty("driver_class_name")
	private String driverClassName;

	private String username;

	@JsonProperty(access = Access.WRITE_ONLY)
	private String password;

	@JsonProperty("password_set")
	private Boolean passwordSet;

	@JsonProperty("created_at")
	private LocalDateTime createdAt;

	@JsonProperty("updated_at")
	private LocalDateTime updatedAt;

	// Constructors
	public DatasourceConfigVO() {
	}

	public DatasourceConfigVO(String name, String type, Boolean enable, String url, String driverClassName,
			String username, String password) {
		this.name = name;
		this.type = type;
		this.enable = enable;
		this.url = url;
		this.driverClassName = driverClassName;
		this.username = username;
		this.password = password;
	}

	// Getters and Setters
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDriverClassName() {
		return driverClassName;
	}

	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Boolean getPasswordSet() {
		return passwordSet;
	}

	public void setPasswordSet(Boolean passwordSet) {
		this.passwordSet = passwordSet;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	@Override
	public String toString() {
		return "DatasourceConfigVO{" + "id=" + id + ", name='" + name + '\'' + ", type='" + type + '\'' + ", enable="
				+ enable + ", url='" + url + '\'' + ", driverClassName='" + driverClassName + '\'' + ", username='"
				+ username + '\'' + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + '}';
	}

}
