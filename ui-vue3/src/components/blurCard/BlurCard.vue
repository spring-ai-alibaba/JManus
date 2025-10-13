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
  <button class="blur-card" @click="handleClick" :style="wrapperStyle">
    <Icon v-if="content?.icon" :icon="content.icon" class="blur-card-icon" />
    <div v-if="content?.title || content?.description" class="blur-card-content">
      <h3>{{ content?.title }}</h3>
      <p>{{ content?.description }}</p>
    </div>
  </button>
</template>

<script setup lang="ts">
import { Icon } from '@iconify/vue'
import type { CSSProperties } from 'vue'

const props = defineProps<{
  content?: {
    title: string
    description: string
    icon: string
  }
  wrapperStyle?: CSSProperties
}>()

const emit = defineEmits(['clickCard'])

const handleClick = () => {
  console.log('[BlurCard] handleClick called with content:', props.content)
  emit('clickCard', props.content)
  console.log('[BlurCard] clickCard event emitted')
}
</script>

<style scoped>
.blur-card {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  background: rgba(var(--bg-primary-rgb), 0.6);
  border: 1px solid var(--border-secondary, var(--selection-bg, rgba(102, 126, 234, 0.3)));
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  text-align: left;
  display: flex;
  align-items: flex-start;
  gap: 16px;
  &:hover {
    border-color: rgba(102, 126, 234, 0.5);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
    transform: translateY(-2px);
  }

  .blur-card-icon {
    font-size: 24px;
    color:  var(--accent-primary);
    margin-top: 4px;
    flex-shrink: 0;
  }

  .blur-card-content {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  .blur-card-content h3 {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-primary);
    margin: 0 0 8px 0;
  }

  .blur-card-content p {
    font-size: 14px;
    color: var(--text-secondary);
    margin: 0;
    line-height: 1.4;
  }

}
</style>
