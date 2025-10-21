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

import { LlmCheckService } from '@/utils/llm-check'
import type { InputMessage } from "@/stores/memory"

export class DirectApiService {
  private static readonly BASE_URL = '/api/executor'

  // Send task directly (direct execution mode)
  public static async sendMessage(query: InputMessage): Promise<any> {
    return LlmCheckService.withLlmCheck(async () => {
      // Add Vue identification flag to distinguish from HTTP requests
      const requestBody = {
        ...query,
        isVueRequest: true
      }
      
      const response = await fetch(`${this.BASE_URL}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      })
      if (!response.ok) throw new Error(`API request failed: ${response.status}`)
      return await response.json()
    })
  }

  // Send task using executeByToolNameAsync with default plan template
  public static async sendMessageWithDefaultPlan(query: InputMessage): Promise<any> {
    return LlmCheckService.withLlmCheck(async () => {
      // Use default plan template ID as toolName
      const toolName = 'default-plan-id-001000222'
      
      // Create replacement parameters with user input
      const replacementParams = {
        'userRequirement': query.input
      }
      
      const requestBody: Record<string, any> = {
        toolName: toolName,
        replacementParams: replacementParams,
        isVueRequest: true
      }
      
      // Include uploaded files if present
      if (query.uploadedFiles && query.uploadedFiles.length > 0) {
        requestBody.uploadedFiles = query.uploadedFiles
        console.log('[DirectApiService] Including uploaded files:', query.uploadedFiles.length)
      }
      
      // Include uploadKey if present
      if (query.uploadKey) {
        requestBody.uploadKey = query.uploadKey
        console.log('[DirectApiService] Including uploadKey:', query.uploadKey)
      }
      
      console.log('[DirectApiService] Sending message with default plan:', requestBody)
      
      const response = await fetch(`${this.BASE_URL}/executeByToolNameAsync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      })
      if (!response.ok) throw new Error(`API request failed: ${response.status}`)
      return await response.json()
    })
  }

  // Stop a running task by plan ID
  public static async stopTask(planId: string): Promise<any> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[DirectApiService] Stopping task for planId:', planId)
      
      const response = await fetch(`${this.BASE_URL}/stopTask/${planId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      })
      
      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || `Failed to stop task: ${response.status}`)
      }
      
      return await response.json()
    })
  }
}
