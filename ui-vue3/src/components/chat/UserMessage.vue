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
  <div class="user-message" :data-message-id="message.id">
    <div class="user-content">
      <div class="message-text">
        {{ message.content }}
      </div>
      
      <!-- Attachments if any -->
      <div v-if="message.attachments?.length" class="attachments">
        <div
          v-for="(attachment, index) in message.attachments"
          :key="index"
          class="attachment-item"
        >
          <Icon icon="carbon:document" class="attachment-icon" />
          <span class="attachment-name">{{ attachment.name }}</span>
          <span class="attachment-size">{{ formatFileSize(attachment.size) }}</span>
        </div>
      </div>
      
      <!-- Timestamp -->
      <div class="message-timestamp">
        {{ formatTimestamp(message.timestamp) }}
      </div>
    </div>
    
    <!-- Message status indicator -->
    <div v-if="message.error" class="message-status error">
      <Icon icon="carbon:warning" class="status-icon" />
      <span class="status-text">{{ message.error }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import { useMessageFormatting } from './composables/useMessageFormatting'
import type { ChatMessage } from './composables/useChatMessages'

interface Props {
  message: ChatMessage
}

defineProps<Props>()

const { formatTimestamp, formatFileSize } = useMessageFormatting()
</script>

<style lang="less" scoped>
.user-message {
  background: rgba(102, 126, 234, 0.15);
  border: 1px solid var(--border-secondary, var(--selection-bg, rgba(102, 126, 234, 0.3)));
  border-radius: 16px 16px 4px 16px;
  padding: 16px 20px;
  max-width: 80%;
  align-self: flex-end;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  animation: fadeInUp 0.3s ease-out;
  position: relative;
  overflow: hidden;
}

.user-message::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, var(--accent-primary, var(--accent-primary, #667eea)), #764ba2);
}

.user-message-content {
  color: var(--text-primary, var(--text-primary, #ffffff));
  line-height: 1.6;
  font-size: 16px;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-message-info {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.7));
  font-size: 12px;
}

.user-message-timestamp {
  font-size: 12px;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.6));
}

.user-message-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.7));
}

.user-message-status.sent {
  color: var(--success, var(--success, #22c55e));
}

.user-message-status.sending {
  color: var(--warning, var(--warning, #fbbf24));
}

.user-message-status.failed {
  color: var(--error, #ff6b6b);
}

.user-message-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.user-message-action-btn {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid var(--border-primary, var(--scrollbar-thumb, rgba(255, 255, 255, 0.2)));
  border-radius: 6px;
  color: var(--text-tertiary, rgba(255, 255, 255, 0.7));
  padding: 4px 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.user-message-action-btn:hover {
  background: var(--scrollbar-thumb, rgba(255, 255, 255, 0.2));
  color: var(--text-primary, rgba(255, 255, 255, 0.9));
  border-color: var(--border-primary, var(--scrollbar-thumb-hover, rgba(255, 255, 255, 0.3)));
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .user-message {
    .user-content {
      max-width: 85%;
      padding: 10px 14px;
      border-radius: 16px 16px 4px 16px;
      
      .message-text {
        font-size: 13px;
      }
    }
  }
}
</style>
