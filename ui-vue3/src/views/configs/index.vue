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
  <div class="config-container">
    <!-- Top title bar -->
    <div class="config-header">
      <!-- <h1>Configuration Center</h1> -->
      <div class="header-actions">
        <div class="header-actions-left">
          <button class="action-btn" @click="$router.push('/')">
            <Icon icon="carbon:arrow-left" />
            {{ $t('backHome') }}
          </button>
          <LanguageSwitcher />
        </div>
        <div class="header-actions-right">
          <NamespaceSwitch />
        </div>
      </div>
    </div>

    <!-- Main content area -->
    <div class="config-content">
      <!-- Left navigation -->
      <nav class="config-nav">
        <template v-for="(item, index) in categories">
          <div
            v-if="!item.disabled"
            :key="index"
            class="nav-item"
            :class="{ active: activeCategory === item.key }"
            @click="handleNavClick(item.key)"
          >
            <Icon
              :icon="item.icon"
              width="20"
              height="20"
              style="display: inline-block; flex-shrink: 0"
            />
            <span>{{ item.label }}</span>
          </div>
        </template>
      </nav>

      <!-- Right configuration details -->
      <div class="config-details">
        <component :is="activeComponent" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { useI18n } from 'vue-i18n'
import BasicConfig from './basicConfig.vue'
import ModelConfig from './modelConfig.vue'
import McpConfig from './mcpConfig.vue'
import DatabaseConfig from './databaseConfig.vue'
import LanguageSwitcher from '@/components/language-switcher/LanguageSwitcher.vue'
import NamespaceConfig from './namespaceConfig.vue'
import NamespaceSwitch from './components/namespaceSwitch.vue'
import PlanTemplateConfig from './planTemplateConfig.vue'

// Define component name for Vue linting rules
defineOptions({
  name: 'ConfigsIndex',
})

type ConfigComponent =
  | typeof BasicConfig
  | typeof ModelConfig
  | typeof McpConfig
  | typeof DatabaseConfig
  | typeof NamespaceConfig
  | typeof PlanTemplateConfig

interface CategoryMap {
  [key: string]: ConfigComponent | undefined
  basic: typeof BasicConfig
  model: typeof ModelConfig
  mcp: typeof McpConfig
  database: typeof DatabaseConfig
  namespace: typeof NamespaceConfig
  planTemplate: typeof PlanTemplateConfig
}

const { t } = useI18n()

const route = useRoute()
const router = useRouter()

const activeCategory = ref(route.params.category || 'basic')

const categoryMap: CategoryMap = {
  basic: BasicConfig,
  model: ModelConfig,
  mcp: McpConfig,
  database: DatabaseConfig,
  namespace: NamespaceConfig,
  planTemplate: PlanTemplateConfig,
}

const activeComponent = computed(() => {
  const categoryKey = activeCategory.value as string
  return categoryMap[categoryKey] || BasicConfig
})

const categories = computed(() => [
  { key: 'basic', label: t('config.categories.basic'), icon: 'carbon:settings' },
  { key: 'model', label: t('config.categories.model'), icon: 'carbon:build-image' },
  { key: 'mcp', label: t('config.categories.mcp'), icon: 'carbon:tool-box' },
  { key: 'database', label: t('config.categories.database'), icon: 'carbon:database' },
  {
    key: 'namespace',
    label: t('config.categories.namespace'),
    disabled: false,
    icon: 'carbon:batch-job',
  },
  {
    key: 'planTemplate',
    label: t('config.categories.planTemplate'),
    disabled: false,
    icon: 'carbon:document',
  },
])

watch(
  () => route.params.category,
  newCategory => {
    if (newCategory) {
      activeCategory.value = newCategory as string
    }
  }
)
const handleNavClick = (categoryKey: string) => {
  router.push({
    name: route.name as string,
    params: {
      ...route.params,
      category: categoryKey,
    },
    query: route.query,
  })
}
</script>

<style scoped>
.config-container {
  height: 100vh;
  background: rgba(255, 255, 255, 0.02);
  color: #fff;
}

.config-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.header-actions-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.config-header h1 {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-clip: text;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  font-size: 24px;
  font-weight: 600;
}

.config-content {
  display: flex;
  height: calc(100vh - 80px);
}

.config-nav {
  width: 242px;
  padding: 20px;
  border-right: 1px solid rgba(255, 255, 255, 0.1);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.nav-item :deep(svg) {
  color: rgba(255, 255, 255, 0.8);
  flex-shrink: 0;
  display: inline-block;
  vertical-align: middle;
}

.nav-item :deep(.iconify) {
  display: inline-block;
  width: 20px;
  height: 20px;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.nav-item.active {
  background: rgba(102, 126, 234, 0.1);
  border: 1px solid rgba(102, 126, 234, 0.2);
}

.config-details {
  flex: 1;
  padding: 24px 30px;
  overflow-y: auto;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  transition: all 0.3s;
}

.action-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}
</style>
