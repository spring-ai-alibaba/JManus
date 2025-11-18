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

import { CommonApiService } from '@/api/common-api-service'
import { useTaskStore } from '@/stores/task'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'
import { reactive, readonly, ref } from 'vue'

/**
 * Composable for managing plan execution with reactive state
 * Singleton pattern - maintains reactive PlanExecutionRecord map
 * Pure business logic layer - no dependency on UI layer
 */
export function usePlanExecution() {
  const POLL_INTERVAL = 5000
  const POST_COMPLETION_POLL_COUNT = 10 // Continue polling 3 times after completion to ensure summary is fetched

  // Tracked plan IDs (plans being polled)
  const trackedPlanIds = ref<Set<string>>(new Set())

  // Track completed plans that still need polling for summary
  const completedPlansPollCount = reactive(new Map<string, number>())

  // Reactive map of PlanExecutionRecord by planId (rootPlanId or currentPlanId)
  // This is the main reactive state that components watch
  const planExecutionRecords = reactive(new Map<string, PlanExecutionRecord>())

  // Polling state
  const isPolling = ref(false)
  const pollTimer = ref<number | null>(null)

  /**
   * Get PlanExecutionRecord by planId
   */
  const getPlanExecutionRecord = (planId: string): PlanExecutionRecord | undefined => {
    return planExecutionRecords.get(planId)
  }

  /**
   * Set a cached plan record in the reactive map
   * Useful for restoring conversation history
   */
  const setCachedPlanRecord = (planId: string, record: PlanExecutionRecord): void => {
    if (!planId) {
      console.warn('[usePlanExecution] Cannot cache plan record with empty planId')
      return
    }
    planExecutionRecords.set(planId, record)
    console.log('[usePlanExecution] Cached plan record:', planId)
  }

  /**
   * Get all tracked plan IDs
   */
  const getTrackedPlanIds = (): string[] => {
    return Array.from(trackedPlanIds.value)
  }

  /**
   * Add planId to tracking and start polling if not already polling
   */
  const trackPlan = (planId: string): void => {
    if (!planId) {
      console.warn('[usePlanExecution] Cannot track empty planId')
      return
    }

    trackedPlanIds.value.add(planId)
    console.log('[usePlanExecution] Tracking plan:', planId)

    // Start polling if not already started
    if (!pollTimer.value) {
      startPolling()
    }

    // Immediately fetch the plan details
    pollPlanStatus(planId)
  }

  /**
   * Remove planId from tracking
   */
  const untrackPlan = (planId: string): void => {
    trackedPlanIds.value.delete(planId)
    console.log('[usePlanExecution] Untracking plan:', planId)

    // Stop polling if no more plans to track
    if (trackedPlanIds.value.size === 0) {
      stopPolling()
    }
  }

  /**
   * Poll plan execution status for a specific planId
   */
  const pollPlanStatus = async (planId: string): Promise<void> => {
    if (!planId) return

    try {
      const details = await CommonApiService.getDetails(planId)

      if (!details) {
        console.warn(
          `[usePlanExecution] No details received for planId: ${planId} - this might be a temporary network issue`
        )
        return
      }

      // Determine the key to use (rootPlanId or currentPlanId)
      // Priority: use rootPlanId if available, otherwise use currentPlanId
      const recordKey = details.rootPlanId || details.currentPlanId

      if (!recordKey) {
        console.warn('[usePlanExecution] Plan record has no rootPlanId or currentPlanId:', details)
        return
      }

      // Update reactive map - this will trigger watchers in components
      // Always use recordKey (rootPlanId or currentPlanId) as the map key
      // This ensures consistency with how useMessageDialog stores planId in dialog.planId
      planExecutionRecords.set(recordKey, details)

      // If the passed planId is different from recordKey, also store it with the passed planId
      // This handles cases where the API returns a different planId than what was requested
      if (planId !== recordKey) {
        planExecutionRecords.set(planId, details)
        console.log('[usePlanExecution] Stored record with both keys:', { planId, recordKey })
      }

      console.log('[usePlanExecution] Updated plan execution record:', {
        planId,
        recordKey,
        rootPlanId: details.rootPlanId,
        currentPlanId: details.currentPlanId,
        completed: details.completed,
        status: details.status,
      })

      // Handle completion - continue polling to ensure summary is fetched
      if (details.completed) {
        console.log(`[usePlanExecution] Plan ${recordKey} completed, checking for summary...`)

        // Mark task as no longer running
        const taskStore = useTaskStore()
        if (taskStore.currentTask && taskStore.currentTask.isRunning) {
          taskStore.currentTask.isRunning = false
        }

        // Check if we have summary or if we need to continue polling
        const hasSummary = details.summary || details.result || details.message
        const currentPollCount = completedPlansPollCount.get(recordKey) || 0

        if (!hasSummary && currentPollCount < POST_COMPLETION_POLL_COUNT) {
          // Continue polling to fetch summary
          completedPlansPollCount.set(recordKey, currentPollCount + 1)
          console.log(
            `[usePlanExecution] Plan ${recordKey} completed but no summary yet, continuing to poll (${currentPollCount + 1}/${POST_COMPLETION_POLL_COUNT})`
          )
          // Don't untrack yet - keep polling
        } else {
          // We have summary or reached max poll count, proceed with cleanup
          console.log(`[usePlanExecution] Plan ${recordKey} completed, cleaning up...`, {
            hasSummary: !!hasSummary,
            pollCount: currentPollCount,
          })

          // Delete execution details from backend
          try {
            await CommonApiService.deleteExecutionDetails(recordKey)
            console.log(`[usePlanExecution] Deleted execution details for plan: ${recordKey}`)
          } catch (error: unknown) {
            const message = error instanceof Error ? error.message : 'Unknown error'
            console.error(`[usePlanExecution] Failed to delete execution details: ${message}`)
          }

          // Remove from tracking
          untrackPlan(planId)

          // Clean up poll count tracking
          completedPlansPollCount.delete(recordKey)

          // Remove from reactive map after a delay
          setTimeout(() => {
            planExecutionRecords.delete(recordKey)
          }, 5000)
        }
      }

      // Handle errors
      if (
        details.status === 'failed' &&
        details.message &&
        !details.message.includes('Failed to get detailed information')
      ) {
        console.error(`[usePlanExecution] Plan ${recordKey} failed:`, details.message)
        // Keep the record in the map so components can handle the error
      }
    } catch (error: unknown) {
      console.error(`[usePlanExecution] Failed to poll plan status for ${planId}:`, error)
    }
  }

  /**
   * Poll all tracked plans
   */
  const pollAllTrackedPlans = async (): Promise<void> => {
    if (isPolling.value) {
      console.log('[usePlanExecution] Previous polling still in progress, skipping')
      return
    }

    // Poll both tracked plans and completed plans that still need polling
    const plansToPoll = new Set(trackedPlanIds.value)
    for (const [planId] of completedPlansPollCount.entries()) {
      plansToPoll.add(planId)
    }

    if (plansToPoll.size === 0) {
      return
    }

    try {
      isPolling.value = true

      // Poll all plans in parallel (both tracked and completed ones waiting for summary)
      const pollPromises = Array.from(plansToPoll).map(planId => pollPlanStatus(planId))
      await Promise.all(pollPromises)
    } catch (error: unknown) {
      console.error('[usePlanExecution] Failed to poll tracked plans:', error)
    } finally {
      isPolling.value = false
    }
  }

  /**
   * Start polling all tracked plans
   */
  const startPolling = (): void => {
    if (pollTimer.value) {
      clearInterval(pollTimer.value)
    }

    pollTimer.value = window.setInterval(() => {
      pollAllTrackedPlans()
    }, POLL_INTERVAL)

    console.log('[usePlanExecution] Started polling')
  }

  /**
   * Stop polling
   * Only stops if there are no tracked plans AND no completed plans waiting for summary
   */
  const stopPolling = (): void => {
    // Check if there are any completed plans still being polled
    if (completedPlansPollCount.size > 0) {
      console.log(
        `[usePlanExecution] Not stopping polling - ${completedPlansPollCount.size} completed plans still waiting for summary`
      )
      return
    }

    if (pollTimer.value) {
      clearInterval(pollTimer.value)
      pollTimer.value = null
    }
    console.log('[usePlanExecution] Stopped polling')
  }

  /**
   * Immediately poll a specific plan
   */
  const pollPlanStatusImmediately = async (planId: string): Promise<void> => {
    console.log(`[usePlanExecution] Polling plan status immediately for: ${planId}`)
    await pollPlanStatus(planId)
  }

  /**
   * Handle plan execution request - track the planId and start polling
   * Called by useMessageDialog when a new plan execution starts
   * This method is the entry point for tracking plan execution
   */
  const handlePlanExecutionRequested = (planId: string): void => {
    console.log('[usePlanExecution] Received plan execution request:', { planId })

    if (!planId) {
      console.error('[usePlanExecution] Invalid plan execution request: missing planId')
      return
    }

    // Mark task as running
    const taskStore = useTaskStore()
    taskStore.setTaskRunning(planId)

    // Track the plan
    trackPlan(planId)
  }

  /**
   * Clean up resources
   */
  const cleanup = (): void => {
    stopPolling()
    trackedPlanIds.value.clear()
    completedPlansPollCount.clear()
    planExecutionRecords.clear()
    isPolling.value = false
  }

  return {
    // Reactive state - components can watch this
    planExecutionRecords: readonly(planExecutionRecords),
    trackedPlanIds: readonly(trackedPlanIds),
    isPolling: readonly(isPolling),

    // Methods
    getPlanExecutionRecord,
    setCachedPlanRecord,
    getTrackedPlanIds,
    trackPlan,
    untrackPlan,
    handlePlanExecutionRequested,
    pollPlanStatusImmediately,
    startPolling,
    stopPolling,
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
