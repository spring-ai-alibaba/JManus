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
   * Parse date from backend (handles LocalDateTime format from Java)
   * Java LocalDateTimeSerializer can serialize as:
   * - ISO-8601 string: "2025-01-17T10:30:00" or "2025-01-17T10:30:00.123"
   * - Array format: [2025, 1, 17, 10, 30, 0] (if WRITE_DATES_AS_TIMESTAMPS is enabled)
   * @param dateValue Date value from backend (string, array, or object)
   * @returns Date object or current date if parsing fails
   */
  const parseBackendDate = (dateValue: string | number[] | any): Date => {
    if (!dateValue) {
      console.warn('[useConversationHistory] Date value is null/undefined, using current time')
      return new Date()
    }

    try {
      // Handle array format: [year, month, day, hour, minute, second, nanosecond]
      if (Array.isArray(dateValue)) {
        const [year, month, day, hour = 0, minute = 0, second = 0, nanosecond = 0] = dateValue
        const date = new Date(
          year,
          month - 1, // Month is 0-indexed in JavaScript
          day,
          hour,
          minute,
          second,
          Math.floor(nanosecond / 1000000) // Convert nanoseconds to milliseconds
        )
        if (!isNaN(date.getTime())) {
          return date
        }
        console.warn('[useConversationHistory] Failed to parse date array:', dateValue)
        return new Date()
      }

      // Handle string format
      if (typeof dateValue === 'string') {
        // First, try parsing as-is (works in most modern browsers for ISO format)
        let date = new Date(dateValue)

        // Check if date is valid
        if (!isNaN(date.getTime())) {
          return date
        }

        // If parsing failed, try manual parsing of ISO format
        // Format: YYYY-MM-DDTHH:mm:ss or YYYY-MM-DDTHH:mm:ss.SSS
        const isoMatch = dateValue.match(
          /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?$/
        )
        if (isoMatch) {
          const [, year, month, day, hour, minute, second, millis] = isoMatch
          date = new Date(
            parseInt(year),
            parseInt(month) - 1, // Month is 0-indexed
            parseInt(day),
            parseInt(hour),
            parseInt(minute),
            parseInt(second),
            millis ? parseInt(millis.substring(0, 3)) : 0 // Only use first 3 digits of milliseconds
          )
          if (!isNaN(date.getTime())) {
            return date
          }
        }

        // Try adding timezone if missing
        if (!dateValue.includes('Z') && !dateValue.includes('+') && !dateValue.includes('-', 10)) {
          date = new Date(dateValue + 'Z')
          if (!isNaN(date.getTime())) {
            return date
          }
        }

        console.warn('[useConversationHistory] Failed to parse date string:', dateValue)
        return new Date()
      }

      // Handle number (timestamp in milliseconds or seconds)
      if (typeof dateValue === 'number') {
        // If number is less than 13 digits, assume it's in seconds
        const timestamp = dateValue.toString().length < 13 ? dateValue * 1000 : dateValue
        const date = new Date(timestamp)
        if (!isNaN(date.getTime())) {
          return date
        }
        console.warn('[useConversationHistory] Failed to parse date number:', dateValue)
        return new Date()
      }

      // Unknown format
      console.warn('[useConversationHistory] Unknown date format:', dateValue, typeof dateValue)
      return new Date()
    } catch (error) {
      console.warn('[useConversationHistory] Error parsing date:', dateValue, error)
      return new Date()
    }
  }

  /**
   * Process and add a single history record to the dialog
   */
  const processHistoryRecord = (record: PlanExecutionRecord): void => {
    if (!record) return

    // Debug: Log the actual date values before parsing
    console.log('[useConversationHistory] Processing record:', {
      userRequest: record.userRequest,
      startTime: record.startTime,
      startTimeType: typeof record.startTime,
      endTime: record.endTime,
      endTimeType: typeof record.endTime,
    })

    // Add user message (the original query)
    if (record.userRequest && record.startTime) {
      const parsedDate = parseBackendDate(record.startTime)
      console.log('[useConversationHistory] Parsed date:', {
        original: record.startTime,
        parsed: parsedDate,
        parsedISO: parsedDate.toISOString(),
        isValid: !isNaN(parsedDate.getTime()),
      })
      messageDialog.addMessage('user', record.userRequest, {
        timestamp: parsedDate,
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
            ? parseBackendDate(record.endTime)
            : record.startTime
              ? parseBackendDate(record.startTime)
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

      // Debug: Log date formats to help diagnose parsing issues
      if (historyRecords.length > 0) {
        const firstRecord = historyRecords[0]
        console.log('[useConversationHistory] Sample date formats:', {
          startTime: firstRecord.startTime,
          endTime: firstRecord.endTime,
          startTimeType: typeof firstRecord.startTime,
        })
      }

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
