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

import type { PlanExecutionRecord } from '@/types/plan-execution-record'

export interface Memory {
  id: number
  conversation_id: string
  memory_name: string
  create_time: string
  messages?: MessageBubble[]
}

export interface MemoryResponse {
  success: boolean
  message: string
  data?: Memory
  memories?: Memory[]
  total_count?: number
  response_time: string
}

export interface MessageBubble {
  messageType: string
  text: string
}

export class MemoryApiService {
  private static readonly BASE_URL = '/api/memories'

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

  static async getMemories(): Promise<Memory[]> {
    try {
      const response = await fetch(`${this.BASE_URL}`)
      const result = await this.handleResponse(response)
      const data: MemoryResponse = await result.json()
      return data.memories ?? []
    } catch (error) {
      console.error('Failed to get memory list:', error)
      throw error
    }
  }

  static async getMemory(conversationId: string): Promise<Memory> {
    try {
      const response = await fetch(`${this.BASE_URL}/single?conversationId=${conversationId}`)
      const result = await this.handleResponse(response)
      const data: MemoryResponse = await result.json()
      if (!data.data) {
        throw new Error('Memory not found')
      }
      return data.data
    } catch (error) {
      console.error('Failed to get memory:', error)
      throw error
    }
  }

  static async createMemory(conversationId: string, memoryName: string): Promise<Memory> {
    try {
      const response = await fetch(`${this.BASE_URL}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          conversation_id: conversationId,
          memory_name: memoryName,
        }),
      })
      const result = await this.handleResponse(response)
      const data: MemoryResponse = await result.json()
      if (!data.data) {
        throw new Error('Failed to create memory')
      }
      return data.data
    } catch (error) {
      console.error('Failed to create memory:', error)
      throw error
    }
  }

  static async updateMemory(conversationId: string, memoryName: string): Promise<Memory> {
    try {
      const response = await fetch(`${this.BASE_URL}`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          conversation_id: conversationId,
          memory_name: memoryName,
        }),
      })
      const result = await this.handleResponse(response)
      const data: MemoryResponse = await result.json()
      if (!data.data) {
        throw new Error('Failed to update memory')
      }
      return data.data
    } catch (error) {
      console.error('Failed to update memory:', error)
      throw error
    }
  }

  static async deleteMemory(conversationId: string): Promise<void> {
    try {
      const response = await fetch(`${this.BASE_URL}/${conversationId}`, {
        method: 'DELETE',
      })
      await this.handleResponse(response)
    } catch (error) {
      console.error('Failed to delete memory:', error)
      throw error
    }
  }

  static async generateConversationId(): Promise<Memory> {
    try {
      const response = await fetch(`${this.BASE_URL}/generate-id`)
      const result = await this.handleResponse(response)
      const data: MemoryResponse = await result.json()
      if (!data.data) {
        throw new Error('Failed to generate conversation ID')
      }
      return data.data
    } catch (error) {
      console.error('Failed to generate conversation ID:', error)
      throw error
    }
  }

  /**
   * Get conversation history (plan execution records) for a specific conversation
   * @param conversationId The conversation ID
   * @returns Array of plan execution records
   */
  static async getConversationHistory(conversationId: string): Promise<PlanExecutionRecord[]> {
    try {
      const response = await fetch(`${this.BASE_URL}/${conversationId}/history`)
      const result = await this.handleResponse(response)
      const data: PlanExecutionRecord[] = await result.json()
      return data || []
    } catch (error) {
      console.error('Failed to get conversation history:', error)
      throw error
    }
  }
}
