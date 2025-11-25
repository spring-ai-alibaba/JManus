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
package com.alibaba.cloud.ai.lynxe.planning.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Plan Template Access Level Enum Defines the access level for plan templates
 */
public enum PlanTemplateAccessLevel {

	/**
	 * Read-only access level - templates cannot be modified or deleted from frontend
	 */
	READ_ONLY("readOnly"),

	/**
	 * Editable access level - templates can be modified and deleted (default)
	 */
	EDITABLE("editable");

	private final String value;

	PlanTemplateAccessLevel(String value) {
		this.value = value;
	}

	/**
	 * Get the string value for JSON serialization
	 * @return String value
	 */
	@JsonValue
	public String getValue() {
		return value;
	}

	/**
	 * Get enum from string value for JSON deserialization
	 * @param value String value
	 * @return PlanTemplateAccessLevel enum, defaults to EDITABLE if not found
	 */
	@JsonCreator
	public static PlanTemplateAccessLevel fromValue(String value) {
		if (value == null || value.trim().isEmpty()) {
			return EDITABLE;
		}
		for (PlanTemplateAccessLevel level : values()) {
			if (level.value.equalsIgnoreCase(value)) {
				return level;
			}
		}
		// Default to EDITABLE for unknown values
		return EDITABLE;
	}

	/**
	 * Check if the access level is read-only
	 * @param value String value
	 * @return true if read-only, false otherwise
	 */
	public static boolean isReadOnly(String value) {
		return READ_ONLY.value.equalsIgnoreCase(value);
	}

}
