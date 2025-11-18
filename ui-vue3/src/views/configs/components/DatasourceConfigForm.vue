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
  <form class="datasource-config-form" @submit.prevent>
    <div class="form-item">
      <label>{{ $t('config.databaseConfig.name') }} <span class="required">*</span></label>
      <input
        :value="formData.name || ''"
        @input="handleInput('name', $event)"
        type="text"
        class="config-input"
        :placeholder="$t('config.databaseConfig.namePlaceholder')"
        :disabled="props.isEditMode ?? false"
      />
    </div>

    <div class="form-item">
      <label>{{ $t('config.databaseConfig.type') }} <span class="required">*</span></label>
      <select
        :value="formData.type || ''"
        @change="handleSelectChange('type', $event)"
        class="config-input"
      >
        <option value="">{{ $t('config.databaseConfig.selectType') }}</option>
        <option value="mysql">MySQL</option>
        <option value="h2">H2</option>
        <option value="postgresql">PostgreSQL</option>
        <option value="oracle">Oracle</option>
        <option value="sqlserver">SQL Server</option>
      </select>
    </div>

    <div class="form-item">
      <label>{{ $t('config.databaseConfig.enable') }}</label>
      <div class="toggle-container">
        <label class="toggle-switch">
          <input
            type="checkbox"
            :checked="formData.enable"
            @change="handleToggleChange('enable', $event)"
          />
          <span class="toggle-slider"></span>
        </label>
        <span class="toggle-label">
          {{
            formData.enable
              ? $t('config.databaseConfig.enabled')
              : $t('config.databaseConfig.disabled')
          }}
        </span>
      </div>
    </div>

    <div class="form-item">
      <label>{{ $t('config.databaseConfig.url') }} <span class="required">*</span></label>
      <input
        v-model="urlDisplayValue"
        type="text"
        class="config-input"
        :placeholder="$t('config.databaseConfig.urlPlaceholder')"
      />
      <div class="form-hint">
        {{ $t('config.databaseConfig.urlHint') }}
      </div>
    </div>

    <div class="form-item">
      <label
        >{{ $t('config.databaseConfig.driverClassName') }} <span class="required">*</span></label
      >
      <input
        :value="formData.driver_class_name || ''"
        @input="handleInput('driver_class_name', $event)"
        type="text"
        class="config-input"
        :placeholder="$t('config.databaseConfig.driverClassNamePlaceholder')"
        readonly
      />
      <div class="form-hint">{{ $t('config.databaseConfig.driverClassNameHint') }}</div>
    </div>

    <div class="form-item">
      <label>{{ $t('config.databaseConfig.username') }} <span class="required">*</span></label>
      <input
        :value="formData.username || ''"
        @input="handleInput('username', $event)"
        type="text"
        class="config-input"
        :placeholder="$t('config.databaseConfig.usernamePlaceholder')"
      />
    </div>

    <div class="form-item">
      <label>
        {{ $t('config.databaseConfig.password') }}
        <span v-if="!formData.password_set" class="required">*</span>
      </label>
      <input
        :value="formData.password || ''"
        @input="handleInput('password', $event)"
        type="password"
        class="config-input"
        :placeholder="$t('config.databaseConfig.passwordPlaceholder')"
      />
      <div
        v-if="
          formData.password_set ||
          (formData.password !== undefined &&
            formData.password !== null &&
            formData.password !== '')
        "
        class="password-status-hint password-set"
      >
        <Icon icon="carbon:checkmark-filled" />
        {{ $t('config.databaseConfig.passwordSetHint') }}
      </div>
      <div
        v-else-if="!formData.password_set && (!formData.password || formData.password === '')"
        class="password-status-hint password-not-set"
      >
        <Icon icon="carbon:warning" />
        {{ $t('config.databaseConfig.passwordNotSetHint') }}
      </div>
    </div>

    <div class="form-item">
      <button
        class="test-connection-btn"
        @click="testConnection"
        :disabled="testingConnection || !canTestConnection"
      >
        <Icon v-if="testingConnection" icon="carbon:loading" class="spinning" />
        <Icon v-else icon="carbon:connection-signal" />
        {{
          testingConnection
            ? $t('config.databaseConfig.testing')
            : $t('config.databaseConfig.testConnection')
        }}
      </button>
      <div v-if="testResult" :class="['test-result', testResult.success ? 'success' : 'error']">
        <Icon :icon="testResult.success ? 'carbon:checkmark-filled' : 'carbon:error-filled'" />
        {{ testResult.message }}
      </div>
    </div>
  </form>
</template>

<script setup lang="ts">
import {
  DatasourceConfigApiService,
  type DatasourceConfig,
} from '@/api/datasource-config-api-service'
import { Icon } from '@iconify/vue'
import { computed, ref } from 'vue'

// Driver class name mapping based on database type
const DRIVER_CLASS_NAMES: Record<string, string> = {
  mysql: 'com.mysql.cj.jdbc.Driver',
  h2: 'org.h2.Driver',
  postgresql: 'org.postgresql.Driver',
  oracle: 'oracle.jdbc.OracleDriver',
  sqlserver: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
}

// JDBC URL prefix mapping based on database type
const JDBC_URL_PREFIXES: Record<string, string> = {
  mysql: 'jdbc:mysql://',
  h2: 'jdbc:h2:',
  postgresql: 'jdbc:postgresql://',
  oracle: 'jdbc:oracle:thin:@',
  sqlserver: 'jdbc:sqlserver://',
}

interface Props {
  formData: DatasourceConfig
  isEditMode?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isEditMode: false,
})

const emit = defineEmits<{
  'update:formData': [data: DatasourceConfig]
}>()

const testingConnection = ref(false)
const testResult = ref<{ success: boolean; message: string } | null>(null)

// Track URL input value for display (without JDBC prefix)
const urlDisplayValue = computed({
  get: () => {
    if (!props.formData.url) {
      return ''
    }
    return getDisplayUrl(props.formData.url, props.formData.type || null)
  },
  set: (value: string) => {
    // When user types, store the raw value temporarily
    // We'll normalize it on blur
    if (props.formData.type) {
      const normalizedUrl = normalizeUrl(value, props.formData.type)
      emit('update:formData', {
        ...props.formData,
        url: normalizedUrl,
      })
    } else {
      emit('update:formData', {
        ...props.formData,
        url: value,
      })
    }
  },
})

const canTestConnection = computed(() => {
  // Password can be empty string, so we check if it's defined or if password_set is true
  const hasPassword = props.formData.password !== undefined
  return (
    props.formData.url &&
    props.formData.driver_class_name &&
    props.formData.username &&
    (hasPassword || props.formData.password_set)
  )
})

/**
 * Normalize URL by adding JDBC prefix if needed
 * If user enters "localhost:3306/dbname", it becomes "jdbc:mysql://localhost:3306/dbname"
 */
const normalizeUrl = (url: string, dbType: string | null): string => {
  if (!url || !dbType) {
    return url
  }

  const prefix = JDBC_URL_PREFIXES[dbType.toLowerCase()]
  if (!prefix) {
    return url
  }

  // If URL already starts with jdbc:, return as is
  if (url.toLowerCase().startsWith('jdbc:')) {
    return url
  }

  // Add JDBC prefix
  return prefix + url
}

/**
 * Get display URL (remove JDBC prefix for display)
 */
const getDisplayUrl = (url: string, dbType: string | null): string => {
  if (!url || !dbType) {
    return url || ''
  }

  const prefix = JDBC_URL_PREFIXES[dbType.toLowerCase()]
  if (!prefix) {
    return url
  }

  // If URL starts with the prefix, remove it for display
  if (url.toLowerCase().startsWith(prefix.toLowerCase())) {
    return url.substring(prefix.length)
  }

  return url
}

const handleInput = (field: keyof DatasourceConfig, event: Event) => {
  const target = event.target as HTMLInputElement
  const value = target.value

  const newFormData = {
    ...props.formData,
    [field]: value,
  }
  // When password is entered, set password_set to true
  if (field === 'password' && value !== undefined && value !== null) {
    newFormData.password_set = true
  }
  emit('update:formData', newFormData)
  // Clear test result when form data changes
  if (testResult.value) {
    testResult.value = null
  }
}

const handleSelectChange = (field: keyof DatasourceConfig, event: Event) => {
  const target = event.target as HTMLSelectElement
  const newValue = target.value

  // Auto-fill driver class name when database type is selected
  if (field === 'type' && newValue && DRIVER_CLASS_NAMES[newValue.toLowerCase()]) {
    const newFormData = {
      ...props.formData,
      [field]: newValue,
      driver_class_name: DRIVER_CLASS_NAMES[newValue.toLowerCase()],
    }

    // Normalize URL when database type changes
    if (newFormData.url) {
      // Get the display URL (without any JDBC prefix)
      const displayUrl = getDisplayUrl(newFormData.url, props.formData.type || null)
      // Normalize with new database type
      newFormData.url = normalizeUrl(displayUrl, newValue)
    }

    emit('update:formData', newFormData)
  } else {
    emit('update:formData', {
      ...props.formData,
      [field]: newValue,
    })
  }
  // Clear test result when form data changes
  if (testResult.value) {
    testResult.value = null
  }
}

const handleToggleChange = (field: keyof DatasourceConfig, event: Event) => {
  const target = event.target as HTMLInputElement
  emit('update:formData', {
    ...props.formData,
    [field]: target.checked,
  })
}

const testConnection = async () => {
  if (!canTestConnection.value) {
    return
  }

  // If password is set but not provided for testing, require password input
  // If password_set is true but password is undefined, we need password from user
  if (props.formData.password_set && props.formData.password === undefined) {
    testResult.value = {
      success: false,
      message: 'Please enter password to test connection',
    }
    return
  }

  testingConnection.value = true
  testResult.value = null

  try {
    const result = await DatasourceConfigApiService.testConnection(props.formData)
    testResult.value = result
  } catch (error) {
    testResult.value = {
      success: false,
      message: error instanceof Error ? error.message : String(error),
    }
  } finally {
    testingConnection.value = false
  }
}
</script>

<style scoped>
.datasource-config-form {
  padding: 0;
}

.form-item {
  margin-bottom: 24px;
}

.form-item label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.form-item .required {
  color: #ef5350;
  margin-left: 2px;
}

.config-input {
  width: 100%;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  transition: all 0.3s ease;
}

.config-input:focus {
  outline: none;
  border-color: rgba(102, 126, 234, 0.5);
  background: rgba(255, 255, 255, 0.08);
}

.config-input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.config-input:disabled {
  background: rgba(255, 255, 255, 0.03);
  border-color: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.5);
  cursor: not-allowed;
}

.config-input select {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 10px 12px;
}

.config-input select:focus {
  outline: none;
  border-color: rgba(102, 126, 234, 0.5);
  background: rgba(255, 255, 255, 0.08);
}

.toggle-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 50px;
  height: 24px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.2);
  transition: all 0.4s ease;
  border-radius: 24px;
}

.toggle-slider:before {
  position: absolute;
  content: '';
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: all 0.4s ease;
  border-radius: 50%;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.toggle-switch input:checked + .toggle-slider {
  background-color: rgba(76, 175, 80, 0.6);
}

.toggle-switch input:checked + .toggle-slider:before {
  transform: translateX(26px);
  background-color: #4caf50;
}

.toggle-switch:hover .toggle-slider {
  background-color: rgba(255, 255, 255, 0.3);
}

.toggle-switch:hover input:checked + .toggle-slider {
  background-color: rgba(76, 175, 80, 0.8);
}

.toggle-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  transition: color 0.3s ease;
}

.form-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 4px;
}

.password-status-hint {
  margin-top: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.password-status-hint.password-set {
  background: rgba(76, 175, 80, 0.15);
  border: 1px solid rgba(76, 175, 80, 0.3);
  color: #4caf50;
}

.password-status-hint.password-not-set {
  background: rgba(255, 152, 0, 0.15);
  border: 1px solid rgba(255, 152, 0, 0.3);
  color: #ff9800;
}

.password-status-hint svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.test-connection-btn {
  width: 100%;
  padding: 12px;
  background: rgba(102, 126, 234, 0.2);
  border: 1px solid rgba(102, 126, 234, 0.4);
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #667eea;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 0.3s ease;
}

.test-connection-btn:hover:not(:disabled) {
  background: rgba(102, 126, 234, 0.3);
  border-color: rgba(102, 126, 234, 0.6);
  transform: translateY(-1px);
}

.test-connection-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
}

.test-result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.test-result.success {
  background: rgba(76, 175, 80, 0.2);
  border: 1px solid rgba(76, 175, 80, 0.4);
  color: #4caf50;
}

.test-result.error {
  background: rgba(244, 67, 54, 0.2);
  border: 1px solid rgba(244, 67, 54, 0.4);
  color: #ef5350;
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
