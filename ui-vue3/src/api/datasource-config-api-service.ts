/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

export interface DatasourceConfig {
  id?: number
  name: string
  type: string
  enable: boolean
  url: string
  driver_class_name: string
  username: string
  password?: string
  password_set?: boolean
  created_at?: string
  updated_at?: string
}

export class DatasourceConfigApiService {
  private static readonly BASE_URL = '/api/datasource-configs'

  /**
   * Get all datasource configurations
   */
  public static async getAllConfigs(): Promise<DatasourceConfig[]> {
    const response = await fetch(this.BASE_URL)
    if (!response.ok) {
      throw new Error(`Failed to fetch datasource configurations: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Get datasource configuration by ID
   */
  public static async getConfigById(id: number): Promise<DatasourceConfig> {
    const response = await fetch(`${this.BASE_URL}/${id}`)
    if (!response.ok) {
      throw new Error(`Failed to fetch datasource configuration: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Get datasource configuration by name
   */
  public static async getConfigByName(name: string): Promise<DatasourceConfig> {
    const response = await fetch(`${this.BASE_URL}/name/${encodeURIComponent(name)}`)
    if (!response.ok) {
      throw new Error(`Failed to fetch datasource configuration: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Get enabled datasource configurations
   */
  public static async getEnabledConfigs(): Promise<DatasourceConfig[]> {
    const response = await fetch(`${this.BASE_URL}/enabled`)
    if (!response.ok) {
      throw new Error(`Failed to fetch enabled datasource configurations: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Create new datasource configuration
   */
  public static async createConfig(config: DatasourceConfig): Promise<DatasourceConfig> {
    const response = await fetch(this.BASE_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(config),
    })
    if (!response.ok) {
      throw new Error(`Failed to create datasource configuration: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Update existing datasource configuration
   */
  public static async updateConfig(
    id: number,
    config: DatasourceConfig
  ): Promise<DatasourceConfig> {
    const response = await fetch(`${this.BASE_URL}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(config),
    })
    if (!response.ok) {
      throw new Error(`Failed to update datasource configuration: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Delete datasource configuration
   */
  public static async deleteConfig(id: number): Promise<void> {
    const response = await fetch(`${this.BASE_URL}/${id}`, {
      method: 'DELETE',
    })
    if (!response.ok) {
      throw new Error(`Failed to delete datasource configuration: ${response.statusText}`)
    }
  }

  /**
   * Check if datasource configuration exists by name
   */
  public static async existsByName(name: string): Promise<boolean> {
    const response = await fetch(`${this.BASE_URL}/exists/${encodeURIComponent(name)}`)
    if (!response.ok) {
      throw new Error(`Failed to check datasource configuration existence: ${response.statusText}`)
    }
    return await response.json()
  }

  /**
   * Test database connection
   */
  public static async testConnection(
    config: DatasourceConfig
  ): Promise<{ success: boolean; message: string }> {
    const response = await fetch(`${this.BASE_URL}/test-connection`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(config),
    })
    if (!response.ok) {
      throw new Error(`Failed to test connection: ${response.statusText}`)
    }
    return await response.json()
  }
}
