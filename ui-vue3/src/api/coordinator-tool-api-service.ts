// /*
//  * Copyright 2025 the original author or authors.
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *      https://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// export interface CoordinatorToolVO {
//   id?: number
//   toolName: string
//   toolDescription: string
//   planTemplateId: string
//   httpEndpoint?: string | undefined
//   mcpEndpoint?: string | undefined
//   inputSchema: string
//   serviceGroup?: string
//   enableInternalToolcall?: boolean
//   enableHttpService?: boolean
//   enableMcpService?: boolean
//   createTime?: string
//   updateTime?: string
// }

// export interface CoordinatorToolConfig {
//   enabled: boolean
//   success: boolean
//   message?: string
// }

// export class CoordinatorToolApiService {
//   private static readonly BASE_URL = '/api/coordinator-tools'

//   /**
//    * Get all coordinator tools
//    * @returns List of all coordinator tools
//    */
//   public static async getAllCoordinatorTools(): Promise<CoordinatorToolVO[]> {
//     try {
//       const response = await fetch(`${this.BASE_URL}`, {
//         method: 'GET',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//       })

//       if (!response.ok) {
//         throw new Error(`Failed to get coordinator tools: ${response.status}`)
//       }

//       const result = await response.json()
//       return result || []
//     } catch (error: unknown) {
//       console.error('Failed to get coordinator tools:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to get coordinator tools: ' + message)
//     }
//   }

//   /**
//    * Get CoordinatorTool configuration information
//    */
//   public static async getCoordinatorToolConfig(): Promise<CoordinatorToolConfig> {
//     try {
//       const response = await fetch(`${this.BASE_URL}/config`, {
//         method: 'GET',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//       })

//       if (!response.ok) {
//         throw new Error(`Failed to get coordinator tool config: ${response.status}`)
//       }

//       return await response.json()
//     } catch (error: unknown) {
//       console.error('Failed to get CoordinatorTool configuration:', error)
//       // Return default configuration
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       return {
//         enabled: true,
//         success: false,
//         message: message,
//       }
//     }
//   }

//   /**
//    * Get all endpoint list
//    */
//   public static async getAllEndpoints(): Promise<string[]> {
//     try {
//       const response = await fetch(`${this.BASE_URL}/endpoints`, {
//         method: 'GET',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//       })

//       if (!response.ok) {
//         throw new Error(`Failed to get endpoints: ${response.status}`)
//       }

//       return await response.json()
//     } catch (error: unknown) {
//       console.error('Failed to get endpoints:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to get endpoints: ' + message)
//     }
//   }

//   /**
//    * Get coordinator tool by plan template ID
//    * @param planTemplateId Plan template ID
//    * @returns Coordinator tool if exists, null if not found
//    */
//   public static async getCoordinatorToolByTemplate(
//     planTemplateId: string
//   ): Promise<CoordinatorToolVO | null> {
//     console.log(
//       '[CoordinatorToolApiService] Getting coordinator tool, planTemplateId:',
//       planTemplateId
//     )
//     console.log(
//       '[CoordinatorToolApiService] Request URL:',
//       `${this.BASE_URL}/by-template/${planTemplateId}`
//     )

//     try {
//       const response = await fetch(`${this.BASE_URL}/by-template/${planTemplateId}`, {
//         method: 'GET',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//       })

//       console.log('[CoordinatorToolApiService] Response status:', response.status)

//       if (!response.ok) {
//         const errorText = await response.text()
//         console.error('[CoordinatorToolApiService] Response error content:', errorText)
//         throw new Error(`Failed to get coordinator tool: ${response.status} - ${errorText}`)
//       }

//       // Get response text first to check if empty
//       const text = await response.text()

//       // If response body is empty, treat as not found
//       if (!text || text.trim() === '') {
//         console.log(
//           '[CoordinatorToolApiService] Tool not found for planTemplateId (empty response):',
//           planTemplateId
//         )
//         return null
//       }

//       // Parse JSON response
//       let result: CoordinatorToolVO | null
//       try {
//         result = JSON.parse(text)
//       } catch (parseError) {
//         console.error('[CoordinatorToolApiService] Failed to parse JSON response:', parseError)
//         return null
//       }

//       // If result is null or empty object, treat as not found
//       if (result === null || (typeof result === 'object' && Object.keys(result).length === 0)) {
//         console.log(
//           '[CoordinatorToolApiService] Tool not found for planTemplateId:',
//           planTemplateId
//         )
//         return null
//       }

//       console.log('[CoordinatorToolApiService] Successfully got coordinator tool:', result)
//       return result
//     } catch (error: unknown) {
//       console.error('[CoordinatorToolApiService] Failed to get coordinator tool:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to get coordinator tool: ' + message)
//     }
//   }

//   /**
//    * Create a default coordinator tool VO for a plan template
//    * This creates a VO object but does not save it to the database
//    * @param planTemplateId Plan template ID
//    * @param planTitle Optional plan template title
//    * @param planDescription Optional plan template description
//    * @returns Default coordinator tool VO
//    */
//   public static createDefaultCoordinatorTool(
//     planTemplateId: string,
//     planTitle?: string,
//     planDescription?: string
//   ): CoordinatorToolVO {
//     return {
//       toolName: planTitle ? `tool_${planTitle}` : '',
//       toolDescription: planDescription ?? '',
//       planTemplateId: planTemplateId,
//       inputSchema: '[]',
//       serviceGroup: '',
//       enableInternalToolcall: false,
//       enableHttpService: false,
//       enableMcpService: false,
//     }
//   }

//   /**
//    * Create coordinator tool
//    */
//   public static async createCoordinatorTool(tool: CoordinatorToolVO): Promise<CoordinatorToolVO> {
//     console.log('[CoordinatorToolApiService] Starting to create coordinator tool')
//     console.log('[CoordinatorToolApiService] Original data:', JSON.stringify(tool, null, 2))
//     console.log('[CoordinatorToolApiService] Request URL:', `${this.BASE_URL}`)

//     // Only send necessary fields, excluding createTime and updateTime
//     const requestData = {
//       id: tool.id,
//       toolName: tool.toolName,
//       toolDescription: tool.toolDescription,
//       inputSchema: tool.inputSchema,
//       planTemplateId: tool.planTemplateId,
//       httpEndpoint: tool.httpEndpoint,
//       mcpEndpoint: tool.mcpEndpoint,
//       serviceGroup: tool.serviceGroup,
//       enableInternalToolcall: tool.enableInternalToolcall,
//       enableHttpService: tool.enableHttpService,
//       enableMcpService: tool.enableMcpService,
//     }

//     console.log(
//       '[CoordinatorToolApiService] Cleaned sending data:',
//       JSON.stringify(requestData, null, 2)
//     )

//     try {
//       const response = await fetch(`${this.BASE_URL}`, {
//         method: 'POST',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//         body: JSON.stringify(requestData),
//       })

//       console.log('[CoordinatorToolApiService] Response status:', response.status)
//       console.log('[CoordinatorToolApiService] Response status text:', response.statusText)

//       if (!response.ok) {
//         let errorMessage = 'Unknown error'
//         try {
//           const errorData = await response.json()
//           errorMessage = errorData.message || errorData.error || errorMessage
//         } catch {
//           // If JSON parsing fails, try to get text
//           const errorText = await response.text()
//           errorMessage = errorText || errorMessage
//         }
//         console.error('[CoordinatorToolApiService] Response error content:', errorMessage)
//         throw new Error(`Failed to create coordinator tool: ${response.status} - ${errorMessage}`)
//       }

//       const result = await response.json()
//       console.log('[CoordinatorToolApiService] Created successfully, result:', result)
//       return result
//     } catch (error: unknown) {
//       console.error('[CoordinatorToolApiService] Failed to create coordinator tool:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to create coordinator tool: ' + message)
//     }
//   }

//   /**
//    * Update coordinator tool
//    */
//   public static async updateCoordinatorTool(
//     id: number,
//     tool: CoordinatorToolVO
//   ): Promise<CoordinatorToolVO> {
//     console.log('[CoordinatorToolApiService] Starting to update coordinator tool, ID:', id)
//     console.log('[CoordinatorToolApiService] Sending data:', tool)
//     console.log('[CoordinatorToolApiService] Request URL:', `${this.BASE_URL}/${id}`)

//     // Only send necessary fields, excluding createTime and updateTime
//     const requestData = {
//       id: tool.id,
//       toolName: tool.toolName,
//       toolDescription: tool.toolDescription,
//       inputSchema: tool.inputSchema,
//       planTemplateId: tool.planTemplateId,
//       httpEndpoint: tool.httpEndpoint,
//       mcpEndpoint: tool.mcpEndpoint,
//       serviceGroup: tool.serviceGroup,
//       enableInternalToolcall: tool.enableInternalToolcall,
//       enableHttpService: tool.enableHttpService,
//       enableMcpService: tool.enableMcpService,
//     }

//     console.log('[CoordinatorToolApiService] Cleaned sending data:', requestData)

//     try {
//       const response = await fetch(`${this.BASE_URL}/${id}`, {
//         method: 'PUT',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//         body: JSON.stringify(requestData),
//       })

//       console.log('[CoordinatorToolApiService] Response status:', response.status)
//       console.log('[CoordinatorToolApiService] Response status text:', response.statusText)

//       if (!response.ok) {
//         let errorMessage = 'Unknown error'
//         try {
//           const errorData = await response.json()
//           errorMessage = errorData.message || errorData.error || errorMessage
//         } catch {
//           // If JSON parsing fails, try to get text
//           const errorText = await response.text()
//           errorMessage = errorText || errorMessage
//         }
//         console.error('[CoordinatorToolApiService] Response error content:', errorMessage)
//         throw new Error(`Failed to update coordinator tool: ${response.status} - ${errorMessage}`)
//       }

//       const result = await response.json()
//       console.log('[CoordinatorToolApiService] Updated successfully, result:', result)
//       return result
//     } catch (error: unknown) {
//       console.error('[CoordinatorToolApiService] Failed to update coordinator tool:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to update coordinator tool: ' + message)
//     }
//   }

//   /**
//    * Delete coordinator tool
//    */
//   public static async deleteCoordinatorTool(
//     id: number
//   ): Promise<{ success: boolean; message: string }> {
//     console.log('[CoordinatorToolApiService] Starting to delete coordinator tool, ID:', id)
//     console.log('[CoordinatorToolApiService] Request URL:', `${this.BASE_URL}/${id}`)

//     try {
//       const response = await fetch(`${this.BASE_URL}/${id}`, {
//         method: 'DELETE',
//         headers: {
//           'Content-Type': 'application/json',
//         },
//       })

//       console.log('[CoordinatorToolApiService] Response status:', response.status)
//       console.log('[CoordinatorToolApiService] Response status text:', response.statusText)

//       if (!response.ok) {
//         const errorText = await response.text()
//         console.error('[CoordinatorToolApiService] Response error content:', errorText)
//         throw new Error(`Failed to delete coordinator tool: ${response.status} - ${errorText}`)
//       }

//       const result = await response.json()
//       console.log('[CoordinatorToolApiService] Deleted successfully, result:', result)
//       return result
//     } catch (error: unknown) {
//       console.error('[CoordinatorToolApiService] Failed to delete coordinator tool:', error)
//       const message = error instanceof Error ? error.message : 'Unknown error'
//       throw new Error('Failed to delete coordinator tool: ' + message)
//     }
//   }
// }
