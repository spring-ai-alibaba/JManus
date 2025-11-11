<!--
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
-->

<template>
  <div class="direct-page">
    <div class="direct-chat">
      <Sidebar ref="sidebarRef" @planExecutionRequested="handlePlanExecutionRequested" />
      <!-- Left Panel - Chat -->
      <div class="left-panel" :style="{ width: computedLeftPanelWidth + '%' }">
        <div class="chat-header">
          <button class="back-button" @click="goBack">
            <Icon icon="carbon:arrow-left" />
          </button>
          <h2>{{ $t('conversation') }}</h2>
          <div class="header-actions">
            <LanguageSwitcher />
            <button class="config-button" @click="newChat" :title="$t('memory.newChat')">
              <Icon icon="carbon:add" width="20" />
            </button>
            <button class="config-button" @click="handleConfig" :title="$t('direct.configuration')">
              <Icon icon="carbon:settings-adjust" width="20" />
            </button>
            <button
              class="cron-task-btn"
              @click="memoryStore.toggleSidebar()"
              :title="$t('memory.selectMemory')"
            >
              <Icon icon="carbon:calendar" width="20" />
            </button>
          </div>
        </div>

        <!-- Chat Container -->
        <div class="chat-content">
          <ChatContainer
            ref="chatRef"
            mode="direct"
            :initial-prompt="prompt || ''"
            @step-selected="handleStepSelected"
            @sub-plan-step-selected="handleSubPlanStepSelected"
          />
        </div>

        <!-- Input Area -->
        <InputArea
          :key="$i18n.locale"
          ref="inputRef"
          :disabled="isLoading"
          :placeholder="isLoading ? t('input.waiting') : t('input.placeholder')"
          :initial-value="prompt"
          @send="handleSendMessage"
          @clear="handleInputClear"
          @focus="handleInputFocus"
          @update-state="handleInputUpdateState"
          @plan-mode-clicked="handlePlanModeClicked"
        />
      </div>

      <!-- Resizer -->
      <div
        class="panel-resizer"
        @mousedown="startResize"
        @dblclick="resetPanelSize"
        :title="$t('direct.panelResizeHint')"
      >
        <div class="resizer-line"></div>
      </div>

      <!-- Right Panel - Preview -->
      <RightPanel
        ref="rightPanelRef"
        :style="{ width: 100 - leftPanelWidth + '%' }"
        :current-root-plan-id="currentRootPlanId"
      />
    </div>

    <!-- Memory Modal -->
    <Memory @memory-selected="memorySelected" />

    <!-- Message toast component -->
    <div v-if="message.show" class="message-toast" :class="message.type">
      <div class="message-content">
        <span>{{ message.text }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CommonApiService } from '@/api/common-api-service'
import { MemoryApiService } from '@/api/memory-api-service'
import { PlanActApiService } from '@/api/plan-act-api-service'
import ChatContainer from '@/components/chat/ChatContainer.vue'
import InputArea from '@/components/input/InputArea.vue'
import LanguageSwitcher from '@/components/language-switcher/LanguageSwitcher.vue'
import Memory from '@/components/memory/Memory.vue'
import RightPanel from '@/components/right-panel/RightPanel.vue'
import Sidebar from '@/components/sidebar/Sidebar.vue'
import { useMessage } from '@/composables/useMessage'
import type { InputMessage } from '@/stores/memory'
import { memoryStore } from '@/stores/memory'
import { sidebarStore } from '@/stores/sidebar'
import { templateStore } from '@/stores/templateStore'
import { useTaskStore } from '@/stores/task'
import type { PlanExecutionRequestPayload } from '@/types/plan-execution'
import type { PlanExecutionRecord } from '@/types/plan-execution-record'
import { planExecutionManager } from '@/utils/plan-execution-manager'
import { Icon } from '@iconify/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

// Define component name for Vue linting rules
defineOptions({
  name: 'DirectIndex',
})

// Extended InputMessage with optional properties
interface ExtendedInputMessage extends InputMessage {
  attachments?: unknown[]
  toolName?: string
  replacementParams?: Record<string, unknown>
}

// API Response type
interface ApiResponse {
  planId?: string
  message?: string
  result?: string
  [key: string]: unknown
}

// Message type from chat component
interface ChatMessage {
  id: string
  [key: string]: unknown
}
const route = useRoute()
const router = useRouter()
const taskStore = useTaskStore()
const { t } = useI18n()
const { message, showMessage } = useMessage()

const prompt = ref<string>('')
const inputOnlyContent = ref<string>('')
const rightPanelRef = ref()
const chatRef = ref()
const inputRef = ref()
const sidebarRef = ref()
const isExecutingPlan = ref(false)
const isLoading = ref(false)
const currentRootPlanId = ref<string | null>(null)

// Related to panel width
const leftPanelWidth = ref(50) // Left panel width percentage
const isResizing = ref(false)
const startX = ref(0)
const startLeftWidth = ref(0)

// Computed left panel width that adjusts based on sidebar width
const computedLeftPanelWidth = computed(() => {
  if (sidebarStore.isCollapsed) {
    return leftPanelWidth.value
  }

  // When sidebar is expanded, calculate available width for left panel
  // Get sidebar width from the sidebar component if available
  let sidebarWidth = 26 // Default sidebar width

  // Try to get actual sidebar width from the sidebar component
  if (sidebarRef.value && sidebarRef.value.sidebarWidth !== undefined) {
    sidebarWidth = sidebarRef.value.sidebarWidth
  }

  // Calculate maximum available width for left panel
  // Total width is 100%, so left panel max = 100% - sidebar width
  const maxAvailableWidth = 100 - sidebarWidth

  // Ensure left panel width doesn't exceed available space
  // Also maintain minimum width of 20%
  return Math.max(20, Math.min(maxAvailableWidth, leftPanelWidth.value))
})

onMounted(() => {
  console.log('[Direct] onMounted called')
  console.log('[Direct] taskStore.currentTask:', taskStore.currentTask)
  console.log('[Direct] taskStore.hasUnprocessedTask():', taskStore.hasUnprocessedTask())

  // Register event callbacks to planExecutionManager
  planExecutionManager.setEventCallbacks({
    onPlanUpdate: (rootPlanId: string) => {
      console.log('[Direct] Plan update event received for rootPlanId:', rootPlanId)

      if (!shouldProcessEventForCurrentPlan(rootPlanId)) {
        return
      }

      console.log('[Direct] Processing plan update for current rootPlanId:', rootPlanId)

      // Call chat component's handlePlanUpdate method
      if (chatRef.value && typeof chatRef.value.handlePlanUpdate === 'function') {
        console.log('[Direct] Calling chatRef.handlePlanUpdate with rootPlanId:', rootPlanId)
        chatRef.value.handlePlanUpdate(rootPlanId)
      } else {
        console.warn('[Direct] chatRef.handlePlanUpdate method not available')
      }

      // Call right panel component's updateDisplayedPlanProgress method
      if (
        rightPanelRef.value &&
        typeof rightPanelRef.value.updateDisplayedPlanProgress === 'function'
      ) {
        console.log(
          '[Direct] Calling rightPanelRef.updateDisplayedPlanProgress with rootPlanId:',
          rootPlanId
        )
        rightPanelRef.value.updateDisplayedPlanProgress(rootPlanId)
      } else {
        console.warn('[Direct] rightPanelRef.updateDisplayedPlanProgress method not available')
      }
    },

    onPlanCompleted: (rootPlanId: string) => {
      console.log('[Direct] Plan completed event received for rootPlanId:', rootPlanId)

      if (!shouldProcessEventForCurrentPlan(rootPlanId)) {
        return
      }

      console.log('[Direct] Processing plan completion for current rootPlanId:', rootPlanId)

      // Call chat component's handlePlanCompleted method
      if (chatRef.value && typeof chatRef.value.handlePlanCompleted === 'function') {
        const planDetails = planExecutionManager.getCachedPlanRecord(rootPlanId)

        // Retrieved plan details from cache for processing
        if (planDetails && planDetails.completed) {
          if (planDetails.summary && planDetails.summary.trim().length > 0) {
            console.log(
              '[Direct]  Plan details are valid and complete from polling cache, updating UI'
            )
            chatRef.value.handlePlanCompleted(planDetails)
          } else {
            console.warn(
              '[Direct]  Plan completed but summary is empty, still proceeding with available data'
            )
            chatRef.value.handlePlanCompleted(planDetails)
          }
        } else {
          console.warn('[Direct]  Plan details incomplete in cache, attempting API fallback')
          // Fallback: try getting it from the API (maybe it has been deleted)
          CommonApiService.getDetails(rootPlanId)
            .then(freshDetails => {
              console.log('[Direct]  Got fresh details from API:', freshDetails)
              if (freshDetails && freshDetails.completed) {
                console.log('[Direct] Fresh details from API are valid, updating UI')
                planExecutionManager.setCachedPlanRecord(rootPlanId, freshDetails)
                chatRef.value.handlePlanCompleted(freshDetails)
              } else {
                console.warn('[Direct]  API details also incomplete, using best available data')
                chatRef.value.handlePlanCompleted(planDetails ?? { planId: rootPlanId })
              }
            })
            .catch(error => {
              console.error(
                '[Direct] API call failed (likely record deleted), using cached data:',
                error
              )
              chatRef.value.handlePlanCompleted(planDetails ?? { planId: rootPlanId })
            })
        }
      } else {
        console.warn('[Direct] chatRef.handlePlanCompleted method not available')
      }

      // Clear current root plan ID when plan is completed
      currentRootPlanId.value = null
      console.log('[Direct] 🧹 Cleared currentRootPlanId after plan completion')

      //Clear sessionPlanId to ensure that subsequent file uploads use the new planId
      if (inputRef.value && typeof inputRef.value.resetSession === 'function') {
        console.log('[Direct] 🧹 Calling inputRef.resetSession to clear sessionPlanId')
        inputRef.value.resetSession()
      }
    },

    onDialogRoundStart: (rootPlanId: string) => {
      console.log('[Direct] Dialog round start event received for rootPlanId:', rootPlanId)

      // Set current root plan ID when dialog starts
      currentRootPlanId.value = rootPlanId
      console.log('[Direct] Set currentRootPlanId to:', rootPlanId)

      // Call chat component's handleDialogRoundStart method (without query parameter)
      if (chatRef.value && typeof chatRef.value.handleDialogRoundStart === 'function') {
        console.log('[Direct] Calling chatRef.handleDialogRoundStart with planId:', rootPlanId)
        chatRef.value.handleDialogRoundStart(rootPlanId)
      } else {
        console.warn('[Direct] chatRef.handleDialogRoundStart method not available')
      }
    },

    onChatInputClear: () => {
      console.log('[Direct] Chat input clear event received')
      handleInputClear()
    },

    onChatInputUpdateState: (rootPlanId: string) => {
      console.log('[Direct] Chat input update state event received for rootPlanId:', rootPlanId)

      if (!shouldProcessEventForCurrentPlan(rootPlanId, true)) {
        return
      }

      const uiState = planExecutionManager.getCachedUIState(rootPlanId)
      if (uiState) {
        handleInputUpdateState(uiState.enabled, uiState.placeholder)
      }
    },

    onPlanError: (message: string) => {
      // No right panel notification needed, uncomment if needed
      // showMessage(`${t('planTemplate.executionFailed')}: ${message}`, 'error')
      chatRef.value.handlePlanError(message)
    },
  })

  console.log('[Direct] Event callbacks registered to planExecutionManager')

  // Restore conversation history if conversationId exists in localStorage
  const savedConversationId = memoryStore.getConversationId()
  if (savedConversationId) {
    console.log('[Direct] Found saved conversationId, restoring conversation:', savedConversationId)
    nextTick(async () => {
      try {
        // Fetch conversation history from backend
        const historyRecords = await MemoryApiService.getConversationHistory(savedConversationId)
        console.log('[DirectView] Restored conversation history on page load:', historyRecords)

        // Convert each PlanExecutionRecord to chat messages and display them
        for (const record of historyRecords) {
          if (!record) continue

          // Add user message (the original query)
          if (record.userRequest && chatRef.value && record.startTime) {
            chatRef.value.addMessage('user', record.userRequest, {
              timestamp: new Date(record.startTime),
            })
          }

          // Add assistant message (the result/summary)
          if (chatRef.value && record.currentPlanId) {
            const assistantContent =
              record.summary || record.result || record.message || 'Execution completed'

            // Convert API record to plan execution record format
            const planExecutionRecord: Partial<PlanExecutionRecord> = {
              currentPlanId: record.currentPlanId,
              status: record.completed ? 'completed' : 'running',
            }

            // Add optional fields if they exist
            if (record.rootPlanId) planExecutionRecord.rootPlanId = record.rootPlanId
            if (record.summary) planExecutionRecord.summary = record.summary
            if (record.completed !== undefined) planExecutionRecord.completed = record.completed
            if (record.agentExecutionSequence)
              planExecutionRecord.agentExecutionSequence = record.agentExecutionSequence

            chatRef.value.addMessage('assistant', assistantContent, {
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
              planExecutionManager.setCachedPlanRecord(
                record.rootPlanId,
                planExecutionRecord as PlanExecutionRecord
              )
            }
          }
        }

        // Scroll to bottom after loading history
        await nextTick()
        if (chatRef.value) {
          chatRef.value.scrollToBottom()
        }

        console.log(
          '[DirectView] Successfully restored conversation with',
          historyRecords.length,
          'dialog rounds'
        )
      } catch (error) {
        console.error('[DirectView] Failed to restore conversation history:', error)
        // Don't show error message to user on page load, just log it
      }
    })
  }

  // Initialize sidebar data
  templateStore.loadPlanTemplateList()

  // Check if there is a task in the store
  if (taskStore.hasUnprocessedTask() && taskStore.currentTask) {
    const taskContent = taskStore.currentTask.prompt
    console.log('[Direct] Found unprocessed task from store:', taskContent)

    // Check if task content is not empty before processing
    if (taskContent.trim()) {
      // Mark the task as processed to prevent duplicate responses
      taskStore.markTaskAsProcessed()

      // Execute task directly without showing content in input box
      nextTick(async () => {
        try {
          console.log('[Direct] Calling handleChatSendMessage with taskContent:', taskContent)
          await handleChatSendMessage({
            input: taskContent,
          })
        } catch (error) {
          console.warn('[Direct] handleChatSendMessage failed, falling back to prompt:', error)
          prompt.value = taskContent
        }
      })
    } else {
      // Task has empty content, just mark it as processed and don't execute
      console.log('[Direct] Task has empty content, marking as processed without execution')
      taskStore.markTaskAsProcessed()
    }
  } else {
    // Check if there is a task to input (for pre-filling input without executing)
    const taskToInput = taskStore.getAndClearTaskToInput()
    if (taskToInput) {
      inputOnlyContent.value = taskToInput
      console.log('[Direct] Setting inputOnlyContent for input only:', inputOnlyContent.value)
      // Don't set prompt.value since this is just for input pre-filling
    } else {
      // Degrade to URL parameters (backward compatibility)
      prompt.value = (route.query.prompt as string) || ''
      console.log('[Direct] Received task from URL:', prompt.value)
      console.log('[Direct] No unprocessed task in store')
    }
  }

  // Restore panel width from localStorage
  const savedWidth = localStorage.getItem('directPanelWidth')
  if (savedWidth) {
    leftPanelWidth.value = parseFloat(savedWidth)
  }

  console.log('[Direct] Final prompt value:', prompt.value)

  // Set input content if there's inputOnlyContent (after component is mounted)
  if (inputOnlyContent.value) {
    nextTick(() => {
      if (inputRef.value && typeof inputRef.value.setInputValue === 'function') {
        inputRef.value.setInputValue(inputOnlyContent.value)
        console.log('[Direct] Set input value:', inputOnlyContent.value)
        inputOnlyContent.value = '' // Clear after setting
      }
    })
  }

  // Listen for plan-execution-requested events on window
  window.addEventListener('plan-execution-requested', ((event: CustomEvent) => {
    console.log('[DirectView] Received plan-execution-requested event:', event.detail)
    handlePlanExecutionRequested(event.detail)
  }) as EventListener)

  // No longer need to check pending plans in sessionStorage
  // Because emitPlanExecutionRequested in task.ts already sends events directly
})

// Listen for changes in the store's task (only handle unprocessed tasks)
watch(
  () => taskStore.currentTask,
  newTask => {
    console.log('[Direct] Watch taskStore.currentTask triggered, newTask:', newTask)
    if (newTask && !newTask.processed && newTask.prompt.trim()) {
      const taskContent = newTask.prompt
      taskStore.markTaskAsProcessed()
      console.log('[Direct] Received new task from store:', taskContent)

      // Execute task directly without showing content in input box
      nextTick(async () => {
        try {
          console.log(
            '[Direct] Directly executing new task via handleChatSendMessage:',
            taskContent
          )
          await handleChatSendMessage({ input: taskContent })
        } catch (error) {
          console.warn('[Direct] handleChatSendMessage failed for new task:', error)
        }
      })
    } else {
      console.log('[Direct] Task is null, already processed, or has empty prompt - ignoring')
    }
  },
  { immediate: false }
)

// Listen for changes in prompt value, only for logging purposes
watch(
  () => prompt.value,
  (newPrompt, oldPrompt) => {
    console.log('[Direct] prompt value changed from:', oldPrompt, 'to:', newPrompt)
    // Prompt is now only used for input field initial value, no automatic execution
  },
  { immediate: false }
)

// Listen for changes in taskToInput (for handling cron task template setting)
watch(
  () => taskStore.taskToInput,
  newTaskToInput => {
    console.log('[Direct] Watch taskStore.taskToInput triggered, newTaskToInput:', newTaskToInput)
    if (newTaskToInput.trim()) {
      console.log('[Direct] Setting input value from taskToInput:', newTaskToInput)
      nextTick(() => {
        if (inputRef.value && typeof inputRef.value.setInputValue === 'function') {
          inputRef.value.setInputValue(newTaskToInput.trim())
          console.log('[Direct] Input value set from taskToInput watch:', newTaskToInput.trim())
          // Clear the taskToInput after setting
          taskStore.getAndClearTaskToInput()
        }
      })
    }
  },
  { immediate: false }
)

onUnmounted(() => {
  console.log('[Direct] onUnmounted called, cleaning up resources')

  // Clear current root plan ID
  currentRootPlanId.value = null

  // Clean up plan execution manager resources
  planExecutionManager.cleanup()

  // Remove event listeners
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  window.removeEventListener('plan-execution-requested', ((event: CustomEvent) => {
    handlePlanExecutionRequested(event.detail)
  }) as EventListener)
})

// Methods related to panel size adjustment
const startResize = (e: MouseEvent) => {
  isResizing.value = true
  startX.value = e.clientX
  startLeftWidth.value = leftPanelWidth.value

  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'

  e.preventDefault()
}

const handleMouseMove = (e: MouseEvent) => {
  if (!isResizing.value) return

  const containerWidth = window.innerWidth
  const deltaX = e.clientX - startX.value
  const deltaPercent = (deltaX / containerWidth) * 100

  let newWidth = startLeftWidth.value + deltaPercent

  // Limit panel width between 20% and 80%
  newWidth = Math.max(20, Math.min(80, newWidth))

  leftPanelWidth.value = newWidth
}

const handleMouseUp = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''

  // Save to localStorage
  localStorage.setItem('directPanelWidth', leftPanelWidth.value.toString())
}

const resetPanelSize = () => {
  leftPanelWidth.value = 50
  localStorage.setItem('directPanelWidth', '50')
}

// Helper function to check if the event should be processed for the current plan
const shouldProcessEventForCurrentPlan = (
  rootPlanId: string,
  allowSpecialIds: boolean = false
): boolean => {
  // If no current plan is set, allow all events (initial state)
  if (!currentRootPlanId.value) {
    return true
  }

  // Check if this event is for the current active plan
  if (rootPlanId === currentRootPlanId.value) {
    return true
  }

  // Allow special IDs for UI state updates (error handling, etc.)
  if (allowSpecialIds && (rootPlanId === 'ui-state' || rootPlanId === 'error')) {
    return true
  }

  // Otherwise, ignore the event
  console.log(
    '[Direct] Ignoring event for non-current rootPlanId:',
    rootPlanId,
    'current:',
    currentRootPlanId.value
  )
  return false
}

// Handle message sending from ChatContainer via event
const handleChatSendMessage = async (query: InputMessage) => {
  let assistantMessage: ChatMessage | null = null

  try {
    console.log('[DirectView] Processing send-message event:', query)

    // Validate input - don't send empty requests to backend
    if (!query.input.trim()) {
      console.warn('[DirectView] Empty input detected, skipping backend request')
      return
    }

    // Add user message to UI
    const userMessage = chatRef.value?.addMessage('user', query.input)
    const extendedQuery = query as ExtendedInputMessage
    if (extendedQuery.attachments && userMessage) {
      chatRef.value?.updateMessage(userMessage.id, { attachments: extendedQuery.attachments })
    }

    // Add assistant thinking message
    assistantMessage = chatRef.value?.addMessage('assistant', '', {
      thinking: t('chat.thinkingProcessing'),
    })

    if (assistantMessage) {
      chatRef.value?.startStreaming(assistantMessage.id)
    }

    // Import and call DirectApiService to send message to backend
    const { DirectApiService } = await import('@/api/direct-api-service')

    // Check if a specific tool is selected (toolName and replacementParams in query)
    let response: ApiResponse

    if (extendedQuery.toolName && extendedQuery.replacementParams) {
      // Execute selected tool (from dialog)
      console.log(
        '[DirectView] Calling DirectApiService.executeByToolName with tool:',
        extendedQuery.toolName
      )
      response = (await DirectApiService.executeByToolName(
        extendedQuery.toolName,
        extendedQuery.replacementParams as Record<string, string>,
        query.uploadedFiles,
        query.uploadKey,
        'VUE_DIALOG'
      )) as ApiResponse
    } else {
      // Use default plan template (from dialog)
      console.log('[DirectView] Calling DirectApiService.sendMessageWithDefaultPlan')
      response = (await DirectApiService.sendMessageWithDefaultPlan(
        query,
        'VUE_DIALOG'
      )) as ApiResponse
    }

    console.log('[DirectView] API response received:', response)

    // Save conversationId from response to memoryStore
    const responseWithConversationId = response as { conversationId?: string }
    if (responseWithConversationId.conversationId) {
      memoryStore.setConversationId(responseWithConversationId.conversationId)
      console.log(
        '[DirectView] Saved conversationId to memoryStore:',
        responseWithConversationId.conversationId
      )
    }

    // Handle the response
    const typedResponse = response as { planId?: string }
    if (typedResponse.planId && assistantMessage) {
      // Plan mode: Update message with plan execution info
      chatRef.value?.updateMessage(assistantMessage.id, {
        thinking: t('chat.planningExecution'),
        planExecution: {
          currentPlanId: typedResponse.planId,
          rootPlanId: typedResponse.planId,
          status: 'running',
        },
      })

      // Set current root plan ID for the new plan execution
      currentRootPlanId.value = typedResponse.planId || null
      console.log('[DirectView] Set currentRootPlanId to:', typedResponse.planId)

      // Start polling for plan updates
      planExecutionManager.handlePlanExecutionRequested(typedResponse.planId!, query.input)
      console.log('[DirectView] Started polling for plan execution updates')
    } else if (assistantMessage) {
      // Direct mode: Show the response
      chatRef.value?.updateMessage(assistantMessage.id, {
        content: response.message || response.result || 'No response received from backend',
      })
      chatRef.value?.stopStreaming(assistantMessage.id)
    }
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    console.error('[DirectView] Send message failed:', errorMessage)

    // Show error message
    chatRef.value?.addMessage('assistant', `Error: ${errorMessage}`)
    if (assistantMessage) {
      chatRef.value?.stopStreaming(assistantMessage.id)
    }
  }
}

// Event handler for input area send button
const handleSendMessage = async (message: InputMessage) => {
  console.log('[DirectView] Send message from input:', JSON.stringify(message))

  // Directly handle the message sending
  await handleChatSendMessage(message)
}

const handleInputClear = () => {
  console.log('[DirectView] Input cleared')
  if (inputRef.value && typeof inputRef.value.clear === 'function') {
    inputRef.value.clear()
  }
}

const handleInputFocus = () => {
  console.log('[DirectView] Input focused')
}

const handleInputUpdateState = (enabled: boolean, placeholder?: string) => {
  console.log('[DirectView] Input state updated:', enabled, placeholder)
  isLoading.value = !enabled
}

const handleStepSelected = (stepId: string) => {
  console.log('[DirectView] Step selected:', stepId)

  // Forward step selection to right panel
  if (rightPanelRef.value && typeof rightPanelRef.value.handleStepSelected === 'function') {
    console.log('[DirectView] Forwarding step selection to right panel:', stepId)
    rightPanelRef.value.handleStepSelected(stepId)
  } else {
    console.warn('[DirectView] rightPanelRef.handleStepSelected method not available')
  }
}

const handleSubPlanStepSelected = (
  parentPlanId: string,
  subPlanId: string,
  stepIndex: number,
  subStepIndex: number
) => {
  console.log('[DirectView] Sub plan step selected:', {
    parentPlanId,
    subPlanId,
    stepIndex,
    subStepIndex,
  })

  // Forward sub plan step selection to right panel
  if (rightPanelRef.value && typeof rightPanelRef.value.handleSubPlanStepSelected === 'function') {
    console.log('[DirectView] Forwarding sub plan step selection to right panel:', {
      parentPlanId,
      subPlanId,
      stepIndex,
      subStepIndex,
    })
    rightPanelRef.value.handleSubPlanStepSelected(parentPlanId, subPlanId, stepIndex, subStepIndex)
  } else {
    console.warn('[DirectView] rightPanelRef.handleSubPlanStepSelected method not available')
  }
}

const handlePlanModeClicked = () => {
  console.log('[DirectView] Plan mode button clicked')
  // Toggle sidebar display state
  sidebarStore.toggleSidebar()
  console.log('[DirectView] Sidebar toggled, isCollapsed:', sidebarStore.isCollapsed)
}

const goBack = () => {
  router.push('/home')
}

const handleConfig = () => {
  router.push('/configs')
}

const handlePlanExecutionRequested = async (payload: PlanExecutionRequestPayload) => {
  console.log('[DirectView] Plan execution requested:', payload)

  // Prevent duplicate execution
  if (isExecutingPlan.value) {
    console.log('[DirectView] Plan execution already in progress, ignoring request')
    return
  }

  isExecutingPlan.value = true

  // Mark whether user message has been added
  let userMessageAdded = false
  let assistantMessage: ChatMessage | null = null

  // Add user and assistant messages using the same pattern as handleChatSendMessage
  try {
    console.log('[DirectView] Adding messages for plan execution:', payload.title)

    // Add user message
    chatRef.value?.addMessage('user', payload.title)
    userMessageAdded = true

    // Add assistant message to show system feedback
    assistantMessage = chatRef.value?.addMessage('assistant', '', {
      thinking: t('chat.planningExecution'),
    })

    if (assistantMessage) {
      chatRef.value?.startStreaming(assistantMessage.id)
      console.log('[DirectView] Added assistant message for plan execution:', assistantMessage.id)
    }
  } catch (messageError) {
    console.warn('[DirectView] Failed to add messages:', messageError)
  }
  try {
    // Get the plan template ID
    const planTemplateId = payload.planData.planTemplateId

    if (!planTemplateId) {
      throw new Error(t('direct.planTemplateIdNotFound'))
    }

    console.log(
      '[Direct] Executing plan with templateId:',
      planTemplateId,
      'params:',
      payload.params
    )

    // Call real API to execute plan
    console.log('[Direct] About to call PlanActApiService.executePlan')

    // Get uploaded files from InputArea component
    const uploadedFiles = payload.uploadedFiles ?? []
    const uploadKey = payload.uploadKey ?? undefined

    console.log('[Direct] 🔍 DEBUG - InputArea uploadedFiles:', uploadedFiles)
    console.log('[Direct] 🔍 DEBUG - InputArea uploadKey:', uploadKey)
    console.log('[Direct] Executing with uploaded files:', uploadedFiles.length)
    console.log('[Direct] Executing with replacement params:', payload.replacementParams)

    let response
    if (payload.params?.trim()) {
      console.log('[Direct] Calling executePlan with rawParam:', payload.params.trim())
      response = await PlanActApiService.executePlan(
        planTemplateId,
        payload.params.trim(),
        uploadedFiles,
        payload.replacementParams,
        uploadKey,
        'VUE_SIDEBAR' // Request from sidebar
      )
    } else {
      console.log('[Direct] Calling executePlan without rawParam')
      response = await PlanActApiService.executePlan(
        planTemplateId,
        undefined,
        uploadedFiles,
        payload.replacementParams,
        uploadKey,
        'VUE_SIDEBAR' // Request from sidebar
      )
    }

    console.log('[Direct] Plan execution API response:', response)

    // Use the returned planId to start the plan execution process
    const executionResponse = response as { planId?: string }
    if (executionResponse.planId && assistantMessage) {
      console.log(
        '[Direct] Got planId from response:',
        executionResponse.planId,
        'starting plan execution'
      )

      // Update assistant message with plan execution info
      chatRef.value?.updateMessage(assistantMessage.id, {
        thinking: t('chat.planningExecution'),
        planExecution: {
          currentPlanId: executionResponse.planId,
          rootPlanId: executionResponse.planId,
          status: 'running',
        },
      })

      // Set current root plan ID for the new plan execution
      currentRootPlanId.value = executionResponse.planId || null
      console.log('[Direct] Set currentRootPlanId to:', executionResponse.planId)

      // Use planExecutionManager to handle plan execution
      console.log('[Direct] Delegating plan execution to planExecutionManager')
      planExecutionManager.handlePlanExecutionRequested(executionResponse.planId!, payload.title)
    } else {
      console.error('[Direct] No planId in response:', response)
      throw new Error(t('direct.executionFailedNoPlanId'))
    }
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    console.error('[Direct] Plan execution failed:', errorMessage)
    const errorStack = error instanceof Error ? error.stack : undefined
    console.error('[Direct] Error details:', { message: errorMessage, stack: errorStack })

    // Clear current root plan ID on error
    currentRootPlanId.value = null

    // Handle error messages using consistent pattern
    try {
      console.log('[Direct] Adding error messages to chat')

      // Only add user message if it hasn't been added before
      if (!userMessageAdded) {
        chatRef.value?.addMessage('user', payload.title)
      }

      // Update assistant message with error or add new error message
      if (assistantMessage) {
        chatRef.value?.updateMessage(assistantMessage.id, {
          content: `${t('direct.executionFailed')}: ${(error as Error).message || t('common.unknownError')}`,
          thinking: undefined,
        })
        chatRef.value?.stopStreaming(assistantMessage.id)
      } else {
        chatRef.value?.addMessage(
          'assistant',
          `${t('direct.executionFailed')}: ${(error as Error).message || t('common.unknownError')}`
        )
      }
    } catch (errorHandlingError) {
      console.error('[Direct] Failed to add error messages:', errorHandlingError)
      // Note: This would need toast import if used in this context
      alert(
        `${t('direct.executionFailed')}: ${(error as Error).message || t('common.unknownError')}`
      )
    }
  } finally {
    console.log('[Direct] Plan execution finished, resetting isExecutingPlan flag')
    isExecutingPlan.value = false
  }
}

const memorySelected = async () => {
  // Memory sidebar is already closed by selectMemory() calling toggleSidebar()
  // Load conversation history if a memory is selected
  if (memoryStore.selectMemoryId) {
    console.log('[DirectView] Memory selected:', memoryStore.selectMemoryId)

    try {
      // Clear current chat first
      if (chatRef.value) {
        chatRef.value.clearMessages()
      }

      // Set the conversation ID in memory store
      memoryStore.setConversationId(memoryStore.selectMemoryId)

      // Fetch conversation history from backend
      const historyRecords = await MemoryApiService.getConversationHistory(
        memoryStore.selectMemoryId
      )
      console.log('[DirectView] Loaded conversation history:', historyRecords)

      // Convert each PlanExecutionRecord to chat messages and display them
      for (const record of historyRecords) {
        if (!record) continue

        // Add user message (the original query)
        if (record.userRequest && chatRef.value && record.startTime) {
          const userMessage = chatRef.value.addMessage('user', record.userRequest, {
            timestamp: new Date(record.startTime),
          })
          console.log('[DirectView] Added user message:', userMessage)
        }

        // Add assistant message (the result/summary)
        if (chatRef.value && record.currentPlanId) {
          const assistantContent =
            record.summary || record.result || record.message || 'Execution completed'

          // Convert API record to plan execution record format
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

          const assistantMessage = chatRef.value.addMessage('assistant', assistantContent, {
            timestamp:
              record.endTime && record.endTime
                ? new Date(record.endTime)
                : record.startTime
                  ? new Date(record.startTime)
                  : new Date(),
            planExecution: planExecutionRecord as PlanExecutionRecord,
          })
          console.log('[DirectView] Added assistant message:', assistantMessage)

          // Store the plan record in the plan execution manager cache for future reference
          if (record.rootPlanId) {
            planExecutionManager.setCachedPlanRecord(
              record.rootPlanId,
              planExecutionRecord as PlanExecutionRecord
            )
          }
        }
      }

      // Scroll to bottom after loading history
      await nextTick()
      if (chatRef.value) {
        chatRef.value.scrollToBottom()
      }

      console.log(
        '[DirectView] Successfully loaded conversation history with',
        historyRecords.length,
        'dialog rounds'
      )
    } catch (error) {
      console.error('[DirectView] Failed to load conversation history:', error)
      showMessage(t('memory.loadHistoryFailed'), 'error')
    }
  }
}

const newChat = () => {
  memoryStore.clearMemoryId()
  memoryStore.clearConversationId()
  if (chatRef.value) {
    chatRef.value.clearMessages()
  }
}
</script>

<style lang="less" scoped>
.direct-page {
  width: 100%;
  display: flex;
  position: relative;
}

.direct-chat {
  height: 100vh;
  width: 100%;
  background: #0a0a0a;
  display: flex;
}

.left-panel {
  position: relative;
  border-right: none; /* Remove the original border, which will be provided by the resizer */
  display: flex;
  flex-direction: column;
  height: 100vh; /* Use fixed height */
  overflow: hidden; /* Prevent panel itself overflow */
  transition: width 0.1s ease; /* Smooth transition */
}

.panel-resizer {
  width: 6px;
  height: 100vh;
  background: #1a1a1a;
  cursor: col-resize;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s ease;
  flex-shrink: 0;

  &:hover {
    background: #2a2a2a;

    .resizer-line {
      background: #4a90e2;
      width: 2px;
    }
  }

  &:active {
    background: #3a3a3a;
  }
}

.resizer-line {
  width: 1px;
  height: 40px;
  background: #3a3a3a;
  border-radius: 1px;
  transition: all 0.2s ease;
}

/* Adjust right panel styles */
:deep(.right-panel) {
  transition: width 0.1s ease;
}

.chat-header {
  padding: 20px 24px;
  border-bottom: 1px solid #1a1a1a;
  display: flex;
  align-items: center;
  gap: 16px;
  background: rgba(255, 255, 255, 0.02);
  flex-shrink: 0; /* Ensure the header will not be compressed */
  position: sticky; /* Fix the header at the top */
  top: 0;
  z-index: 100;

  h2 {
    flex: 1;
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: #ffffff;
  }
}

.chat-content {
  flex: 1; /* Occupy remaining space */
  display: flex;
  flex-direction: column;
  min-height: 0; /* Allow shrink */
  overflow: hidden; /* Prevent overflow */
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-button {
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
  }
}

.config-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
  }
}

.cron-task-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.2);
  }
}

.loading-prompt {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888888;
  font-size: 16px;
  padding: 50px;
}

/* Message toast styles */
.message-toast {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 9999;
  min-width: 320px;
  max-width: 480px;
  padding: 16px 20px;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: space-between;
  animation: slideInRight 0.3s ease-out;
  font-size: 14px;
  font-weight: 500;
}

.message-toast.error {
  color: #fff2f0;
  background-color: #ff4d4f;
}

.message-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  word-break: break-all;
}

.message-content i {
  font-size: 16px;
}
</style>
