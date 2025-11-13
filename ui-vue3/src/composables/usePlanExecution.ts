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

import { ref, reactive, readonly } from 'vue'
import { CommonApiService } from '@/api/common-api-service'
import { DirectApiService } from '@/api/direct-api-service'
import { PlanActApiService } from '@/api/plan-act-api-service'
import { useTaskStore } from '@/stores/task'
import type { UIStateData } from '@/types/cache-data'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'

// Define event callback interface
interface EventCallbacks {
  onPlanUpdate?: (rootPlanId: string) => void
  onPlanCompleted?: (rootPlanId: string) => void
  onDialogRoundStart?: (rootPlanId: string) => void
  onChatInputUpdateState?: (rootPlanId: string) => void
  onChatInputClear?: () => void
  onPlanError?: (message: string) => void
}

/**
 * Composable for managing plan execution
 * Provides methods to handle plan execution requests, polling, and event callbacks
 */
export function usePlanExecution() {
  const POLL_INTERVAL = 5000

  // Reactive state
  const activePlanId = ref<string | null>(null)
  const lastSequenceSize = ref(0)
  const isPolling = ref(false)
  const pollTimer = ref<number | null>(null)

  // Event callbacks
  const callbacks = ref<EventCallbacks>({})

  // Cache for PlanExecutionRecord by rootPlanId
  const planExecutionCache = reactive(new Map<string, PlanExecutionRecord>())

  // Cache for UI state by rootPlanId
  const uiStateCache = reactive(new Map<string, UIStateData>())

  /**
   * Get cached plan execution record by rootPlanId
   */
  const getCachedPlanRecord = (rootPlanId: string): PlanExecutionRecord | undefined => {
    return planExecutionCache.get(rootPlanId)
  }

  /**
   * Get cached UI state by rootPlanId
   */
  const getCachedUIState = (rootPlanId: string): UIStateData | undefined => {
    return uiStateCache.get(rootPlanId)
  }

  /**
   * Set cached UI state by rootPlanId
   */
  const setCachedUIState = (rootPlanId: string, uiState: UIStateData): void => {
    uiStateCache.set(rootPlanId, uiState)
    console.log(`[usePlanExecution] Cached UI state for rootPlanId: ${rootPlanId}`)
  }

  /**
   * Get all cached plan execution records
   */
  const getAllCachedRecords = (): Map<string, PlanExecutionRecord> => {
    return new Map(planExecutionCache)
  }

  /**
   * Check if a plan execution record exists in cache
   */
  const hasCachedPlanRecord = (rootPlanId: string): boolean => {
    return planExecutionCache.has(rootPlanId)
  }

  /**
   * Set cached plan execution record by rootPlanId
   */
  const setCachedPlanRecord = (rootPlanId: string, record: PlanExecutionRecord): void => {
    planExecutionCache.set(rootPlanId, record)
    console.log(`[usePlanExecution] Cached plan execution record for rootPlanId: ${rootPlanId}`)
  }

  /**
   * Clear cached plan execution record by rootPlanId
   */
  const clearCachedPlanRecord = (rootPlanId: string): boolean => {
    const deleted = planExecutionCache.delete(rootPlanId)
    if (deleted) {
      console.log(
        `[usePlanExecution] Cleared cached plan execution record for rootPlanId: ${rootPlanId}`
      )
    }
    return deleted
  }

  /**
   * Clear all cached plan execution records
   */
  const clearAllCachedRecords = (): void => {
    const planCacheSize = planExecutionCache.size
    const uiStateCacheSize = uiStateCache.size

    planExecutionCache.clear()
    uiStateCache.clear()

    console.log(
      `[usePlanExecution] Cleared all caches - Plans: ${planCacheSize}, UI States: ${uiStateCacheSize}`
    )
  }

  /**
   * Set event callbacks
   */
  const setEventCallbacks = (newCallbacks: EventCallbacks): void => {
    callbacks.value = { ...callbacks.value, ...newCallbacks }
    console.log('[usePlanExecution] Event callbacks set:', Object.keys(newCallbacks))
  }

  // Event emission helpers
  const emitChatInputClear = (): void => {
    if (callbacks.value.onChatInputClear) {
      callbacks.value.onChatInputClear()
    }
  }

  const emitChatInputUpdateState = (rootPlanId: string): void => {
    if (callbacks.value.onChatInputUpdateState) {
      callbacks.value.onChatInputUpdateState(rootPlanId)
    }
  }

  const emitDialogRoundStart = (rootPlanId: string): void => {
    if (callbacks.value.onDialogRoundStart) {
      callbacks.value.onDialogRoundStart(rootPlanId)
    }
  }

  const emitPlanUpdate = (rootPlanId: string): void => {
    if (callbacks.value.onPlanUpdate) {
      callbacks.value.onPlanUpdate(rootPlanId)
    }
  }

  const emitPlanCompleted = (rootPlanId: string): void => {
    if (callbacks.value.onPlanCompleted) {
      callbacks.value.onPlanCompleted(rootPlanId)
    }
  }

  const emitPlanError = (message: string): void => {
    if (callbacks.value.onPlanError) {
      callbacks.value.onPlanError(message)
    }
  }

  /**
   * Validate request and prepare UI
   */
  const validateAndPrepareUIForNewRequest = (query: string): boolean => {
    if (!query) {
      console.warn('[usePlanExecution] Query is empty')
      return false
    }

    if (activePlanId.value) {
      // There is already an active plan, cannot start new request
      return false
    }

    // Clear input and set to disabled state
    emitChatInputClear()

    // Cache UI state data first
    const uiStatePlanId = activePlanId.value ?? 'ui-state'
    setCachedUIState(uiStatePlanId, { enabled: false, placeholder: 'Processing...' })
    emitChatInputUpdateState(uiStatePlanId)

    return true
  }

  /**
   * Send user message and set plan ID
   */
  const sendUserMessageAndSetPlanId = async (query: string): Promise<unknown> => {
    try {
      // Use direct execution mode API to send message
      const response = (await DirectApiService.sendMessage({
        input: query,
      })) as Record<string, unknown>

      if (response?.planId) {
        activePlanId.value = response.planId as string
        return response
      } else if (response?.planTemplateId) {
        // If response contains planTemplateId instead of planId
        activePlanId.value = response.planTemplateId as string
        return { ...response, planId: response.planTemplateId }
      }

      console.error('[usePlanExecution] Failed to get planId from response:', response)
      throw new Error('Failed to get valid planId from API response')
    } catch (error: unknown) {
      console.error('[usePlanExecution] API call failed:', error)
      throw error
    }
  }

  /**
   * Get plan details
   */
  const getPlanDetails = async (planId: string): Promise<PlanExecutionRecord | null> => {
    try {
      // Use CommonApiService's getDetails method
      const details = await CommonApiService.getDetails(planId)

      // Cache the plan execution record by rootPlanId if it exists
      if (details?.rootPlanId) {
        planExecutionCache.set(details.rootPlanId, details)
        console.log(
          `[usePlanExecution] Cached plan execution record for rootPlanId: ${details.rootPlanId}`
        )
      }

      return details
    } catch (error: unknown) {
      console.error('[usePlanExecution] Failed to get plan details:', error)
      const message = error instanceof Error ? error.message : 'Failed to get plan'
      return {
        currentPlanId: planId,
        status: 'failed',
        message: message,
      }
    }
  }

  /**
   * Handle plan completion common logic
   */
  const handlePlanCompletion = (details: PlanExecutionRecord): void => {
    emitPlanCompleted(details.rootPlanId ?? '')
    lastSequenceSize.value = 0
    stopPolling()

    // Mark task as no longer running
    const taskStore = useTaskStore()
    if (taskStore.currentTask && taskStore.currentTask.isRunning) {
      taskStore.currentTask.isRunning = false
      console.log('[usePlanExecution] Task marked as completed')
    }

    // Delay deletion of plan execution record
    try {
      setTimeout(async () => {
        if (activePlanId.value) {
          try {
            await PlanActApiService.deletePlanTemplate(activePlanId.value)
            console.log(
              `[usePlanExecution] Plan template ${activePlanId.value} deleted successfully`
            )
          } catch (error: unknown) {
            const message = error instanceof Error ? error.message : 'Unknown error'
            console.log(`Delete plan execution record failed: ${message}`)
          }
        }
      }, 5000)
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      console.log(`Delete plan execution record failed: ${message}`)
    }

    if (details.completed) {
      activePlanId.value = null
      emitChatInputUpdateState(details.rootPlanId ?? '')
    }
  }

  /**
   * Handle plan error
   */
  const handlePlanError = (details: PlanExecutionRecord): void => {
    emitPlanError(details.message ?? '')
    lastSequenceSize.value = 0
    stopPolling()

    // Mark task as no longer running
    const taskStore = useTaskStore()
    if (taskStore.currentTask && taskStore.currentTask.isRunning) {
      taskStore.currentTask.isRunning = false
      console.log('[usePlanExecution] Task marked as stopped due to error')
    }

    // Delay deletion of plan execution record
    try {
      setTimeout(async () => {
        if (activePlanId.value) {
          try {
            await PlanActApiService.deletePlanTemplate(activePlanId.value)
            console.log(
              `[usePlanExecution] Plan template ${activePlanId.value} deleted successfully`
            )
          } catch (error: unknown) {
            const message = error instanceof Error ? error.message : 'Unknown error'
            console.log(`Delete plan execution record failed: ${message}`)
          }
        }
      }, 5000)
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      console.log(`Delete plan execution record failed: ${message}`)
    }
  }

  /**
   * Poll plan execution status
   */
  const pollPlanStatus = async (): Promise<void> => {
    if (!activePlanId.value) return

    if (isPolling.value) {
      console.log('[usePlanExecution] Previous polling still in progress, skipping')
      return
    }

    try {
      isPolling.value = true

      const details = await getPlanDetails(activePlanId.value)

      if (!details) {
        console.warn(
          '[usePlanExecution] No details received from API - this might be a temporary network issue'
        )
        return
      }

      // Only handle actual plan execution failures, not network errors
      if (
        details.status &&
        details.status === 'failed' &&
        details.message &&
        !details.message.includes('Failed to get detailed information')
      ) {
        handlePlanError(details)
        return
      }

      // Update cache with latest plan details if rootPlanId exists
      if (details.rootPlanId) {
        setCachedPlanRecord(details.rootPlanId, details)
      }

      emitPlanUpdate(details.rootPlanId ?? '')

      if (details.completed) {
        handlePlanCompletion(details)
      }
    } catch (error: unknown) {
      console.error('[usePlanExecution] Failed to poll plan status:', error)
    } finally {
      isPolling.value = false
    }
  }

  /**
   * Start polling plan execution status
   */
  const startPolling = (): void => {
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
    }

    pollTimer.value = window.setInterval(() => {
      pollPlanStatus()
    }, POLL_INTERVAL)

    console.log('[usePlanExecution] Started polling')
  }

  /**
   * Immediately poll plan execution status (for manual refresh trigger)
   */
  const pollPlanStatusImmediately = async (): Promise<void> => {
    console.log('[usePlanExecution] Polling plan status immediately')
    await pollPlanStatus()
  }

  /**
   * Stop polling
   */
  const stopPolling = (): void => {
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
    }
    console.log('[usePlanExecution] Stopped polling')
  }

  /**
   * Start plan execution sequence
   */
  const initiatePlanExecutionSequence = (query: string, planId: string): void => {
    console.log(
      `[usePlanExecution] Starting plan execution sequence for query: "${query}", planId: ${planId}`
    )

    // Use planId as rootPlanId for now (assume they are the same initially)
    const rootPlanId = planId

    // Mark task as running in task store
    const taskStore = useTaskStore()
    taskStore.setTaskRunning(planId)
    console.log('[usePlanExecution] Task marked as running with planId:', planId)

    // Try to emit dialog start
    emitDialogRoundStart(rootPlanId)

    startPolling()
  }

  /**
   * Handle user message send request
   */
  const handleUserMessageSendRequested = async (query: string): Promise<void> => {
    if (!validateAndPrepareUIForNewRequest(query)) {
      return
    }

    try {
      await sendUserMessageAndSetPlanId(query)

      if (activePlanId.value) {
        initiatePlanExecutionSequence(query, activePlanId.value)
      } else {
        throw new Error('Failed to get valid plan ID')
      }
    } catch (error: unknown) {
      console.error('[usePlanExecution] Failed to send user message:', error)
      // Set UI state to enabled for error recovery
      const errorPlanId = activePlanId.value ?? 'error'
      setCachedUIState(errorPlanId, { enabled: true })

      emitChatInputUpdateState(errorPlanId)
      activePlanId.value = null
    }
  }

  /**
   * Handle plan execution request
   */
  const handlePlanExecutionRequested = (planId: string, query?: string): void => {
    console.log('[usePlanExecution] Received plan execution request:', { planId, query })

    if (planId) {
      activePlanId.value = planId
      initiatePlanExecutionSequence(query ?? 'Execute Plan', planId)
    } else {
      console.error('[usePlanExecution] Invalid plan execution request: missing planId')
    }
  }

  /**
   * Handle plan execution request with cache lookup by rootPlanId
   */
  const handleCachedPlanExecution = (rootPlanId: string, query?: string): boolean => {
    const cachedRecord = getCachedPlanRecord(rootPlanId)

    if (cachedRecord?.currentPlanId) {
      console.log(
        `[usePlanExecution] Found cached plan execution record for rootPlanId: ${rootPlanId}`
      )
      handlePlanExecutionRequested(cachedRecord.currentPlanId, query)
      return true
    } else {
      console.log(
        `[usePlanExecution] No cached plan execution record found for rootPlanId: ${rootPlanId}`
      )
      return false
    }
  }

  /**
   * Clean up resources
   */
  const cleanup = (): void => {
    stopPolling()
    activePlanId.value = null
    lastSequenceSize.value = 0
    isPolling.value = false

    // Clear all cached plan execution records
    clearAllCachedRecords()
  }

  return {
    // State
    activePlanId: readonly(activePlanId),
    isPolling: readonly(isPolling),
    lastSequenceSize: readonly(lastSequenceSize),

    // Cache methods
    getCachedPlanRecord,
    setCachedPlanRecord,
    clearCachedPlanRecord,
    hasCachedPlanRecord,
    getAllCachedRecords,
    getCachedUIState,
    setCachedUIState,
    clearAllCachedRecords,

    // Event callbacks
    setEventCallbacks,

    // Plan execution methods
    handlePlanExecutionRequested,
    handleCachedPlanExecution,
    handleUserMessageSendRequested,
    initiatePlanExecutionSequence,

    // Polling methods
    startPolling,
    stopPolling,
    pollPlanStatus,
    pollPlanStatusImmediately,

    // Utility methods
    cleanup,
  }
}

// Singleton instance for global use
let singletonInstance: ReturnType<typeof usePlanExecution> | null = null

/**
 * Get or create singleton instance of usePlanExecution
 */
export function usePlanExecutionSingleton() {
  if (!singletonInstance) {
    singletonInstance = usePlanExecution()
  }
  return singletonInstance
}

