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

import { reactive, ref, computed } from 'vue'
import type {
  PlanTemplateConfigVO,
  StepConfig,
  ToolConfigVO,
  InputSchemaParam,
} from '@/types/plan-template'
import { PlanTemplateApiService } from '@/api/plan-template-with-tool-api-service'

/**
 * Composable for managing PlanTemplateConfigVO
 * Provides getter and setter methods for all properties
 */
export function usePlanTemplateConfig() {
  // Reactive state for PlanTemplateConfigVO
  const config = reactive<PlanTemplateConfigVO>({
    title: '',
    steps: [],
    directResponse: false,
    planType: 'dynamic_agent',
    planTemplateId: '',
    readOnly: false,
    serviceGroup: '',
  })

  // Loading state
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const getTitle = () => config.title
  const getSteps = () => config.steps || []
  const getDirectResponse = () => config.directResponse || false
  const getPlanType = () => config.planType || 'dynamic_agent'
  const getPlanTemplateId = () => config.planTemplateId || ''
  const getReadOnly = () => config.readOnly || false
  const getServiceGroup = () => config.serviceGroup || ''
  const getToolConfig = () => config.toolConfig

  // Setters
  const setTitle = (title: string) => {
    config.title = title
  }

  const setSteps = (steps: StepConfig[]) => {
    config.steps = steps || []
  }

  const addStep = (step: StepConfig) => {
    if (!config.steps) {
      config.steps = []
    }
    config.steps.push(step)
  }

  const removeStep = (index: number) => {
    if (config.steps && index >= 0 && index < config.steps.length) {
      config.steps.splice(index, 1)
    }
  }

  const updateStep = (index: number, step: StepConfig) => {
    if (config.steps && index >= 0 && index < config.steps.length) {
      config.steps[index] = { ...config.steps[index], ...step }
    }
  }

  const setDirectResponse = (directResponse: boolean) => {
    config.directResponse = directResponse
  }

  const setPlanType = (planType: string) => {
    config.planType = planType
  }

  const setPlanTemplateId = (planTemplateId: string) => {
    config.planTemplateId = planTemplateId
  }

  const setReadOnly = (readOnly: boolean) => {
    config.readOnly = readOnly
  }

  const setServiceGroup = (serviceGroup: string) => {
    config.serviceGroup = serviceGroup
  }

  const setToolConfig = (toolConfig: ToolConfigVO | undefined) => {
    if (toolConfig === undefined) {
      delete config.toolConfig
    } else {
      config.toolConfig = toolConfig
    }
  }

  // ToolConfig getters
  const getToolName = () => config.toolConfig?.toolName || ''
  const getToolDescription = () => config.toolConfig?.toolDescription || ''
  const getToolServiceGroup = () => config.toolConfig?.serviceGroup || ''
  const getEnableInternalToolcall = () => config.toolConfig?.enableInternalToolcall ?? true
  const getEnableHttpService = () => config.toolConfig?.enableHttpService ?? false
  const getEnableMcpService = () => config.toolConfig?.enableMcpService ?? false
  const getPublishStatus = () => config.toolConfig?.publishStatus || 'PUBLISHED'
  const getInputSchema = () => config.toolConfig?.inputSchema || []

  // ToolConfig setters
  const setToolName = (toolName: string) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.toolName = toolName
  }

  const setToolDescription = (toolDescription: string) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.toolDescription = toolDescription
  }

  const setToolServiceGroup = (serviceGroup: string) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.serviceGroup = serviceGroup
  }

  const setEnableInternalToolcall = (enable: boolean) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableInternalToolcall = enable
  }

  const setEnableHttpService = (enable: boolean) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableHttpService = enable
  }

  const setEnableMcpService = (enable: boolean) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.enableMcpService = enable
  }

  const setPublishStatus = (status: string) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.publishStatus = status
  }

  const setInputSchema = (inputSchema: InputSchemaParam[]) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    config.toolConfig.inputSchema = inputSchema || []
  }

  const addInputSchemaParam = (param: InputSchemaParam) => {
    if (!config.toolConfig) {
      config.toolConfig = {}
    }
    if (!config.toolConfig.inputSchema) {
      config.toolConfig.inputSchema = []
    }
    config.toolConfig.inputSchema.push(param)
  }

  const removeInputSchemaParam = (index: number) => {
    if (
      config.toolConfig?.inputSchema &&
      index >= 0 &&
      index < config.toolConfig.inputSchema.length
    ) {
      config.toolConfig.inputSchema.splice(index, 1)
    }
  }

  const updateInputSchemaParam = (index: number, param: InputSchemaParam) => {
    if (
      config.toolConfig?.inputSchema &&
      index >= 0 &&
      index < config.toolConfig.inputSchema.length
    ) {
      config.toolConfig.inputSchema[index] = { ...config.toolConfig.inputSchema[index], ...param }
    }
  }

  // Get full config
  const getConfig = (): PlanTemplateConfigVO => {
    return { ...config }
  }

  // Set full config
  const setConfig = (newConfig: PlanTemplateConfigVO) => {
    const updatedConfig: PlanTemplateConfigVO = {
      title: newConfig.title || '',
      steps: newConfig.steps || [],
      directResponse: newConfig.directResponse || false,
      planType: newConfig.planType || 'dynamic_agent',
      planTemplateId: newConfig.planTemplateId || '',
      readOnly: newConfig.readOnly || false,
      serviceGroup: newConfig.serviceGroup || '',
    }
    if (newConfig.toolConfig) {
      updatedConfig.toolConfig = { ...newConfig.toolConfig }
    }
    Object.assign(config, updatedConfig)
  }

  // Reset to default
  const reset = () => {
    Object.assign(config, {
      title: '',
      steps: [],
      directResponse: false,
      planType: 'dynamic_agent',
      planTemplateId: '',
      readOnly: false,
      serviceGroup: '',
    })
    // Remove toolConfig if it exists
    if ('toolConfig' in config) {
      delete config.toolConfig
    }
    error.value = null
  }

  // Load from API
  const load = async (planTemplateId: string) => {
    if (!planTemplateId) {
      error.value = 'Plan template ID is required'
      return false
    }

    try {
      isLoading.value = true
      error.value = null

      const loadedConfig = await PlanTemplateApiService.getPlanTemplateConfigVO(planTemplateId)
      setConfig(loadedConfig)
      return true
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load plan template config'
      console.error('Failed to load plan template config:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  // Save to API
  const save = async (): Promise<boolean> => {
    if (!config.planTemplateId) {
      error.value = 'Plan template ID is required'
      return false
    }

    try {
      isLoading.value = true
      error.value = null

      const result = await PlanTemplateApiService.createOrUpdatePlanTemplateWithTool(getConfig())
      return result.success
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to save plan template config'
      console.error('Failed to save plan template config:', err)
      return false
    } finally {
      isLoading.value = false
    }
  }

  // Validation
  const validate = (): { isValid: boolean; errors: string[] } => {
    const errors: string[] = []

    if (!config.planTemplateId?.trim()) {
      errors.push('Plan template ID is required')
    }

    if (!config.title?.trim()) {
      errors.push('Title is required')
    }

    if (!config.steps || config.steps.length === 0) {
      errors.push('At least one step is required')
    }

    return {
      isValid: errors.length === 0,
      errors,
    }
  }

  // Computed properties
  const isValid = computed(() => validate().isValid)
  const hasToolConfig = computed(() => config.toolConfig !== undefined)

  return {
    // State
    config,
    isLoading,
    error,

    // Getters
    getTitle,
    getSteps,
    getDirectResponse,
    getPlanType,
    getPlanTemplateId,
    getReadOnly,
    getServiceGroup,
    getToolConfig,
    getToolName,
    getToolDescription,
    getToolServiceGroup,
    getEnableInternalToolcall,
    getEnableHttpService,
    getEnableMcpService,
    getPublishStatus,
    getInputSchema,
    getConfig,

    // Setters
    setTitle,
    setSteps,
    addStep,
    removeStep,
    updateStep,
    setDirectResponse,
    setPlanType,
    setPlanTemplateId,
    setReadOnly,
    setServiceGroup,
    setToolConfig,
    setToolName,
    setToolDescription,
    setToolServiceGroup,
    setEnableInternalToolcall,
    setEnableHttpService,
    setEnableMcpService,
    setPublishStatus,
    setInputSchema,
    addInputSchemaParam,
    removeInputSchemaParam,
    updateInputSchemaParam,
    setConfig,

    // Actions
    reset,
    load,
    save,
    validate,

    // Computed
    isValid,
    hasToolConfig,
  }
}

