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

import type { Tool } from '@/types/tool'

/**
 * Tool API service class
 * Provides basic tool-related functionality without agent dependencies
 */
export class ToolApiService {
  /**
   * Handle HTTP response
   */
  private static async handleResponse(response: Response) {
    if (!response.ok) {
      try {
        const errorData = await response.json()
        throw new Error(errorData.message || `API request failed: ${response.status}`)
      } catch {
        throw new Error(`API request failed: ${response.status} ${response.statusText}`)
      }
    }
    return response
  }

  /**
   * Get available tools from backend API
   */
  static async getAvailableTools(): Promise<Tool[]> {
    try {
      const response = await fetch('/api/tools')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to get available tools:', error)
      throw error
    }
  }
}
