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

import { ref, computed, readonly } from 'vue'
import type { PlanExecutionRecord, AgentExecutionRecord } from '@/types/plan-execution-record'
import type { ChatMessage, CompatiblePlanExecutionRecord, InputMessage } from '@/types/message-dialog'
import { DirectApiService } from '@/api/direct-api-service'
import { memoryStore } from '@/stores/memory'
import { planExecutionManager } from '@/utils/plan-execution-manager'

/**
 * Utility type to convert readonly arrays to mutable ones
 */
type MakeMutable<T> = T extends readonly (infer U)[] ? U[] : T

/**
 * Convert readonly PlanExecutionRecord to mutable compatible version with strong typing
 */
function convertPlanExecutionRecord<T extends Record<string, unknown>>(
  record: T
): CompatiblePlanExecutionRecord {
  const converted = { ...record } as unknown as CompatiblePlanExecutionRecord

  if ('agentExecutionSequence' in record && Array.isArray(record.agentExecutionSequence)) {
    converted.agentExecutionSequence = record.agentExecutionSequence.map((agent: unknown) =>
      convertAgentExecutionRecord(agent as Record<string, unknown>)
    )
  }

  return converted
}

/**
 * Convert readonly AgentExecutionRecord to mutable compatible version with strong typing
 */
function convertAgentExecutionRecord<T extends Record<string, unknown>>(
  record: T
): AgentExecutionRecord {
  const converted = { ...record } as unknown as AgentExecutionRecord

  if ('subPlanExecutionRecords' in record && Array.isArray(record.subPlanExecutionRecords)) {
    converted.subPlanExecutionRecords = record.subPlanExecutionRecords.map((subPlan: unknown) =>
      convertPlanExecutionRecord(subPlan as Record<string, unknown>)
    )
  }

  return converted
}

/**
 * Convert a message with potentially readonly arrays to a fully mutable version
 */
export function convertMessageToCompatible<T extends Record<string, unknown>>(
  message: T
): ChatMessage {
  const converted = { ...message } as unknown as ChatMessage

  if ('thinkingDetails' in message && message.thinkingDetails) {
    converted.thinkingDetails = convertPlanExecutionRecord(
      message.thinkingDetails as Record<string, unknown>
    )
  }

  if ('planExecution' in message && message.planExecution) {
    converted.planExecution = convertPlanExecutionRecord(
      message.planExecution as Record<string, unknown>
    )
  }

  if ('attachments' in message && Array.isArray(message.attachments)) {
    converted.attachments = [...message.attachments] as MakeMutable<typeof message.attachments>
  }

  return converted
}

/**
 * Chat messages state management
 */
export function useChatMessages() {
  // State
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const streamingMessageId = ref<string | null>(null)
  const activeMessageId = ref<string | null>(null)

  // Computed properties
  const lastMessage = computed(() => {
    return messages.value.length > 0 ? messages.value[messages.value.length - 1] : null
  })

  const isStreaming = computed(() => {
    return streamingMessageId.value !== null
  })

  const hasMessages = computed(() => {
    return messages.value.length > 0
  })

  // Methods
  const addMessage = (
    type: 'user' | 'assistant',
    content: string,
    options?: Partial<ChatMessage>
  ): ChatMessage => {
    const message: ChatMessage = {
      id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type,
      content,
      timestamp: new Date(),
      isStreaming: false,
      ...options,
    }

    messages.value.push(message)
    return message
  }

  const updateMessage = (id: string, updates: Partial<ChatMessage>) => {
    const index = messages.value.findIndex(m => m.id === id)
    if (index !== -1) {
      messages.value[index] = { ...messages.value[index], ...updates }
    }
  }

  const removeMessage = (id: string) => {
    const index = messages.value.findIndex(m => m.id === id)
    if (index !== -1) {
      messages.value.splice(index, 1)
    }
  }

  const clearMessages = () => {
    messages.value = []
    streamingMessageId.value = null
    activeMessageId.value = null
  }

  const startStreaming = (messageId: string) => {
    streamingMessageId.value = messageId
    updateMessage(messageId, { isStreaming: true })
  }

  const stopStreaming = (messageId?: string) => {
    if (messageId) {
      updateMessage(messageId, { isStreaming: false })
    }
    if (streamingMessageId.value === messageId) {
      streamingMessageId.value = null
    }
  }

  const setActiveMessage = (id: string | null) => {
    activeMessageId.value = id
  }

  const appendToMessage = (id: string, content: string, field: keyof ChatMessage = 'content') => {
    const message = messages.value.find(m => m.id === id)
    if (message && typeof message[field] === 'string') {
      updateMessage(id, { [field]: (message[field] as string) + content })
    }
  }

  const updateMessageThinkingDetails = (id: string, thinkingDetails: PlanExecutionRecord) => {
    updateMessage(id, { thinkingDetails })
  }

  const updateMessageThinking = (id: string, thinking: string) => {
    updateMessage(id, { thinking })
  }

  const updateMessagePlanExecution = (id: string, planExecution: PlanExecutionRecord) => {
    updateMessage(id, { planExecution })
  }

  const findMessage = (id: string): ChatMessage | undefined => {
    return messages.value.find(m => m.id === id)
  }

  const getMessageIndex = (id: string): number => {
    return messages.value.findIndex(m => m.id === id)
  }

  /**
   * Send chat message and handle response
   * This method handles sending messages via DirectApiService and updating the message list
   */
  const sendChatMessage = async (
    query: InputMessage,
    options?: {
      thinkingText?: string
      planningText?: string
      onPlanIdReceived?: (planId: string) => void
    }
  ): Promise<{ success: boolean; planId?: string; conversationId?: string; error?: string }> => {
    let assistantMessage: ChatMessage | null = null

    try {
      console.log('[useChatMessages] Processing send-message event:', query)

      // Validate input - don't send empty requests to backend
      if (!query.input.trim()) {
        console.warn('[useChatMessages] Empty input detected, skipping backend request')
        return { success: false, error: 'Empty input' }
      }

      // Add user message to UI
      const userMessage = addMessage('user', query.input)
      const extendedQuery = query as InputMessage & {
        attachments?: unknown[]
        toolName?: string
        replacementParams?: Record<string, unknown>
      }
      if (extendedQuery.attachments) {
        updateMessage(userMessage.id, { attachments: extendedQuery.attachments as File[] })
      }

      // Add assistant thinking message
      assistantMessage = addMessage('assistant', '', {
        thinking: options?.thinkingText || 'Processing...',
      })

      if (assistantMessage) {
        startStreaming(assistantMessage.id)
      }

      // Call DirectApiService to send message to backend
      let response: { planId?: string; conversationId?: string; message?: string; result?: string; [key: string]: unknown }

      if (extendedQuery.toolName && extendedQuery.replacementParams) {
        // Execute selected tool (from dialog)
        console.log(
          '[useChatMessages] Calling DirectApiService.executeByToolName with tool:',
          extendedQuery.toolName
        )
        response = (await DirectApiService.executeByToolName(
          extendedQuery.toolName,
          extendedQuery.replacementParams as Record<string, string>,
          query.uploadedFiles,
          query.uploadKey,
          'VUE_DIALOG'
        )) as typeof response
      } else {
        // Use default plan template (from dialog)
        console.log('[useChatMessages] Calling DirectApiService.sendMessageWithDefaultPlan')
        response = (await DirectApiService.sendMessageWithDefaultPlan(query, 'VUE_DIALOG')) as typeof response
      }

      console.log('[useChatMessages] API response received:', response)

      // Save conversationId from response to memoryStore
      if (response.conversationId) {
        memoryStore.setConversationId(response.conversationId as string)
        console.log('[useChatMessages] Saved conversationId to memoryStore:', response.conversationId)
      }

      // Handle the response
      if (response.planId && assistantMessage) {
        // Plan mode: Update message with plan execution info
        updateMessage(assistantMessage.id, {
          thinking: options?.planningText || 'Planning execution...',
          planExecution: {
            currentPlanId: response.planId,
            rootPlanId: response.planId,
            status: 'running',
          },
        })

        // Notify callback if provided
        if (options?.onPlanIdReceived) {
          options.onPlanIdReceived(response.planId)
        }

        // Start polling for plan updates
        planExecutionManager.handlePlanExecutionRequested(response.planId, query.input)
        console.log('[useChatMessages] Started polling for plan execution updates')

        const result: { success: boolean; planId?: string; conversationId?: string; error?: string } = {
          success: true,
          planId: response.planId,
        }
        if (response.conversationId) {
          result.conversationId = response.conversationId as string
        }
        return result
      } else if (assistantMessage) {
        // Direct response mode: Show the response
        updateMessage(assistantMessage.id, {
          content: (response.message || response.result || 'No response received from backend') as string,
        })
        stopStreaming(assistantMessage.id)

        const result: { success: boolean; planId?: string; conversationId?: string; error?: string } = {
          success: true,
        }
        if (response.conversationId) {
          result.conversationId = response.conversationId as string
        }
        return result
      }

      return { success: true }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : String(error)
      console.error('[useChatMessages] Send message failed:', errorMessage)

      // Show error message
      addMessage('assistant', `Error: ${errorMessage}`)
      if (assistantMessage) {
        stopStreaming(assistantMessage.id)
      }

      return {
        success: false,
        error: errorMessage,
      }
    }
  }

  return {
    // State
    messages: readonly(messages),
    isLoading,
    streamingMessageId: readonly(streamingMessageId),
    activeMessageId: readonly(activeMessageId),

    // Computed
    lastMessage,
    isStreaming,
    hasMessages,

    // Methods
    addMessage,
    updateMessage,
    removeMessage,
    clearMessages,
    startStreaming,
    stopStreaming,
    setActiveMessage,
    appendToMessage,
    updateMessageThinking,
    updateMessageThinkingDetails,
    updateMessagePlanExecution,
    findMessage,
    getMessageIndex,
    sendChatMessage,
  }
}

// Message utilities
export const createUserMessage = (content: string): Omit<ChatMessage, 'id' | 'timestamp'> => ({
  type: 'user',
  content,
  isStreaming: false,
})

export const createAssistantMessage = (
  options?: Partial<ChatMessage>
): Omit<ChatMessage, 'id' | 'timestamp'> => ({
  type: 'assistant',
  content: '',
  thinking: '',
  isStreaming: true,
  ...options,
})
