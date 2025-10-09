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
package com.alibaba.cloud.ai.manus.mapreduce;

import java.util.Map;
import java.util.Properties;

/**
 * JobConfiguration holds configuration information for a MapReduce job.
 * 
 * <p>This class provides methods to set and get various job parameters,
 * including mapper/reducer classes, input/output formats, and other
 * job-specific settings.</p>
 */
public interface JobConfiguration {

    /**
     * Sets a configuration property.
     *
     * @param name the property name
     * @param value the property value
     */
    void set(String name, String value);

    /**
     * Gets a configuration property.
     *
     * @param name the property name
     * @return the property value, or null if not set
     */
    String get(String name);

    /**
     * Gets a configuration property with a default value.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value, or the default value if not set
     */
    String get(String name, String defaultValue);

    /**
     * Gets a configuration property as an integer.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value as an integer
     */
    int getInt(String name, int defaultValue);

    /**
     * Gets a configuration property as a long.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value as a long
     */
    long getLong(String name, long defaultValue);

    /**
     * Gets a configuration property as a boolean.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value as a boolean
     */
    boolean getBoolean(String name, boolean defaultValue);

    /**
     * Sets a configuration property as an integer.
     *
     * @param name the property name
     * @param value the property value
     */
    void setInt(String name, int value);

    /**
     * Sets a configuration property as a long.
     *
     * @param name the property name
     * @param value the property value
     */
    void setLong(String name, long value);

    /**
     * Sets a configuration property as a boolean.
     *
     * @param name the property name
     * @param value the property value
     */
    void setBoolean(String name, boolean value);

    /**
     * Gets all configuration properties as a Map.
     *
     * @return a Map containing all configuration properties
     */
    Map<String, String> getAll();

    /**
     * Sets all configuration properties from a Map.
     *
     * @param properties the properties to set
     */
    void setAll(Map<String, String> properties);

    /**
     * Sets all configuration properties from a Properties object.
     *
     * @param properties the properties to set
     */
    void setAll(Properties properties);

    /**
     * Removes a configuration property.
     *
     * @param name the property name
     * @return the removed property value, or null if not set
     */
    String remove(String name);

    /**
     * Checks if a configuration property exists.
     *
     * @param name the property name
     * @return true if the property exists
     */
    boolean contains(String name);

    /**
     * Clears all configuration properties.
     */
    void clear();

    /**
     * Gets the number of configuration properties.
     *
     * @return the number of properties
     */
    int size();

    /**
     * Checks if the configuration is empty.
     *
     * @return true if the configuration is empty
     */
    boolean isEmpty();
}
