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
  <div class="input-area">
    <!-- File upload component at the top, full width -->
    <FileUploadComponent
      ref="fileUploadRef"
      :disabled="isDisabled"
      @files-uploaded="handleFilesUploaded"
      @files-removed="handleFilesRemoved"
      @upload-key-changed="handleUploadKeyChanged"
      @upload-started="handleUploadStarted"
      @upload-completed="handleUploadCompleted"
      @upload-error="handleUploadError"
    />

    <div class="input-container">
      <!-- First line: User input form -->
      <div class="input-row-first">
        <textarea
          v-model="currentInput"
          ref="inputRef"
          class="chat-input"
          :placeholder="currentPlaceholder"
          :disabled="isDisabled"
          @keydown="handleKeydown"
          @input="adjustInputHeight"
        ></textarea>
      </div>

      <!-- Second line: Selection input, Func-Agent mode and send button -->
      <div class="input-row-second">
        <select
          v-model="selectedOption"
          class="selection-input"
          :title="$t('input.selectionTitle')"
          :disabled="isLoadingTools || isDisabled"
        >
          <option value="">{{ $t('input.defaultFuncAgent') }}</option>
          <option v-for="option in selectionOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
        <button class="plan-mode-btn" :title="$t('input.planMode')" @click="handlePlanModeClick">
          <Icon icon="carbon:document" />
          {{ $t('input.planMode') }}
        </button>
        <button
          v-if="!isTaskRunning"
          class="send-button"
          :disabled="!currentInput.trim() || isDisabled"
          @click="handleSend"
          :title="$t('input.send')"
        >
          <Icon icon="carbon:send-alt" />
          {{ $t('input.send') }}
        </button>
        <button
          v-else
          class="send-button stop-button"
          @click="handleStop"
          :title="$t('input.stop')"
        >
          <Icon icon="carbon:stop-filled" />
          {{ $t('input.stop') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FileInfo } from '@/api/file-upload-api-service'
import FileUploadComponent from '@/components/file-upload/FileUploadComponent.vue'
import { useMessageDialogSingleton } from '@/composables/useMessageDialog'
import { usePlanExecutionSingleton } from '@/composables/usePlanExecution'
import { usePlanTemplateConfigSingleton } from '@/composables/usePlanTemplateConfig'
import { memoryStore } from '@/stores/memory'
import { sidebarStore } from '@/stores/sidebar'
import { useTaskStore } from '@/stores/task'
import type { InputMessage } from '@/types/message-dialog'
import { Icon } from '@iconify/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const taskStore = useTaskStore()
const templateConfig = usePlanTemplateConfigSingleton()
const messageDialog = useMessageDialogSingleton()
const planExecution = usePlanExecutionSingleton()

// Track if task is running
const isTaskRunning = computed(() => taskStore.hasRunningTask())

interface Props {
  initialValue?: string
  selectionOptions?: Array<{ value: string; label: string }>
}

interface InnerToolOption {
  value: string
  label: string
  toolName: string
  planTemplateId: string
  paramName: string
}

interface Emits {
  (e: 'selection-changed', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  initialValue: '',
  selectionOptions: () => [],
})

const emit = defineEmits<Emits>()

const inputRef = ref<HTMLTextAreaElement>()
const fileUploadRef = ref<InstanceType<typeof FileUploadComponent>>()
const currentInput = ref('')
const defaultPlaceholder = computed(() => t('input.placeholder'))
const fileUploadPlaceholder = ref<string | null>(null)
const currentPlaceholder = computed(() => {
  // Priority: messageDialog inputPlaceholder > fileUploadPlaceholder > default
  if (messageDialog.inputPlaceholder.value) {
    return messageDialog.inputPlaceholder.value
  }
  if (fileUploadPlaceholder.value) {
    return fileUploadPlaceholder.value
  }
  return defaultPlaceholder.value
})
const uploadedFiles = ref<string[]>([])
const uploadKey = ref<string | null>(null)
const selectedOption = ref('')
const innerToolOptions = ref<InnerToolOption[]>([])
const isLoadingTools = ref(false)

// History management
const HISTORY_STORAGE_KEY = 'chatInputHistory'
const MAX_HISTORY_SIZE = 20
const inputHistory = ref<string[]>([])
const historyIndex = ref(-1) // -1 means not browsing history
const originalInputBeforeHistory = ref('') // Store original input when starting to browse history

// Detect operating system for platform-specific shortcuts
const isMac = computed(() => {
  if (typeof window === 'undefined') return false
  return /Mac|iPhone|iPad|iPod/.test(navigator.platform) || /Mac/.test(navigator.userAgent)
})

// Load inner tools with single parameter
const loadInnerTools = async () => {
  isLoadingTools.value = true
  try {
    console.log('[InputArea] Loading inner tools from planTemplateList...')
    const allTools = templateConfig.getAllCoordinatorToolsFromTemplates()

    // Filter tools: enableInternalToolcall=true and exactly one parameter
    const filteredTools: InnerToolOption[] = []

    for (const tool of allTools) {
      // Check if it's an internal toolcall
      if (!tool.enableInternalToolcall) {
        continue
      }

      // Parse inputSchema to count parameters
      try {
        const inputSchema = JSON.parse(tool.inputSchema || '[]')
        if (Array.isArray(inputSchema) && inputSchema.length === 1) {
          // Exactly one parameter
          const param = inputSchema[0]
          filteredTools.push({
            value: tool.planTemplateId,
            label: `${tool.toolName} (${param.name})`,
            toolName: tool.toolName,
            planTemplateId: tool.planTemplateId,
            paramName: param.name,
          })
        }
      } catch (e) {
        console.warn('[InputArea] Failed to parse inputSchema for tool:', tool.toolName, e)
      }
    }

    innerToolOptions.value = filteredTools
    console.log('[InputArea] Loaded', filteredTools.length, 'inner tools with single parameter')

    // Restore selected tool from localStorage and validate it still exists
    const savedTool = localStorage.getItem('inputAreaSelectedTool')
    if (savedTool) {
      const toolExists = filteredTools.some(tool => tool.value === savedTool)
      if (toolExists) {
        selectedOption.value = savedTool
        console.log('[InputArea] Restored selected tool from localStorage:', savedTool)
      } else {
        console.log('[InputArea] Saved tool no longer available, clearing selection')
        localStorage.removeItem('inputAreaSelectedTool')
        selectedOption.value = ''
      }
    }
  } catch (error) {
    console.error('[InputArea] Failed to load inner tools:', error)
    innerToolOptions.value = []
  } finally {
    isLoadingTools.value = false
  }
}

// Computed property for selection options (use inner tools if available, otherwise use props)
const selectionOptions = computed(() => {
  if (innerToolOptions.value.length > 0) {
    return innerToolOptions.value.map(tool => ({
      value: tool.value,
      label: tool.label,
    }))
  }
  return props.selectionOptions
})

// Watch for selection changes and persist to localStorage
watch(selectedOption, newValue => {
  emit('selection-changed', newValue)
  // Save to localStorage
  localStorage.setItem('inputAreaSelectedTool', newValue || '')
})

// History management functions
const loadHistory = () => {
  try {
    const stored = localStorage.getItem(HISTORY_STORAGE_KEY)
    if (stored) {
      inputHistory.value = JSON.parse(stored)
    }
  } catch (error) {
    console.error('[InputArea] Failed to load history:', error)
    inputHistory.value = []
  }
}

const saveHistory = () => {
  try {
    localStorage.setItem(HISTORY_STORAGE_KEY, JSON.stringify(inputHistory.value))
  } catch (error) {
    console.error('[InputArea] Failed to save history:', error)
  }
}

const saveToHistory = (input: string) => {
  if (!input.trim()) return

  // Remove duplicate if exists
  const index = inputHistory.value.indexOf(input)
  if (index !== -1) {
    inputHistory.value.splice(index, 1)
  }

  // Add to the beginning
  inputHistory.value.unshift(input)

  // Keep only the last MAX_HISTORY_SIZE items
  if (inputHistory.value.length > MAX_HISTORY_SIZE) {
    inputHistory.value = inputHistory.value.slice(0, MAX_HISTORY_SIZE)
  }

  saveHistory()
}

const navigateHistory = (direction: number) => {
  if (inputHistory.value.length === 0) return

  // If starting to browse history, save current input
  if (historyIndex.value === -1) {
    originalInputBeforeHistory.value = currentInput.value
  }

  // Calculate new index
  let newIndex = historyIndex.value + direction

  if (newIndex < -1) {
    newIndex = -1
  } else if (newIndex >= inputHistory.value.length) {
    newIndex = inputHistory.value.length - 1
  }

  historyIndex.value = newIndex

  // Update input based on history index
  if (historyIndex.value === -1) {
    // Restore original input
    currentInput.value = originalInputBeforeHistory.value
    originalInputBeforeHistory.value = ''
  } else {
    currentInput.value = inputHistory.value[historyIndex.value]
  }

  adjustInputHeight()
}

// Load inner tools on mount
onMounted(() => {
  loadInnerTools()
  loadHistory()

  // Watch for plan completion to reset session (reactive approach)
  watch(
    () => planExecution.planExecutionRecords,
    records => {
      // Check if any tracked plan is completed
      for (const planDetails of records.values()) {
        if (planDetails.completed) {
          console.log('[InputArea] Plan completed, resetting session')
          resetSession()
          // Only reset once per completion
          break
        }
      }
    },
    { deep: true }
  )

  // Watch for taskToInput changes and set input value automatically
  watch(
    () => taskStore.taskToInput,
    newTaskToInput => {
      if (newTaskToInput?.trim()) {
        console.log('[InputArea] taskToInput changed, setting input value:', newTaskToInput)
        nextTick(() => {
          setInputValue(newTaskToInput.trim())
          taskStore.getAndClearTaskToInput()
        })
      }
    },
    { immediate: false }
  )
})

// Function to reset session when starting a new conversation session
const resetSession = () => {
  console.log('[FileUpload] Resetting session and clearing uploadKey')
  fileUploadRef.value?.resetSession()
}

// Auto-reset session when component is unmounted to prevent memory leaks
onUnmounted(() => {
  resetSession()
})
// File upload event handlers
const handleFilesUploaded = (files: FileInfo[], key: string | null) => {
  uploadedFiles.value = files.map(file => file.originalName)
  uploadKey.value = key
  console.log('[InputArea] Files uploaded:', files.length, 'uploadKey:', key)

  // Update placeholder to show files are attached
  if (uploadedFiles.value.length > 0) {
    fileUploadPlaceholder.value = t('input.filesAttached', { count: uploadedFiles.value.length })
  }
}

const handleFilesRemoved = (files: FileInfo[]) => {
  uploadedFiles.value = files.map(file => file.originalName)
  console.log('[InputArea] Files removed, remaining:', files.length)

  // Update placeholder
  if (uploadedFiles.value.length === 0) {
    fileUploadPlaceholder.value = null
  } else {
    fileUploadPlaceholder.value = t('input.filesAttached', { count: uploadedFiles.value.length })
  }
}

const handleUploadKeyChanged = (key: string | null) => {
  uploadKey.value = key
  console.log('[InputArea] Upload key changed:', key)
}

const handleUploadStarted = () => {
  console.log('[InputArea] Upload started')
}

const handleUploadCompleted = () => {
  console.log('[InputArea] Upload completed')
}

const handleUploadError = (error: unknown) => {
  console.error('[InputArea] Upload error:', error)
}

// Computed property for disabled state - use messageDialog isLoading
const isDisabled = computed(() => messageDialog.isLoading.value)

const adjustInputHeight = () => {
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
      inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 120) + 'px'
    }
  })
}

const handleKeydown = (event: KeyboardEvent) => {
  // Ctrl+Enter (Windows/Linux) or Cmd+Enter (Mac) to send
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
    event.preventDefault()
    handleSend()
    return
  }

  // Platform-specific history navigation:
  // Mac: Option+ArrowUp/Down
  // Windows/Linux: Ctrl+ArrowUp/Down
  // ArrowUp: go to newer (index 0, 1, 2...)
  // ArrowDown: go to older (index -1, then older items)
  const historyModifier = isMac.value ? event.altKey : event.ctrlKey

  if (event.key === 'ArrowUp' && historyModifier) {
    event.preventDefault()
    navigateHistory(1) // Go to newer (next in history array)
    return
  }

  if (event.key === 'ArrowDown' && historyModifier) {
    event.preventDefault()
    navigateHistory(-1) // Go to older (previous in history array)
    return
  }

  // If user starts typing while browsing history, reset history index
  if (
    historyIndex.value !== -1 &&
    event.key.length === 1 &&
    !event.ctrlKey &&
    !event.metaKey &&
    !event.altKey
  ) {
    historyIndex.value = -1
    originalInputBeforeHistory.value = ''
  }
}

const handleSend = async () => {
  if (!currentInput.value.trim() || isDisabled.value) return

  const finalInput = currentInput.value.trim()

  // Save to history before sending
  saveToHistory(finalInput)

  // Reset history browsing state
  historyIndex.value = -1
  originalInputBeforeHistory.value = ''

  // Prepare query with tool information if selected
  const query: InputMessage = {
    input: finalInput,
    memoryId: memoryStore.selectMemoryId,
    uploadedFiles: uploadedFiles.value,
  }

  // Add uploadKey if it exists
  if (uploadKey.value) {
    query.uploadKey = uploadKey.value
    console.log('[InputArea] Including uploadKey in message:', uploadKey.value)
  } else {
    console.log('[InputArea] No uploadKey available for message')
  }

  // Check if a tool is selected
  if (selectedOption.value) {
    const selectedTool = innerToolOptions.value.find(tool => tool.value === selectedOption.value)

    if (selectedTool) {
      // Add tool information to query for backend processing
      const extendedQuery = query as InputMessage & {
        toolName?: string
        replacementParams?: Record<string, string>
      }
      extendedQuery.toolName = selectedTool.planTemplateId
      extendedQuery.replacementParams = {
        [selectedTool.paramName]: finalInput,
      }
      console.log('[InputArea] Sending message with tool:', selectedTool.toolName)
    }
  }

  // Call sendMessage from useMessageDialog directly
  try {
    await messageDialog.sendMessage(query)
  } catch (error: unknown) {
    const errorMessage = error instanceof Error ? error.message : String(error)
    console.error('[InputArea] Send message failed:', errorMessage)
  }

  // Clear the input but keep uploaded files and uploadKey for follow-up conversations
  clearInput()
}

const handlePlanModeClick = () => {
  // Toggle sidebar display state
  sidebarStore.toggleSidebar()
  console.log(
    '[InputArea] Plan mode button clicked, sidebar toggled, isCollapsed:',
    sidebarStore.isCollapsed
  )
}

const handleStop = async () => {
  console.log('[InputArea] Stop button clicked')
  const success = await taskStore.stopCurrentTask()
  if (success) {
    console.log('[InputArea] Task stopped successfully')
  } else {
    console.error('[InputArea] Failed to stop task')
  }
}

/**
 * Clear the input box
 */
const clearInput = () => {
  currentInput.value = ''
  adjustInputHeight()
}

/**
 * Update the state of the input area (enable/disable)
 * @param {boolean} enabled - Whether to enable input
 * @param {string} [placeholder] - Placeholder text when enabled
 */
const updateState = (enabled: boolean, placeholder?: string) => {
  // Update state in useMessageDialog (which will update inputPlaceholder)
  messageDialog.updateInputState(enabled, placeholder)
}

/**
 * Set the input value without triggering send
 * @param {string} value - The value to set
 */
const setInputValue = (value: string) => {
  currentInput.value = value
  adjustInputHeight()
}

/**
 * Get the current value of the input box
 * @returns {string} The text value of the current input box (trimmed)
 */
const getQuery = () => {
  return currentInput.value.trim()
}

// Watch for initialValue changes
watch(
  () => props.initialValue,
  newValue => {
    if (newValue.trim()) {
      currentInput.value = newValue
      adjustInputHeight()
    }
  },
  { immediate: true }
)

// Expose methods to the parent component (if needed)
defineExpose({
  clearInput,
  updateState,
  getQuery,
  focus: () => inputRef.value?.focus(),
  get uploadedFiles() {
    return fileUploadRef.value?.uploadedFiles?.map(f => f.originalName) || []
  },
  get uploadKey() {
    return fileUploadRef.value?.uploadKey || null
  },
})
</script>

<style lang="less" scoped>
.input-area {
  min-height: 112px;
  padding: 10px 12px;
  border-top: 1px solid #1a1a1a;
  background: rgba(255, 255, 255, 0.02);
  /* Ensure the input area is always at the bottom */
  flex-shrink: 0; /* Won't be compressed */
  position: sticky; /* Fixed at the bottom */
  bottom: 0;
  z-index: 100;
  /* Add a slight shadow to distinguish the message area */
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(20px);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px 16px;

  &:focus-within {
    border-color: #667eea;
  }
}

.input-row-first {
  display: flex;
  align-items: center;
  width: 100%;
}

.input-row-second {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.selection-input {
  flex-shrink: 0;
  padding: 6px 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  outline: none;
  width: 24ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.3);
  }

  &:focus {
    border-color: #667eea;
    background: rgba(255, 255, 255, 0.08);
  }

  option {
    background: #1a1a1a;
    color: #ffffff;
    white-space: normal;
    padding: 4px 8px;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.chat-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #ffffff;
  font-size: 14px;
  line-height: 1.5;
  resize: none;
  min-height: 20px;
  max-height: 120px;

  &::placeholder {
    color: #666666;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;

    &::placeholder {
      color: #444444;
    }
  }
}

.plan-mode-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: #667eea;
    transform: translateY(-1px);
  }
}

.send-button {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #ffffff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &.stop-button {
    background: linear-gradient(135deg, #f56565 0%, #c53030 100%);

    &:hover {
      background: linear-gradient(135deg, #fc8181 0%, #e53e3e 100%);
    }
  }
}

.clear-memory-btn {
  width: 1.5em;
  height: 1.5em;
}
</style>
