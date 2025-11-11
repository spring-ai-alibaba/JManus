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
  <div class="config-section">
    <div class="section-header">
      <Icon icon="carbon:code" width="16" />
      <span>{{ $t('sidebar.dynamicAgentPlan') }}</span>
    </div>
    <!-- Error Display -->
    <div v-if="planTypeError" class="error-section">
      <div class="error-message">
        <Icon icon="carbon:warning" width="16" />
        <div class="error-content">
          <div class="error-title">{{ $t('sidebar.planTypeError') }}</div>
          <div class="error-description">{{ planTypeError }}</div>
        </div>
      </div>
    </div>

    <!-- Visual JSON Editor -->
    <div v-else class="visual-editor">
      <!-- Plan Basic Info -->
      <div class="plan-basic-info">
        <div class="form-row">
          <label class="form-label">{{ $t('sidebar.title') }}</label>
          <input
            v-model="displayData.title"
            type="text"
            class="form-input"
            :class="{ error: titleError }"
            :placeholder="$t('sidebar.titlePlaceholder')"
          />
          <!-- Inline validation message for title -->
          <div v-if="titleError" class="field-error-message">
            <Icon icon="carbon:warning" width="12" />
            {{ titleError }}
          </div>
        </div>

        <!-- Plan Template ID (Read-only) -->
        <div class="form-row">
          <label class="form-label">{{ $t('sidebar.planTemplateId') }}</label>
          <input
            :value="currentPlanTemplateId"
            type="text"
            class="form-input readonly-input"
            readonly
            :placeholder="$t('sidebar.planTemplateIdPlaceholder')"
          />
        </div>
      </div>

      <!-- Steps Editor -->
      <div class="steps-section">
        <div class="steps-header">
          <label class="form-label">{{ $t('sidebar.tasks') }}</label>
          <div class="steps-actions">
            <button @click="addStep" class="btn btn-xs" :title="$t('sidebar.addStep')">
              <Icon icon="carbon:add" width="12" />
            </button>
          </div>
        </div>

        <div class="steps-container">
          <div v-for="(step, index) in displayData.steps" :key="index" class="step-item">
            <div class="step-header">
              <span class="step-number">{{ $t('sidebar.subtask') }} {{ index + 1 }}</span>
              <div class="step-actions">
                <button
                  @click="moveStepUp(index)"
                  :disabled="index === 0"
                  class="btn btn-xs"
                  :title="$t('sidebar.moveUp')"
                >
                  <Icon icon="carbon:chevron-up" width="12" />
                </button>
                <button
                  @click="moveStepDown(index)"
                  :disabled="index === displayData.steps.length - 1"
                  class="btn btn-xs"
                  :title="$t('sidebar.moveDown')"
                >
                  <Icon icon="carbon:chevron-down" width="12" />
                </button>
                <button
                  @click="removeStep(index)"
                  class="btn btn-xs btn-danger"
                  :title="$t('sidebar.removeStep')"
                >
                  <Icon icon="carbon:trash-can" width="12" />
                </button>
              </div>
            </div>

            <div class="step-content">
              <!-- Step Requirement -->
              <div class="form-row">
                <label class="form-label">{{ $t('sidebar.stepRequirement') }}</label>
                <textarea
                  v-model="step.stepRequirement"
                  class="form-textarea auto-resize"
                  :placeholder="$t('sidebar.stepRequirementPlaceholder')"
                  rows="4"
                  @input="autoResizeTextarea($event)"
                ></textarea>
              </div>

              <!-- Terminate Columns -->
              <div class="form-row">
                <label class="form-label">{{ $t('sidebar.terminateColumns') }}</label>

                <textarea
                  v-model="step.terminateColumns"
                  class="form-textarea auto-resize"
                  :placeholder="$t('sidebar.terminateColumnsPlaceholder')"
                  rows="4"
                  @input="autoResizeTextarea($event)"
                ></textarea>

                <!-- Preview Section -->
                <div
                  v-if="step.terminateColumns && step.terminateColumns.trim()"
                  class="preview-section"
                >
                  <div class="preview-label">{{ $t('sidebar.preview') }}:</div>
                  <div class="preview-content">
                    <div class="preview-text">
                      {{ $t('sidebar.systemWillReturnListWithTableHeaderFormat') }}:
                      <span class="preview-table-header">{{
                        formatTableHeader(step.terminateColumns)
                      }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Model Name -->
              <div class="form-row">
                <label class="form-label">{{ $t('sidebar.modelName') }}</label>
                <div class="model-selector-wrapper">
                  <div
                    class="model-selector"
                    :class="{
                      'is-open': isModelDropdownOpenForStep(index),
                      'is-disabled': isLoadingModels,
                    }"
                  >
                    <!-- Input field with dropdown arrow -->
                    <div class="model-input-wrapper">
                      <input
                        :value="getModelDisplayValue(index)"
                        type="text"
                        class="form-input model-search-input"
                        :placeholder="getModelPlaceholder(index)"
                        :disabled="isLoadingModels"
                        @click.stop="openModelDropdown(index)"
                        @focus="openModelDropdown(index)"
                        @input="handleModelSearchInput($event, index)"
                        @keydown.escape="closeModelDropdown(index)"
                        @keydown.enter.prevent="selectFirstFilteredModel(index)"
                        @keydown.down.prevent="navigateModelDown(index)"
                        @keydown.up.prevent="navigateModelUp(index)"
                      />
                      <Icon
                        icon="carbon:chevron-down"
                        width="14"
                        class="dropdown-arrow"
                        :class="{ 'is-open': isModelDropdownOpenForStep(index) }"
                        @click.stop="toggleModelDropdown(index)"
                      />
                    </div>

                    <!-- Dropdown list -->
                    <div
                      v-if="isModelDropdownOpenForStep(index)"
                      class="model-dropdown"
                      @click.stop
                    >
                      <!-- Loading state -->
                      <div v-if="isLoadingModels" class="dropdown-item disabled">
                        {{ $t('sidebar.loading') }}
                      </div>

                      <!-- Error state -->
                      <div v-else-if="modelsLoadError" class="dropdown-item disabled error">
                        {{ $t('sidebar.modelLoadError') }}
                      </div>

                      <!-- No models found -->
                      <div
                        v-else-if="getFilteredModelsForStep(index).length === 0"
                        class="dropdown-item disabled"
                      >
                        {{ $t('sidebar.noModelsFound') }}
                      </div>

                      <!-- Model options -->
                      <div
                        v-for="(model, idx) in getFilteredModelsForStep(index)"
                        :key="model.value"
                        class="dropdown-item"
                        :class="{
                          'is-selected': step.modelName === model.value,
                          'is-highlighted': getHighlightedIndex(index) === idx,
                        }"
                        @click="selectModelForStep(model.value, index)"
                        @mouseenter="setHighlightedIndex(index, idx)"
                      >
                        {{ model.value }}
                        <Icon
                          v-if="step.modelName === model.value"
                          icon="carbon:checkmark"
                          width="12"
                          class="check-icon"
                        />
                      </div>

                      <!-- Default empty option -->
                      <div
                        class="dropdown-item"
                        :class="{
                          'is-selected': !step.modelName,
                          'is-highlighted': getHighlightedIndex(index) === -1,
                        }"
                        @click="selectModelForStep('', index)"
                        @mouseenter="setHighlightedIndex(index, -1)"
                      >
                        {{ $t('sidebar.noModelSelected') }}
                        <Icon
                          v-if="!step.modelName"
                          icon="carbon:checkmark"
                          width="12"
                          class="check-icon"
                        />
                      </div>
                    </div>
                  </div>

                  <!-- Error refresh button -->
                  <button
                    v-if="modelsLoadError"
                    @click="loadAvailableModels"
                    class="btn btn-sm btn-danger"
                    :title="$t('sidebar.retryLoadModels')"
                  >
                    <Icon icon="carbon:warning" width="14" />
                    {{ $t('sidebar.retry') }}
                  </button>
                </div>

                <!-- Error message -->
                <div v-if="modelsLoadError" class="error-message">
                  <Icon icon="carbon:warning" width="12" />
                  {{ modelsLoadError }}
                </div>
              </div>

              <!-- Tool Selection -->
              <div class="form-row">
                <AssignedTools
                  :title="$t('sidebar.selectedTools')"
                  :selected-tool-ids="step.selectedToolKeys || []"
                  :available-tools="props.availableTools"
                  :add-button-text="$t('sidebar.addRemoveTools')"
                  :empty-text="$t('sidebar.noTools')"
                  :use-grid-layout="true"
                  @add-tools="showToolSelectionModal(index)"
                  @tools-filtered="
                    (filteredTools: string[]) => handleToolsFiltered(index, filteredTools)
                  "
                />
              </div>
            </div>
          </div>

          <!-- Empty State -->
          <div v-if="displayData.steps.length === 0" class="empty-steps">
            <Icon icon="carbon:add-alt" width="32" class="empty-icon" />
            <p>{{ $t('sidebar.noSteps') }}</p>
            <button @click="addStep" class="btn btn-primary">
              <Icon icon="carbon:add" width="14" />
              {{ $t('sidebar.addFirstStep') }}
            </button>
          </div>
        </div>
      </div>

      <!-- JSON Preview (Optional) -->
      <div class="json-preview" v-if="showJsonPreview">
        <div class="preview-header">
          <label class="form-label">{{ $t('sidebar.jsonPreview') }}</label>
          <button @click="closeJsonPreview" class="btn btn-xs">
            <Icon icon="carbon:close" width="12" />
          </button>
        </div>
        <pre class="json-code">{{ formattedJsonOutput }}</pre>
      </div>

      <!-- Toggle JSON Preview -->
      <div class="editor-footer">
        <button @click="toggleJsonPreview" class="btn btn-sm btn-secondary">
          <Icon icon="carbon:code" width="14" />
          {{ showJsonPreview ? $t('sidebar.hideJson') : $t('sidebar.showJson') }}
        </button>
        <div class="section-actions">
          <button
            class="btn btn-sm"
            @click="handleCopyPlan"
            :disabled="isGenerating || isExecuting"
            :title="$t('sidebar.copyPlan')"
          >
            <Icon icon="carbon:copy" width="14" />
            {{ $t('sidebar.copyPlan') }}
          </button>
          <button
            class="btn btn-sm"
            @click="handleRollback"
            :disabled="!(canRollback ?? false)"
            :title="$t('sidebar.rollback')"
          >
            <Icon icon="carbon:undo" width="14" />
          </button>
          <button
            class="btn btn-sm"
            @click="handleRestore"
            :disabled="!(canRestore ?? false)"
            :title="$t('sidebar.restore')"
          >
            <Icon icon="carbon:redo" width="14" />
          </button>
          <button
            class="btn btn-primary"
            @click="handleSave"
            :disabled="isGenerating || isExecuting"
          >
            <Icon icon="carbon:save" width="14" />
            Save
          </button>
        </div>
      </div>
    </div>

    <!-- Tool Selection Modal -->
    <ToolSelectionModal
      v-model="showToolModal"
      :tools="props.availableTools"
      :selected-tool-ids="
        currentStepIndex >= 0 ? displayData.steps[currentStepIndex]?.selectedToolKeys || [] : []
      "
      @confirm="handleToolSelectionConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ConfigApiService, type ModelOption } from '@/api/config-api-service'
import AssignedTools from '@/components/shared/AssignedTools.vue'
import ToolSelectionModal from '@/components/tool-selection-modal/ToolSelectionModal.vue'
import { usePlanTemplateConfigSingleton } from '@/composables/usePlanTemplateConfig'
import type { StepData } from '@/types/plan-execution'
import { Icon } from '@iconify/vue'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useJsonEditor, type JsonEditorProps } from './json-editor-logic'

const { t } = useI18n()

// Define props interface specific to JsonEditorV2
interface JsonEditorV2Props {
  jsonContent: string
  canRollback: boolean
  canRestore: boolean
  isGenerating?: boolean
  isExecuting?: boolean
  currentPlanTemplateId: string
  availableTools?: Array<{
    key: string
    name: string
    description: string
    enabled: boolean
    serviceGroup: string
    selectable: boolean
  }>
}

// Props
const props = withDefaults(defineProps<JsonEditorV2Props>(), {
  availableTools: () => [],
  currentPlanTemplateId: '',
  isGenerating: false,
  isExecuting: false,
})

// Emits
const emit = defineEmits<{
  rollback: []
  restore: []
  save: []
  'copy-plan': []
  'update:jsonContent': [value: string]
}>()

// Create compatible props object for useJsonEditor
const compatibleProps: JsonEditorProps = {
  ...props,
  hiddenFields: [],
}

const {
  showJsonPreview,
  displayData,
  formattedJsonOutput,
  toggleJsonPreview,
  closeJsonPreview,
} = useJsonEditor(compatibleProps, emit)

// Get template config singleton for action handlers
const templateConfig = usePlanTemplateConfigSingleton()

// Step management functions (moved from json-editor-logic.ts)
const addStep = () => {
  const newStep: StepData = {
    stepRequirement: '',
    agentName: 'ConfigurableDynaAgent', // Default agent name
    modelName: null, // Default to null (no model selected)
    selectedToolKeys: [],
    terminateColumns: '',
    agentType: '',
    stepContent: '',
  }
  displayData.steps.push(newStep)
}

const removeStep = (index: number) => {
  if (index >= 0 && index < displayData.steps.length) {
    displayData.steps.splice(index, 1)
  }
}

const moveStepUp = (index: number) => {
  if (index > 0) {
    const step = displayData.steps.splice(index, 1)[0]
    displayData.steps.splice(index - 1, 0, step)
  }
}

const moveStepDown = (index: number) => {
  if (index < displayData.steps.length - 1) {
    const step = displayData.steps.splice(index, 1)[0]
    displayData.steps.splice(index + 1, 0, step)
  }
}

// Action handlers (moved from json-editor-logic.ts to usePlanTemplateConfig)
const handleRollback = () => {
  templateConfig.handleRollback()
  emit('rollback')
}

const handleRestore = () => {
  templateConfig.handleRestore()
  emit('restore')
}

const handleSave = async () => {
  await templateConfig.handleSave()
  emit('save')
}

// Error state
const planTypeError = ref<string | null>(null)
const titleError = ref<string>('')

// Model selection state
const availableModels = ref<ModelOption[]>([])
const isLoadingModels = ref(false)
const modelsLoadError = ref<string>('')

// Per-step state for dropdown (stepIndex -> state)
const modelSearchFilters = ref<Map<number, string>>(new Map())
const openDropdownSteps = ref<Set<number>>(new Set())
const highlightedIndices = ref<Map<number, number>>(new Map())

// Get search filter for a specific step
const getSearchFilter = (stepIndex: number): string => {
  return modelSearchFilters.value.get(stepIndex) ?? ''
}

// Set search filter for a specific step
const setSearchFilter = (stepIndex: number, value: string) => {
  modelSearchFilters.value.set(stepIndex, value)
}

// Filtered models based on search for a specific step
const getFilteredModelsForStep = (stepIndex: number) => {
  const filter = getSearchFilter(stepIndex)
  if (!filter.trim()) {
    return availableModels.value
  }
  const searchTerm = filter.toLowerCase().trim()
  return availableModels.value.filter(
    model =>
      model.value.toLowerCase().includes(searchTerm) ||
      model.label.toLowerCase().includes(searchTerm)
  )
}

// Get display value for a specific step
const getModelDisplayValue = (stepIndex: number): string => {
  const step = displayData.steps[stepIndex]
  const filter = getSearchFilter(stepIndex)
  if (openDropdownSteps.value.has(stepIndex) && filter !== '') {
    return filter
  }
  return step.modelName ?? ''
}

// Get placeholder text based on selected model
const getModelPlaceholder = (stepIndex: number) => {
  if (isLoadingModels.value) {
    return ''
  }
  if (modelsLoadError.value) {
    return ''
  }
  const step = displayData.steps[stepIndex]
  if (step.modelName) {
    return ''
  }
  return (t('sidebar.modelNameDescription') as string) || ''
}

// Check if dropdown is open for a specific step
const isModelDropdownOpenForStep = (stepIndex: number): boolean => {
  return openDropdownSteps.value.has(stepIndex)
}

// Model dropdown functions
const openModelDropdown = (stepIndex: number) => {
  if (!isLoadingModels.value) {
    openDropdownSteps.value.add(stepIndex)
  }
}

const closeModelDropdown = (stepIndex: number) => {
  openDropdownSteps.value.delete(stepIndex)
  highlightedIndices.value.set(stepIndex, -1)
  // Reset search filter to selected model name
  const step = displayData.steps[stepIndex]
  setSearchFilter(stepIndex, step.modelName ?? '')
}

const toggleModelDropdown = (stepIndex: number) => {
  if (isModelDropdownOpenForStep(stepIndex)) {
    closeModelDropdown(stepIndex)
  } else {
    openModelDropdown(stepIndex)
  }
}

const selectModelForStep = (modelName: string, stepIndex: number) => {
  const step = displayData.steps[stepIndex]
  step.modelName = modelName
  setSearchFilter(stepIndex, modelName)
  closeModelDropdown(stepIndex)
}

// Handle search input
const handleModelSearchInput = (event: Event, stepIndex: number) => {
  const target = event.target as HTMLInputElement
  setSearchFilter(stepIndex, target.value)
  openModelDropdown(stepIndex)
}

// Get highlighted index for a step
const getHighlightedIndex = (stepIndex: number): number => {
  return highlightedIndices.value.get(stepIndex) ?? -1
}

// Set highlighted index for a step
const setHighlightedIndex = (stepIndex: number, index: number) => {
  highlightedIndices.value.set(stepIndex, index)
}

// Keyboard navigation
const selectFirstFilteredModel = (stepIndex: number) => {
  const highlightedIndex = getHighlightedIndex(stepIndex)
  const filtered = getFilteredModelsForStep(stepIndex)

  // If a specific item is highlighted, select it
  if (highlightedIndex >= 0 && highlightedIndex < filtered.length) {
    selectModelForStep(filtered[highlightedIndex].value, stepIndex)
  } else if (highlightedIndex === -1) {
    // Select empty option
    selectModelForStep('', stepIndex)
  } else if (filtered.length > 0) {
    // Fallback to first item
    selectModelForStep(filtered[0].value, stepIndex)
  } else {
    const step = displayData.steps[stepIndex]
    if (!step.modelName) {
      selectModelForStep('', stepIndex)
    }
  }
}

const navigateModelDown = (stepIndex: number) => {
  if (!isModelDropdownOpenForStep(stepIndex)) {
    openModelDropdown(stepIndex)
    setHighlightedIndex(stepIndex, 0)
    return
  }
  const filtered = getFilteredModelsForStep(stepIndex)
  const totalItems = filtered.length + 1 // +1 for "no model selected" option
  const currentIndex = getHighlightedIndex(stepIndex)
  const newIndex = Math.min(currentIndex + 1, totalItems - 1)
  setHighlightedIndex(stepIndex, newIndex)
}

const navigateModelUp = (stepIndex: number) => {
  if (!isModelDropdownOpenForStep(stepIndex)) {
    openModelDropdown(stepIndex)
    const filtered = getFilteredModelsForStep(stepIndex)
    setHighlightedIndex(stepIndex, filtered.length)
    return
  }
  const currentIndex = getHighlightedIndex(stepIndex)
  const newIndex = Math.max(currentIndex - 1, -1)
  setHighlightedIndex(stepIndex, newIndex)
}

// Click outside to close dropdown
const handleClickOutside = (event: MouseEvent) => {
  const target = event.target as HTMLElement
  // Check if click is inside any model selector
  const modelSelector = target.closest('.model-selector')
  if (!modelSelector) {
    // Close all open dropdowns
    openDropdownSteps.value.clear()
  }
}

// Initialize search filters when steps change
watch(
  () => displayData.steps,
  newSteps => {
    newSteps.forEach((step, index) => {
      if (!modelSearchFilters.value.has(index)) {
        setSearchFilter(index, step.modelName ?? '')
      }
    })
  },
  { deep: true, immediate: true }
)

// Tool selection state - use sidebar store's availableTools
const showToolModal = ref(false)
const currentStepIndex = ref<number>(-1)

// Load available models
const loadAvailableModels = async () => {
  if (isLoadingModels.value) return

  isLoadingModels.value = true
  modelsLoadError.value = ''

  try {
    const response = await ConfigApiService.getAvailableModels()
    availableModels.value = response.options
  } catch (error) {
    console.error('Failed to load models:', error)
    modelsLoadError.value = error instanceof Error ? error.message : 'Failed to load models'
    availableModels.value = []
  } finally {
    isLoadingModels.value = false
  }
}

// Available tools are now loaded from sidebar store

// Tool selection functions
const showToolSelectionModal = (stepIndex: number) => {
  currentStepIndex.value = stepIndex
  showToolModal.value = true
  console.log('[JsonEditorV2] Available tools from props:', props.availableTools)
}

const handleToolSelectionConfirm = (selectedToolIds: string[]) => {
  if (currentStepIndex.value >= 0 && currentStepIndex.value < displayData.steps.length) {
    // Update the specific step's selected tool keys
    displayData.steps[currentStepIndex.value].selectedToolKeys = [...selectedToolIds]
  }
  showToolModal.value = false
  currentStepIndex.value = -1
}

const handleToolsFiltered = (stepIndex: number, filteredTools: string[]) => {
  if (stepIndex >= 0 && stepIndex < displayData.steps.length) {
    // Update the step's selected tool keys with filtered tools
    displayData.steps[stepIndex].selectedToolKeys = [...filteredTools]
  }
}

// Copy plan function
const handleCopyPlan = () => {
  emit('copy-plan')
}

// Initialize parsedData with default structure
const initializeParsedData = () => {
  try {
    // Clear any previous errors
    planTypeError.value = null

    // Initialize with default structure if not exists
    if (!displayData.title) {
      displayData.title = ''
    }
    displayData.directResponse = false // Always false for dynamic agent planning
  } catch (error) {
    const errorMessage = `Failed to initialize JsonEditorV2: ${error instanceof Error ? error.message : 'Unknown error'}`
    planTypeError.value = errorMessage
    console.error(errorMessage, error)
  }
}

// Watch for parsedData changes to validate structure
watch(
  () => displayData,
  newData => {
    try {
      // Soft validation for title - show warning but don't block the form
      if (!newData.title.trim()) {
        titleError.value = 'Title is required field'
      } else {
        titleError.value = ''
      }

      // Clear any structural errors
      planTypeError.value = null
    } catch (error) {
      planTypeError.value = `Invalid data structure: ${error instanceof Error ? error.message : 'Unknown error'}`
      titleError.value = ''
    }
  },
  { immediate: true, deep: true }
)

// Watch for props changes
watch(
  () => props.jsonContent,
  (newContent, oldContent) => {
    console.log(
      '[JsonEditorV2] Props watch triggered - jsonContent changed from',
      oldContent,
      'to',
      newContent
    )
    if (newContent && newContent !== oldContent) {
      console.log('[JsonEditorV2] Force parsing new jsonContent:', newContent)
      try {
        const parsed = JSON.parse(newContent)
        console.log('[JsonEditorV2] Parsed new jsonContent:', parsed)
        Object.assign(displayData, {
          title: parsed.title || '',
          steps: parsed.steps || [],
          directResponse: false,
          planTemplateId: parsed.planTemplateId || props.currentPlanTemplateId || '',
          planType: parsed.planType || 'dynamic_agent',
        })
        console.log('[JsonEditorV2] Updated displayData with new content:', displayData)
      } catch (error) {
        console.warn('[JsonEditorV2] Failed to parse new jsonContent:', error)
      }
    }
  },
  { immediate: true }
)

// Initialize on mount
onMounted(() => {
  console.log('[JsonEditorV2] Component mounted with jsonContent:', props.jsonContent)
  console.log(
    '[JsonEditorV2] Component mounted with currentPlanTemplateId:',
    props.currentPlanTemplateId
  )
  console.log('[JsonEditorV2] Component mounted with displayData:', displayData)

  // Force parse the current jsonContent to ensure it's processed
  if (props.jsonContent) {
    console.log('[JsonEditorV2] Force parsing jsonContent on mount:', props.jsonContent)
    // Call parseJsonToVisual directly to ensure it's processed
    try {
      const parsed = JSON.parse(props.jsonContent)
      console.log('[JsonEditorV2] Parsed jsonContent on mount:', parsed)
      Object.assign(displayData, {
        title: parsed.title || '',
        steps: parsed.steps || [],
        directResponse: false,
        planTemplateId: parsed.planTemplateId || props.currentPlanTemplateId || '',
        planType: parsed.planType || 'dynamic_agent',
      })
      console.log('[JsonEditorV2] Updated displayData on mount:', displayData)
    } catch (error) {
      console.warn('[JsonEditorV2] Failed to parse jsonContent on mount:', error)
    }
  }

  initializeParsedData()
  loadAvailableModels()

  // Add click outside listener
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  // Remove click outside listener
  document.removeEventListener('click', handleClickOutside)
})

const autoResizeTextarea = (event: Event) => {
  const textarea = event.target as HTMLTextAreaElement

  textarea.style.height = 'auto'

  const lineHeight = 20
  const lines = Math.ceil(textarea.scrollHeight / lineHeight)

  const minRows = 4
  const maxRows = 12
  const targetRows = Math.max(minRows, Math.min(maxRows, lines))

  const newHeight = targetRows * lineHeight
  textarea.style.height = `${newHeight}px`
  textarea.rows = targetRows

  if (lines > maxRows) {
    textarea.style.overflowY = 'auto'
  } else {
    textarea.style.overflowY = 'hidden'
  }
}

// Format table header preview
const formatTableHeader = (terminateColumns: string): string => {
  if (!terminateColumns.trim()) {
    return ''
  }

  // Split by comma and clean up each column name
  const columns = terminateColumns
    .split(',')
    .map(col => col.trim())
    .filter(col => col.length > 0)

  if (columns.length === 0) {
    return ''
  }

  // Format as |col1|col2|col3|
  return `|${columns.join('|')}|`
}
</script>

<style scoped>
.config-section {
  margin-bottom: 16px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px;
}

.section-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  color: #667eea;
  font-size: 13px;
  font-weight: 600;
  gap: 8px;
}

.section-actions {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

/* Error Section Styles */
.error-section {
  margin-bottom: 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: 8px;
  padding: 16px;
}

.error-message {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  color: #ef4444;
}

.error-content {
  flex: 1;
}

.error-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
}

.error-description {
  font-size: 12px;
  color: rgba(239, 68, 68, 0.8);
  line-height: 1.4;
}

/* Visual Editor Styles */
.visual-editor {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.plan-basic-info {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.form-row {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 10px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
}

.form-input,
.form-select,
.form-textarea {
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.3);
  color: white;
  font-size: 11px;
  font-family: inherit;
  transition: all 0.2s ease;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

/* Error state for form inputs */
.form-input.error,
.form-select.error,
.form-textarea.error {
  border-color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
}

/* Field error message */
.field-error-message {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: #ef4444;
  margin-top: 4px;
  padding: 4px 8px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 4px;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.readonly-input {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.6);
  cursor: not-allowed;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
}

.readonly-input:focus {
  border-color: rgba(255, 255, 255, 0.05);
  box-shadow: none;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  line-height: 1.4;
}

.form-textarea.auto-resize {
  resize: none;
  transition: height 0.2s ease;
  overflow-y: auto;
  max-height: 240px;
}

/* Model Selector Styles */
.model-selector-wrapper {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.model-selector {
  position: relative;
  flex: 1;
}

.model-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.model-search-input {
  flex: 1;
  padding-right: 32px;
}

.dropdown-arrow {
  position: absolute;
  right: 12px;
  color: rgba(255, 255, 255, 0.5);
  pointer-events: none;
  transition: transform 0.2s ease;
}

.dropdown-arrow.is-open {
  transform: rotate(180deg);
}

.model-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: rgba(0, 0, 0, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  max-height: 200px;
  overflow-y: auto;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.dropdown-item {
  padding: 8px 12px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  transition: all 0.2s ease;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.dropdown-item:last-child {
  border-bottom: none;
}

.dropdown-item:hover,
.dropdown-item.is-highlighted {
  background: rgba(102, 126, 234, 0.2);
  color: white;
}

.dropdown-item.is-selected {
  background: rgba(102, 126, 234, 0.15);
  color: #667eea;
  font-weight: 500;
}

.dropdown-item.disabled {
  color: rgba(255, 255, 255, 0.5);
  cursor: not-allowed;
  font-style: italic;
}

.dropdown-item.disabled.error {
  color: #ef4444;
}

.check-icon {
  color: #667eea;
  margin-left: 8px;
}

.model-selector.is-disabled .model-search-input {
  opacity: 0.5;
  cursor: not-allowed;
}

.tool-keys-display {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 32px;
  padding: 8px;
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
}

.tool-key-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tool-key-input {
  flex: 1;
  font-size: 10px;
}

.remove-tool-key-btn {
  width: 20px;
  height: 20px;
  background: transparent;
  border: none;
  border-radius: 2px;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.remove-tool-key-btn:hover {
  background: rgba(239, 68, 68, 0.2);
  color: #ef4444;
}

.no-tool-keys {
  color: rgba(255, 255, 255, 0.5);
  font-size: 10px;
  font-style: italic;
  text-align: center;
  padding: 8px;
}

.btn-add-tool-key {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: white;
  align-self: flex-start;
}

.btn-add-tool-key:hover:not(:disabled) {
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%);
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
}

/* Steps Section */
.steps-section {
  margin-bottom: 20px;
}

.steps-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.steps-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-count-badge {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
}

.error-badge {
  font-size: 10px;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  border: 1px solid rgba(239, 68, 68, 0.2);
  display: flex;
  align-items: center;
  gap: 2px;
}

.error-message {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  color: #ef4444;
  margin-top: 4px;
  padding: 4px 8px;
  background: rgba(239, 68, 68, 0.1);
  border-radius: 4px;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.steps-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.step-item {
  background: rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  overflow: hidden;
}

.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 16px;
  background: rgba(102, 126, 234, 0.1);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.step-number {
  font-weight: 600;
  color: #667eea;
  font-size: 11px;
  min-width: 20px;
}

.step-actions {
  display: flex;
  gap: 4px;
}

.step-content {
  padding: 16px;
}

.agent-selector {
  display: flex;
  gap: 8px;
  align-items: center;
}

.agent-select {
  flex: 1;
}

.btn-add-step {
  padding: 6px 8px;
  min-width: auto;
}

/* Empty State */
.empty-steps {
  text-align: center;
  padding: 40px 20px;
  color: rgba(255, 255, 255, 0.6);
}

.empty-icon {
  color: rgba(255, 255, 255, 0.3);
  margin-bottom: 12px;
}

/* JSON Preview */
.json-preview {
  margin-bottom: 16px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 6px;
  overflow: hidden;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.json-code {
  padding: 12px;
  margin: 0;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  background: transparent;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

/* Button Styles */
.btn {
  padding: 6px 12px;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
}

.btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  color: white;
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.btn-sm {
  padding: 4px 8px;
  font-size: 11px;
}

.btn-xs {
  padding: 2px 4px;
  font-size: 10px;
  min-width: auto;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #5a6fd8 0%, #6a4190 100%);
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.3);
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.7);
}

.btn-danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}

/* Preview Section Styles */
.preview-section {
  margin-top: 8px;
  padding: 8px 12px;
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.2);
  border-radius: 6px;
  font-size: 10px;
}

.preview-label {
  font-weight: 600;
  color: #667eea;
  margin-bottom: 4px;
  font-size: 9px;
}

.preview-content {
  color: white;
}

.preview-text {
  line-height: 1.4;
  color: white;
}

.preview-table-header {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  background: rgba(0, 0, 0, 0.3);
  padding: 2px 6px;
  border-radius: 3px;
  color: #ef4444;
  font-weight: 600;
  border: 1px solid rgba(239, 68, 68, 0.3);
}
</style>
