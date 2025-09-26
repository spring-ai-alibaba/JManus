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
      <textarea
        v-model="currentInput"
        ref="inputRef"
        class="chat-input"
        :placeholder="currentPlaceholder"
        :disabled="isDisabled"
        @keydown="handleKeydown"
        @input="adjustInputHeight"
      ></textarea>
      <button class="plan-mode-btn" :title="$t('input.planMode')" @click="handlePlanModeClick">
        <Icon icon="carbon:document" />
        {{ $t('input.planMode') }}
      </button>
      <button
        class="send-button"
        :disabled="!currentInput.trim() || isDisabled"
        @click="handleSend"
        :title="$t('input.send')"
      >
        <Icon icon="carbon:send-alt" />
        {{ $t('input.send') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { useI18n } from 'vue-i18n'
import { memoryStore } from "@/stores/memory"
import type { InputMessage } from "@/stores/memory"
import { FileInfo } from "@/api/file-upload-api-service"
import FileUploadComponent from "@/components/file-upload/FileUploadComponent.vue"

const { t } = useI18n()

interface Props {
  placeholder?: string
  disabled?: boolean
  initialValue?: string
}

interface Emits {
  (e: 'send', message: InputMessage): void
  (e: 'clear'): void
  (e: 'update-state', enabled: boolean, placeholder?: string): void
  (e: 'plan-mode-clicked'): void
}



const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  disabled: false,
  initialValue: '',
})

const emit = defineEmits<Emits>()

const inputRef = ref<HTMLTextAreaElement>()
const fileUploadRef = ref<InstanceType<typeof FileUploadComponent>>()
const currentInput = ref('')
const defaultPlaceholder = computed(() => props.placeholder || t('input.placeholder'))
const currentPlaceholder = ref(defaultPlaceholder.value)
const uploadedFiles = ref<FileInfo[]>([])
const uploadKey = ref<string | null>(null)

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
  uploadedFiles.value = files
  uploadKey.value = key
  console.log('[InputArea] Files uploaded:', files.length, 'uploadKey:', key)
  
  // Update placeholder to show files are attached
  if (uploadedFiles.value.length > 0) {
    currentPlaceholder.value = t('input.filesAttached', { count: uploadedFiles.value.length })
  }
}

const handleFilesRemoved = (files: FileInfo[]) => {
  uploadedFiles.value = files
  console.log('[InputArea] Files removed, remaining:', files.length)
  
  // Update placeholder
  if (uploadedFiles.value.length === 0) {
    currentPlaceholder.value = defaultPlaceholder.value
  } else {
    currentPlaceholder.value = t('input.filesAttached', { count: uploadedFiles.value.length })
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

const handleUploadError = (error: any) => {
  console.error('[InputArea] Upload error:', error)
}

// Computed property to ensure 'disabled' is a boolean type
const isDisabled = computed(() => Boolean(props.disabled))

const adjustInputHeight = () => {
  nextTick(() => {
    if (inputRef.value) {
      inputRef.value.style.height = 'auto'
      inputRef.value.style.height = Math.min(inputRef.value.scrollHeight, 120) + 'px'
    }
  })
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    handleSend()
  }
}

const handleSend = () => {
  if (!currentInput.value.trim() || isDisabled.value) return

  let finalInput = currentInput.value.trim()

  // If files are uploaded, add file information to the query
  if (uploadedFiles.value.length > 0) {
    const fileInfo = uploadedFiles.value.map(f => `${f.originalName}`).join(', ')
    finalInput += ` [Uploaded files: ${fileInfo}]`
  }

  const query: InputMessage = {
    input: finalInput,
    memoryId: memoryStore.selectMemoryId,
    uploadedFiles: uploadedFiles.value
  }
  
  // Add uploadKey if it exists
  if (uploadKey.value) {
    query.uploadKey = uploadKey.value
    console.log('[InputArea] Including uploadKey in message:', uploadKey.value)
  } else {
    console.log('[InputArea] No uploadKey available for message')
  }

  // Use Vue's emit to send a message
  emit('send', query)

  // Clear the input but keep uploaded files and uploadKey for follow-up conversations
  clearInput()
}

const handlePlanModeClick = () => {
  // Trigger the plan mode toggle event
  emit('plan-mode-clicked')
}


/**
 * Clear the input box
 */
const clearInput = () => {
  currentInput.value = ''
  adjustInputHeight()
  emit('clear')
}

/**
 * Update the state of the input area (enable/disable)
 * @param {boolean} enabled - Whether to enable input
 * @param {string} [placeholder] - Placeholder text when enabled
 */
const updateState = (enabled: boolean, placeholder?: string) => {
  if (placeholder) {
    currentPlaceholder.value = enabled ? placeholder : t('input.waiting')
  }
  emit('update-state', enabled, placeholder)
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
  (newValue) => {
    if (newValue && newValue.trim()) {
      currentInput.value = newValue
      adjustInputHeight()
    }
  },
  { immediate: true }
)

// Expose methods to the parent component
defineExpose({
  clearInput,
  updateState,
  setInputValue,
  getQuery,
  resetSession,
  focus: () => inputRef.value?.focus(),
  get uploadedFiles() { return fileUploadRef.value?.uploadedFiles || [] },
  get uploadKey() { return fileUploadRef.value?.uploadKey || null }
})

onMounted(() => {
  // Initialization logic after component mounting
})

onUnmounted(() => {
  // Cleanup logic before component unmounting
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
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 12px;
  padding: 12px 16px;

  &:focus-within {
    border-color: #667eea;
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
}

.clear-memory-btn{
  width: 1.5em;
  height: 1.5em;
}

</style>
