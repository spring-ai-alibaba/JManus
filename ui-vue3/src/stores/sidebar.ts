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

import { PlanActApiService } from '@/api/plan-act-api-service'
import { ToolApiService } from '@/api/tool-api-service'
import { i18n } from '@/base/i18n'
import type { PlanTemplate } from '@/types/plan-template'
import type { Tool } from '@/types/tool'
import { reactive } from 'vue'

type TabType = 'list' | 'config'

export class SidebarStore {
  // Basic state
  isCollapsed = false
  currentTab: TabType = 'list'

  // Template list related state
  currentPlanTemplateId: string | null = null
  planTemplateList: PlanTemplate[] = []
  selectedTemplate: PlanTemplate | null = null
  isLoading = false
  errorMessage = ''

  // Configuration related state
  jsonContent = ''
  planType = 'dynamic_agent'
  generatorPrompt = ''
  executionParams = ''
  isGenerating = false
  isExecuting = false

  // Version control
  planVersions: string[] = []
  currentVersionIndex = -1

  // Available tools state
  availableTools: Array<{
    key: string
    name: string
    description: string
    enabled: boolean
    serviceGroup: string
    selectable: boolean
  }> = []
  isLoadingTools = false
  toolsLoadError = ''

  // Track task requirement modifications
  hasTaskRequirementModified = false

  // Organization method: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc'
  organizationMethod: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc' = 'by_time'

  // Template service group mapping (templateId -> serviceGroup)
  templateServiceGroups: Map<string, string> = new Map()

  // Group collapse state (groupName -> isCollapsed)
  groupCollapseState: Map<string | null, boolean> = new Map()

  constructor() {
    // Ensure properties are properly initialized
    this.planVersions = []
    this.currentVersionIndex = -1
    // Load organization method from localStorage
    const savedMethod = localStorage.getItem('sidebarOrganizationMethod')
    if (
      savedMethod &&
      ['by_time', 'by_abc', 'by_group_time', 'by_group_abc'].includes(savedMethod)
    ) {
      this.organizationMethod = savedMethod as
        | 'by_time'
        | 'by_abc'
        | 'by_group_time'
        | 'by_group_abc'
    }
    // Load group collapse state from localStorage
    this.loadGroupCollapseState()
  }

  // Load group collapse state from localStorage
  loadGroupCollapseState() {
    try {
      const saved = localStorage.getItem('sidebarGroupCollapseState')
      if (saved) {
        const parsed = JSON.parse(saved)
        this.groupCollapseState = new Map(
          Object.entries(parsed).map(([k, v]) => [k === 'null' ? null : k, v as boolean])
        )
      }
    } catch (error) {
      console.warn('[SidebarStore] Failed to load group collapse state:', error)
    }
  }

  // Save group collapse state to localStorage
  saveGroupCollapseState() {
    try {
      // Convert Map to object, handling null keys properly
      const obj: Record<string, boolean> = {}
      this.groupCollapseState.forEach((value, key) => {
        // Convert null key to 'null' string for JSON serialization
        const objKey = key ?? 'null'
        obj[objKey] = value
      })
      localStorage.setItem('sidebarGroupCollapseState', JSON.stringify(obj))
    } catch (error) {
      console.warn('[SidebarStore] Failed to save group collapse state:', error)
    }
  }

  // Toggle group collapse state
  toggleGroupCollapse(groupName: string | null) {
    // Use null as the key in Map, but convert to 'null' string for localStorage
    const currentState = this.groupCollapseState.get(groupName) ?? false
    this.groupCollapseState.set(groupName, !currentState)
    this.saveGroupCollapseState()
  }

  // Check if group is collapsed
  isGroupCollapsed(groupName: string | null): boolean {
    // Use null as the key directly in Map
    return this.groupCollapseState.get(groupName) ?? false
  }

  // Helper function to parse date from different formats
  parseDateTime(dateValue: unknown): Date {
    if (!dateValue) {
      return new Date()
    }

    // If array format [year, month, day, hour, minute, second, nanosecond]
    if (Array.isArray(dateValue) && dateValue.length >= 6) {
      // JavaScript Date constructor months start from 0, so subtract 1
      return new Date(
        dateValue[0],
        dateValue[1] - 1,
        dateValue[2],
        dateValue[3],
        dateValue[4],
        dateValue[5],
        Math.floor(dateValue[6] / 1000000)
      )
    }

    // If string format, parse directly
    if (typeof dateValue === 'string') {
      return new Date(dateValue)
    }

    // Return current time for other cases
    return new Date()
  }

  // Computed properties
  get sortedTemplates(): PlanTemplate[] {
    const templates = [...this.planTemplateList]

    switch (this.organizationMethod) {
      case 'by_time':
        return templates.sort((a, b) => {
          const timeA = this.parseDateTime(a.updateTime ?? a.createTime)
          const timeB = this.parseDateTime(b.updateTime ?? b.createTime)
          return timeB.getTime() - timeA.getTime()
        })
      case 'by_abc':
        return templates.sort((a, b) => {
          const titleA = (a.title ?? '').toLowerCase()
          const titleB = (b.title ?? '').toLowerCase()
          return titleA.localeCompare(titleB)
        })
      case 'by_group_time':
      case 'by_group_abc': {
        // For grouped methods, return templates sorted within groups
        // The grouping logic will be handled in the component
        const groups = new Map<string, PlanTemplate[]>()
        const ungrouped: PlanTemplate[] = []

        templates.forEach(template => {
          const serviceGroup = this.templateServiceGroups.get(template.id) ?? ''
          if (!serviceGroup || serviceGroup === 'default' || serviceGroup === '') {
            ungrouped.push(template)
          } else {
            if (!groups.has(serviceGroup)) {
              groups.set(serviceGroup, [])
            }
            groups.get(serviceGroup)!.push(template)
          }
        })

        // Sort within each group
        const sortedGroups = new Map<string, PlanTemplate[]>()
        groups.forEach((templatesInGroup, groupName) => {
          const sorted = [...templatesInGroup]
          if (this.organizationMethod === 'by_group_time') {
            sorted.sort((a, b) => {
              const timeA = this.parseDateTime(a.updateTime ?? a.createTime)
              const timeB = this.parseDateTime(b.updateTime ?? b.createTime)
              return timeB.getTime() - timeA.getTime()
            })
          } else {
            // by_group_abc
            sorted.sort((a, b) => {
              const titleA = (a.title ?? '').toLowerCase()
              const titleB = (b.title ?? '').toLowerCase()
              return titleA.localeCompare(titleB)
            })
          }
          sortedGroups.set(groupName, sorted)
        })

        // Sort ungrouped templates
        if (this.organizationMethod === 'by_group_time') {
          ungrouped.sort((a, b) => {
            const timeA = this.parseDateTime(a.updateTime ?? a.createTime)
            const timeB = this.parseDateTime(b.updateTime ?? b.createTime)
            return timeB.getTime() - timeA.getTime()
          })
        } else {
          ungrouped.sort((a, b) => {
            const titleA = (a.title ?? '').toLowerCase()
            const titleB = (b.title ?? '').toLowerCase()
            return titleA.localeCompare(titleB)
          })
        }

        // Return flat list (grouping will be handled in component)
        const result: PlanTemplate[] = []
        // Add ungrouped first
        result.push(...ungrouped)
        // Add grouped templates sorted by group name
        const sortedGroupNames = Array.from(sortedGroups.keys()).sort()
        sortedGroupNames.forEach(groupName => {
          result.push(...sortedGroups.get(groupName)!)
        })
        return result
      }
      default:
        return templates.sort((a, b) => {
          const timeA = this.parseDateTime(a.updateTime ?? a.createTime)
          const timeB = this.parseDateTime(b.updateTime ?? b.createTime)
          return timeB.getTime() - timeA.getTime()
        })
    }
  }

  // Get grouped templates for display
  get groupedTemplates(): Map<string | null, PlanTemplate[]> {
    if (this.organizationMethod !== 'by_group_time' && this.organizationMethod !== 'by_group_abc') {
      // Return all templates in a single group for non-grouped methods
      return new Map([[null, this.sortedTemplates]])
    }

    const groups = new Map<string | null, PlanTemplate[]>()
    const ungrouped: PlanTemplate[] = []

    // Use sorted templates directly (already sorted by sortedTemplates getter)
    const sorted = this.sortedTemplates

    sorted.forEach(template => {
      const serviceGroup = this.templateServiceGroups.get(template.id) ?? ''
      if (!serviceGroup || serviceGroup === 'default' || serviceGroup === '') {
        ungrouped.push(template)
      } else {
        if (!groups.has(serviceGroup)) {
          groups.set(serviceGroup, [])
        }
        groups.get(serviceGroup)!.push(template)
      }
    })

    // Create result map with ungrouped first, then sorted groups
    const result = new Map<string | null, PlanTemplate[]>()
    if (ungrouped.length > 0) {
      result.set(null, ungrouped)
    }
    // Add sorted groups
    const sortedGroupNames = Array.from(groups.keys()).sort()
    sortedGroupNames.forEach(groupName => {
      result.set(groupName, groups.get(groupName)!)
    })

    return result
  }

  // Set organization method
  setOrganizationMethod(method: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc') {
    this.organizationMethod = method
    localStorage.setItem('sidebarOrganizationMethod', method)
  }

  get canRollback(): boolean {
    return this.planVersions.length > 1 && this.currentVersionIndex > 0
  }

  get canRestore(): boolean {
    return this.planVersions.length > 1 && this.currentVersionIndex < this.planVersions.length - 1
  }

  get computedApiUrl(): string {
    if (!this.selectedTemplate) return ''
    const baseUrl = `/api/plan-template/execute/${this.selectedTemplate.id}`
    const params = this.executionParams.trim()
    // GET method, parameter name is allParams
    return params ? `${baseUrl}?allParams=${encodeURIComponent(params)}` : baseUrl
  }

  // Actions
  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed
  }

  switchToTab(tab: TabType) {
    this.currentTab = tab
  }

  async loadPlanTemplateList() {
    this.isLoading = true
    this.errorMessage = ''
    try {
      console.log('[SidebarStore] Starting to load plan template list...')
      const response = await PlanActApiService.getAllPlanTemplates() as { templates?: PlanTemplate[] }
      if (response?.templates && Array.isArray(response.templates)) {
        this.planTemplateList = response.templates
        console.log(
          `[SidebarStore] Successfully loaded ${response.templates.length} plan templates`
        )
        // Load service group information for each template
        await this.loadTemplateServiceGroups()
      } else {
        this.planTemplateList = []
        console.warn('[SidebarStore] API returned abnormal data format, using empty list', response)
      }
    } catch (error: unknown) {
      console.error('[SidebarStore] Failed to load plan template list:', error)
      this.planTemplateList = []
      const message = error instanceof Error ? error.message : 'Unknown error'
      this.errorMessage = `Load failed: ${message}`
    } finally {
      this.isLoading = false
    }
  }

  // Load service group information for templates
  async loadTemplateServiceGroups() {
    this.templateServiceGroups.clear()
    const { CoordinatorToolApiService } = await import('@/api/coordinator-tool-api-service')
    for (const template of this.planTemplateList) {
      try {
        const toolData = await CoordinatorToolApiService.getCoordinatorToolByTemplate(template.id)
        if (toolData?.serviceGroup) {
          this.templateServiceGroups.set(template.id, toolData.serviceGroup)
        }
      } catch (error) {
        // Silently ignore errors for templates without published tools
        console.debug(`No service group found for template ${template.id}:`, error)
      }
    }
  }

  async selectTemplate(template: PlanTemplate) {
    this.currentPlanTemplateId = template.id
    this.selectedTemplate = template
    this.currentTab = 'config'

    // Clear jsonContent immediately to prevent stale data
    this.jsonContent = ''

    await this.loadTemplateData(template)
    console.log(`[SidebarStore] Selected plan template: ${template.id}`)
  }

  async loadTemplateData(template: PlanTemplate) {
    try {
      const versionsResponse = await PlanActApiService.getPlanVersions(template.id)
      this.planVersions = (versionsResponse as { versions?: string[] }).versions || []
      if (this.planVersions.length > 0) {
        const latestContent = this.planVersions[this.planVersions.length - 1]
        this.jsonContent = latestContent
        this.currentVersionIndex = this.planVersions.length - 1
        // Reset modification flag when loading new template
        this.hasTaskRequirementModified = false
        try {
          const parsed = JSON.parse(latestContent)
          if (parsed.prompt) {
            this.generatorPrompt = parsed.prompt
          }
          if (parsed.params) {
            this.executionParams = parsed.params
          }
          // Update planType based on the loaded template's JSON content
          if (parsed.planType) {
            this.planType = parsed.planType
            console.log(`[SidebarStore] Updated planType to: ${this.planType}`)
          }
        } catch {
          console.warn('Unable to parse JSON content to get prompt information')
        }
      } else {
        this.jsonContent = ''
        this.generatorPrompt = ''
        this.executionParams = ''
        this.planType = 'dynamic_agent'
        this.hasTaskRequirementModified = false
      }
    } catch (error: unknown) {
      console.error('Failed to load template data:', error)
      throw error
    }
  }

  async createNewTemplate(planType: string) {
    const emptyTemplate: PlanTemplate = {
      id: `new-${Date.now()}`,
      title: i18n.global.t('sidebar.newTemplateName'),
      description: i18n.global.t('sidebar.newTemplateDescription'),
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }
    this.selectedTemplate = emptyTemplate
    this.currentPlanTemplateId = null
    this.jsonContent = ''
    this.generatorPrompt = ''
    this.executionParams = ''
    this.planVersions = []
    this.currentVersionIndex = -1
    this.currentTab = 'config'
    // Reset to default planType for new templates
    this.planType = planType
    // Reset modification flag for new template
    this.hasTaskRequirementModified = false

    // Reload available tools to ensure fresh tool list
    console.log('[SidebarStore] 🔄 Reloading available tools for new template')
    await this.loadAvailableTools()

    console.log('[SidebarStore] Created new empty plan template, switching to config tab')
  }

  async deleteTemplate(template: PlanTemplate) {
    if (!template.id) {
      console.warn('[SidebarStore] deleteTemplate: Invalid template object or ID')
      return
    }
    try {
      await PlanActApiService.deletePlanTemplate(template.id)
      if (this.currentPlanTemplateId === template.id) {
        this.clearSelection()
      }
      await this.loadPlanTemplateList()
      console.log(`[SidebarStore] Plan template ${template.id} has been deleted`)
    } catch (error: unknown) {
      console.error('Failed to delete plan template:', error)
      await this.loadPlanTemplateList()
      throw error
    }
  }

  clearSelection() {
    this.currentPlanTemplateId = null
    this.selectedTemplate = null
    this.jsonContent = ''
    this.generatorPrompt = ''
    this.executionParams = ''
    this.planVersions = []
    this.currentVersionIndex = -1
    this.currentTab = 'list'
    this.hasTaskRequirementModified = false
  }

  clearExecutionParams() {
    this.executionParams = ''
  }

  rollbackVersion() {
    if (this.canRollback && this.currentVersionIndex > 0) {
      this.currentVersionIndex--
      this.jsonContent = this.planVersions[this.currentVersionIndex] || ''
    }
  }

  restoreVersion() {
    if (this.canRestore && this.currentVersionIndex < this.planVersions.length - 1) {
      this.currentVersionIndex++
      this.jsonContent = this.planVersions[this.currentVersionIndex] || ''
    }
  }

  async saveTemplate() {
    if (!this.selectedTemplate) return
    const content = this.jsonContent.trim()
    if (!content) {
      throw new Error('Content cannot be empty')
    }
    try {
      JSON.parse(content)
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : 'Unknown error'
      throw new Error('Invalid format, please correct and save.\nError: ' + message)
    }
    try {
      const saveResult = await PlanActApiService.savePlanTemplate(this.selectedTemplate.id, content)

      // Update the selected template ID with the real planId returned from backend
      if ((saveResult as { planId?: string })?.planId && this.selectedTemplate.id.startsWith('new-')) {
        console.log(
          '[SidebarStore] Updating template ID from',
          this.selectedTemplate.id,
          'to',
          (saveResult as { planId: string }).planId
        )
        this.selectedTemplate.id = (saveResult as { planId: string }).planId
        this.currentPlanTemplateId = (saveResult as { planId: string }).planId
      }

      if (this.currentVersionIndex < this.planVersions.length - 1) {
        this.planVersions = this.planVersions.slice(0, this.currentVersionIndex + 1)
      }
      this.planVersions.push(content)
      this.currentVersionIndex = this.planVersions.length - 1
      // Reset modification flag after successful save
      this.hasTaskRequirementModified = false
      return saveResult
    } catch (error: unknown) {
      console.error('Failed to save plan template:', error)
      throw error
    }
  }

  preparePlanExecution() {
    if (!this.selectedTemplate) return null
    this.isExecuting = true
    try {
      let planData
      try {
        planData = JSON.parse(this.jsonContent)
        planData.planTemplateId = this.selectedTemplate.id
      } catch {
        throw new Error('Failed to parse plan data')
      }
      const title = this.selectedTemplate.title ?? planData.title ?? 'Execution Plan'
      return {
        title,
        planData,
        params: this.executionParams.trim() || undefined,
        replacementParams: undefined as Record<string, string> | undefined,
      }
    } catch (error: unknown) {
      console.error('Failed to prepare plan execution:', error)
      this.isExecuting = false
      throw error
    }
  }

  finishPlanExecution() {
    this.isExecuting = false
  }
  // Load available tools from backend
  async loadAvailableTools() {
    if (this.isLoadingTools) {
      return // Avoid duplicate requests
    }

    this.isLoadingTools = true
    this.toolsLoadError = ''

    try {
      console.log('[SidebarStore] Loading available tools...')
      const tools = await ToolApiService.getAvailableTools()
      console.log('[SidebarStore] Loaded available tools:', tools)
      // Transform tools to ensure they have all required fields
      this.availableTools = tools.map((tool: Tool) => ({
        key: tool.key || '',
        name: tool.name || '',
        description: tool.description || '',
        enabled: tool.enabled || false,
        serviceGroup: tool.serviceGroup || 'default',
        selectable: tool.selectable,
      }))
    } catch (error) {
      console.error('[SidebarStore] Error loading tools:', error)
      this.toolsLoadError = error instanceof Error ? error.message : 'Unknown error'
      this.availableTools = []
    } finally {
      this.isLoadingTools = false
    }
  }
}

export const sidebarStore = reactive(new SidebarStore())
