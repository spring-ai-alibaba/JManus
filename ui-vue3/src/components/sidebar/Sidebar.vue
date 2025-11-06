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
  <div
    class="sidebar-wrapper"
    :class="{ 'sidebar-wrapper-collapsed': sidebarStore.isCollapsed }"
    :style="{ width: sidebarWidth + '%' }"
  >
    <div class="sidebar-content">
      <div class="sidebar-content-header">
        <div class="sidebar-content-title">{{ $t('sidebar.title') }}</div>
      </div>

      <!-- Tab Switcher -->
      <div class="tab-switcher">
        <button
          class="tab-button"
          :class="{ active: sidebarStore.currentTab === 'list' }"
          @click="sidebarStore.switchToTab('list')"
        >
          <Icon icon="carbon:list" width="16" />
          {{ $t('sidebar.templateList') }}
        </button>
        <button
          class="tab-button"
          :class="{ active: sidebarStore.currentTab === 'config' }"
          @click="sidebarStore.switchToTab('config')"
          :disabled="!sidebarStore.selectedTemplate"
        >
          <Icon icon="carbon:settings" width="16" />
          {{ $t('sidebar.configuration') }}
        </button>
      </div>

      <!-- List Tab Content -->
      <div v-if="sidebarStore.currentTab === 'list'" class="tab-content">
        <div class="new-task-section">
          <button
            class="new-task-btn"
            @click="() => sidebarStore.createNewTemplate(sidebarStore.planType)"
          >
            <Icon icon="carbon:add" width="16" />
            {{ $t('sidebar.newPlan') }}
            <span class="shortcut">⌘ K</span>
          </button>
        </div>

        <div class="sidebar-content-list">
          <!-- Loading state -->
          <div v-if="sidebarStore.isLoading" class="loading-state">
            <Icon icon="carbon:circle-dash" width="20" class="spinning" />
            <span>{{ $t('sidebar.loading') }}</span>
          </div>

          <!-- Error state -->
          <div v-else-if="sidebarStore.errorMessage" class="error-state">
            <Icon icon="carbon:warning" width="20" />
            <span>{{ sidebarStore.errorMessage }}</span>
            <button @click="sidebarStore.loadPlanTemplateList" class="retry-btn">
              {{ $t('sidebar.retry') }}
            </button>
          </div>

          <!-- Empty state -->
          <div v-else-if="sidebarStore.planTemplateList.length === 0" class="empty-state">
            <Icon icon="carbon:document" width="32" />
            <span>{{ $t('sidebar.noTemplates') }}</span>
          </div>

          <!-- Plan template list -->
          <div
            v-else
            v-for="template in sidebarStore.sortedTemplates"
            :key="template.id"
            class="sidebar-content-list-item"
            :class="{
              'sidebar-content-list-item-active':
                template.id === sidebarStore.currentPlanTemplateId,
            }"
            @click="sidebarStore.selectTemplate(template)"
          >
            <div class="task-icon">
              <Icon icon="carbon:document" width="20" />
            </div>
            <div class="task-details">
              <div class="task-title">{{ template.title || $t('sidebar.unnamedPlan') }}</div>
              <div class="task-preview">
                {{ getTaskPreviewText(template) }}
              </div>
            </div>
            <div class="task-time">
              {{
                getRelativeTimeString(
                  sidebarStore.parseDateTime(template.updateTime || template.createTime)
                )
              }}
            </div>
            <div class="task-actions">
              <button
                class="delete-task-btn"
                :title="$t('sidebar.deleteTemplate')"
                @click.stop="sidebarStore.deleteTemplate(template)"
              >
                <Icon icon="carbon:close" width="16" />
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Config Tab Content -->
      <div v-else-if="sidebarStore.currentTab === 'config'" class="tab-content config-tab">
        <div v-if="sidebarStore.selectedTemplate" class="config-container">
          <!-- Template Info Header -->
          <div class="template-info-header">
            <div class="template-info">
              <h3>{{ sidebarStore.selectedTemplate.title || $t('sidebar.unnamedPlan') }}</h3>
              <span class="template-id">ID: {{ sidebarStore.selectedTemplate.id }}</span>
            </div>
            <button class="back-to-list-btn" @click="sidebarStore.switchToTab('list')">
              <Icon icon="carbon:arrow-left" width="16" />
            </button>
          </div>

          <!-- Section 2: JSON Editor (Conditional based on plan type) -->
          <!-- Use JsonEditorV2 for dynamic_agent type -->
          <JsonEditorV2
            v-if="sidebarStore.planType === 'dynamic_agent'"
            :key="sidebarStore.currentPlanTemplateId || 'default'"
            :json-content="sidebarStore.jsonContent"
            :can-rollback="sidebarStore.canRollback"
            :can-restore="sidebarStore.canRestore"
            :is-generating="sidebarStore.isGenerating"
            :is-executing="sidebarStore.isExecuting"
            :current-plan-template-id="sidebarStore.currentPlanTemplateId || ''"
            @rollback="handleRollback"
            @restore="handleRestore"
            @save="handleSaveTemplate"
            @copy-plan="handleCopyPlan"
            @update:json-content="(value: string) => (sidebarStore.jsonContent = value)"
          />

          <!-- Use JsonEditor for simple or other types -->
          <JsonEditor
            v-else
            :key="'simple-' + (sidebarStore.currentPlanTemplateId || 'default')"
            :json-content="sidebarStore.jsonContent"
            :can-rollback="sidebarStore.canRollback"
            :can-restore="sidebarStore.canRestore"
            :is-generating="sidebarStore.isGenerating"
            :is-executing="sidebarStore.isExecuting"
            :current-plan-template-id="sidebarStore.currentPlanTemplateId || ''"
            @rollback="handleRollback"
            @restore="handleRestore"
            @save="handleSaveTemplate"
            @update:json-content="(value: string) => (sidebarStore.jsonContent = value)"
          />

          <!-- Section 3: Execution Controller -->
          <ExecutionController
            ref="executionControllerRef"
            :current-plan-template-id="sidebarStore.currentPlanTemplateId || ''"
            :is-executing="sidebarStore.isExecuting"
            :is-generating="sidebarStore.isGenerating"
            :show-publish-button="showPublishButton"
            :tool-info="currentToolInfo"
            @execute-plan="handleExecutePlan"
            @publish-mcp-service="handlePublishMcpService"
            @clear-params="handleClearExecutionParams"
            @update-execution-params="handleUpdateExecutionParams"
            @save-before-execute="handleSaveBeforeExecute"
          />
        </div>
      </div>
    </div>

    <!-- Sidebar Resizer -->
    <div
      class="sidebar-resizer"
      @mousedown="startResize"
      @dblclick="resetSidebarWidth"
      :title="$t('sidebar.resizeHint')"
    >
      <div class="resizer-line"></div>
    </div>
  </div>

  <!-- Publish MCP Service Modal -->
  <PublishServiceModal
    ref="publishMcpModalRef"
    v-model="showPublishMcpModal"
    :plan-template-id="sidebarStore.currentPlanTemplateId || ''"
    :plan-description="sidebarStore.selectedTemplate?.description || ''"
    @published="handleMcpServicePublished"
  />

  <!-- Copy Plan Modal -->
  <div v-if="showCopyPlanModal" class="modal-overlay" @click="closeCopyPlanModal">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>{{ $t('sidebar.copyPlan') }}</h3>
        <button class="close-btn" @click="closeCopyPlanModal">
          <Icon icon="carbon:close" width="16" />
        </button>
      </div>
      <div class="modal-body">
        <div class="form-row">
          <label class="form-label">{{ $t('sidebar.newPlanTitle') }}</label>
          <input
            v-model="newPlanTitle"
            type="text"
            class="form-input"
            :placeholder="$t('sidebar.enterNewPlanTitle')"
            @keyup.enter="confirmCopyPlan"
          />
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="closeCopyPlanModal">
          {{ $t('common.cancel') }}
        </button>
        <button
          class="btn btn-primary"
          @click="confirmCopyPlan"
          :disabled="!newPlanTitle.trim() || isCopyingPlan"
        >
          <Icon v-if="isCopyingPlan" icon="carbon:loading" width="16" class="spinning" />
          {{ isCopyingPlan ? $t('sidebar.copying') : $t('sidebar.copyPlan') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { CoordinatorToolConfig, CoordinatorToolVO } from '@/api/coordinator-tool-api-service'
import { CoordinatorToolApiService } from '@/api/coordinator-tool-api-service'
import PublishServiceModal from '@/components/publish-service-modal/PublishServiceModal.vue'
import { useToast } from '@/plugins/useToast'
import { sidebarStore } from '@/stores/sidebar'
import type { PlanExecutionRequestPayload } from '@/types/plan-execution'
import type { PlanTemplate } from '@/types/plan-template'
import { Icon } from '@iconify/vue'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ExecutionController from './ExecutionController.vue'
import JsonEditor from './JsonEditor.vue'
import JsonEditorV2 from './JsonEditorV2.vue'

const { t } = useI18n()
const toast = useToast()

// Sidebar width management
const sidebarWidth = ref(80) // Default width percentage
const isResizing = ref(false)
const startX = ref(0)
const startWidth = ref(0)

// Component refs
const executionControllerRef = ref<InstanceType<typeof ExecutionController> | null>(null)

// Tool information state
const currentToolInfo = ref<CoordinatorToolVO>({
  toolName: '',
  toolDescription: '',
  planTemplateId: '',
  inputSchema: '[]',
  enableHttpService: false,
  enableMcpService: false,
  enableInternalToolcall: false,
  serviceGroup: '',
})

// Store tool information for all templates
const templateToolInfo = ref<Partial<Record<string, CoordinatorToolVO>>>({})
const publishMcpModalRef = ref<InstanceType<typeof PublishServiceModal> | null>(null)

// CoordinatorTool configuration
const coordinatorToolConfig = ref<CoordinatorToolConfig>({
  enabled: true,
  success: true,
})

// Computed property: whether to show publish MCP service button
const showPublishButton = computed(() => {
  return coordinatorToolConfig.value.enabled
})

// Watch for changes in currentPlanTemplateId and jsonContent
watch(
  () => sidebarStore.currentPlanTemplateId,
  (newId, oldId) => {
    console.log('[Sidebar] currentPlanTemplateId changed from', oldId, 'to', newId)
  }
)

watch(
  () => sidebarStore.jsonContent,
  (newContent, oldContent) => {
    console.log('[Sidebar] jsonContent changed from', oldContent, 'to', newContent)
  }
)

// Load CoordinatorTool configuration
const loadCoordinatorToolConfig = async () => {
  try {
    const config = await CoordinatorToolApiService.getCoordinatorToolConfig()
    coordinatorToolConfig.value = config
  } catch (error) {
    console.error('Failed to load CoordinatorTool configuration:', error)
    // Use default configuration
    coordinatorToolConfig.value = {
      enabled: true,
      success: false,
      message: error instanceof Error ? error.message : 'Unknown error',
    }
  }
}

// Use pinia store
// Use TS object-implemented sidebarStore
// Use sidebarStore instance directly, no pinia needed

// Emits - Keep some events for communication with external components
const emit = defineEmits<{
  planExecutionRequested: [payload: PlanExecutionRequestPayload]
}>()

const handleSaveTemplate = async () => {
  try {
    const saveResult = await sidebarStore.saveTemplate()

    if (saveResult?.duplicate) {
      toast.success(
        t('sidebar.saveCompleted', {
          message: saveResult.message,
          versionCount: saveResult.versionCount,
        })
      )
    } else if (saveResult?.saved) {
      toast.success(
        t('sidebar.saveSuccess', {
          message: saveResult.message,
          versionCount: saveResult.versionCount,
        })
      )
      // Refresh parameter requirements after successful save
      refreshParameterRequirements()
    } else if (saveResult?.message) {
      toast.success(t('sidebar.saveStatus', { message: saveResult.message }))
    }
  } catch (error: any) {
    console.error('Failed to save plan modifications:', error)
    toast.error(error.message || t('sidebar.saveFailed'))
    throw error // Re-throw to allow caller to handle
  }
}

const handleSaveBeforeExecute = async () => {
  console.log('[Sidebar] 💾 Save before execute requested')
  await handleSaveTemplate()
}

// Method to refresh parameter requirements
const refreshParameterRequirements = async () => {
  // Add a delay to ensure the backend has processed the new template and committed the transaction
  await new Promise(resolve => setTimeout(resolve, 1000))

  console.log(
    '[Sidebar] 🔄 Refreshing parameter requirements for templateId:',
    sidebarStore.currentPlanTemplateId
  )

  // Use nextTick to ensure all reactive updates are complete
  await nextTick()

  // Get ExecutionController component through ref and call its refresh method
  if (executionControllerRef.value) {
    console.log('[Sidebar] 📞 Calling ExecutionController.loadParameterRequirements()')
    executionControllerRef.value.loadParameterRequirements()

    // Add a retry mechanism in case the first call fails due to timing
    setTimeout(() => {
      if (executionControllerRef.value) {
        console.log(
          '[Sidebar] 🔄 Retry: Calling ExecutionController.loadParameterRequirements() again'
        )
        executionControllerRef.value.loadParameterRequirements()
      }
    }, 2000) // Increased delay to 2 seconds for database transaction commit
  } else {
    console.warn('[Sidebar] ⚠️ ExecutionController ref not available')
  }

  // Refresh parameter requirements in PublishMcpServiceModal
  if (publishMcpModalRef.value) {
    console.log('[Sidebar] 📞 Calling PublishMcpServiceModal.loadParameterRequirements()')
    publishMcpModalRef.value.loadParameterRequirements()
  } else {
    console.warn('[Sidebar] ⚠️ PublishMcpServiceModal ref not available')
  }
}

// Version control handlers
const handleRollback = () => {
  try {
    if (typeof sidebarStore.rollbackVersion === 'function') {
      sidebarStore.rollbackVersion()
    } else {
      console.warn('rollbackVersion method is not available')
    }
  } catch (error) {
    console.error('Error during rollback operation:', error)
    toast.error(t('sidebar.rollbackFailed') || 'Rollback failed')
  }
}

const handleRestore = () => {
  try {
    if (typeof sidebarStore.restoreVersion === 'function') {
      sidebarStore.restoreVersion()
    } else {
      console.warn('restoreVersion method is not available')
    }
  } catch (error) {
    console.error('Error during restore operation:', error)
    toast.error(t('sidebar.restoreFailed') || 'Restore failed')
  }
}

const handleExecutePlan = async (payload: PlanExecutionRequestPayload) => {
  console.log(
    '[Sidebar] 🎯 handleExecutePlan called with payload:',
    JSON.stringify(payload, null, 2)
  )
  console.log('[Sidebar] 📊 Current sidebarStore state:', {
    currentPlanTemplateId: sidebarStore.currentPlanTemplateId,
    selectedTemplate: sidebarStore.selectedTemplate?.id,
    jsonContent: sidebarStore.jsonContent.substring(0, 100) + '...',
  })

  try {
    const planData = sidebarStore.preparePlanExecution()
    console.log('[Sidebar] 📋 Prepared plan data:', JSON.stringify(planData, null, 2))

    if (!planData) {
      console.log('[Sidebar] ❌ No plan data available, returning')
      return
    }

    // Always use the prepared plan data (which includes planTemplateId)
    // Add replacement parameters to plan data if provided
    const finalPlanData = {
      ...planData,
      uploadedFiles: payload.uploadedFiles,
      uploadKey: payload.uploadKey,
    }

    if (payload.replacementParams && Object.keys(payload.replacementParams).length > 0) {
      console.log('[Sidebar] 🔄 Processing replacement parameters:', payload.replacementParams)
      finalPlanData.replacementParams = payload.replacementParams
    }

    console.log('[Sidebar] ✅ Final plan data:', JSON.stringify(finalPlanData, null, 2))

    // Use the prepared plan data for the payload
    const finalPayload: PlanExecutionRequestPayload = {
      ...payload,
      title: finalPlanData.title,
      planData: finalPlanData.planData,
      params: finalPlanData.params,
      uploadedFiles: finalPlanData.uploadedFiles,
      uploadKey: finalPlanData.uploadKey,
    }

    console.log(
      '[Sidebar] 📤 Emitting planExecutionRequested with final payload:',
      JSON.stringify(finalPayload, null, 2)
    )
    emit('planExecutionRequested', finalPayload)

    console.log('[Sidebar] ✅ Event emitted successfully')
  } catch (error: any) {
    console.error('[Sidebar] ❌ Error executing plan:', error)
    toast.error(t('sidebar.executeFailed') + ': ' + error.message)
  } finally {
    console.log('[Sidebar] 🧹 Cleaning up after execution')
    sidebarStore.finishPlanExecution()
    // Clear execution parameters after successful execution
    sidebarStore.clearExecutionParams()
    // Also clear ExecutionController parameters
    if (executionControllerRef.value) {
      console.log('[Sidebar] 🧹 Calling ExecutionController.clearExecutionParams')
      executionControllerRef.value.clearExecutionParams()
    }
    console.log('[Sidebar] ✅ Cleanup completed')
  }
}

// MCP service publishing related state
const showPublishMcpModal = ref(false)

// Copy plan related state
const showCopyPlanModal = ref(false)
const newPlanTitle = ref('')
const isCopyingPlan = ref(false)

const handlePublishMcpService = () => {
  console.log('[Sidebar] Publish MCP service button clicked')
  console.log('[Sidebar] currentPlanTemplateId:', sidebarStore.currentPlanTemplateId)

  if (!sidebarStore.currentPlanTemplateId) {
    console.log('[Sidebar] No plan template selected, showing warning')
    toast.error(t('mcpService.selectPlanTemplateFirst'))
    return
  }

  console.log('[Sidebar] Opening publish MCP service modal')
  showPublishMcpModal.value = true
}

const handleMcpServicePublished = async (tool: CoordinatorToolVO | null) => {
  if (tool === null) {
    console.log('MCP service deleted successfully')
    toast.success(t('mcpService.deleteSuccess'))
    // Reset tool information
    currentToolInfo.value = {
      toolName: '',
      toolDescription: '',
      planTemplateId: '',
      inputSchema: '[]',
      enableHttpService: false,
      enableMcpService: false,
      enableInternalToolcall: false,
      serviceGroup: '',
    }
  } else {
    console.log('MCP service published successfully:', tool)
    toast.success(t('mcpService.publishSuccess'))
    // Update tool information
    currentToolInfo.value = {
      ...tool,
      toolName: tool.toolName,
      serviceGroup: tool.serviceGroup ?? '',
    }
  }

  // Reload available tools to include the newly published service
  console.log('[Sidebar] 🔄 Reloading available tools after service publish/delete')
  await sidebarStore.loadAvailableTools()

  // Reload tool info for all templates to update the task preview
  await loadAllTemplateToolInfo()
}

// Execution Controller event handlers
const handleClearExecutionParams = () => {
  sidebarStore.clearExecutionParams()
}

const handleUpdateExecutionParams = (params: string) => {
  sidebarStore.executionParams = params
}

// Copy plan handler functions
const handleCopyPlan = () => {
  console.log('[Sidebar] Copy plan clicked')

  if (!sidebarStore.selectedTemplate) {
    console.log('[Sidebar] No template selected, cannot copy')
    toast.error(t('sidebar.selectPlanFirst'))
    return
  }

  newPlanTitle.value = (sidebarStore.selectedTemplate.title ?? t('sidebar.unnamedPlan')) + ' (copy)'
  console.log('[Sidebar] Opening copy plan modal')
  showCopyPlanModal.value = true
}

const closeCopyPlanModal = () => {
  showCopyPlanModal.value = false
  newPlanTitle.value = ''
  isCopyingPlan.value = false
}

const confirmCopyPlan = async () => {
  if (!newPlanTitle.value.trim()) {
    toast.error(t('sidebar.titleRequired'))
    return
  }

  if (!sidebarStore.selectedTemplate || !sidebarStore.jsonContent) {
    toast.error(t('sidebar.noPlanToCopy'))
    return
  }

  isCopyingPlan.value = true

  try {
    const currentPlan = JSON.parse(sidebarStore.jsonContent)
    const newPlan = {
      ...currentPlan,
      title: newPlanTitle.value.trim(),
      planTemplateId: '', // New plan should not have the same template ID
    }

    const { PlanActApiService } = await import('@/api/plan-act-api-service')
    const result = await PlanActApiService.savePlanTemplate('', JSON.stringify(newPlan))

    if (result.saved) {
      toast.success(t('sidebar.copyPlanSuccess', { title: newPlanTitle.value.trim() }))
      await sidebarStore.loadPlanTemplateList()
      closeCopyPlanModal()
    } else {
      toast.error(t('sidebar.copyPlanFailed', { message: result.message || 'Unknown error' }))
    }
  } catch (error: any) {
    console.error('[Sidebar] Error copying plan:', error)
    toast.error(t('sidebar.copyPlanFailed', { message: error.message || 'Unknown error' }))
  } finally {
    isCopyingPlan.value = false
  }
}

// Load tool information when plan template changes
const loadToolInfo = async (planTemplateId: string | null) => {
  if (!planTemplateId) {
    currentToolInfo.value = {
      toolName: '',
      toolDescription: '',
      planTemplateId: '',
      inputSchema: '[]',
      enableHttpService: false,
      enableMcpService: false,
      enableInternalToolcall: false,
      serviceGroup: '',
    }
    return
  }

  try {
    const toolData = await CoordinatorToolApiService.getCoordinatorToolByTemplate(planTemplateId)
    if (toolData) {
      currentToolInfo.value = {
        ...toolData,
        toolName: toolData.toolName,
        serviceGroup: toolData.serviceGroup ?? '',
      }
    } else {
      // No tool found or not published, don't show any call examples
      currentToolInfo.value = {
        toolName: '',
        toolDescription: '',
        planTemplateId: planTemplateId,
        inputSchema: '[]',
        enableHttpService: false,
        enableMcpService: false,
        enableInternalToolcall: false,
        serviceGroup: '',
      }
    }
  } catch (error) {
    console.error('Failed to load tool information:', error)
    currentToolInfo.value = {
      toolName: '',
      toolDescription: '',
      planTemplateId: planTemplateId,
      inputSchema: '[]',
      enableHttpService: false,
      enableMcpService: false,
      enableInternalToolcall: false,
      serviceGroup: '',
    }
  }
}

// Utility functions
const getRelativeTimeString = (date: Date): string => {
  // Check if date is valid
  if (isNaN(date.getTime())) {
    console.warn('Invalid date received:', date)
    return t('time.unknown')
  }

  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMinutes = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMinutes < 1) return t('time.now')
  if (diffMinutes < 60) return t('time.minuteAgo', { count: diffMinutes })
  if (diffHours < 24) return t('time.hourAgo', { count: diffHours })
  if (diffDays < 30) return t('time.dayAgo', { count: diffDays })

  return date.toLocaleDateString('zh-CN')
}

// Get task preview text - show tool name if published, otherwise empty
const getTaskPreviewText = (template: PlanTemplate): string => {
  const toolInfo = templateToolInfo.value[template.id]
  if (!toolInfo) {
    return '' // Return empty string when no tools are published
  }
  return `${t('sidebar.publishedTool')}: ${toolInfo.toolName}`
}

// Load tool information for all templates
const loadAllTemplateToolInfo = async () => {
  for (const template of sidebarStore.planTemplateList) {
    try {
      const toolData = await CoordinatorToolApiService.getCoordinatorToolByTemplate(template.id)
      if (toolData?.toolName) {
        templateToolInfo.value[template.id] = toolData
      }
    } catch (error) {
      // Silently ignore errors for templates without published tools
      console.debug(`No tool found for template ${template.id}:`, error)
    }
  }
}

// Sidebar resize methods
const startResize = (e: MouseEvent) => {
  isResizing.value = true
  startX.value = e.clientX
  startWidth.value = sidebarWidth.value

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

  let newWidth = startWidth.value + deltaPercent

  // Limit sidebar width between 15% and 100%
  newWidth = Math.max(15, Math.min(100, newWidth))

  sidebarWidth.value = newWidth
}

const handleMouseUp = () => {
  isResizing.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''

  // Save to localStorage
  localStorage.setItem('sidebarWidth', sidebarWidth.value.toString())
}

const resetSidebarWidth = () => {
  sidebarWidth.value = 80
  localStorage.setItem('sidebarWidth', '80')
}

// Watch for plan template changes to load tool information
watch(
  () => sidebarStore.currentPlanTemplateId,
  newPlanTemplateId => {
    loadToolInfo(newPlanTemplateId)
  },
  { immediate: true }
)

// Lifecycle
onMounted(async () => {
  await sidebarStore.loadPlanTemplateList()
  await loadAllTemplateToolInfo() // Load tool info after templates are loaded
  loadCoordinatorToolConfig()
  sidebarStore.loadAvailableTools() // Load available tools on sidebar mount

  // Restore sidebar width from localStorage
  const savedWidth = localStorage.getItem('sidebarWidth')
  if (savedWidth) {
    sidebarWidth.value = parseFloat(savedWidth)
  }
})

onUnmounted(() => {
  // Clean up event listeners
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
})

// Expose methods for parent component to call
defineExpose({
  loadPlanTemplateList: sidebarStore.loadPlanTemplateList,
  toggleSidebar: sidebarStore.toggleSidebar,
  currentPlanTemplateId: sidebarStore.currentPlanTemplateId,
})
</script>

<style scoped>
.sidebar-wrapper {
  position: relative;
  height: 100vh;
  background: rgba(255, 255, 255, 0.05);
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  transition: width 0.1s ease;
  overflow: hidden;
  display: flex;
}
.sidebar-wrapper-collapsed {
  border-right: none;
  width: 0 !important;
  /* transform: translateX(-100%); */

  .sidebar-content,
  .sidebar-resizer {
    opacity: 0;
    pointer-events: none;
  }
}

.sidebar-content {
  height: 100%;
  width: 100%;
  padding: 12px 0 12px 12px;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease-in-out;
  flex: 1;

  .sidebar-content-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    overflow: hidden;

    .sidebar-content-title {
      font-size: 20px;
      font-weight: 600;

      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }

  .tab-switcher {
    display: flex;
    margin-bottom: 16px;
    padding-right: 12px;
    background: rgba(255, 255, 255, 0.05);
    border-radius: 8px;
    padding: 4px;

    .tab-button {
      flex: 1;
      padding: 8px 12px;
      background: transparent;
      border: none;
      border-radius: 6px;
      color: rgba(255, 255, 255, 0.7);
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: all 0.2s ease;

      &:hover:not(:disabled) {
        background: rgba(255, 255, 255, 0.1);
        color: rgba(255, 255, 255, 0.9);
      }

      &.active {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        box-shadow: 0 2px 4px rgba(102, 126, 234, 0.3);
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
    }
  }

  .tab-content {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
  }

  .config-tab {
    .config-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow-y: auto;
      padding-right: 12px;

      .template-info-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 16px;
        padding: 12px;
        background: rgba(255, 255, 255, 0.05);
        border-radius: 8px;

        .template-info {
          flex: 1;
          min-width: 0;

          h3 {
            margin: 0 0 4px 0;
            font-size: 14px;
            font-weight: 600;
            color: white;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }

          .template-id {
            font-size: 11px;
            color: rgba(255, 255, 255, 0.5);
          }
        }

        .back-to-list-btn {
          width: 28px;
          height: 28px;
          background: transparent;
          border: none;
          border-radius: 4px;
          color: rgba(255, 255, 255, 0.7);
          cursor: pointer;
          display: flex;
          align-items: center;
          justify-content: center;
          transition: all 0.2s ease;

          &:hover {
            background: rgba(255, 255, 255, 0.1);
            color: white;
          }
        }
      }

      .json-editor {
        width: 100%;
        background: rgba(0, 0, 0, 0.3);
        border: 1px solid rgba(255, 255, 255, 0.2);
        border-radius: 6px;
        color: white;
        font-size: 12px;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        padding: 8px;
        resize: vertical;
        min-height: 100px;

        &:focus {
          outline: none;
          border-color: #667eea;
          box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
        }

        &::placeholder {
          color: rgba(255, 255, 255, 0.4);
        }
      }

      .json-editor {
        min-height: 200px;
        font-size: 11px;
        line-height: 1.5;
        white-space: pre-wrap;
        overflow-wrap: break-word;
        word-break: break-word;
        tab-size: 2;
        font-variant-ligatures: none;
      }
    }
  }
}

.new-task-section {
  margin-bottom: 16px;
  padding-right: 12px;

  .new-task-btn {
    width: 100%;
    padding: 12px 16px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    border: none;
    border-radius: 8px;
    color: white;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    transition: all 0.2s ease;

    &:hover {
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
    }

    .shortcut {
      font-size: 12px;
      opacity: 0.8;
      margin-left: auto;
    }
  }
}

.sidebar-content-list {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow-y: auto;
  padding-right: 12px;

  .loading-state,
  .error-state,
  .empty-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 32px 16px;
    color: rgba(255, 255, 255, 0.6);
    font-size: 14px;
    text-align: center;
    gap: 12px;

    .spinning {
      animation: spin 1s linear infinite;
    }

    .retry-btn {
      padding: 8px 16px;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 4px;
      color: white;
      cursor: pointer;
      font-size: 12px;
      transition: background-color 0.2s ease;

      &:hover {
        background: rgba(255, 255, 255, 0.2);
      }
    }
  }

  .sidebar-content-list-item {
    display: flex;
    align-items: flex-start;
    padding: 12px;
    margin-bottom: 8px;
    background: rgba(255, 255, 255, 0.05);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.2s ease;
    position: relative;

    &:hover {
      background: rgba(255, 255, 255, 0.1);
      border-color: rgba(255, 255, 255, 0.2);
      transform: translateY(-1px);
    }

    &.sidebar-content-list-item-active {
      border: 2px solid #667eea;
      background: rgba(102, 126, 234, 0.1);
    }

    .task-icon {
      margin-right: 12px;
      color: #667eea;
      flex-shrink: 0;
      margin-top: 2px;
    }

    .task-details {
      flex: 1;
      min-width: 0;

      .task-title {
        font-size: 14px;
        font-weight: 600;
        color: white;
        margin-bottom: 4px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .task-preview {
        font-size: 12px;
        color: rgba(255, 255, 255, 0.7);
        line-height: 1.4;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .task-time {
      font-size: 11px;
      color: rgba(255, 255, 255, 0.5);
      margin-left: 8px;
      flex-shrink: 0;
      position: absolute;
      top: 12px;
      right: 40px;
    }

    .task-actions {
      display: flex;
      align-items: center;
      margin-left: 8px;
      flex-shrink: 0;

      .delete-task-btn {
        width: 24px;
        height: 24px;
        background: transparent;
        border: none;
        border-radius: 4px;
        color: rgba(255, 255, 255, 0.5);
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s ease;
        position: absolute;
        top: 12px;
        right: 12px;

        &:hover {
          background: rgba(255, 0, 0, 0.2);
          color: #ff6b6b;
        }
      }
    }
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* Sidebar Resizer Styles */
.sidebar-resizer {
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

/* Copy Plan Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #1a1a1a;
  border-radius: 8px;
  padding: 0;
  min-width: 400px;
  max-width: 500px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.modal-header h3 {
  margin: 0;
  color: white;
  font-size: 16px;
  font-weight: 600;
}

.close-btn {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.close-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.modal-body {
  padding: 20px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.form-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-label {
  font-size: 14px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

.form-input {
  padding: 10px 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.3);
  color: white;
  font-size: 13px;
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2);
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.2s ease;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #5566dd 0%, #653b91 100%);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.spinning {
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
</style>
