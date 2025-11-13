/*
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
 */

import { ref } from 'vue'
import type { Tool } from '@/types/tool'
import { ToolApiService } from '@/api/tool-api-service'

export interface AvailableTool {
  key: string
  name: string
  description: string
  enabled: boolean
  serviceGroup: string
  selectable: boolean
}

/**
 * Composable for managing available tools
 * Provides state and methods for loading and managing available tools
 */
export function useAvailableTools() {
  // Available tools state
  const availableTools = ref<AvailableTool[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Load available tools from backend
  const loadAvailableTools = async () => {
    if (isLoading.value) {
      return // Avoid duplicate requests
    }

    isLoading.value = true
    error.value = null

    try {
      console.log('[useAvailableTools] Loading available tools...')
      const tools = await ToolApiService.getAvailableTools()
      console.log('[useAvailableTools] Loaded available tools:', tools)
      // Transform tools to ensure they have all required fields
      availableTools.value = tools.map((tool: Tool) => ({
        key: tool.key || '',
        name: tool.name || '',
        description: tool.description || '',
        enabled: tool.enabled || false,
        serviceGroup: tool.serviceGroup || 'default',
        selectable: tool.selectable,
      }))
    } catch (err) {
      console.error('[useAvailableTools] Error loading tools:', err)
      error.value = err instanceof Error ? err.message : 'Unknown error'
      availableTools.value = []
    } finally {
      isLoading.value = false
    }
  }

  // Reset tools state
  const reset = () => {
    availableTools.value = []
    isLoading.value = false
    error.value = null
  }

  return {
    // State
    availableTools,
    isLoading,
    error,

    // Actions
    loadAvailableTools,
    reset,
  }
}

// Singleton instance for global use
let singletonInstance: ReturnType<typeof useAvailableTools> | null = null

/**
 * Get or create singleton instance of useAvailableTools
 */
export function useAvailableToolsSingleton() {
  if (!singletonInstance) {
    singletonInstance = useAvailableTools()
  }
  return singletonInstance
}
