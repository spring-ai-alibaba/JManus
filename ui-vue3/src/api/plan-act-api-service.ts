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

import type { CronConfig } from '@/types/cron-task'
import { LlmCheckService } from '@/utils/llm-check'
import { DirectApiService } from '@/api/direct-api-service'

export class PlanActApiService {
  private static readonly PLAN_TEMPLATE_URL = '/api/plan-template'
  private static readonly CRON_TASK_URL = '/api/cron-tasks'

  // Execute generated plan using ManusController.executeByToolNameAsync
  public static async executePlan(
    planTemplateId: string,
    rawParam?: string,
    uploadedFiles?: string[],
    replacementParams?: Record<string, string>,
    uploadKey?: string
  ): Promise<any> {
    return LlmCheckService.withLlmCheck(async () => {
      console.log('[PlanActApiService] executePlan called with:', {
        planTemplateId,
        rawParam,
        uploadedFiles,
        replacementParams,
        uploadKey,
      })

      // Add rawParam to replacementParams if provided (backend expects it in replacementParams)
      if (rawParam) {
        if (!replacementParams) {
          replacementParams = {}
        }
        replacementParams['userRequirement'] = rawParam
        console.log('[PlanActApiService] Added rawParam to replacementParams:', rawParam)
      }

      // Use the unified DirectApiService method
      return await DirectApiService.executeByToolName(
        planTemplateId,
        replacementParams,
        uploadedFiles,
        uploadKey
      )
    })
  }

  // Save plan to server
  public static async savePlanTemplate(planId: string, planJson: string): Promise<any> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/save`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId, planJson }),
    })
    if (!response.ok) throw new Error(`Failed to save plan: ${response.status}`)
    return await response.json()
  }

  // Get all versions of plan
  public static async getPlanVersions(planId: string): Promise<any> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/versions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId }),
    })
    if (!response.ok) throw new Error(`Failed to get plan versions: ${response.status}`)
    return await response.json()
  }

  // Get specific version of plan
  public static async getVersionPlan(planId: string, versionIndex: number): Promise<any> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/get-version`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId, versionIndex: versionIndex.toString() }),
    })
    if (!response.ok) throw new Error(`Failed to get specific version plan: ${response.status}`)
    return await response.json()
  }

  // Get all plan template list
  public static async getAllPlanTemplates(): Promise<any> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/list`)
    if (!response.ok) throw new Error(`Failed to get plan template list: ${response.status}`)
    return await response.json()
  }

  // Delete plan template
  public static async deletePlanTemplate(planId: string): Promise<any> {
    const response = await fetch(`${this.PLAN_TEMPLATE_URL}/delete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ planId }),
    })
    if (!response.ok) throw new Error(`Failed to delete plan template: ${response.status}`)
    return await response.json()
  }

  // Create cron task
  public static async createCronTask(cronConfig: CronConfig): Promise<CronConfig> {
    const response = await fetch(this.CRON_TASK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cronConfig),
    })
    if (!response.ok) {
      try {
        const errorData = await response.json()
        throw new Error(errorData.message || `Failed to create cron task: ${response.status}`)
      } catch {
        throw new Error(`Failed to create cron task: ${response.status}`)
      }
    }
    return await response.json()
  }
}
