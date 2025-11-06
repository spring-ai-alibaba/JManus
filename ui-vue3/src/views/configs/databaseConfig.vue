<!--
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
-->
<template>
  <ConfigPanel>
    <template #title>
      <h2>{{ t('config.databaseConfig.title') }}</h2>
    </template>

    <div class="database-layout">
      <!-- Datasource List -->
      <div class="config-list">
        <div class="list-header">
          <h3>{{ t('config.databaseConfig.configList') }}</h3>
          <span class="config-count">({{ configs.length }})</span>
        </div>

        <div class="search-box">
          <input
            v-model="searchQuery"
            type="text"
            :placeholder="t('config.databaseConfig.search')"
            class="search-input"
          />
          <Icon icon="carbon:search" class="search-icon" />
        </div>

        <div class="configs-container" v-if="!loading">
          <div
            v-for="config in filteredConfigs"
            :key="config.id ?? `config-${config.name}`"
            class="config-card"
            :class="{ active: selectedConfig?.id === config.id }"
            @click="selectConfig(config)"
          >
            <div class="config-card-header">
              <span class="config-name">{{ config.name }}</span>
              <div class="config-status-toggle" @click.stop="toggleConfigStatus(config)">
                <div class="status-toggle" :class="{ enabled: config.enable }">
                  <div class="toggle-thumb"></div>
                  <span class="toggle-label">{{
                    config.enable
                      ? t('config.databaseConfig.enabled')
                      : t('config.databaseConfig.disabled')
                  }}</span>
                </div>
              </div>
            </div>
            <div class="config-type-badge">
              <Icon icon="carbon:database" class="type-icon" />
              <span class="type-badge" :class="config.type.toLowerCase()">
                {{ config.type.toUpperCase() }}
              </span>
            </div>
            <div class="config-summary">
              <div class="summary-item">
                <span class="summary-label">{{ t('config.databaseConfig.url') }}:</span>
                <span class="summary-value">{{ truncateUrl(config.url) }}</span>
              </div>
              <div class="summary-item">
                <span class="summary-label">{{ t('config.databaseConfig.username') }}:</span>
                <span class="summary-value">{{ config.username }}</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="loading-state">
          <Icon icon="carbon:loading" class="loading-icon" />
          {{ t('common.loading') }}
        </div>

        <div v-if="!loading && filteredConfigs.length === 0" class="empty-state">
          <Icon icon="carbon:database" class="empty-icon" />
          <p>{{ searchQuery ? t('config.notFound') : t('config.databaseConfig.noConfigs') }}</p>
        </div>

        <!-- Add configuration button -->
        <div class="add-config-button-container">
          <button class="add-btn" @click="startAddConfig">
            <Icon icon="carbon:add" />
            {{ t('config.databaseConfig.newConfig') }}
          </button>
        </div>
      </div>

      <!-- Config Detail (Edit Mode) -->
      <div class="config-detail" v-if="selectedConfig">
        <div class="detail-header">
          <h3>{{ selectedConfig.name }}</h3>
          <div class="detail-actions">
            <button class="action-btn primary" @click="handleSave" :disabled="loading">
              <Icon icon="carbon:save" />
              {{ t('config.databaseConfig.save') }}
            </button>
            <button class="action-btn danger" @click="handleDelete" :disabled="loading">
              <Icon icon="carbon:trash-can" />
              {{ t('config.databaseConfig.delete') }}
            </button>
          </div>
        </div>
        <div class="detail-content">
          <DatasourceConfigForm
            :form-data="formData"
            :is-edit-mode="true"
            @update:form-data="formData = $event"
          />
        </div>
      </div>

      <!-- Add configuration form panel -->
      <div v-else-if="showAddForm" class="config-detail">
        <div class="detail-header">
          <h3>{{ t('config.databaseConfig.newConfig') }}</h3>
          <div class="detail-actions">
            <button class="action-btn secondary" @click="cancelAddConfig">
              {{ t('common.cancel') }}
            </button>
            <button class="action-btn primary" @click="handleCreate" :disabled="loading">
              <Icon icon="carbon:save" />
              {{ t('config.databaseConfig.create') }}
            </button>
          </div>
        </div>
        <div class="detail-content">
          <DatasourceConfigForm
            :form-data="formData"
            :is-edit-mode="false"
            @update:form-data="formData = $event"
          />
        </div>
      </div>

      <!-- Empty state when no config selected -->
      <div v-else class="config-detail empty-detail">
        <div class="empty-detail-content">
          <Icon icon="carbon:database" class="empty-detail-icon" />
          <p>{{ t('config.databaseConfig.selectOrCreate') }}</p>
        </div>
      </div>
    </div>
  </ConfigPanel>

  <!-- Delete Confirmation Modal -->
  <Modal v-model="showDeleteModal" :title="t('config.databaseConfig.deleteConfirm')">
    <p>{{ t('config.databaseConfig.deleteMessage', { name: configToDelete?.name }) }}</p>
    <template #footer>
      <button class="btn-secondary" @click="showDeleteModal = false">
        {{ t('common.cancel') }}
      </button>
      <button class="btn-danger" @click="confirmDelete" :disabled="deleting">
        <Icon v-if="deleting" icon="carbon:loading" class="loading-icon" />
        {{ t('config.databaseConfig.delete') }}
      </button>
    </template>
  </Modal>
</template>

<script setup lang="ts">
import {
  DatasourceConfigApiService,
  type DatasourceConfig,
} from '@/api/datasource-config-api-service'
import Modal from '@/components/modal/index.vue'
import { useToast } from '@/plugins/useToast'
import DatasourceConfigForm from '@/views/configs/components/DatasourceConfigForm.vue'
import ConfigPanel from '@/views/configs/components/configPanel.vue'
import { Icon } from '@iconify/vue'
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const toast = useToast()

// Reactive data
const configs = ref<DatasourceConfig[]>([])
const selectedConfig = ref<DatasourceConfig | null>(null)
const showDeleteModal = ref(false)
const showAddForm = ref(false)
const searchQuery = ref('')
const loading = ref(false)
const deleting = ref(false)
const configToDelete = ref<DatasourceConfig | null>(null)

// Form data
const formData = ref<DatasourceConfig>({
  name: '',
  type: '',
  enable: true,
  url: '',
  driver_class_name: '',
  username: '',
  password: '',
})

// Computed property: Filtered configs
const filteredConfigs = computed(() => {
  if (!searchQuery.value.trim()) {
    return configs.value
  }

  const query = searchQuery.value.toLowerCase()
  return configs.value.filter(
    (config: DatasourceConfig) =>
      config.name.toLowerCase().includes(query) ||
      config.type.toLowerCase().includes(query) ||
      config.url.toLowerCase().includes(query)
  )
})

/**
 * Load all datasource configurations
 */
const loadConfigs = async () => {
  loading.value = true
  try {
    configs.value = await DatasourceConfigApiService.getAllConfigs()
  } catch (error) {
    console.error('Failed to load datasource configurations:', error)
    toast.error(
      `Failed to load configurations: ${error instanceof Error ? error.message : String(error)}`
    )
  } finally {
    loading.value = false
  }
}

/**
 * Select a configuration
 */
const selectConfig = (config: DatasourceConfig) => {
  selectedConfig.value = config
  showAddForm.value = false
  // Always set password to empty for security, but preserve password_set flag
  formData.value = {
    ...config,
    password: '', // Never send password to frontend
  }
}

/**
 * Start adding a new configuration
 */
const startAddConfig = () => {
  selectedConfig.value = null
  showAddForm.value = true
  formData.value = {
    name: '',
    type: '',
    enable: true,
    url: '',
    driver_class_name: '',
    username: '',
    password: '',
  }
}

/**
 * Cancel adding configuration
 */
const cancelAddConfig = () => {
  showAddForm.value = false
  formData.value = {
    name: '',
    type: '',
    enable: true,
    url: '',
    driver_class_name: '',
    username: '',
    password: '',
  }
}

/**
 * Handle save (update existing)
 */
const handleSave = async () => {
  if (!selectedConfig.value?.id) {
    toast.error('No configuration selected')
    return
  }

  // If password was not set before, password field must be provided (can be empty string)
  if (!formData.value.password_set && formData.value.password === undefined) {
    toast.error('Password is required')
    return
  }

  loading.value = true
  try {
    // Create a copy of formData for update
    const updateData: DatasourceConfig = {
      ...formData.value,
    }
    // Only include password in update if it was explicitly provided (including empty string)
    if (formData.value.password === undefined) {
      delete updateData.password
    }
    await DatasourceConfigApiService.updateConfig(selectedConfig.value.id, updateData)
    toast.success('Configuration updated successfully')
    await loadConfigs()
    // Reload selected config
    const updated = await DatasourceConfigApiService.getConfigById(selectedConfig.value.id)
    selectConfig(updated)
  } catch (error) {
    console.error('Failed to update configuration:', error)
    toast.error(
      `Failed to update configuration: ${error instanceof Error ? error.message : String(error)}`
    )
  } finally {
    loading.value = false
  }
}

/**
 * Handle create (new configuration)
 */
const handleCreate = async () => {
  // Validate required fields (password can be empty string, but field must be provided)
  if (
    !formData.value.name ||
    !formData.value.type ||
    !formData.value.url ||
    !formData.value.driver_class_name ||
    !formData.value.username ||
    formData.value.password === undefined
  ) {
    toast.error('Please fill in all required fields')
    return
  }

  loading.value = true
  try {
    const created = await DatasourceConfigApiService.createConfig(formData.value)
    toast.success('Configuration created successfully')
    await loadConfigs()
    selectConfig(created)
    showAddForm.value = false
  } catch (error) {
    console.error('Failed to create configuration:', error)
    toast.error(
      `Failed to create configuration: ${error instanceof Error ? error.message : String(error)}`
    )
  } finally {
    loading.value = false
  }
}

/**
 * Handle delete
 */
const handleDelete = () => {
  if (!selectedConfig.value) {
    return
  }
  configToDelete.value = selectedConfig.value
  showDeleteModal.value = true
}

/**
 * Confirm delete
 */
const confirmDelete = async () => {
  if (!configToDelete.value?.id) {
    return
  }

  deleting.value = true
  try {
    await DatasourceConfigApiService.deleteConfig(configToDelete.value.id)
    toast.success('Configuration deleted successfully')
    await loadConfigs()
    selectedConfig.value = null
    showDeleteModal.value = false
    configToDelete.value = null
  } catch (error) {
    console.error('Failed to delete configuration:', error)
    toast.error(
      `Failed to delete configuration: ${error instanceof Error ? error.message : String(error)}`
    )
  } finally {
    deleting.value = false
  }
}

/**
 * Toggle config status
 */
const toggleConfigStatus = async (config: DatasourceConfig) => {
  if (!config.id) {
    return
  }

  try {
    const updated = { ...config, enable: !config.enable }
    await DatasourceConfigApiService.updateConfig(config.id, updated)
    toast.success(`Configuration ${updated.enable ? 'enabled' : 'disabled'} successfully`)
    await loadConfigs()
    if (selectedConfig.value?.id === config.id) {
      const reloaded = await DatasourceConfigApiService.getConfigById(config.id)
      selectConfig(reloaded)
    }
  } catch (error) {
    console.error('Failed to toggle config status:', error)
    toast.error(
      `Failed to toggle status: ${error instanceof Error ? error.message : String(error)}`
    )
  }
}

/**
 * Truncate URL for display
 */
const truncateUrl = (url: string) => {
  if (url.length > 50) {
    return url.substring(0, 47) + '...'
  }
  return url
}

// Load configs on mount
onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.database-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  height: 100%;
}

.config-list {
  border-right: 1px solid rgba(255, 255, 255, 0.1);
  padding-right: 24px;
  overflow-y: auto;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.list-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

.config-count {
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 8px;
  border-radius: 10px;
}

.search-box {
  position: relative;
  margin-bottom: 24px;
}

.search-input {
  width: 100%;
  padding: 10px 36px 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  transition: all 0.3s ease;
}

.search-input:focus {
  outline: none;
  border-color: rgba(102, 126, 234, 0.5);
  background: rgba(255, 255, 255, 0.08);
}

.search-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.search-icon {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: rgba(255, 255, 255, 0.6);
  font-size: 18px;
  pointer-events: none;
}

.configs-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.config-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.config-card:hover {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.15);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.config-card.active {
  border-color: rgba(102, 126, 234, 0.5);
  background: rgba(102, 126, 234, 0.1);
  box-shadow: 0 0 0 1px rgba(102, 126, 234, 0.2);
}

.config-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.config-name {
  font-weight: 600;
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
}

.config-status-toggle {
  display: flex;
  align-items: center;
}

.status-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.05);
}

.status-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
}

.status-toggle.enabled {
  color: #4caf50;
}

.status-toggle.enabled .toggle-label {
  color: #4caf50;
}

.toggle-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
  transition: color 0.3s ease;
}

.config-type-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.type-icon {
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
}

.type-badge {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
}

.type-badge.mysql {
  background: rgba(0, 117, 143, 0.3);
  color: #4dd0e1;
  border: 1px solid rgba(0, 117, 143, 0.5);
}

.type-badge.h2 {
  background: rgba(30, 136, 229, 0.3);
  color: #64b5f6;
  border: 1px solid rgba(30, 136, 229, 0.5);
}

.type-badge.postgresql {
  background: rgba(51, 103, 145, 0.3);
  color: #90caf9;
  border: 1px solid rgba(51, 103, 145, 0.5);
}

.type-badge.oracle {
  background: rgba(244, 67, 54, 0.3);
  color: #ef5350;
  border: 1px solid rgba(244, 67, 54, 0.5);
}

.type-badge.sqlserver {
  background: rgba(156, 39, 176, 0.3);
  color: #ce93d8;
  border: 1px solid rgba(156, 39, 176, 0.5);
}

.config-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  align-items: flex-start;
}

.summary-label {
  font-weight: 500;
  color: rgba(255, 255, 255, 0.6);
  min-width: 70px;
}

.summary-value {
  color: rgba(255, 255, 255, 0.9);
  word-break: break-all;
  font-family: monospace;
  background: rgba(255, 255, 255, 0.05);
  padding: 2px 6px;
  border-radius: 3px;
}

.add-config-button-container {
  margin-top: 24px;
}

.add-btn {
  width: 100%;
  padding: 12px;
  background: rgba(76, 175, 80, 0.2);
  border: 1px solid rgba(76, 175, 80, 0.4);
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #4caf50;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.add-btn:hover {
  background: rgba(76, 175, 80, 0.3);
  border-color: rgba(76, 175, 80, 0.6);
  transform: translateY(-1px);
}

.config-detail {
  padding-left: 24px;
  overflow-y: auto;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.detail-header h3 {
  margin: 0;
  font-size: 20px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
}

.detail-actions {
  display: flex;
  gap: 12px;
}

.action-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: all 0.3s ease;
}

.action-btn.primary {
  background: rgba(76, 175, 80, 0.2);
  border: 1px solid rgba(76, 175, 80, 0.4);
  color: #4caf50;
}

.action-btn.primary:hover:not(:disabled) {
  background: rgba(76, 175, 80, 0.3);
  border-color: rgba(76, 175, 80, 0.6);
  transform: translateY(-1px);
}

.action-btn.danger {
  background: rgba(244, 67, 54, 0.2);
  border: 1px solid rgba(244, 67, 54, 0.4);
  color: #ef5350;
}

.action-btn.danger:hover:not(:disabled) {
  background: rgba(244, 67, 54, 0.3);
  border-color: rgba(244, 67, 54, 0.6);
  transform: translateY(-1px);
}

.action-btn.secondary {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.8);
}

.action-btn.secondary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.95);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
}

.detail-content {
  padding: 20px 0;
}

.loading-state,
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: rgba(255, 255, 255, 0.6);
}

.loading-icon,
.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
  color: rgba(255, 255, 255, 0.4);
}

.empty-detail {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.empty-detail-content {
  text-align: center;
  color: rgba(255, 255, 255, 0.5);
}

.empty-detail-icon {
  font-size: 64px;
  margin-bottom: 20px;
  opacity: 0.3;
  color: rgba(255, 255, 255, 0.3);
}

.btn-secondary,
.btn-danger {
  padding: 10px 20px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.btn-secondary {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.8);
}

.btn-secondary:hover {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.95);
}

.btn-danger {
  background: rgba(244, 67, 54, 0.2);
  border: 1px solid rgba(244, 67, 54, 0.4);
  color: #ef5350;
}

.btn-danger:hover:not(:disabled) {
  background: rgba(244, 67, 54, 0.3);
  border-color: rgba(244, 67, 54, 0.6);
  transform: translateY(-1px);
}

.btn-danger:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
}
</style>
