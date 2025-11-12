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

// Plan template related type definitions
import type { PlanExecutionRequestPayload } from './plan-execution'

export interface PlanTemplate {
  id: string
  title?: string
  description?: string
  createTime: string
  updateTime?: string
  planJson?: string
  prompt?: string
  params?: string
}

export interface PlanTemplateListResponse {
  count: number
  templates: PlanTemplate[]
}

export interface PlanVersionsResponse {
  versions: string[]
}

export interface PlanTemplateEvents {
  planTemplateSelected: [payload: { templateId: string; template: PlanTemplate }]
  planTemplateDeleted: [payload: { templateId: string }]
  newTaskRequested: []
  planVersionsLoaded: [payload: { templateId: string; versions: string[] }]
  planParamsChanged: [payload: { prompt: string; params: string }]
  jsonContentSet: [payload: { content: string }]
  jsonContentClear: []
  planTemplateConfigRequested: [payload: { templateId: string; template: PlanTemplate }]
  configTabClosed: []
  planExecutionRequested: [payload: PlanExecutionRequestPayload]
}

// Plan template initialization and registration types
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

// Plan template configuration types
export interface InputSchemaParam {
  name: string
  description: string
  type: string
  required?: boolean
}

export interface ToolConfigVO {
  toolDescription?: string
  enableInternalToolcall?: boolean
  enableHttpService?: boolean
  enableMcpService?: boolean
  publishStatus?: string
  inputSchema?: InputSchemaParam[]
}

export interface StepConfig {
  stepRequirement?: string
  agentName?: string
  modelName?: string
  terminateColumns?: string
}

export interface PlanTemplateConfigVO {
  title?: string
  steps?: StepConfig[]
  directResponse?: boolean
  planType?: string
  planTemplateId?: string
  readOnly?: boolean
  serviceGroup?: string
  toolConfig?: ToolConfigVO
  createTime?: string
  updateTime?: string
}

export interface CreateOrUpdatePlanTemplateWithToolResponse {
  success: boolean
  planTemplateId: string
  toolRegistered: boolean
}
