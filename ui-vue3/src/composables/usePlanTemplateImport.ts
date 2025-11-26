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

import { PlanTemplateApiService } from '@/api/plan-template-with-tool-api-service'
import type { PlanTemplateConfigVO } from '@/types/plan-template'

/**
 * Options for importing plan templates
 */
export interface ImportPlanTemplateOptions {
  /**
   * Whether to show a confirmation dialog before importing
   * @default false
   */
  showConfirm?: boolean
  /**
   * Confirmation message text (required if showConfirm is true)
   */
  confirmMessage?: string
  /**
   * Callback function called on successful import
   * @param result - Import result with success count, failure count, etc.
   * @param templates - The imported templates array
   */
  onSuccess?: (
    result: {
      success: boolean
      total: number
      successCount: number
      failureCount: number
    },
    templates: PlanTemplateConfigVO[]
  ) => void | Promise<void>
  /**
   * Callback function called on import failure
   * @param error - Error message or Error object
   */
  onError?: (error: string | Error) => void
  /**
   * Callback function to reload templates after import
   */
  onReload?: () => void | Promise<void>
  /**
   * Callback function called when only one template is imported (for auto-selection)
   * @param template - The imported template
   */
  onSingleTemplateImported?: (template: PlanTemplateConfigVO) => void | Promise<void>
}

/**
 * Composable for importing plan templates from JSON files
 */
export function usePlanTemplateImport() {
  /**
   * Handle importing plan templates from a file input event
   * @param event - File input change event
   * @param options - Import options for customization
   */
  const handleImport = async (
    event: Event,
    options: ImportPlanTemplateOptions = {}
  ): Promise<void> => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]

    if (!file) {
      return
    }

    const {
      showConfirm = false,
      confirmMessage,
      onSuccess,
      onError,
      onReload,
      onSingleTemplateImported,
    } = options

    try {
      // Read file content
      const fileContent = await file.text()
      const templates: PlanTemplateConfigVO[] = JSON.parse(fileContent)

      // Validate that it's an array
      if (!Array.isArray(templates)) {
        const errorMsg = 'Invalid format: expected an array of plan templates'
        input.value = ''
        if (onError) {
          onError(errorMsg)
        } else {
          throw new Error(errorMsg)
        }
        return
      }

      // Show confirmation dialog if requested
      if (showConfirm && confirmMessage) {
        const confirmed = confirm(confirmMessage)
        if (!confirmed) {
          input.value = ''
          return
        }
      }

      // Import templates
      const result = await PlanTemplateApiService.importPlanTemplates(templates)

      if (result.success) {
        // Call success callback if provided
        if (onSuccess) {
          await onSuccess(result, templates)
        }

        // Reload templates if callback provided
        if (onReload) {
          await onReload()
        }

        // Handle single template import (for auto-selection)
        if (onSingleTemplateImported && result.successCount === 1 && templates.length === 1) {
          const importedTemplate = templates[0]
          if (importedTemplate.planTemplateId) {
            await onSingleTemplateImported(importedTemplate)
          }
        }
      } else {
        // Call error callback if import failed
        if (onError) {
          onError('Import failed')
        } else {
          console.error('Import failed')
        }
      }
    } catch (error) {
      console.error('Failed to import plan templates:', error)
      const errorMessage =
        error instanceof Error ? error.message : 'Failed to import plan templates'
      if (onError) {
        onError(errorMessage)
      }
    } finally {
      // Clear the input
      input.value = ''
    }
  }

  return {
    handleImport,
  }
}
