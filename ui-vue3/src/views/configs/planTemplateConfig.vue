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
  <div class="plan-template-panel">
    <div class="config-header">
      <div class="header-left">
        <h2>{{ $t('config.planTemplate.title') }}</h2>
        <div class="config-stats">
          <span class="stat-item">
            <span class="stat-label">{{ $t('config.planTemplate.totalTemplates') }}:</span>
            <span class="stat-value">{{ planTemplates.length }}</span>
          </span>
        </div>
      </div>
      <div class="header-right">
        <div class="import-export-actions">
          <button
            @click="handleExport"
            class="action-btn"
            :title="$t('config.planTemplate.export')"
            :disabled="loading || planTemplates.length === 0"
          >
            📤 {{ $t('config.planTemplate.export') }}
          </button>
          <label class="action-btn" :title="$t('config.planTemplate.import')">
            📥 {{ $t('config.planTemplate.import') }}
            <input
              type="file"
              accept=".json"
              @change="handleImport"
              style="display: none"
              :disabled="loading"
            />
          </label>
        </div>
      </div>
    </div>

    <!-- Loading Status -->
    <div v-if="initialLoading" class="loading-container">
      <div class="loading-spinner"></div>
      <p>{{ $t('config.loading') }}</p>
    </div>

    <!-- Plan Templates List -->
    <div v-else-if="planTemplates.length > 0" class="templates-list">
      <div
        v-for="template in planTemplates"
        :key="template.planTemplateId"
        class="template-item"
      >
        <div class="template-info">
          <div class="template-header">
            <h3 class="template-title">{{ template.title || $t('config.planTemplate.untitled') }}</h3>
            <span class="template-id">ID: {{ template.planTemplateId }}</span>
          </div>
          <div class="template-meta">
            <span v-if="template.serviceGroup" class="meta-item">
              <span class="meta-label">{{ $t('config.planTemplate.serviceGroup') }}:</span>
              <span class="meta-value">{{ template.serviceGroup }}</span>
            </span>
            <span v-if="template.planType" class="meta-item">
              <span class="meta-label">{{ $t('config.planTemplate.planType') }}:</span>
              <span class="meta-value">{{ template.planType }}</span>
            </span>
            <span v-if="template.steps && template.steps.length > 0" class="meta-item">
              <span class="meta-label">{{ $t('config.planTemplate.steps') }}:</span>
              <span class="meta-value">{{ template.steps.length }}</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="empty-state">
      <p>{{ $t('config.planTemplate.noTemplates') }}</p>
    </div>

    <!-- Message Prompt -->
    <transition name="message-fade">
      <div v-if="message.show" :class="['message-toast', message.type]">
        {{ message.text }}
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { PlanTemplateApiService } from '@/api/plan-template-with-tool-api-service'
import type { PlanTemplateConfigVO } from '@/types/plan-template'
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'

// Initialize i18n
const { t } = useI18n()

// Reactive data
const initialLoading = ref(true)
const loading = ref(false)
const planTemplates = ref<PlanTemplateConfigVO[]>([])

// Message Prompt
const message = reactive({
  show: false,
  text: '',
  type: 'success' as 'success' | 'error',
})

// Show message
const showMessage = (text: string, type: 'success' | 'error' = 'success') => {
  message.text = text
  message.type = type
  message.show = true

  setTimeout(() => {
    message.show = false
  }, 3000)
}

// Load all plan templates
const loadPlanTemplates = async () => {
  try {
    initialLoading.value = true
    const templates = await PlanTemplateApiService.getAllPlanTemplateConfigVOs()
    planTemplates.value = templates
    console.log(`Loaded ${templates.length} plan templates`)
  } catch (error) {
    console.error('Failed to load plan templates:', error)
    showMessage(t('config.planTemplate.loadFailed'), 'error')
    planTemplates.value = []
  } finally {
    initialLoading.value = false
  }
}

// Handle export
const handleExport = async () => {
  if (loading.value || planTemplates.value.length === 0) {
    return
  }

  try {
    loading.value = true
    const templates = await PlanTemplateApiService.exportAllPlanTemplates()

    // Create JSON string
    const dataStr = JSON.stringify(templates, null, 2)
    const dataBlob = new Blob([dataStr], { type: 'application/json' })

    // Create download link
    const link = document.createElement('a')
    link.href = URL.createObjectURL(dataBlob)
    link.download = `plan-templates-export-${new Date().toISOString().split('T')[0]}.json`
    link.click()

    // Clean up
    URL.revokeObjectURL(link.href)

    showMessage(t('config.planTemplate.exportSuccess'))
  } catch (error) {
    console.error('Failed to export plan templates:', error)
    const errorMessage =
      error instanceof Error ? error.message : t('config.planTemplate.exportFailed')
    showMessage(errorMessage, 'error')
  } finally {
    loading.value = false
  }
}

// Handle import
const handleImport = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]

  if (!file) {
    return
  }

  try {
    loading.value = true

    // Read file content
    const fileContent = await file.text()
    const templates = JSON.parse(fileContent) as PlanTemplateConfigVO[]

    // Validate that it's an array
    if (!Array.isArray(templates)) {
      throw new Error(t('config.planTemplate.invalidFormat'))
    }

    // Confirm import
    const confirmed = confirm(t('config.planTemplate.importConfirm'))
    if (!confirmed) {
      input.value = ''
      return
    }

    // Import templates
    const result = await PlanTemplateApiService.importPlanTemplates(templates)

    if (result.success) {
      const successMsg = t('config.planTemplate.importSuccess', {
        total: result.total,
        success: result.successCount,
        failed: result.failureCount,
      })
      showMessage(successMsg, result.failureCount > 0 ? 'error' : 'success')

      // Reload templates
      await loadPlanTemplates()
    } else {
      showMessage(t('config.planTemplate.importFailed'), 'error')
    }
  } catch (error) {
    console.error('Failed to import plan templates:', error)
    const errorMessage =
      error instanceof Error ? error.message : t('config.planTemplate.importFailed')
    showMessage(errorMessage, 'error')
  } finally {
    loading.value = false
    // Clear the input
    input.value = ''
  }
}

// Load templates when component is mounted
onMounted(() => {
  loadPlanTemplates()
})
</script>

<style scoped>
.plan-template-panel {
  position: relative;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.config-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
}

.header-left,
.header-right {
  display: flex;
  align-items: center;
}

.config-stats {
  display: flex;
  margin-left: 16px;
  gap: 12px;
}

.stat-item {
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.05);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.stat-label {
  color: rgba(255, 255, 255, 0.6);
  margin-right: 4px;
}

.stat-value {
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}

.import-export-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 4px;
  color: rgba(255, 255, 255, 0.8);
  padding: 6px 12px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.95);
  border-color: rgba(255, 255, 255, 0.25);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(255, 255, 255, 0.7);
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top: 2px solid #667eea;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.templates-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.template-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s ease;
}

.template-item:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.15);
}

.template-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.template-title {
  margin: 0;
  font-size: 16px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

.template-id {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-family: monospace;
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 6px;
  border-radius: 4px;
}

.template-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
}

.meta-label {
  color: rgba(255, 255, 255, 0.6);
}

.meta-value {
  color: rgba(255, 255, 255, 0.9);
  font-weight: 500;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: rgba(255, 255, 255, 0.5);
}

.message-toast {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 12px 20px;
  border-radius: 8px;
  color: white;
  font-weight: 500;
  z-index: 1000;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  transform: translateX(100%);
  animation: slide-in 0.3s ease-out forwards;
}

.message-toast.success {
  background: #10b981;
}

.message-toast.error {
  background: #ef4444;
}

.message-fade-enter-active,
.message-fade-leave-active {
  transition: all 0.3s ease;
}

.message-fade-enter-from {
  transform: translateX(100%);
  opacity: 0;
}

.message-fade-leave-to {
  transform: translateX(100%);
  opacity: 0;
}

@keyframes slide-in {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}
</style>

