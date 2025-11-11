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

import { ToolApiService } from '@/api/tool-api-service'
import type { PlanTemplateConfigVO } from '@/types/plan-template'
import type { Tool } from '@/types/tool'
import { reactive } from 'vue'
import { templateStore } from './templateStore'

type TabType = 'list' | 'config'

export class SidebarStore {
  // Basic state (sidebar UI state only)
  isCollapsed = false
  currentTab: TabType = 'list'

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

  // Reference to templateStore for template-related operations
  get templateStore() {
    return templateStore
  }

  // Computed properties that delegate to templateStore
  get jsonContent(): string {
    return templateStore.jsonContent
  }

  get planType(): string {
    return templateStore.planType
  }

  get planVersions(): string[] {
    return templateStore.planVersions
  }

  get currentVersionIndex(): number {
    return templateStore.currentVersionIndex
  }

  get currentPlanTemplateId(): string | null {
    return templateStore.currentPlanTemplateId
  }

  get planTemplateList() {
    return templateStore.planTemplateList
  }

  get selectedTemplate() {
    return templateStore.selectedTemplate
  }

  get isLoading(): boolean {
    return templateStore.isLoading
  }

  get errorMessage(): string {
    return templateStore.errorMessage
  }

  get hasTaskRequirementModified(): boolean {
    return templateStore.hasTaskRequirementModified
  }

  get organizationMethod() {
    return templateStore.organizationMethod
  }

  get templateServiceGroups() {
    return templateStore.templateServiceGroups
  }

  get groupCollapseState() {
    return templateStore.groupCollapseState
  }

  get sortedTemplates() {
    return templateStore.sortedTemplates
  }

  get groupedTemplates() {
    return templateStore.groupedTemplates
  }

  get canRollback(): boolean {
    return templateStore.canRollback
  }

  get canRestore(): boolean {
    return templateStore.canRestore
  }

  get templateConfigInstance() {
    return templateStore.templateConfigInstance
  }

  // Delegate methods to templateStore
  loadGroupCollapseState() {
    return templateStore.loadGroupCollapseState()
  }

  saveGroupCollapseState() {
    return templateStore.saveGroupCollapseState()
  }

  toggleGroupCollapse(groupName: string | null) {
    return templateStore.toggleGroupCollapse(groupName)
  }

  isGroupCollapsed(groupName: string | null): boolean {
    return templateStore.isGroupCollapsed(groupName)
  }

  setOrganizationMethod(method: 'by_time' | 'by_abc' | 'by_group_time' | 'by_group_abc') {
    return templateStore.setOrganizationMethod(method)
  }

  // Actions
  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed
  }

  switchToTab(tab: TabType) {
    this.currentTab = tab
  }

  // Delegate template operations to templateStore
  async loadPlanTemplateList() {
    return templateStore.loadPlanTemplateList()
  }

  async selectTemplate(template: PlanTemplateConfigVO) {
    await templateStore.selectTemplate(template)
    this.currentTab = 'config'
  }

  async createNewTemplate(planType: string) {
    await templateStore.createNewTemplate(planType)
    this.currentTab = 'config'
    // Reload available tools to ensure fresh tool list
    console.log('[SidebarStore] 🔄 Reloading available tools for new template')
    await this.loadAvailableTools()
  }

  async deleteTemplate(template: PlanTemplateConfigVO) {
    return templateStore.deleteTemplate(template)
  }

  clearSelection() {
    templateStore.clearSelection()
    this.currentTab = 'list'
  }

  rollbackVersion() {
    return templateStore.rollbackVersion()
  }

  restoreVersion() {
    return templateStore.restoreVersion()
  }

  async saveTemplate() {
    return templateStore.saveTemplate()
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
