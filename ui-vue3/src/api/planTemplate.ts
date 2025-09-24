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

export interface PlanTemplateInitRequest {
  language: string
}

export interface PlanTemplateInitResponse {
  success: boolean
  language: string
  planNames: string[]
  initResult: {
    success: boolean
    successCount: number
    errorCount: number
    successList: string[]
    errorList: string[]
    errors: Record<string, string>
  }
  registerResult: {
    success: boolean
    successCount: number
    errorCount: number
    successList: string[]
    errorList: string[]
    errors: Record<string, string>
  }
  message: string
}

export interface PlanTemplateRegisterRequest {
  planNames: string[]
}

export interface PlanTemplateRegisterResponse {
  success: boolean
  totalRequested: number
  successCount: number
  errorCount: number
  successList: string[]
  errorList: string[]
  errors: Record<string, string>
  message: string
}

export interface PlanTemplateStatus {
  success: boolean
  totalPlanTemplates: number
  registeredPlanTemplates: number
  unregisteredPlanTemplates: number
  registeredPlanTemplateIds: string[]
}

export interface RegisteredPlanTemplate {
  toolId: number
  toolName: string
  planTemplateId: string
  description: string
  endpoint: string
  serviceGroup: string
}

export interface RegisteredPlanTemplatesResponse {
  success: boolean
  registeredTemplates: RegisteredPlanTemplate[]
  count: number
}

/**
 * Handle fetch response
 */
const handleResponse = async (response: Response) => {
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Network error' }))
    throw new Error(error.message || `HTTP error! status: ${response.status}`)
  }
  return response.json()
}

/**
 * Initialize and register plan templates as inner toolcalls
 */
export const initAndRegisterPlanTemplates = async (data: PlanTemplateInitRequest): Promise<PlanTemplateInitResponse> => {
  const response = await fetch('/api/plan-template-publish/init-and-register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  })
  return handleResponse(response)
}

/**
 * Register specific plan templates as inner toolcalls
 */
export const registerPlanTemplates = async (data: PlanTemplateRegisterRequest): Promise<PlanTemplateRegisterResponse> => {
  const response = await fetch('/api/plan-template-publish/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  })
  return handleResponse(response)
}

/**
 * Unregister plan templates from inner toolcalls
 */
export const unregisterPlanTemplates = async (data: PlanTemplateRegisterRequest): Promise<PlanTemplateRegisterResponse> => {
  const response = await fetch('/api/plan-template-publish/unregister', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(data),
  })
  return handleResponse(response)
}

/**
 * Get registration status
 */
export const getPlanTemplateStatus = async (): Promise<PlanTemplateStatus> => {
  const response = await fetch('/api/plan-template-publish/status')
  return handleResponse(response)
}

/**
 * Get all registered plan templates
 */
export const getRegisteredPlanTemplates = async (): Promise<RegisteredPlanTemplatesResponse> => {
  const response = await fetch('/api/plan-template-publish/registered')
  return handleResponse(response)
}
