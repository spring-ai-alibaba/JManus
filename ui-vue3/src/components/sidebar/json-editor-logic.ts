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

import { reactive, computed, watch, ref } from 'vue'
// import { useI18n } from 'vue-i18n' // Currently unused
import type { DisplayPlanData } from '@/types/plan-execution'
import { templateStore } from '@/stores/templateStore'

export interface JsonEditorProps {
  jsonContent: string
  canRollback: boolean
  canRestore: boolean
  isGenerating: boolean
  isExecuting: boolean
  hiddenFields?: string[]
  currentPlanTemplateId: string
}

export interface JsonEditorEmits {
  (e: 'rollback'): void
  (e: 'restore'): void
  (e: 'save'): void
  (e: 'update:jsonContent', value: string): void
}

/**
 * Business logic for JsonEditor component
 */
export function useJsonEditor(props: JsonEditorProps, emit: JsonEditorEmits) {
  // I18n
  // const { t } = useI18n() // Currently unused

  // State
  const showJsonPreview = ref(false)

  // Reactive display data - Based on DynamicAgentExecutionPlan structure
  const displayData = reactive<DisplayPlanData>({
    title: '',
    steps: [],
    directResponse: false, // Always false for dynamic agent planning
    planTemplateId: props.currentPlanTemplateId || '',
    planType: 'dynamic_agent',
  })

  /**
   * Parse JSON content into visual data
   * Maps backend DynamicAgentExecutionPlan structure to frontend DisplayPlanData
   */
  const parseJsonToVisual = (jsonContent: string) => {
    console.log('[JsonEditorLogic] parseJsonToVisual called with jsonContent:', jsonContent)
    console.log(
      '[JsonEditorLogic] parseJsonToVisual called with currentPlanTemplateId:',
      props.currentPlanTemplateId
    )

    try {
      if (!jsonContent) {
        console.log('[JsonEditorLogic] No jsonContent, resetting to default')
        // Reset to default - matches DynamicAgentExecutionPlan structure
        Object.assign(displayData, {
          title: '',
          steps: [],
          directResponse: false,
          planTemplateId: props.currentPlanTemplateId || '',
          planType: 'dynamic_agent',
        })
        console.log('[JsonEditorLogic] Reset displayData to:', displayData)
        return
      }

      const parsed = JSON.parse(jsonContent)
      console.log('[JsonEditorLogic] Parsed JSON:', parsed)

      // Map basic fields from DynamicAgentExecutionPlan
      displayData.title = parsed.title || ''
      displayData.directResponse = false // Always false for dynamic agent planning
      displayData.planTemplateId = parsed.planTemplateId || props.currentPlanTemplateId || ''
      displayData.planType = parsed.planType || 'dynamic_agent'

      // Parse steps - maps ExecutionStep structure
      displayData.steps = (parsed.steps || []).map((step: unknown) => {
        const stepObj = step as Record<string, unknown>
        return {
          stepRequirement: (stepObj.stepRequirement as string) || '',
          agentName: (stepObj.agentName as string) || '',
          modelName: (stepObj.modelName as string | null) || null, // Default to null if not specified
          selectedToolKeys: (stepObj.selectedToolKeys as string[]) || [],
          terminateColumns: (stepObj.terminateColumns as string) || '',
        }
      })

      console.log('[JsonEditorLogic] Updated displayData to:', displayData)
    } catch (error) {
      console.warn('Failed to parse JSON content:', error)
      // Reset to default when parsing fails to prevent stale data
      Object.assign(displayData, {
        title: '',
        steps: [],
        directResponse: false,
        planTemplateId: props.currentPlanTemplateId || '',
        planType: 'dynamic_agent',
      })
      console.log('[JsonEditorLogic] Error occurred, reset displayData to:', displayData)
    }
  }

  /**
   * Convert visual data back to JSON
   * Maps frontend DisplayPlanData to backend DynamicAgentExecutionPlan structure
   */
  const convertVisualToJson = (): string => {
    try {
      const result: Record<string, unknown> = {
        title: displayData.title,
        steps: displayData.steps.map(step => ({
          stepRequirement: step.stepRequirement,
          agentName: step.agentName,
          modelName: step.modelName ?? '', // Convert null to empty string for JSON
          selectedToolKeys: step.selectedToolKeys,
          terminateColumns: step.terminateColumns,
        })),
        directResponse: displayData.directResponse,
        planTemplateId: displayData.planTemplateId,
        planType: displayData.planType,
      }

      return JSON.stringify(result, null, 2)
    } catch (error) {
      console.error('Failed to convert visual data to JSON:', error)
      return '{}'
    }
  }

  // Computed properties
  const formattedJsonOutput = computed(() => convertVisualToJson())

  // Watch for JSON content changes
  watch(
    () => props.jsonContent,
    (newContent, oldContent) => {
      console.log(
        '[JsonEditorLogic] Watch triggered - jsonContent changed from',
        oldContent,
        'to',
        newContent
      )
      parseJsonToVisual(newContent)
    },
    { immediate: true }
  )

  // Watch for display data changes and emit updates
  watch(
    displayData,
    () => {
      const jsonOutput = convertVisualToJson()
      emit('update:jsonContent', jsonOutput)
    },
    { deep: true }
  )

  // Watch for currentPlanTemplateId changes
  watch(
    () => props.currentPlanTemplateId,
    newId => {
      if (newId) {
        displayData.planTemplateId = newId
      }
    }
  )

  // Track initial step requirements to detect actual changes
  let initialStepRequirements: string[] = []
  let isInitialLoad = true

  // Watch for stepRequirement/stepContent changes to track modifications
  watch(
    () => displayData.steps.map(step => step.stepRequirement || step.stepContent || ''),
    newRequirements => {
      // Skip on initial load
      if (isInitialLoad) {
        initialStepRequirements = [...newRequirements]
        isInitialLoad = false
        return
      }

      // Only set flag if requirements actually changed
      const hasChanged =
        newRequirements.length !== initialStepRequirements.length ||
        newRequirements.some((req, index) => req !== (initialStepRequirements[index] || ''))

      if (hasChanged) {
        templateStore.hasTaskRequirementModified = true
      }
    },
    { deep: true }
  )

  // Reset initial state when JSON content changes (new template loaded)
  watch(
    () => props.jsonContent,
    () => {
      isInitialLoad = true
      initialStepRequirements = []
    }
  )

  // JSON preview functions
  const toggleJsonPreview = () => {
    showJsonPreview.value = !showJsonPreview.value
  }

  const closeJsonPreview = () => {
    showJsonPreview.value = false
  }

  return {
    // State
    showJsonPreview,
    displayData,
    formattedJsonOutput,

    // JSON preview
    toggleJsonPreview,
    closeJsonPreview,
  }
}
