<!--
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
-->
<template>
  <div class="chat-container">
    <!-- Messages container -->
    <div
      class="messages"
      ref="messagesRef"
      @scroll="handleScroll"
      @click="handleMessageContainerClick"
    >
      <!-- Message list -->
      <div
        v-for="message in compatibleMessages"
        :key="message.id"
        class="chat-message"
        :class="[getMessageClasses(message), { streaming: isMessageStreaming(message.id) }]"
      >
        <!-- User message -->
        <div v-if="message.type === 'user'" class="user-message" :data-message-id="message.id">
          <div class="user-content">
            <div class="message-text">
              {{ message.content }}
            </div>

            <!-- Attachments if any -->
            <div v-if="message.attachments?.length" class="attachments">
              <div
                v-for="(attachment, index) in message.attachments"
                :key="index"
                class="attachment-item"
              >
                <Icon icon="carbon:document" class="attachment-icon" />
                <span class="attachment-name">{{ attachment.name }}</span>
                <span class="attachment-size">{{ formatFileSize(attachment.size) }}</span>
              </div>
            </div>

            <!-- Timestamp -->
            <div class="message-timestamp">
              {{ formatTimestamp(message.timestamp) }}
            </div>
          </div>

          <!-- Message status indicator -->
          <div v-if="message.error" class="message-status error">
            <Icon icon="carbon:warning" class="status-icon" />
            <span class="status-text">{{ message.error }}</span>
          </div>
        </div>

        <!-- Assistant message -->
        <div v-else-if="message.type === 'assistant'" class="assistant-message">
          <!-- Thinking section (when available) -->
          <ThinkingSection
            v-if="message.thinkingDetails"
            :thinking-details="message.thinkingDetails"
            @step-selected="handleStepSelected"
          />

          <!-- Plan execution section (when available) -->
          <ExecutionDetails
            v-if="message.planExecution"
            :plan-execution="message.planExecution"
            :step-actions="message.stepActions || []"
            :generic-input="message.genericInput || ''"
            @step-selected="handleStepSelected"
          />

          <!-- Response section -->
          <ResponseSection
            v-if="
              message.content ||
              message.error ||
              isMessageStreaming(message.id) ||
              message.planExecution?.userInputWaitState?.waiting
            "
            :content="message.content || ''"
            :is-streaming="isMessageStreaming(message.id) || false"
            v-bind="{
              ...(message.error ? { error: message.error } : {}),
              ...(message.planExecution?.userInputWaitState
                ? { userInputWaitState: message.planExecution.userInputWaitState }
                : {}),
              ...(message.planExecution?.currentPlanId
                ? { planId: message.planExecution.currentPlanId }
                : {}),
            }"
            :timestamp="message.timestamp"
            :generic-input="message.genericInput || ''"
            @copy="() => handleCopyMessage(message.id)"
            @regenerate="() => handleRegenerateMessage(message.id)"
            @retry="() => handleRetryMessage(message.id)"
            @user-input-submitted="
              (inputData: Record<string, unknown>) => handleUserInputSubmit(message, inputData)
            "
          />
        </div>
      </div>

      <!-- Loading indicator -->
      <div v-if="isLoading" class="loading-message">
        <div class="loading-content">
          <Icon icon="carbon:circle-dash" class="loading-icon" />
          <span>{{ $t('chat.processing') }}</span>
        </div>
      </div>
    </div>

    <!-- Scroll to bottom button -->
    <Transition name="scroll-button">
      <button
        v-if="showScrollToBottom"
        class="scroll-to-bottom"
        @click="() => scrollToBottom()"
        :title="$t('chat.scrollToBottom')"
      >
        <Icon icon="carbon:chevron-down" />
      </button>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

// Import new modular components
import ExecutionDetails from './ExecutionDetails.vue'
import ResponseSection from './ResponseSection.vue'
import ThinkingSection from './ThinkingSection.vue'

// Import composables
import { useMessageDialogSingleton } from '@/composables/useMessageDialog'
import type { ChatMessage as ChatMessageType } from '@/types/message-dialog'
import { useMessageFormatting } from './composables/useMessageFormatting'
import { useScrollBehavior } from './composables/useScrollBehavior'

// Import plan execution manager
import { usePlanExecutionSingleton } from '@/composables/usePlanExecution'

interface Emits {
  (e: 'step-selected', stepId: string): void
}

// InputMessage interface removed - not needed in display component
// eslint-disable-next-line @typescript-eslint/no-empty-object-type
interface Props {}

defineProps<Props>()

const emit = defineEmits<Emits>()

// Initialize composables
const { t } = useI18n()

// Message dialog state
const messageDialog = useMessageDialogSingleton()
const { messages, isLoading, streamingMessageId, updateMessage, startStreaming, findMessage } =
  messageDialog

// Plan execution manager
const planExecution = usePlanExecutionSingleton()

// Scroll behavior
const messagesRef = ref<HTMLElement | null>(null)
const { scrollToBottom, autoScrollToBottom, showScrollToBottom } = useScrollBehavior(messagesRef)

// Message formatting
const { getMessageClasses, formatTimestamp, formatFileSize } = useMessageFormatting()

// Local state
const pollingInterval = ref<number>()

// Computed properties
const isMessageStreaming = (messageId: string) => {
  return streamingMessageId.value === messageId
}

// Messages are already in compatible format (useMessageDialog handles conversion)
const compatibleMessages = computed(() => messages.value)

// Event handlers
const handleScroll = () => {
  // Scroll behavior is handled by useScrollBehavior composable
}

const handleMessageContainerClick = (event: Event) => {
  // Handle markdown copy buttons and other click events
  const target = event.target as HTMLElement

  if (target.classList.contains('md-copy-btn')) {
    const rawText = target.getAttribute('data-raw')
    if (rawText) {
      const text = decodeURIComponent(rawText)
      navigator.clipboard.writeText(text).then(() => {
        // Show copy feedback
        target.textContent = t('chat.copied')
        setTimeout(() => {
          target.textContent = t('chat.copy')
        }, 1000)
      })
    }
  }
}

const handleCopyMessage = async (messageId: string) => {
  const message = findMessage(messageId)
  if (!message) return

  try {
    // Strip HTML tags for clipboard
    const plainText = message.content.replace(/<[^>]*>/g, '')
    await navigator.clipboard.writeText(plainText)
    // Could add toast notification here
  } catch (error) {
    console.error('Failed to copy message:', error)
  }
}

const handleRegenerateMessage = (messageId: string) => {
  // Implementation for regenerating assistant response
  const message = findMessage(messageId)
  if (message && message.type === 'assistant') {
    // Reset message content and restart generation
    updateMessage(messageId, {
      content: '',
    })
    startStreaming(messageId)
    // Trigger regeneration logic here
  }
}

const handleRetryMessage = (messageId: string) => {
  // Implementation for retrying failed message
  const message = findMessage(messageId)
  if (message) {
    updateMessage(messageId, {
      content: '',
    })
    startStreaming(messageId)
    // Trigger retry logic here
  }
}

const handleStepSelected = (stepId: string) => {
  console.log('[ChatContainer] Step selected:', stepId)
  emit('step-selected', stepId)
}

const handleUserInputSubmit = (message: ChatMessageType, inputData: Record<string, unknown>) => {
  console.log('[ChatContainer] User input submitted:', inputData, 'for message:', message.id)
  // Handle user input submission - can be extended for more functionality
}

// Message handling methods removed - ChatContainer is now a pure display component

// Watch for plan execution record changes (reactive approach)
watch(
  () => planExecution.planExecutionRecords,
  records => {
    // Process all records in the map
    for (const [planId, planDetails] of records.entries()) {
      // Find the corresponding message
      const messageIndex = messages.value.findIndex(
        m => m.planExecution?.rootPlanId === planId && m.type === 'assistant'
      )

      if (messageIndex === -1) {
        continue
      }

      const message = messages.value[messageIndex]

      // Update planExecution data using updateMessage
      const updates: Partial<ChatMessageType> = {
        planExecution: JSON.parse(JSON.stringify(planDetails)),
      }

      // Handle simple responses (cases without agent execution sequence)
      if (!planDetails.agentExecutionSequence || planDetails.agentExecutionSequence.length === 0) {
        if (planDetails.completed) {
          // Clear thinking state and set final response
          updates.thinking = ''
          const finalResponse =
            planDetails.summary ??
            planDetails.result ??
            planDetails.message ??
            'Execution completed'
          updates.content = finalResponse
        }
      }

      // Handle errors
      if (planDetails.status === 'failed' && planDetails.message) {
        updates.content = `Error: ${planDetails.message}`
        updates.thinking = ''
      }

      // Update the message
      updateMessage(message.id, updates)
    }
  },
  { deep: true, immediate: true }
)

// Scroll handlers (remove unused function)

// Lifecycle
onMounted(() => {
  // Initial prompt processing removed - handled by parent component

  // Auto-scroll to bottom when new messages are added
  watch(
    messages,
    () => {
      nextTick(() => {
        autoScrollToBottom()
      })
    },
    { deep: true }
  )
})

onUnmounted(() => {
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
  }
})
</script>

<style lang="less" scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  background: #1a1a1a;

  .messages {
    flex: 1;
    overflow-y: auto;
    padding: 20px;
    scroll-behavior: smooth;

    // Custom scrollbar
    &::-webkit-scrollbar {
      width: 6px;
    }

    &::-webkit-scrollbar-track {
      background: rgba(255, 255, 255, 0.1);
      border-radius: 3px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(255, 255, 255, 0.3);
      border-radius: 3px;

      &:hover {
        background: rgba(255, 255, 255, 0.5);
      }
    }
  }

  .chat-message {
    width: 100%;

    &.streaming {
      position: relative;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 0;
        right: 0;
        height: 2px;
        background: linear-gradient(90deg, transparent, #4f46e5, transparent);
        animation: streaming-pulse 2s ease-in-out infinite;
      }
    }
  }

  .user-message {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    margin-bottom: 16px;

    .user-content {
      max-width: 70%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: #ffffff;
      padding: 12px 16px;
      border-radius: 18px 18px 4px 18px;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
      position: relative;

      .message-text {
        word-wrap: break-word;
        white-space: pre-wrap;
        line-height: 1.5;
        font-size: 14px;
      }

      .attachments {
        margin-top: 8px;

        .attachment-item {
          display: flex;
          align-items: center;
          gap: 6px;
          padding: 6px 8px;
          background: rgba(255, 255, 255, 0.1);
          border-radius: 8px;
          margin-bottom: 4px;
          font-size: 12px;

          &:last-child {
            margin-bottom: 0;
          }

          .attachment-icon {
            font-size: 14px;
            color: rgba(255, 255, 255, 0.8);
          }

          .attachment-name {
            flex: 1;
            color: #ffffff;
          }

          .attachment-size {
            color: rgba(255, 255, 255, 0.7);
            font-size: 11px;
          }
        }
      }

      .message-timestamp {
        margin-top: 6px;
        font-size: 11px;
        color: rgba(255, 255, 255, 0.7);
        text-align: right;
      }
    }

    .message-status {
      margin-top: 4px;
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 12px;

      &.error {
        color: #ff6b6b;

        .status-icon {
          font-size: 14px;
        }
      }
    }
  }

  .assistant-message {
    margin-bottom: 24px;

    // Add spacing between thinking and response sections
    > * + * {
      margin-top: 16px;
    }
  }

  .loading-message {
    display: flex;
    justify-content: center;
    padding: 20px;

    .loading-content {
      display: flex;
      align-items: center;
      gap: 12px;
      color: #aaaaaa;
      font-size: 14px;

      .loading-icon {
        font-size: 16px;
        animation: spin 1s linear infinite;
      }
    }
  }

  .scroll-to-bottom {
    position: absolute;
    bottom: 30px;
    right: 30px;
    width: 40px;
    height: 40px;
    background: rgba(79, 70, 229, 0.9);
    border: none;
    border-radius: 50%;
    color: #ffffff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    transition: all 0.2s ease;
    z-index: 10;

    &:hover {
      background: rgba(79, 70, 229, 1);
      transform: scale(1.1);
    }

    svg {
      font-size: 20px;
    }
  }
}

// Transitions
.scroll-button-enter-active,
.scroll-button-leave-active {
  transition: all 0.3s ease;
}

.scroll-button-enter-from,
.scroll-button-leave-to {
  opacity: 0;
  transform: translateY(20px) scale(0.8);
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes streaming-pulse {
  0% {
    transform: translateX(-100%);
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  100% {
    transform: translateX(100%);
    opacity: 0;
  }
}

@media (max-width: 768px) {
  .chat-container {
    .messages {
      padding: 16px;
    }

    .scroll-to-bottom {
      bottom: 20px;
      right: 20px;
      width: 36px;
      height: 36px;

      svg {
        font-size: 18px;
      }
    }
  }
}
</style>
