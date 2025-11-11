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

import type {
  PlanTemplateInitRequest,
  PlanTemplateInitResponse,
  PlanTemplateRegisterRequest,
  PlanTemplateRegisterResponse,
  PlanTemplateStatus,
  RegisteredPlanTemplatesResponse,
  PlanTemplateConfigVO,
  CreateOrUpdatePlanTemplateWithToolResponse,
} from '../types/plan-template'

/**
 * Plan template API service class
 * Provides plan template-related functionality
 */
export class PlanTemplateApiService {
  /**
   * Handle HTTP response
   */
  private static async handleResponse(response: Response) {
    if (!response.ok) {
      try {
        const errorData = await response.json()
        throw new Error(errorData.message || `API request failed: ${response.status}`)
      } catch {
        throw new Error(`API request failed: ${response.status} ${response.statusText}`)
      }
    }
    return response
  }

  /**
   * Initialize and register plan templates as inner toolcalls
   */
  static async initAndRegisterPlanTemplates(
    data: PlanTemplateInitRequest
  ): Promise<PlanTemplateInitResponse> {
    try {
      const response = await fetch('/api/plan-template-publish/init-and-register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to initialize and register plan templates:', error)
      throw error
    }
  }

  /**
   * Register specific plan templates as inner toolcalls
   */
  static async registerPlanTemplates(
    data: PlanTemplateRegisterRequest
  ): Promise<PlanTemplateRegisterResponse> {
    try {
      const response = await fetch('/api/plan-template-publish/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to register plan templates:', error)
      throw error
    }
  }

  /**
   * Unregister plan templates from inner toolcalls
   */
  static async unregisterPlanTemplates(
    data: PlanTemplateRegisterRequest
  ): Promise<PlanTemplateRegisterResponse> {
    try {
      const response = await fetch('/api/plan-template-publish/unregister', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to unregister plan templates:', error)
      throw error
    }
  }

  /**
   * Get registration status
   */
  static async getPlanTemplateStatus(): Promise<PlanTemplateStatus> {
    try {
      const response = await fetch('/api/plan-template-publish/status')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to get plan template status:', error)
      throw error
    }
  }

  /**
   * Get all registered plan templates
   */
  static async getRegisteredPlanTemplates(): Promise<RegisteredPlanTemplatesResponse> {
    try {
      const response = await fetch('/api/plan-template-publish/registered')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to get registered plan templates:', error)
      throw error
    }
  }

  /**
   * Create or update plan template and register as coordinator tool
   * This method combines the functionality of both "Save Plan Template" and
   * "Register Plan Templates as Toolcalls" by using PlanTemplateConfigVO
   */
  static async createOrUpdatePlanTemplateWithTool(
    data: PlanTemplateConfigVO
  ): Promise<CreateOrUpdatePlanTemplateWithToolResponse> {
    try {
      const response = await fetch('/api/plan-template/create-or-update-with-tool', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data),
      })
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to create or update plan template with tool:', error)
      throw error
    }
  }

  /**
   * Get plan template configuration VO by plan template ID
   */
  static async getPlanTemplateConfigVO(planTemplateId: string): Promise<PlanTemplateConfigVO> {
    try {
      const response = await fetch(`/api/plan-template/${planTemplateId}/config`)
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to get plan template config VO:', error)
      throw error
    }
  }

  /**
   * Get all plan template configuration VOs
   */
  static async getAllPlanTemplateConfigVOs(): Promise<PlanTemplateConfigVO[]> {
    try {
      const response = await fetch('/api/plan-template/list-config')
      const result = await this.handleResponse(response)
      return await result.json()
    } catch (error) {
      console.error('Failed to get all plan template config VOs:', error)
      throw error
    }
  }
}
