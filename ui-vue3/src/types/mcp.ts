// MCP related type definitions

// Basic MCP server interface
export interface McpServer {
  id: number
  mcpServerName: string
  connectionType: 'STUDIO' | 'SSE' | 'STREAMING'
  connectionConfig: string
  status: 'ENABLE' | 'DISABLE'
}

// Extended MCP server interface (includes UI fields)
export interface ExtendedMcpServer extends McpServer {
  args?: string // Frontend display as JSON string
  env?: string // Frontend display as JSON string
  headers?: string // Frontend display as JSON string
  url?: string
  command?: string
}

// MCP server field request interface
export interface McpServerFieldRequest {
  connectionType: 'STUDIO' | 'SSE' | 'STREAMING'
  mcpServerName: string
  status: 'ENABLE' | 'DISABLE'
  command?: string
  url?: string
  args?: string[]
  env?: Record<string, string>
  headers?: Record<string, string>
}

// MCP server save request interface (merge create and update)
export interface McpServerSaveRequest extends McpServerFieldRequest {
  id?: number // Optional, with id for update, without id for create
}

// MCP server JSON import request interface
export interface McpServerRequest {
  connectionType: 'STUDIO' | 'SSE' | 'STREAMING'
  configJson: string
}

// API response interface
export interface ApiResponse<T = unknown> {
  success: boolean
  message?: string
  data?: T
}

// Form data interface
export interface McpConfigFormData {
  mcpServerName: string
  connectionType: 'STUDIO' | 'SSE' | 'STREAMING'
  command: string
  url: string
  args: string // Frontend input as JSON string
  env: string // Frontend input as JSON string
  status: 'ENABLE' | 'DISABLE'
}

// Message type
export type MessageType = 'success' | 'error' | 'info'

// Message interface
export interface Message {
  show: boolean
  text: string
  type: MessageType
}

// Tab configuration interface
export interface TabConfig {
  name: string
  label: string
}

// JSON validation result interface
export interface JsonValidationResult {
  isValid: boolean
  errors?: string[]
}

// MCP server configuration in JSON format
export interface McpServerJsonConfig {
  command?: string
  args?: string[]
  env?: Record<string, string> | string[] | null
  headers?: Record<string, string> | null
  url?: string
  baseUrl?: string
}

// MCP configuration structure for JSON import
export interface McpConfigJson {
  mcpServers: Record<string, McpServerJsonConfig>
}
