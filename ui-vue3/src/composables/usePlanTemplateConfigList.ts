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

import { ref, computed } from 'vue'
import type { PlanTemplateConfigVO } from '@/types/plan-template'
import { PlanTemplateApiService } from '@/api/plan-template-with-tool-api-service'

/**
 * Composable for managing a list of PlanTemplateConfigVO
 * Provides methods to load and manage plan template list
 */
export function usePlanTemplateConfigList() {
  // State
  const configList = ref<PlanTemplateConfigVO[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  /**
   * Get list view items (returns PlanTemplateConfigVO directly)
   */
  const getListViewItems = computed((): PlanTemplateConfigVO[] => {
    return configList.value
  })

  /**
   * Get service group mapping (templateId -> serviceGroup)
   */
  const getServiceGroupMap = computed((): Map<string, string> => {
    const map = new Map<string, string>()
    configList.value.forEach(config => {
      const planTemplateId = config.planTemplateId
      if (planTemplateId) {
        const serviceGroup = config.serviceGroup || config.toolConfig?.serviceGroup || ''
        if (serviceGroup) {
          map.set(planTemplateId, serviceGroup)
        }
      }
    })
    return map
  })

  /**
   * Get config by plan template ID
   */
  const getConfigById = (planTemplateId: string): PlanTemplateConfigVO | undefined => {
    return configList.value.find(config => config.planTemplateId === planTemplateId)
  }

  /**
   * Get list view item by plan template ID
   */
  const getListViewItemById = (planTemplateId: string): PlanTemplateConfigVO | undefined => {
    return getConfigById(planTemplateId)
  }

  /**
   * Load all plan template configs from API
   */
  const load = async (): Promise<boolean> => {
    try {
      isLoading.value = true
      error.value = null

      const loadedConfigs = await PlanTemplateApiService.getAllPlanTemplateConfigVOs()
      configList.value = loadedConfigs
      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load plan template config list'
      console.error('Failed to load plan template config list:', err)
      configList.value = []
      return false
    } finally {
      isLoading.value = false
    }
  }

  /**
   * Reset to empty list
   */
  const reset = () => {
    configList.value = []
    error.value = null
  }

  /**
   * Refresh the list (reload from API)
   */
  const refresh = async (): Promise<boolean> => {
    return await load()
  }

  return {
    // State
    configList,
    isLoading,
    error,

    // Computed
    getListViewItems,
    getServiceGroupMap,

    // Methods
    getConfigById,
    getListViewItemById,
    load,
    reset,
    refresh,
  }
}

