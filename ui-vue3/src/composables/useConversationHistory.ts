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

import { MemoryApiService } from '@/api/memory-api-service'
import { useMessageDialogSingleton } from '@/composables/useMessageDialog'
import { usePlanExecutionSingleton } from '@/composables/usePlanExecution'
import { useToast } from '@/composables/useToast'
import { memoryStore } from '@/stores/memory'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'
import { useI18n } from 'vue-i18n'

/**
 * Composable for managing conversation history
 * Handles loading and restoring conversation history from backend
 */
export function useConversationHistory() {
  const messageDialog = useMessageDialogSingleton()
  const planExecution = usePlanExecutionSingleton()
  const { showToast } = useToast()
  const { t } = useI18n()

  /**
   * Convert API record to plan execution record format
   */
  const convertApiRecordToPlanExecutionRecord = (
    record: PlanExecutionRecord
  ): Partial<PlanExecutionRecord> => {
    const planExecutionRecord: Partial<PlanExecutionRecord> = {
      currentPlanId: record.currentPlanId,
      status: record.completed ? 'completed' : 'running',
    }

    // Add optional fields if they exist
    if (record.rootPlanId) {
      planExecutionRecord.rootPlanId = record.rootPlanId
    }
    if (record.summary) {
      planExecutionRecord.summary = record.summary
    }
    if (record.completed !== undefined) {
      planExecutionRecord.completed = record.completed
    }
    if (record.agentExecutionSequence) {
      planExecutionRecord.agentExecutionSequence = record.agentExecutionSequence
    }

    return planExecutionRecord
  }

  /**
   * Process and add a single history record to the dialog
   */
  const processHistoryRecord = (record: PlanExecutionRecord): void => {
    if (!record) return

    // Add user message (the original query)
    if (record.userRequest && record.startTime) {
      messageDialog.addMessage('user', record.userRequest, {
        timestamp: new Date(record.startTime),
      })
    }

    // Add assistant message (the result/summary)
    if (record.currentPlanId) {
      const assistantContent =
        record.summary || record.result || record.message || 'Execution completed'

      // Convert API record to plan execution record format
      const planExecutionRecord = convertApiRecordToPlanExecutionRecord(record)

      messageDialog.addMessage('assistant', assistantContent, {
        timestamp:
          record.endTime && record.endTime
            ? new Date(record.endTime)
            : record.startTime
              ? new Date(record.startTime)
              : new Date(),
        planExecution: planExecutionRecord as PlanExecutionRecord,
      })

      // Store the plan record in the plan execution manager cache for future reference
      if (record.rootPlanId) {
        planExecution.setCachedPlanRecord(
          record.rootPlanId,
          planExecutionRecord as PlanExecutionRecord
        )
      }
    }
  }

  /**
   * Load conversation history from backend and restore it to the dialog
   * @param conversationId The conversation ID to load
   * @param clearMessages Whether to clear existing messages before loading (default: false)
   * @param showErrorToast Whether to show error toast on failure (default: true)
   * @returns Promise that resolves when history is loaded
   */
  const loadConversationHistory = async (
    conversationId: string,
    clearMessages: boolean = false,
    showErrorToast: boolean = true
  ): Promise<void> => {
    if (!conversationId) {
      console.warn('[useConversationHistory] Cannot load history: conversationId is empty')
      return
    }

    try {
      // Clear current chat if requested
      if (clearMessages) {
        messageDialog.clearMessages()
      }

      // Set the conversation ID in memory store
      memoryStore.setConversationId(conversationId)

      // Fetch conversation history from backend
      const historyRecords = await MemoryApiService.getConversationHistory(conversationId)
      console.log('[useConversationHistory] Loaded conversation history:', historyRecords)

      // Process each record
      for (const record of historyRecords) {
        processHistoryRecord(record)
      }

      console.log(
        '[useConversationHistory] Successfully loaded conversation history with',
        historyRecords.length,
        'dialog rounds'
      )
    } catch (error) {
      console.error('[useConversationHistory] Failed to load conversation history:', error)
      if (showErrorToast) {
        showToast(t('memory.loadHistoryFailed'), 'error')
      }
      throw error
    }
  }

  /**
   * Restore conversation history on page load (silent mode - no error toast)
   * @param conversationId The conversation ID to restore
   * @returns Promise that resolves when history is restored
   */
  const restoreConversationHistory = async (conversationId: string): Promise<void> => {
    return loadConversationHistory(conversationId, false, false)
  }

  return {
    loadConversationHistory,
    restoreConversationHistory,
    processHistoryRecord,
  }
}

// Singleton instance for global use
let singletonInstance: ReturnType<typeof useConversationHistory> | null = null

/**
 * Get or create singleton instance of useConversationHistory
 */
export function useConversationHistorySingleton() {
  if (!singletonInstance) {
    singletonInstance = useConversationHistory()
  }
  return singletonInstance
}
