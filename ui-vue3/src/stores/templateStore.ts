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
import { PlanTemplateApiService } from '@/api/plan-template-with-tool-api-service'
import { i18n } from '@/base/i18n'
import type { PlanTemplateConfigVO } from '@/types/plan-template'
import { reactive } from 'vue'
import { usePlanTemplateConfigSingleton } from '@/composables/usePlanTemplateConfig'

export class TemplateStore {
  // Loading and error state
  isLoading = false
  errorMessage = ''
  // Track task requirement modifications
  hasTaskRequirementModified = false

  // Organization method: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc'
  organizationMethod: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc' = 'by_time'

  // Template service group mapping (templateId -> serviceGroup)
  templateServiceGroups: Map<string, string> = new Map()

  // Group collapse state (groupName -> isCollapsed)
  groupCollapseState: Map<string | null, boolean> = new Map()

  constructor() {
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
      console.warn('[TemplateStore] Failed to load group collapse state:', error)
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
      console.warn('[TemplateStore] Failed to save group collapse state:', error)
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

  // Reference to template config for accessing planTemplateList
  private templateConfig = usePlanTemplateConfigSingleton()

  // Computed properties
  get sortedTemplates(): PlanTemplateConfigVO[] {
    const templates = [...this.templateConfig.planTemplateList.value]

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
        const groups = new Map<string, PlanTemplateConfigVO[]>()
        const ungrouped: PlanTemplateConfigVO[] = []

        templates.forEach(template => {
          const planTemplateId = template.planTemplateId || ''
          const serviceGroup = this.templateServiceGroups.get(planTemplateId) ?? ''
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
        const sortedGroups = new Map<string, PlanTemplateConfigVO[]>()
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
        const result: PlanTemplateConfigVO[] = []
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
          const timeA = this.parseDateTime(a.updateTime || a.createTime || '')
          const timeB = this.parseDateTime(b.updateTime || b.createTime || '')
          return timeB.getTime() - timeA.getTime()
        })
    }
  }

  // Get grouped templates for display
  get groupedTemplates(): Map<string | null, PlanTemplateConfigVO[]> {
    if (this.organizationMethod !== 'by_group_time' && this.organizationMethod !== 'by_group_abc') {
      // Return all templates in a single group for non-grouped methods
      return new Map([[null, this.sortedTemplates]])
    }

    const groups = new Map<string | null, PlanTemplateConfigVO[]>()
    const ungrouped: PlanTemplateConfigVO[] = []

    // Use sorted templates directly (already sorted by sortedTemplates getter)
    const sorted = this.sortedTemplates

    sorted.forEach(template => {
      const planTemplateId = template.planTemplateId || ''
      const serviceGroup = this.templateServiceGroups.get(planTemplateId) ?? ''
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
    const result = new Map<string | null, PlanTemplateConfigVO[]>()
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

  // Actions
  async loadPlanTemplateList() {
    this.isLoading = true
    this.errorMessage = ''
    try {
      console.log('[TemplateStore] Starting to load plan template list...')
      const configVOs = await PlanTemplateApiService.getAllPlanTemplateConfigVOs()

      // Use PlanTemplateConfigVO directly
      this.templateConfig.planTemplateList.value = configVOs

      // Build service group mapping
      this.templateServiceGroups.clear()
      for (const config of this.templateConfig.planTemplateList.value) {
        const planTemplateId = config.planTemplateId
        if (planTemplateId) {
          const serviceGroup = config.serviceGroup || config.toolConfig?.serviceGroup || ''
          if (serviceGroup) {
            this.templateServiceGroups.set(planTemplateId, serviceGroup)
          }
        }
      }

      console.log(
        `[TemplateStore] Successfully loaded ${this.templateConfig.planTemplateList.value.length} plan templates`
      )
    } catch (error: unknown) {
      console.error('[TemplateStore] Failed to load plan template list:', error)
      this.templateConfig.planTemplateList.value = []
      const message = error instanceof Error ? error.message : 'Unknown error'
      this.errorMessage = `Load failed: ${message}`
    } finally {
      this.isLoading = false
    }
  }

  async selectTemplate(template: PlanTemplateConfigVO) {
    this.templateConfig.currentPlanTemplateId.value = template.planTemplateId || null
    this.templateConfig.selectedTemplate.value = template
    // Reset modification flag when loading new template
    this.hasTaskRequirementModified = false

    console.log(`[TemplateStore] Selected plan template: ${template.planTemplateId}`)
  }

  async createNewTemplate(planType: string) {
    const emptyTemplate: PlanTemplateConfigVO = {
      planTemplateId: `new-${Date.now()}`,
      title: i18n.global.t('sidebar.newTemplateName'),
      planType: planType,
      createTime: new Date().toISOString(),
      updateTime: new Date().toISOString(),
    }
    this.templateConfig.selectedTemplate.value = emptyTemplate
    this.templateConfig.currentPlanTemplateId.value = null

    // Reset modification flag for new template
    this.hasTaskRequirementModified = false

    console.log('[TemplateStore] Created new empty plan template')
  }

  async deleteTemplate(template: PlanTemplateConfigVO) {
    const planTemplateId = template.planTemplateId
    if (!planTemplateId) {
      console.warn('[TemplateStore] deleteTemplate: Invalid template object or ID')
      return
    }
    try {
      await PlanActApiService.deletePlanTemplate(planTemplateId)
      if (this.templateConfig.currentPlanTemplateId.value === planTemplateId) {
        this.clearSelection()
      }
      await this.loadPlanTemplateList()
      console.log(`[TemplateStore] Plan template ${planTemplateId} has been deleted`)
    } catch (error: unknown) {
      console.error('Failed to delete plan template:', error)
      await this.loadPlanTemplateList()
      throw error
    }
  }

  clearSelection() {
    this.templateConfig.currentPlanTemplateId.value = null
    this.templateConfig.selectedTemplate.value = null
    this.hasTaskRequirementModified = false
  }
}

export const templateStore = reactive(new TemplateStore())

