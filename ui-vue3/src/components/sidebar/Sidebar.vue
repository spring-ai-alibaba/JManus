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
  <div class="sidebar-wrapper" :style="{ width: props.width + '%' }">
    <div class="sidebar-content">
      <div class="sidebar-content-header">
        <div class="sidebar-content-title">{{ $t('sidebar.title') }}</div>
        <button class="new-task-btn" @click="handleCreateNewTemplate">
          <Icon icon="carbon:add" width="16" />
          {{ $t('sidebar.newPlan') }}
        </button>
      </div>

      <!-- Template List Content -->
      <div class="tab-content">
        <TemplateList />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// Define component name to satisfy Vue linting rules
defineOptions({
  name: 'SidebarPanel',
})

import { useAvailableToolsSingleton } from '@/composables/useAvailableTools'
import { usePlanTemplateConfigSingleton } from '@/composables/usePlanTemplateConfig'
import { sidebarStore } from '@/stores/sidebar'
import { templateStore } from '@/stores/templateStore'
import { Icon } from '@iconify/vue'
import { onMounted } from 'vue'
import TemplateList from './TemplateList.vue'

// Props
const props = defineProps<{
  width: number
}>()

// Available tools management
const availableToolsStore = useAvailableToolsSingleton()

// Template config management
const templateConfig = usePlanTemplateConfigSingleton()

// Handle create new template
const handleCreateNewTemplate = async () => {
  // Use default plan type or get from templateConfig
  const planType = templateConfig.getPlanType() || 'dynamic_agent'
  await templateStore.createNewTemplate(planType)

  // Load template config for new template
  const newTemplate = templateConfig.selectedTemplate.value
  if (newTemplate) {
    templateConfig.reset()
    templateConfig.setPlanType(newTemplate.planType || 'dynamic_agent')
    if (newTemplate.planTemplateId) {
      templateConfig.setPlanTemplateId(newTemplate.planTemplateId)
    }
    templateConfig.setTitle(newTemplate.title || '')
  }

  // Reload available tools to ensure fresh tool list
  console.log('[Sidebar] 🔄 Reloading available tools for new template')
  await availableToolsStore.loadAvailableTools()
}

// Lifecycle
onMounted(async () => {
  await templateStore.loadPlanTemplateList()
  availableToolsStore.loadAvailableTools() // Load available tools on sidebar mount
})

// Expose methods for parent component to call
defineExpose({
  loadPlanTemplateList: templateStore.loadPlanTemplateList,
  toggleSidebar: sidebarStore.toggleSidebar,
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

.sidebar-content {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease-in-out;
  flex: 1;

  .sidebar-content-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 0px;
    padding: 12px 10px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    overflow: hidden;

    .sidebar-content-title {
      font-size: 16px;
      font-weight: 600;
      color: #ffffff;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .new-task-btn {
      margin: 0px 14px;
      padding: 7px 14px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border: none;
      border-radius: 6px;
      color: white;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      transition: all 0.2s ease;
      white-space: nowrap;
      flex-shrink: 0;

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
      }
    }
  }

  .tab-content {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    overflow: hidden;
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
