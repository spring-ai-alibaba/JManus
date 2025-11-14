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

import type { AgentExecutionRecord, PlanExecutionRecord } from '@/types/plan-execution-record'

/**
 * Local interface to handle readonly compatibility issues
 */
export interface CompatiblePlanExecutionRecord
  extends Omit<PlanExecutionRecord, 'agentExecutionSequence'> {
  agentExecutionSequence?: AgentExecutionRecord[]
}

/**
 * Chat message interface
 */
export interface ChatMessage {
  id: string
  type: 'user' | 'assistant'
  content: string
  timestamp: Date
  thinking?: string
  planExecution?: CompatiblePlanExecutionRecord
  stepActions?: unknown[]
  genericInput?: string
  isStreaming?: boolean
  error?: string
  attachments?: File[]
}

/**
 * Message Dialog interface for the dialog list
 */
export interface MessageDialog {
  id: string
  title: string
  messages: ChatMessage[]
  planId?: string
  conversationId?: string
  createdAt: Date
  updatedAt: Date
  isActive: boolean
}

/**
 * Input message interface for chat input
 */
export interface InputMessage {
  input: string
  memoryId?: string
  uploadedFiles?: string[]
  uploadKey?: string
}
