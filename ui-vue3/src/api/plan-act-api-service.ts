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

// Plan-related API wrapper (TypeScript version for Vue projects)

import { DirectApiService } from '@/api/direct-api-service'
import { LlmCheckService } from '@/utils/llm-check'

export class PlanActApiService {
  private static readonly PLAN_TEMPLATE_URL = '/api/plan-template'

  // Execute generated plan using ManusController.executeByToolNameAsync
  public static async executePlan(
    planTemplateId: string,
    rawParam?: string,
    uploadedFiles?: string[],
    replacementParams?: Record<string, string>,
    uploadKey?: string,
    requestSource: 'VUE_DIALOG' | 'VUE_SIDEBAR' = 'VUE_SIDEBAR'
  ): Promise<unknown> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[PlanActApiService] executePlan called with:', {
        planTemplateId,
        rawParam,
        uploadedFiles,
        replacementParams,
        uploadKey,
        requestSource,
      })

      // Add rawParam to replacementParams if provided (backend expects it in replacementParams)
      if (rawParam) {
        if (!replacementParams) {
          replacementParams = {}
        }
        replacementParams['userRequirement'] = rawParam
        console.log('[PlanActApiService] Added rawParam to replacementParams:', rawParam)
      }

      // Use the unified DirectApiService method (default to VUE_SIDEBAR for plan execution)
      return await DirectApiService.executeByToolName(
        planTemplateId,
        replacementParams,
        uploadedFiles,
        uploadKey,
        requestSource
      )
    })
  }

  // Get all versions of plan
  public static async getPlanVersions(planId: string): Promise<unknown> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/versions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId }),
    })
    if (!response.ok) throw new Error(`Failed to get plan versions: ${response.status}`)
    return await response.json()
  }

  // Get all plan template list
  public static async getAllPlanTemplates(): Promise<unknown> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/list`)
    if (!response.ok) throw new Error(`Failed to get plan template list: ${response.status}`)
    return await response.json()
  }
}
