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
  <div v-if="modelValue" class="modal-overlay" @click="handleCancel">
    <div class="modal-content" @click.stop>
      <div class="modal-header">
        <h3>{{ $t('sidebar.saveConfirmationTitle') }}</h3>
        <button class="close-btn" @click="handleCancel">
          <Icon icon="carbon:close" width="16" />
        </button>
      </div>
      <div class="modal-body">
        <p>{{ $t('sidebar.saveConfirmationMessage') }}</p>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="handleCancel">
          {{ $t('common.cancel') }}
        </button>
        <button class="btn btn-primary" @click="handleSave" :disabled="isSaving">
          <Icon v-if="isSaving" icon="carbon:loading" width="16" class="spinning" />
          {{ isSaving ? $t('sidebar.saving') : $t('common.save') }}
        </button>
        <button class="btn btn-danger" @click="handleContinueWithoutSave">
          {{ $t('sidebar.continueWithoutSave') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { ref } from 'vue'

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'save'): void
  (e: 'continue'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const isSaving = ref(false)

const handleCancel = () => {
  emit('update:modelValue', false)
}

const handleSave = async () => {
  isSaving.value = true
  try {
    emit('save')
    emit('update:modelValue', false)
  } finally {
    isSaving.value = false
  }
}

const handleContinueWithoutSave = () => {
  emit('continue')
  emit('update:modelValue', false)
}
</script>

<style scoped>
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

.modal-body p {
  margin: 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
  line-height: 1.5;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
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

.btn-danger {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626 0%, #b91c1c 100%);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
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
