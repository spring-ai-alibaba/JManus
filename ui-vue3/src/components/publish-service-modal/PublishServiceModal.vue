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
  <Modal v-model="showModal" :title="modalTitle" @confirm="handlePublish">
    <div class="modal-form wide-modal">
      <!-- Tool Description -->
      <div class="form-section">
        <div class="form-item">
          <label>{{ t('mcpService.toolDescriptionRequired') }}</label>
          <textarea
            v-model="formData.userRequest"
            :placeholder="t('mcpService.toolDescriptionPlaceholder')"
            :class="{ error: !formData.userRequest || !formData.userRequest.trim() }"
            class="description-field"
            rows="4"
            required
          />
          <div class="field-description">{{ t('mcpService.toolDescriptionDescription') }}</div>
        </div>
      </div>

      <!-- Parameter Configuration -->
      <div class="form-section">
        <div class="section-title">{{ t('mcpService.parameterConfig') }}</div>

        <!-- Parameter Requirements Help Text -->
        <div v-if="parameterRequirements.hasParameters" class="params-help-text">
          {{ t('sidebar.parameterRequirementsHelp') }}
        </div>

        <div class="parameter-table">
          <table>
            <thead>
              <tr>
                <th>{{ t('mcpService.parameterName') }}</th>
                <th>{{ t('mcpService.parameterDescription') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(param, index) in formData.parameters" :key="index">
                <td>
                  <input
                    type="text"
                    v-model="param.name"
                    :placeholder="t('mcpService.parameterName')"
                    class="parameter-input"
                    :readonly="parameterRequirements.hasParameters"
                    :class="{ 'readonly-input': parameterRequirements.hasParameters }"
                    required
                  />
                </td>
                <td>
                  <input
                    type="text"
                    v-model="param.description"
                    :placeholder="t('mcpService.parameterDescription')"
                    class="parameter-input"
                    required
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Service Publishing Options -->
      <div class="form-section">
        <div class="service-publish-options">
          <!-- Internal Toolcall Publishing Option -->
          <div class="internal-toolcall-publish-option">
            <label class="checkbox-label">
              <input type="checkbox" v-model="publishAsInternalToolcall" class="checkbox-input" />
              <span class="checkbox-text">{{ t('mcpService.publishAsInternalToolcall') }}</span>
            </label>
            <div class="checkbox-description">
              {{ t('mcpService.publishAsInternalToolcallDescription') }}
            </div>
          </div>

          <!-- HTTP POST Service Publishing Option -->
          <div class="http-publish-option">
            <label class="checkbox-label">
              <input type="checkbox" v-model="publishAsHttpService" class="checkbox-input" />
              <span class="checkbox-text">{{ t('mcpService.publishAsHttpService') }}</span>
            </label>
            <div class="checkbox-description">
              {{ t('mcpService.publishAsHttpServiceDescription') }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="button-container">
        <!-- Delete Button - Only shown when saved -->
        <button
          v-if="isSaved"
          class="action-btn danger"
          @click="handleDelete"
          :disabled="deleting"
        >
          <Icon icon="carbon:loading" v-if="deleting" class="loading-icon" />
          <Icon icon="carbon:trash-can" v-else />
          {{ deleting ? t('mcpService.deleting') : t('mcpService.delete') }}
        </button>

        <!-- Publish as Service Button - Always shown -->
        <button class="action-btn primary" @click="handlePublish" :disabled="publishing">
          <Icon icon="carbon:loading" v-if="publishing" class="loading-icon" />
          <Icon icon="carbon:cloud-upload" v-else />
          {{ publishing ? t('mcpService.publishing') : t('mcpService.publishAsService') }}
        </button>
      </div>
    </template>
  </Modal>

  <!-- Error Toast -->
  <div v-if="error" class="error-toast" @click="error = ''">
    <Icon icon="carbon:error" />
    {{ error }}
  </div>

  <!-- Success Toast -->
  <div v-if="success" class="success-toast" @click="success = ''">
    <Icon icon="carbon:checkmark" />
    {{ success }}
  </div>
</template>

<script setup lang="ts">
import {
  PlanParameterApiService,
  type ParameterRequirements,
} from '@/api/plan-parameter-api-service'
import { usePlanTemplateConfigSingleton } from '@/composables/usePlanTemplateConfig'
import Modal from '@/components/modal/index.vue'
import { Icon } from '@iconify/vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

// Get template config singleton
const templateConfig = usePlanTemplateConfigSingleton()

// Props
interface Props {
  modelValue: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
})

// Emits
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  published: [tool: null]
}>()

// Reactive data
const showModal = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const error = ref('')
const success = ref('')
const publishing = ref(false)
const deleting = ref(false)

// Service publishing options
const publishAsHttpService = ref(false)
const publishAsInternalToolcall = ref(true) // Default to true

// Parameter requirements from plan template
const parameterRequirements = ref<ParameterRequirements>({
  parameters: [],
  hasParameters: false,
  requirements: '',
})
const isLoadingParameters = ref(false)

// Form data
const formData = reactive({
  userRequest: '',
  parameters: [] as Array<{ name: string; description: string }>,
})

// Calculate modal title - check if toolConfig exists to determine if updating or creating
const modalTitle = computed(() => {
  const hasToolConfig = !!templateConfig.selectedTemplate.value?.toolConfig
  return hasToolConfig ? t('mcpService.updateService') : t('mcpService.createService')
})

// Check if tool is saved (has toolConfig)
const isSaved = computed(() => {
  return !!templateConfig.selectedTemplate.value?.toolConfig
})

// Initialize form data from templateConfig
const initializeFormData = () => {
  const toolConfig = templateConfig.selectedTemplate.value?.toolConfig
  
  if (toolConfig) {
    // Load from existing toolConfig
    formData.userRequest = toolConfig.toolDescription || ''
    publishAsHttpService.value = toolConfig.enableHttpService ?? false
    publishAsInternalToolcall.value = toolConfig.enableInternalToolcall ?? true
    
    // Load parameters from inputSchema if available, otherwise use parameter requirements
    if (toolConfig.inputSchema && Array.isArray(toolConfig.inputSchema) && toolConfig.inputSchema.length > 0) {
      formData.parameters = toolConfig.inputSchema.map(param => ({
        name: param.name || '',
        description: param.description || '',
      }))
    } else if (!parameterRequirements.value.hasParameters) {
      formData.parameters = []
    }
  } else {
    // Initialize with defaults
    formData.userRequest = ''
    publishAsHttpService.value = false
    publishAsInternalToolcall.value = true
    // Only reset parameters when not loaded from plan template
    if (!parameterRequirements.value.hasParameters) {
      formData.parameters = []
    }
  }
}

// Load parameter requirements from plan template
const loadParameterRequirements = async () => {
  const planTemplateId = templateConfig.currentPlanTemplateId.value
  if (!planTemplateId) {
    parameterRequirements.value = {
      parameters: [],
      hasParameters: false,
      requirements: '',
    }
    return
  }

  isLoadingParameters.value = true
  try {
    const requirements = await PlanParameterApiService.getParameterRequirements(
      planTemplateId
    )
    parameterRequirements.value = requirements

    // Initialize form parameters with extracted parameters
    if (requirements.hasParameters) {
      formData.parameters = requirements.parameters.map(param => ({
        name: param,
        description: '',
      }))
    }
  } catch (error) {
    console.error('[PublishModal] Failed to load parameter requirements:', error)
    // Don't show error for 404 - template might not be ready yet
    if (error instanceof Error && !error.message.includes('404')) {
      console.warn('[PublishModal] Parameter requirements not available yet, will retry later')
    }
    parameterRequirements.value = {
      parameters: [],
      hasParameters: false,
      requirements: '',
    }
  } finally {
    isLoadingParameters.value = false
  }
}

// Show message
const showMessage = (msg: string, type: 'success' | 'error' | 'info') => {
  if (type === 'success') {
    success.value = msg
    setTimeout(() => {
      success.value = ''
    }, 3000)
  } else if (type === 'error') {
    error.value = msg
    setTimeout(() => {
      error.value = ''
    }, 5000)
  }
}

// Validate form
const validateForm = (): boolean => {
  // Validate tool description
  if (!formData.userRequest.trim()) {
    showMessage(t('mcpService.toolDescriptionRequiredError'), 'error')
    return false
  }

  // Validate service group (from templateConfig)
  const serviceGroup = templateConfig.getServiceGroup() || ''
  if (!serviceGroup.trim()) {
    showMessage(t('mcpService.serviceGroupRequiredError'), 'error')
    return false
  }

  // Validate tool name (from templateConfig title)
  const toolName = templateConfig.getTitle() || ''
  if (!toolName.trim()) {
    showMessage(t('mcpService.toolNameRequiredError'), 'error')
    return false
  }

  // Ensure at least one service is selected
  if (!publishAsInternalToolcall.value && !publishAsHttpService.value) {
    showMessage('Please select at least one service type', 'error')
    return false
  }

  // Validate parameter names and descriptions
  for (let i = 0; i < formData.parameters.length; i++) {
    const param = formData.parameters[i]
    if (param.name && !param.description.trim()) {
      showMessage(`Parameter "${param.name}" description cannot be empty`, 'error')
      return false
    }
    if (param.description && !param.name.trim()) {
      showMessage(
        `Parameter description "${param.description}" corresponding name cannot be empty`,
        'error'
      )
      return false
    }
  }

  return true
}

// Handle publishing
const handlePublish = async () => {
  console.log('[PublishModal] Starting to handle publish request')
  console.log('[PublishModal] Form data:', formData)
  console.log('[PublishModal] Publish as HTTP service:', publishAsHttpService.value)

  if (!validateForm()) {
    console.log('[PublishModal] Form validation failed')
    return
  }

  publishing.value = true
  try {
    // Prepare inputSchema
    const inputSchema = formData.parameters
      .filter(param => param.name.trim() && param.description.trim())
      .map(param => ({
        name: param.name.trim(),
        description: param.description.trim(),
        type: 'string',
      }))

    // Update toolConfig in templateConfig
    templateConfig.setToolDescription(formData.userRequest.trim())
    templateConfig.setEnableInternalToolcall(publishAsInternalToolcall.value)
    templateConfig.setEnableHttpService(publishAsHttpService.value)
    templateConfig.setEnableMcpService(false)
    templateConfig.setInputSchema(inputSchema)

    // Save the plan template with updated toolConfig
    const saveSuccess = await templateConfig.save()
    
    if (!saveSuccess) {
      throw new Error('Failed to save plan template')
    }

    // selectedTemplate is automatically refreshed by templateConfig.save()

    // Perform corresponding publishing operations based on publish type
    const enabledServices = []
    if (publishAsInternalToolcall.value) enabledServices.push('Internal Method Call')
    if (publishAsHttpService.value) enabledServices.push('HTTP Service')

    if (enabledServices.length > 0) {
      console.log(
        '[PublishModal] Service published successfully. Enabled services:',
        enabledServices.join(', ')
      )
      showMessage(t('mcpService.publishSuccess'), 'success')
      emit('published', null) // Emit null since state is managed in templateConfig
    } else {
      console.log('[PublishModal] Only saving tool, not publishing as any service')
      showMessage(t('mcpService.saveSuccess'), 'success')
      emit('published', null)
    }
  } catch (err: unknown) {
    console.error('[PublishModal] Failed to publish service:', err)
    const message = err instanceof Error ? err.message : 'Unknown error'
    showMessage(t('mcpService.publishFailed') + ': ' + message, 'error')
  } finally {
    publishing.value = false
  }
}

// Handle delete
const handleDelete = async () => {
  if (deleting.value) return

  // Confirm deletion
  if (!confirm(t('mcpService.deleteConfirmMessage'))) {
    return
  }

  const planTemplateId = templateConfig.currentPlanTemplateId.value
  if (!planTemplateId) {
    showMessage(
      t('mcpService.deleteFailed') + ': ' + t('mcpService.selectPlanTemplateFirst'),
      'error'
    )
    return
  }

  deleting.value = true
  try {
    console.log('[PublishModal] Starting to delete tool config for planTemplateId:', planTemplateId)

    // Remove toolConfig from templateConfig
    templateConfig.setToolConfig(undefined)

    // Save the plan template without toolConfig
    const saveSuccess = await templateConfig.save()
    
    if (!saveSuccess) {
      throw new Error('Failed to save plan template after deletion')
    }

    // selectedTemplate is automatically refreshed by templateConfig.save()

    console.log('[PublishModal] Deleted successfully')
    showMessage(t('mcpService.deleteSuccess'), 'success')

    // Close modal
    showModal.value = false

    // Notify parent component of successful deletion
    emit('published', null)
  } catch (error: unknown) {
    console.error('[PublishModal] Failed to delete tool config:', error)
    const message = error instanceof Error ? error.message : 'Unknown error'
    showMessage(t('mcpService.deleteFailed') + ': ' + message, 'error')
  } finally {
    deleting.value = false
  }
}

// Watch modal display state
const watchModal = async () => {
  if (showModal.value) {
    console.log('[PublishModal] Modal opened, starting to initialize data')
    initializeFormData()
    await loadParameterRequirements()
  }
}

// Watch props changes
watch(() => props.modelValue, watchModal)

// Watch for planTemplateId changes
watch(
  () => templateConfig.currentPlanTemplateId.value,
  (newId, oldId) => {
    if (newId && newId !== oldId) {
      // If this is a new template ID (not from initial load), retry loading parameters
      if (oldId && newId.startsWith('planTemplate-')) {
        // Retry loading parameters with a delay for new templates
        setTimeout(() => {
          loadParameterRequirements()
        }, 1000)
      } else {
        loadParameterRequirements()
      }
    }
  }
)

// Initialize when component mounts
onMounted(async () => {
  if (showModal.value) {
    console.log('[PublishModal] Initialize when component mounted')
    initializeFormData()
    await loadParameterRequirements()
  }
})

// Expose methods for parent component
defineExpose({
  loadParameterRequirements,
})
</script>

<style scoped>
/* Wide modal styles */
:deep(.wide-modal .modal-container) {
  width: 90%;
  max-width: 900px !important; /* Adjust width to 900px */
}

/* Form layout optimization - reference new Model modal styles */
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px; /* Adjust to 16px, consistent with new Model modal */
  width: 100%;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 8px; /* Adjust to 8px, optimize title and input spacing */
  width: 100%;
}

.section-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 8px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px; /* Adjust to 8px, optimize title and input spacing */
}

.form-item label {
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  margin: 0; /* Remove default margin */
}

.required {
  color: #ff6b6b;
}

/* Field description styles */
.field-description {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
  line-height: 1.4;
  font-style: italic;
}

.form-item input,
.form-item textarea {
  width: 100%;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  transition: all 0.3s ease;
  font-family: inherit;
  box-sizing: border-box;
  min-height: 48px; /* Ensure consistent minimum height */
}

.form-item input {
  height: 48px; /* Single line input fixed height */
}

.form-item input:focus,
.form-item textarea:focus {
  border-color: #667eea;
  outline: none;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.15);
}

.form-item input::placeholder,
.form-item textarea::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

/* Service Group Autocomplete Styles */
.service-group-autocomplete {
  position: relative;
}

.service-group-autocomplete input {
  width: 100%;
}

.service-group-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 99999;
  margin-top: 4px;
  background: rgba(15, 15, 20, 0.98);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(102, 126, 234, 0.3);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
  max-height: 200px;
  overflow-y: auto;
}

.service-group-option {
  padding: 12px 16px;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.service-group-option:last-child {
  border-bottom: none;
}

.service-group-option:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.description-field {
  resize: vertical;
  min-height: 80px;
  line-height: 1.5;
  width: 100%;
}

/* Parameter Requirements Help Text */
.params-help-text {
  margin-bottom: 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.4;
  padding: 6px 8px;
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 4px;
}

/* Parameter table responsive */
.parameter-table {
  margin-bottom: 16px;
  width: 100%;
  overflow-x: auto;
}

.parameter-table table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  overflow: hidden;
  min-width: 600px;
}

.parameter-table th {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
  padding: 12px;
  text-align: left;
  font-size: 14px;
  white-space: nowrap;
}

.parameter-table td {
  padding: 8px 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.parameter-input {
  width: 100%;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #fff;
  font-size: 14px;
  transition: all 0.3s ease;
}

.parameter-input:focus {
  border-color: #667eea;
  outline: none;
  background: rgba(255, 255, 255, 0.08);
}

.readonly-input {
  background: rgba(255, 255, 255, 0.02) !important;
  color: rgba(255, 255, 255, 0.6) !important;
  cursor: not-allowed;
  border-color: rgba(255, 255, 255, 0.05) !important;
}

/* Delete and add button styles removed */

/* Endpoint component responsive - supports manual input */
.custom-dropdown {
  position: relative;
  width: 100%;
}

.dropdown-input {
  width: 100%;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  transition: all 0.3s ease;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  height: 48px; /* Ensure consistent height with other inputs */
}

.dropdown-input input {
  background: transparent;
  border: none;
  outline: none;
  color: #fff;
  font-size: 14px;
  width: 100%;
  cursor: text; /* Allow text input */
  height: 100%;
  padding: 0;
}

.dropdown-input input::placeholder {
  color: rgba(255, 255, 255, 0.4);
  font-style: italic;
}

.dropdown-input.active {
  border-color: #667eea;
  outline: none;
  background-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.15);
  transform: translateY(-1px);
}

.dropdown-input:hover {
  border-color: rgba(255, 255, 255, 0.2);
  background-color: rgba(255, 255, 255, 0.07);
  transform: translateY(-1px);
}

.dropdown-arrow {
  transition: transform 0.3s ease;
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
  flex-shrink: 0;
  margin-left: 8px;
}

.dropdown-arrow.rotated {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  left: 0;
  width: 100%;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  overflow: hidden;
  max-height: 200px;
  overflow-y: auto;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(10px);
  z-index: 10;
  margin-top: 4px;
}

.dropdown-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  align-items: center;
  gap: 8px;
}

.dropdown-item:last-child {
  border-bottom: none;
}

.dropdown-item:hover {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2), rgba(102, 126, 234, 0.1));
  color: #a8b3ff;
  transform: translateX(4px);
}

.dropdown-item.selected {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.3), rgba(102, 126, 234, 0.2));
  color: #a8b3ff;
  font-weight: 500;
}

.dropdown-item.custom-input {
  color: rgba(255, 255, 255, 0.6);
  font-style: italic;
  padding-left: 16px; /* Align with other items */
}

.dropdown-item.custom-input .custom-label {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 4px;
}

/* Button container - reference screenshot styles */
.button-container {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 0;
  align-items: center;
  padding: 16px 0;
  min-height: 52px; /* Ensure container has fixed height, prevent jitter */
  position: relative; /* Provide reference for absolutely positioned child elements */
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
}

.action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.action-btn.primary {
  background: rgba(102, 126, 234, 0.2);
  border-color: rgba(102, 126, 234, 0.3);
  color: #a8b3ff;
}

.action-btn.primary:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.3);
}

.action-btn.danger {
  background: rgba(234, 102, 102, 0.2);
  border-color: rgba(234, 102, 102, 0.3);
  color: #ff6b6b;
}

.action-btn.danger:hover:not(:disabled) {
  background: rgba(234, 102, 102, 0.3);
}

.action-btn.danger:disabled {
  background: rgba(234, 102, 102, 0.1);
  border-color: rgba(234, 102, 102, 0.2);
  color: rgba(255, 107, 107, 0.5);
  cursor: not-allowed;
}

/* Read-only input styles */
.readonly-input {
  background: rgba(255, 255, 255, 0.03) !important;
  color: rgba(255, 255, 255, 0.7) !important;
  cursor: not-allowed !important;
  opacity: 0.8;
}

.readonly-input::placeholder {
  color: rgba(255, 255, 255, 0.4) !important;
}

/* Endpoint container layout */
.endpoint-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.endpoint-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

/* Custom dropdown styles - reference screenshot three */
.custom-select {
  position: relative;
  display: inline-block;
  width: 100%; /* Adjust to 100% to fit new layout */
  flex-shrink: 0;
}

.select-input-container {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(20, 20, 25, 0.95);
  border: 2px solid rgba(102, 126, 234, 0.6);
  border-radius: 12px;
  color: #667eea;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 600;
  outline: none;
  width: 100%;
  justify-content: space-between;
  height: 44px;
  box-sizing: border-box;
  box-shadow: 0 0 0 1px rgba(102, 126, 234, 0.3);
  padding: 0 12px;
}

.select-input-container:hover {
  border-color: rgba(102, 126, 234, 0.8);
  background-color: rgba(20, 20, 25, 0.98);
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.4);
}

.select-input-container:focus-within {
  border-color: rgba(102, 126, 234, 0.9);
  outline: none;
  background-color: rgba(20, 20, 25, 0.98);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.3);
}

.input-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.input-icon {
  color: #667eea;
  flex-shrink: 0;
}

.select-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: #667eea;
  font-size: 14px;
  font-weight: 600;
  text-shadow: none;
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
  height: 100%;
}

.select-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
  font-style: italic;
}

.select-arrow-btn {
  background: none;
  border: none;
  color: #667eea;
  cursor: pointer;
  padding: 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
}

.select-arrow-btn:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.chevron {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  opacity: 0.9;
  filter: none;
  width: 16px;
  height: 16px;
}

.select-dropdown {
  position: absolute;
  top: 100%;
  z-index: 99999;
  margin-top: 6px;
  background: rgba(15, 15, 20, 0.98);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(102, 126, 234, 0.3);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
  width: 280px; /* Keep consistent with select-btn width */
  max-height: 280px;
  overflow: hidden;
  /* Ensure not occupying document flow */
  pointer-events: auto;
}

.select-dropdown.dropdown-top {
  top: auto;
  bottom: 100%;
  margin-top: 0;
  margin-bottom: 4px;
}

.dropdown-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(102, 126, 234, 0.2);
  font-size: 14px;
  font-weight: 600;
  color: #667eea;
  background: rgba(102, 126, 234, 0.05);
}

.close-btn {
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
}

.select-options {
  padding: 12px 0;
  max-height: 200px;
  overflow-y: auto;
}

.select-option {
  display: flex;
  align-items: center;
  gap: 0;
  width: 100%;
  padding: 12px 18px;
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
  font-size: 14px;
}

.select-option:hover {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.select-option.active {
  background: rgba(102, 126, 234, 0.2);
  color: #667eea;
  border-left: 3px solid #667eea;
  padding-left: 15px;
  font-weight: 500;
}

.select-option.custom-input {
  color: rgba(255, 255, 255, 0.6);
  font-style: italic;
}

.select-option.custom-input .custom-label {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 4px;
}

.option-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
}

.check-icon {
  color: #667eea;
  opacity: 0.8;
}

/* Manual input area styles */
.manual-input-section {
  padding: 14px 18px;
  border-top: 1px solid rgba(102, 126, 234, 0.2);
  background: rgba(102, 126, 234, 0.03);
}

.manual-input-container {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.manual-input {
  flex: 1;
  padding: 8px 12px;
  background: rgba(20, 20, 25, 0.8);
  border: 1px solid rgba(102, 126, 234, 0.3);
  border-radius: 8px;
  color: #667eea;
  font-size: 14px;
  transition: all 0.3s ease;
}

.manual-input:focus {
  border-color: rgba(102, 126, 234, 0.8);
  outline: none;
  background: rgba(20, 20, 25, 0.9);
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.manual-input::placeholder {
  color: rgba(102, 126, 234, 0.5);
}

.add-manual-btn {
  padding: 8px 12px;
  background: rgba(102, 126, 234, 0.15);
  border: 1px solid rgba(102, 126, 234, 0.3);
  border-radius: 8px;
  color: #667eea;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-manual-btn:hover {
  background: rgba(102, 126, 234, 0.25);
  border-color: rgba(102, 126, 234, 0.5);
}

/* Service publishing option styles */
.service-publish-options {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mcp-publish-option,
.http-publish-option,
.internal-toolcall-publish-option {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
}

/* .http-publish-option, .mcp-publish-option, .internal-toolcall-publish-option now use the same styles */

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  accent-color: #667eea;
  cursor: pointer;
}

.checkbox-text {
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.checkbox-description {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.4;
  margin-left: 24px;
}

/* HTTP service options now use the same styles as MCP service, no special handling needed */

.endpoint-description {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  line-height: 1.4;
  margin-bottom: 8px;
}

/* Two-column layout styles */
.endpoint-url-row {
  display: flex;
  gap: 16px;
  width: 100%;
}

.endpoint-url-row.single-item {
  gap: 0;
}

.endpoint-url-row.single-item .endpoint-item {
  flex: 0 0 50%; /* Maintain 50% width even when displayed alone */
}

.endpoint-item {
  flex: 0 0 50%; /* Fixed width 50%, not responsive */
  min-width: 0;
}

.url-item {
  flex: 1;
  min-width: 0;
}

/* URL display styles */
.url-container {
  width: 100%;
}

.url-display {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  transition: all 0.3s ease;
  min-height: 48px;
}

.url-display:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.2);
}

.url-text {
  flex: 1;
  font-size: 14px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  word-break: break-all;
  color: rgba(255, 255, 255, 0.9);
}

.copy-icon {
  color: rgba(255, 255, 255, 0.6);
  margin-left: 8px;
  flex-shrink: 0;
  transition: all 0.3s ease;
}

.url-display:hover .copy-icon {
  color: rgba(255, 255, 255, 0.8);
}

.backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 99998;
  background: transparent;
}

.slideDown-enter-active,
.slideDown-leave-active {
  transition: all 0.2s ease;
  transform-origin: top;
}

.slideDown-enter-from,
.slideDown-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.95);
}

/* Old save button styles removed */

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* Required field styles */
.required {
  color: #ff4d4f;
  margin-left: 4px;
}

/* Error state styles */
.form-item input.error,
.form-item textarea.error {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 2px rgba(255, 77, 79, 0.1);
}

.form-item input.error:focus,
.form-item textarea.error:focus {
  border-color: #ff4d4f;
  box-shadow: 0 0 0 2px rgba(255, 77, 79, 0.2);
}

/* Tooltip messages */
.error-toast,
.success-toast {
  position: fixed;
  top: 20px;
  right: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  z-index: 10000; /* Ensure above modal */
  animation: slideIn 0.3s ease;
}

.error-toast {
  background: rgba(234, 102, 102, 0.9);
  border: 1px solid rgba(234, 102, 102, 0.5);
}

.success-toast {
  background: rgba(102, 234, 102, 0.9);
  border: 1px solid rgba(102, 234, 102, 0.5);
}

@keyframes slideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}
</style>
