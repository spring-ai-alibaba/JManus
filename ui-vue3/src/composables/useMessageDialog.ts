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

import { DirectApiService } from '@/api/direct-api-service'
import { PlanActApiService } from '@/api/plan-act-api-service'
import { usePlanExecutionSingleton } from '@/composables/usePlanExecution'
import { memoryStore } from '@/stores/memory'
import type {
  ChatMessage,
  CompatiblePlanExecutionRecord,
  InputMessage,
  MessageDialog,
} from '@/types/message-dialog'
import type { PlanExecutionRequestPayload } from '@/types/plan-execution'
import type { AgentExecutionRecord, PlanExecutionRecord } from '@/types/plan-execution-record'
import { computed, readonly, ref, watchEffect } from 'vue'

/**
 * Composable for managing message dialogs
 * Provides methods to manage dialog list and send messages
 */
export function useMessageDialog() {
  // Plan execution manager
  const planExecution = usePlanExecutionSingleton()

  // Dialog list state
  const dialogList = ref<MessageDialog[]>([])
  const activeDialogId = ref<string | null>(null)

  // Computed active rootPlanId derived from active dialog
  // This is more reactive and eliminates duplicate state
  const activeRootPlanId = computed(() => {
    return activeDialog.value?.planId || null
  })

  // Loading state
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const streamingMessageId = ref<string | null>(null)
  const inputPlaceholder = ref<string | null>(null)

  // Computed properties
  const activeDialog = computed(() => {
    if (!activeDialogId.value) {
      return null
    }
    return dialogList.value.find(dialog => dialog.id === activeDialogId.value) || null
  })

  const hasDialogs = computed(() => {
    return dialogList.value.length > 0
  })

  const dialogCount = computed(() => {
    return dialogList.value.length
  })

  // Messages from active dialog
  const messages = computed(() => {
    return activeDialog.value?.messages || []
  })

  /**
   * Create a new dialog
   */
  const createDialog = (title?: string): MessageDialog => {
    const dialog: MessageDialog = {
      id: `dialog_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      title: title || 'New Conversation',
      messages: [],
      createdAt: new Date(),
      updatedAt: new Date(),
      isActive: true,
    }

    dialogList.value.push(dialog)
    activeDialogId.value = dialog.id
    return dialog
  }

  /**
   * Get dialog by ID
   */
  const getDialog = (dialogId: string): MessageDialog | null => {
    return dialogList.value.find(dialog => dialog.id === dialogId) || null
  }

  /**
   * Set active dialog
   */
  const setActiveDialog = (dialogId: string | null) => {
    // Deactivate all dialogs
    dialogList.value.forEach(dialog => {
      dialog.isActive = false
    })

    // Activate selected dialog
    if (dialogId) {
      const dialog = dialogList.value.find(d => d.id === dialogId)
      if (dialog) {
        dialog.isActive = true
        activeDialogId.value = dialogId
      }
    } else {
      activeDialogId.value = null
    }
  }

  /**
   * Add message to a dialog
   */
  const addMessageToDialog = (dialogId: string, message: ChatMessage) => {
    const dialog = dialogList.value.find(d => d.id === dialogId)
    if (dialog) {
      dialog.messages.push(message)
      dialog.updatedAt = new Date()
    }
  }

  /**
   * Update message in a dialog
   */
  const updateMessageInDialog = (
    dialogId: string,
    messageId: string,
    updates: Partial<ChatMessage>
  ) => {
    const dialog = dialogList.value.find(d => d.id === dialogId)
    if (dialog) {
      const messageIndex = dialog.messages.findIndex(m => m.id === messageId)
      if (messageIndex !== -1) {
        dialog.messages[messageIndex] = { ...dialog.messages[messageIndex], ...updates }
        dialog.updatedAt = new Date()
      }
    }
  }

  /**
   * Delete a dialog
   */
  const deleteDialog = (dialogId: string) => {
    const index = dialogList.value.findIndex(d => d.id === dialogId)
    if (index !== -1) {
      dialogList.value.splice(index, 1)
      // If deleted dialog was active, set first dialog as active or null
      if (activeDialogId.value === dialogId) {
        activeDialogId.value = dialogList.value.length > 0 ? dialogList.value[0].id : null
        if (activeDialogId.value) {
          setActiveDialog(activeDialogId.value)
        }
      }
    }
  }

  /**
   * Clear all dialogs
   */
  const clearAllDialogs = () => {
    dialogList.value = []
    activeDialogId.value = null
  }

  /**
   * Send message via send button (InputArea)
   * This method handles sending messages and updating the dialog list
   */
  const sendMessage = async (
    query: InputMessage,
    dialogId?: string
  ): Promise<{ success: boolean; planId?: string; conversationId?: string; error?: string }> => {
    let targetDialog: MessageDialog | null = null
    let assistantMessage: ChatMessage | null = null

    try {
      // Check if there's an active running task
      if (activeRootPlanId.value) {
        const activeRecord = planExecution.planExecutionRecords.get(activeRootPlanId.value)
        if (activeRecord && !activeRecord.completed) {
          const errorMsg = 'Please wait for the current task to complete before starting a new one'
          error.value = errorMsg
          return {
            success: false,
            error: errorMsg,
          }
        }
      }

      isLoading.value = true
      error.value = null

      // Get or create active dialog
      if (dialogId) {
        const existingDialog = getDialog(dialogId)
        if (existingDialog) {
          targetDialog = existingDialog
        } else {
          targetDialog = createDialog()
        }
      } else {
        targetDialog = activeDialog.value || createDialog()
      }

      // Add user message to dialog
      const userMessage: ChatMessage = {
        id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: 'user',
        content: query.input,
        timestamp: new Date(),
        isStreaming: false,
        ...(query.uploadedFiles && {
          attachments: query.uploadedFiles.map(file => {
            // Convert string file names to File objects if needed
            return typeof file === 'string' ? new File([], file) : file
          }),
        }),
      }
      addMessageToDialog(targetDialog.id, userMessage)

      // Add assistant thinking message
      assistantMessage = {
        id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: 'assistant',
        content: '',
        timestamp: new Date(),
        thinking: 'Processing...',
        isStreaming: true,
      }
      addMessageToDialog(targetDialog.id, assistantMessage)

      // Import and call DirectApiService
      const extendedQuery = query as InputMessage & {
        toolName?: string
        replacementParams?: Record<string, string>
      }

      let response: { planId?: string; conversationId?: string; message?: string; result?: string }

      if (extendedQuery.toolName && extendedQuery.replacementParams) {
        // Execute selected tool
        response = (await DirectApiService.executeByToolName(
          extendedQuery.toolName,
          extendedQuery.replacementParams as Record<string, string>,
          query.uploadedFiles,
          query.uploadKey,
          'VUE_DIALOG'
        )) as typeof response
      } else {
        // Use default plan template
        response = (await DirectApiService.sendMessageWithDefaultPlan(
          query,
          'VUE_DIALOG'
        )) as typeof response
      }

      // Update conversationId if present
      if (response.conversationId) {
        targetDialog.conversationId = response.conversationId
        memoryStore.setConversationId(response.conversationId)
      }

      // Update assistant message with response
      if (response.planId) {
        // Plan execution mode
        const rootPlanId = response.planId
        targetDialog.planId = rootPlanId

        updateMessageInDialog(targetDialog.id, assistantMessage.id, {
          thinking: 'Planning execution...',
          planExecution: {
            currentPlanId: rootPlanId,
            rootPlanId: rootPlanId,
            status: 'running',
          },
          isStreaming: false,
        })

        // Track the plan for polling
        planExecution.handlePlanExecutionRequested(rootPlanId)
        console.log('[useMessageDialog] Started tracking plan:', rootPlanId)
      } else {
        // Direct response mode
        const updates: Partial<ChatMessage> = {
          content: response.message || response.result || 'No response received',
          isStreaming: false,
        }
        // Only set thinking if it exists, don't set undefined
        updateMessageInDialog(targetDialog.id, assistantMessage.id, updates)
      }

      return {
        success: true,
        ...(response.planId && { planId: response.planId }),
        ...(response.conversationId && { conversationId: response.conversationId }),
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to send message'
      error.value = errorMessage
      console.error('[useMessageDialog] Send message failed:', err)

      // Update assistant message with error
      if (targetDialog && assistantMessage) {
        updateMessageInDialog(targetDialog.id, assistantMessage.id, {
          content: `Error: ${errorMessage}`,
          error: errorMessage,
          isStreaming: false,
        })
      }

      return {
        success: false,
        error: errorMessage,
      }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Execute plan via execute plan button (ExecutionController)
   * This method handles executing plans and updating the dialog list
   */
  const executePlan = async (
    payload: PlanExecutionRequestPayload,
    dialogId?: string
  ): Promise<{ success: boolean; planId?: string; error?: string }> => {
    let targetDialog: MessageDialog | null = null
    let assistantMessage: ChatMessage | null = null

    try {
      // Check if there's an active running task
      if (activeRootPlanId.value) {
        const activeRecord = planExecution.planExecutionRecords.get(activeRootPlanId.value)
        if (activeRecord && !activeRecord.completed) {
          const errorMsg = 'Please wait for the current task to complete before starting a new one'
          error.value = errorMsg
          return {
            success: false,
            error: errorMsg,
          }
        }
      }

      isLoading.value = true
      error.value = null

      // Get or create active dialog
      if (dialogId) {
        const existingDialog = getDialog(dialogId)
        if (existingDialog) {
          targetDialog = existingDialog
        } else {
          targetDialog = createDialog(payload.title)
        }
      } else {
        targetDialog = activeDialog.value || createDialog(payload.title)
      }

      // Update dialog title if provided
      if (payload.title) {
        targetDialog.title = payload.title
      }

      // Add user message to dialog
      const userMessage: ChatMessage = {
        id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: 'user',
        content: payload.title,
        timestamp: new Date(),
        isStreaming: false,
        ...(payload.uploadedFiles && {
          attachments: payload.uploadedFiles.map(file => {
            return typeof file === 'string' ? new File([], file) : file
          }),
        }),
      }
      addMessageToDialog(targetDialog.id, userMessage)

      // Add assistant thinking message
      assistantMessage = {
        id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: 'assistant',
        content: '',
        timestamp: new Date(),
        thinking: 'Planning execution...',
        isStreaming: true,
      }
      addMessageToDialog(targetDialog.id, assistantMessage)

      // Get plan template ID
      const planTemplateId = payload.planData.planTemplateId
      if (!planTemplateId || planTemplateId === null) {
        throw new Error('Plan template ID is required')
      }

      // Call PlanActApiService.executePlan
      const response = (await PlanActApiService.executePlan(
        planTemplateId as string,
        payload.params,
        payload.uploadedFiles,
        payload.replacementParams,
        payload.uploadKey ?? undefined,
        'VUE_SIDEBAR'
      )) as { planId?: string; conversationId?: string }

      // Update conversationId if present
      if (response.conversationId) {
        targetDialog.conversationId = response.conversationId
        memoryStore.setConversationId(response.conversationId)
      }

      // Update assistant message with plan execution info
      if (response.planId) {
        const rootPlanId = response.planId
        targetDialog.planId = rootPlanId

        updateMessageInDialog(targetDialog.id, assistantMessage.id, {
          thinking: 'Planning execution...',
          planExecution: {
            currentPlanId: rootPlanId,
            rootPlanId: rootPlanId,
            status: 'running',
          },
          isStreaming: false,
        })

        // Track the plan for polling
        planExecution.handlePlanExecutionRequested(rootPlanId)
        console.log('[useMessageDialog] Started tracking plan:', rootPlanId)
      } else {
        const updates: Partial<ChatMessage> = {
          content: 'Plan execution started',
          isStreaming: false,
        }
        // Only set thinking if it exists, don't set undefined
        updateMessageInDialog(targetDialog.id, assistantMessage.id, updates)
      }

      return {
        success: true,
        ...(response.planId && { planId: response.planId }),
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to execute plan'
      error.value = errorMessage
      console.error('[useMessageDialog] Execute plan failed:', err)

      // Update assistant message with error
      if (targetDialog && assistantMessage) {
        updateMessageInDialog(targetDialog.id, assistantMessage.id, {
          content: `Error: ${errorMessage}`,
          error: errorMessage,
          isStreaming: false,
        })
      }

      return {
        success: false,
        error: errorMessage,
      }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Update plan execution status in a dialog
   */
  const updatePlanExecutionStatus = (
    dialogId: string,
    messageId: string,
    planExecution: ChatMessage['planExecution']
  ) => {
    const updates: Partial<ChatMessage> = {}
    if (planExecution !== undefined) {
      updates.planExecution = planExecution
    }
    updateMessageInDialog(dialogId, messageId, updates)
  }

  /**
   * Convert readonly PlanExecutionRecord to mutable CompatiblePlanExecutionRecord
   */
  const convertPlanExecutionRecord = (
    record: PlanExecutionRecord | CompatiblePlanExecutionRecord | Readonly<PlanExecutionRecord>
  ): CompatiblePlanExecutionRecord => {
    // Create a mutable copy of the record
    const converted = { ...record } as CompatiblePlanExecutionRecord

    if ('agentExecutionSequence' in record && Array.isArray(record.agentExecutionSequence)) {
      // Convert readonly array to mutable array
      converted.agentExecutionSequence = record.agentExecutionSequence.map((agent: unknown) =>
        convertAgentExecutionRecord(agent as AgentExecutionRecord)
      )
    }

    return converted
  }

  /**
   * Convert readonly AgentExecutionRecord to mutable version
   */
  const convertAgentExecutionRecord = (record: AgentExecutionRecord): AgentExecutionRecord => {
    const converted = { ...record } as AgentExecutionRecord

    if ('subPlanExecutionRecords' in record && Array.isArray(record.subPlanExecutionRecords)) {
      converted.subPlanExecutionRecords = record.subPlanExecutionRecords.map((subPlan: unknown) =>
        convertPlanExecutionRecord(subPlan as PlanExecutionRecord)
      )
    }

    return converted
  }

  /**
   * Add message to active dialog (convenience method for ChatContainer)
   * Automatically converts readonly planExecution and thinkingDetails to mutable versions
   */
  const addMessage = (
    type: 'user' | 'assistant',
    content: string,
    options?: Partial<ChatMessage>
  ): ChatMessage => {
    if (!activeDialog.value) {
      // Create a new dialog if none exists
      createDialog()
    }

    const dialog = activeDialog.value!

    // Convert planExecution and thinkingDetails if they exist
    const processedOptions: Partial<ChatMessage> = { ...options }
    if (options?.planExecution) {
      processedOptions.planExecution = convertPlanExecutionRecord(options.planExecution)
    }
    if (options?.thinkingDetails) {
      processedOptions.thinkingDetails = convertPlanExecutionRecord(options.thinkingDetails)
    }

    const message: ChatMessage = {
      id: `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      type,
      content,
      timestamp: new Date(),
      isStreaming: false,
      ...processedOptions,
    }

    dialog.messages.push(message)
    dialog.updatedAt = new Date()
    return message
  }

  /**
   * Update message in active dialog (convenience method for ChatContainer)
   * Automatically converts readonly planExecution and thinkingDetails to mutable versions
   */
  const updateMessage = (messageId: string, updates: Partial<ChatMessage>) => {
    if (!activeDialog.value) {
      return
    }

    // Convert planExecution and thinkingDetails if they exist
    const processedUpdates: Partial<ChatMessage> = { ...updates }
    if (updates.planExecution) {
      processedUpdates.planExecution = convertPlanExecutionRecord(updates.planExecution)
    }
    if (updates.thinkingDetails) {
      processedUpdates.thinkingDetails = convertPlanExecutionRecord(updates.thinkingDetails)
    }

    const messageIndex = activeDialog.value.messages.findIndex(m => m.id === messageId)
    if (messageIndex !== -1) {
      activeDialog.value.messages[messageIndex] = {
        ...activeDialog.value.messages[messageIndex],
        ...processedUpdates,
      }
      activeDialog.value.updatedAt = new Date()
    }
  }

  /**
   * Find message in active dialog (convenience method for ChatContainer)
   */
  const findMessage = (messageId: string): ChatMessage | undefined => {
    if (!activeDialog.value) {
      return undefined
    }
    return activeDialog.value.messages.find(m => m.id === messageId)
  }

  /**
   * Start streaming for a message (convenience method for ChatContainer)
   */
  const startStreaming = (messageId: string) => {
    streamingMessageId.value = messageId
    updateMessage(messageId, { isStreaming: true })
  }

  /**
   * Stop streaming for a message (convenience method for ChatContainer)
   */
  const stopStreaming = (messageId?: string) => {
    if (messageId) {
      updateMessage(messageId, { isStreaming: false })
    }
    if (streamingMessageId.value === messageId || !messageId) {
      streamingMessageId.value = null
    }
  }

  /**
   * Clear messages in active dialog (convenience method for ChatContainer)
   */
  const clearMessages = () => {
    if (!activeDialog.value) {
      return
    }
    activeDialog.value.messages = []
    activeDialog.value.updatedAt = new Date()
    streamingMessageId.value = null
  }

  /**
   * Update input state (enabled/disabled)
   */
  const updateInputState = (enabled: boolean, placeholder?: string) => {
    // isLoading is the inverse of enabled
    isLoading.value = !enabled
    if (placeholder !== undefined) {
      inputPlaceholder.value = placeholder
    }
    console.log('[useMessageDialog] Input state updated:', {
      enabled,
      placeholder,
      isLoading: isLoading.value,
    })
  }

  /**
   * Reset state
   */
  const reset = () => {
    dialogList.value = []
    activeDialogId.value = null
    isLoading.value = false
    error.value = null
    inputPlaceholder.value = null
  }

  /**
   * Helper: Update message with plan execution record
   */
  const updateMessageWithPlanRecord = (
    dialog: MessageDialog,
    message: ChatMessage,
    record: PlanExecutionRecord
  ): void => {
      const updates: Partial<ChatMessage> = {
      planExecution: convertPlanExecutionRecord(record),
        isStreaming: !record.completed,
      }

      if (!record.completed) {
        updates.thinking = 'Processing...'
    } else {
      // When plan is completed, handle summary content
      // Only update content if there's no agent execution sequence (simple response)
      // or if we have a summary/result to display
      if (!record.agentExecutionSequence || record.agentExecutionSequence.length === 0) {
        const finalResponse =
          record.summary ?? record.result ?? record.message ?? 'Execution completed'
        if (finalResponse) {
          updates.content = finalResponse
          updates.thinking = ''
        }
      } else if (record.summary) {
        // Even with agent execution sequence, show summary if available
        updates.content = record.summary
        updates.thinking = ''
      }

      // Handle errors
      if (record.status === 'failed' && record.message) {
        updates.content = `Error: ${record.message}`
        updates.thinking = ''
      }
    }

    updateMessageInDialog(dialog.id, message.id, updates)
  }

  /**
   * Watch for PlanExecutionRecord changes and update dialog messages
   * Uses watchEffect for automatic dependency tracking (more Vue 3 idiomatic)
   * Processes all dialogs with planIds, not just active one
   */
  watchEffect(() => {
    const records = planExecution.planExecutionRecords

    // Process all dialogs that have associated planIds
    for (const dialog of dialogList.value) {
      if (!dialog.planId) continue

      const readonlyRecord = records.get(dialog.planId)
      if (!readonlyRecord) continue

      // Convert readonly record to mutable for processing
      // Use type assertion to handle deeply readonly types from reactive Map
      // The convertPlanExecutionRecord function will properly convert nested readonly arrays
      const record = convertPlanExecutionRecord(
        readonlyRecord as unknown as PlanExecutionRecord
      ) as PlanExecutionRecord

      // Find the assistant message with this planId
      const message = dialog.messages.find(m => m.planExecution?.rootPlanId === dialog.planId)
      if (!message) continue

      // Update message with latest plan execution record
      updateMessageWithPlanRecord(dialog, message, record)
      }
  })

  return {
    // State
    dialogList: readonly(dialogList),
    activeDialogId: readonly(activeDialogId),
    isLoading,
    error,
    streamingMessageId: readonly(streamingMessageId),
    inputPlaceholder: readonly(inputPlaceholder),

    // Computed
    activeDialog,
    hasDialogs,
    dialogCount,
    messages,

    // Methods
    createDialog,
    getDialog,
    setActiveDialog,
    addMessageToDialog,
    updateMessageInDialog,
    deleteDialog,
    clearAllDialogs,
    sendMessage,
    executePlan,
    updatePlanExecutionStatus,
    updateInputState,
    reset,

    // Convenience methods for ChatContainer
    addMessage,
    updateMessage,
    findMessage,
    startStreaming,
    stopStreaming,
    clearMessages,
  }
}

// Singleton instance for global use
let singletonInstance: ReturnType<typeof useMessageDialog> | null = null

/**
 * Get or create singleton instance of useMessageDialog
 */
export function useMessageDialogSingleton() {
  if (!singletonInstance) {
    singletonInstance = useMessageDialog()
  }
  return singletonInstance
}
