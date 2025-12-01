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

import type { InputMessage } from '@/types/message-dialog'
import { memoryStore } from '@/stores/memory'
import { LlmCheckService } from '@/utils/llm-check'

export class DirectApiService {
  private static readonly BASE_URL = '/api/executor'

  // Send task directly (direct execution mode)
  public static async sendMessage(query: InputMessage): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      // Add requestSource to distinguish from HTTP requests
      const requestBody = {
        ...query,
        requestSource: 'VUE_DIALOG',
      }

      const response = await fetch(`${this.BASE_URL}/execute`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
      })
      if (!response.ok) throw new Error(`API request failed: ${response.status}`)
      return await response.json()
    })
  }

  // Send simple chat message with SSE streaming (no plan execution, just LLM chat)
  public static async sendChatMessage(
    query: InputMessage,
    requestSource: 'VUE_DIALOG' | 'VUE_SIDEBAR' = 'VUE_DIALOG',
    onChunk?: (chunk: { type: string; content?: string; conversationId?: string; message?: string }) => void
  ): Promise<{ conversationId?: string; message?: string }> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[DirectApiService] sendChatMessage called with:', {
        input: query.input,
        uploadedFiles: query.uploadedFiles,
        uploadKey: query.uploadKey,
        requestSource,
      })

      const requestBody: Record<string, unknown> = {
        input: query.input,
        requestSource: requestSource,
      }

      // Include conversationId from memoryStore if available
      if (memoryStore.conversationId) {
        requestBody.conversationId = memoryStore.conversationId
        console.log(
          '[DirectApiService] Including conversationId from memoryStore:',
          memoryStore.conversationId
        )
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

      console.log(
        '[DirectApiService] Making SSE request to:',
        `${this.BASE_URL}/chat`
      )
      console.log('[DirectApiService] Request body:', requestBody)

      const response = await fetch(`${this.BASE_URL}/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify(requestBody),
      })

      console.log('[DirectApiService] Response status:', response.status, response.ok)
      console.log('[DirectApiService] Response headers:', {
        contentType: response.headers.get('Content-Type'),
        transferEncoding: response.headers.get('Transfer-Encoding'),
      })

      if (!response.ok) {
        const errorText = await response.text()
        console.error('[DirectApiService] Request failed:', errorText)
        throw new Error(`Failed to send chat message: ${response.status}`)
      }

      // Handle SSE streaming
      if (!response.body) {
        throw new Error('Response body is null')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let conversationId: string | undefined
      let accumulatedMessage = ''

      console.log('[DirectApiService] Starting to read SSE stream...')

      try {
        while (true) {
          const { done, value } = await reader.read()
          console.log('[DirectApiService] Read chunk:', { done, valueLength: value?.length })
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          console.log('[DirectApiService] Buffer length:', buffer.length, 'content:', buffer.substring(0, 200))
          const lines = buffer.split('\n\n')
          buffer = lines.pop() || '' // Keep incomplete line in buffer
          console.log('[DirectApiService] Split into', lines.length, 'lines, buffer remaining:', buffer.length)

          for (const line of lines) {
            console.log('[DirectApiService] Processing line:', line)
            if (line.startsWith('data:')) {
              // Handle both 'data:' and 'data: ' formats
              const data = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
              console.log('[DirectApiService] Extracted data:', data)
              try {
                const parsed = JSON.parse(data) as {
                  type: string
                  content?: string
                  conversationId?: string
                  message?: string
                }
                console.log('[DirectApiService] Parsed SSE event:', parsed)

                if (parsed.type === 'start' && parsed.conversationId) {
                  conversationId = parsed.conversationId
                  console.log('[DirectApiService] Got start event with conversationId:', conversationId)
                  if (onChunk) {
                    onChunk({ type: 'start', conversationId: parsed.conversationId })
                  }
                } else if (parsed.type === 'chunk' && parsed.content) {
                  accumulatedMessage += parsed.content
                  console.log('[DirectApiService] Got chunk, accumulated length:', accumulatedMessage.length)
                  if (onChunk) {
                    onChunk({ type: 'chunk', content: parsed.content })
                  }
                } else if (parsed.type === 'done') {
                  console.log('[DirectApiService] Got done event')
                  if (onChunk) {
                    onChunk({ type: 'done' })
                  }
                } else if (parsed.type === 'error') {
                  console.error('[DirectApiService] Got error event:', parsed.message)
                  if (onChunk) {
                    onChunk({ type: 'error', message: parsed.message || 'Streaming error occurred' })
                  }
                  // Break the loop to stop processing
                  reader.releaseLock()
                  throw new Error(parsed.message || 'Streaming error occurred')
                }
              } catch (parseError) {
                console.error('[DirectApiService] Error parsing SSE data:', parseError, 'Data:', data)
              }
            }
          }
        }
      } finally {
        reader.releaseLock()
      }

      const result: { conversationId?: string; message?: string } = {}
      if (conversationId) {
        result.conversationId = conversationId
      }
      result.message = accumulatedMessage || 'No response received'
      console.log('[DirectApiService] sendChatMessage completed:', result)
      return result
    })
  }

  // Unified method to execute by tool name (replaces both sendMessageWithDefaultPlan and PlanActApiService.executePlan)
  public static async executeByToolName(
    toolName: string,
    replacementParams?: Record<string, string>,
    uploadedFiles?: string[],
    uploadKey?: string,
    requestSource: 'VUE_DIALOG' | 'VUE_SIDEBAR' = 'VUE_DIALOG',
    serviceGroup?: string
  ): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[DirectApiService] executeByToolName called with:', {
        toolName,
        replacementParams,
        uploadedFiles,
        uploadKey,
        requestSource,
        serviceGroup,
      })

      const requestBody: Record<string, unknown> = {
        toolName: toolName,
        requestSource: requestSource,
      }

      // Include conversationId from memoryStore if available
      if (memoryStore.conversationId) {
        requestBody.conversationId = memoryStore.conversationId
        console.log(
          '[DirectApiService] Including conversationId from memoryStore:',
          memoryStore.conversationId
        )
      }

      // Include serviceGroup if provided
      if (serviceGroup) {
        requestBody.serviceGroup = serviceGroup
        console.log('[DirectApiService] Including serviceGroup:', serviceGroup)
      }

      // Include replacement parameters if present
      if (replacementParams && Object.keys(replacementParams).length > 0) {
        requestBody.replacementParams = replacementParams
        console.log('[DirectApiService] Including replacement params:', replacementParams)
      }

      // Include uploaded files if present
      if (uploadedFiles && uploadedFiles.length > 0) {
        requestBody.uploadedFiles = uploadedFiles
        console.log('[DirectApiService] Including uploaded files:', uploadedFiles.length)
      }

      // Include uploadKey if present
      if (uploadKey) {
        requestBody.uploadKey = uploadKey
        console.log('[DirectApiService] Including uploadKey:', uploadKey)
      }

      console.log(
        '[DirectApiService] Making request to:',
        `${this.BASE_URL}/executeByToolNameAsync`
      )
      console.log('[DirectApiService] Request body:', requestBody)

      const response = await fetch(`${this.BASE_URL}/executeByToolNameAsync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
      })

      console.log('[DirectApiService] Response status:', response.status, response.ok)

      if (!response.ok) {
        const errorText = await response.text()
        console.error('[DirectApiService] Request failed:', errorText)
        throw new Error(`Failed to execute: ${response.status}`)
      }

      const result = await response.json()
      console.log('[DirectApiService] executeByToolName response:', result)
      return result
    })
  }

  // Stop a running task by plan ID
  public static async stopTask(planId: string): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[DirectApiService] Stopping task for planId:', planId)

      const response = await fetch(`${this.BASE_URL}/stopTask/${planId}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      })

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.error || `Failed to stop task: ${response.status}`)
      }

      return await response.json()
    })
  }
}
