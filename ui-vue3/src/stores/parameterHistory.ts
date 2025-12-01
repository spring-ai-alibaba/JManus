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
import { reactive } from 'vue'

/**
 * Parameter history store for persisting parameter sets per tool
 * Stores up to 5 unique parameter sets per tool (identified by planTemplateId)
 */
export class ParameterHistoryStore {
  // Parameter history storage: Map<planTemplateId, Array<Record<string, string>>>
  // Stores up to 5 unique parameter sets per tool
  private parameterHistory: Map<string, Array<Record<string, string>>> = new Map()

  // Navigation state: Map<paramName, historyIndex>
  // Tracks which history index each parameter is currently viewing (-1 means viewing current, not history)
  private paramHistoryIndices: Map<string, number> = new Map()

  // LocalStorage key for persisting parameter history
  private readonly PARAMETER_HISTORY_KEY = 'parameterHistory'
  private readonly PARAM_HISTORY_INDICES_KEY = 'paramHistoryIndices'

  // Maximum number of parameter sets to store per tool
  readonly MAX_HISTORY_SIZE = 5

  constructor() {
    // Load parameter history from localStorage on initialization
    this.loadFromStorage()
  }

  /**
   * Load parameter history from localStorage
   */
  private loadFromStorage() {
    try {
      // Load parameter history
      const savedHistory = localStorage.getItem(this.PARAMETER_HISTORY_KEY)
      if (savedHistory) {
        const parsed = JSON.parse(savedHistory)
        // Convert array format back to Map
        this.parameterHistory = new Map(Object.entries(parsed))
        console.log('[ParameterHistoryStore] Loaded parameter history from localStorage:', this.parameterHistory.size, 'tools')
      }

      // Load navigation indices
      const savedIndices = localStorage.getItem(this.PARAM_HISTORY_INDICES_KEY)
      if (savedIndices) {
        const parsed = JSON.parse(savedIndices)
        this.paramHistoryIndices = new Map(Object.entries(parsed))
        console.log('[ParameterHistoryStore] Loaded parameter history indices from localStorage:', this.paramHistoryIndices.size, 'parameters')
      }
    } catch (error) {
      console.warn('[ParameterHistoryStore] Failed to load from localStorage:', error)
    }
  }

  /**
   * Save parameter history to localStorage
   */
  private saveToStorage() {
    try {
      // Convert Map to object for JSON serialization
      const historyObj = Object.fromEntries(this.parameterHistory)
      localStorage.setItem(this.PARAMETER_HISTORY_KEY, JSON.stringify(historyObj))
      console.log('[ParameterHistoryStore] Saved parameter history to localStorage')

      // Save navigation indices
      const indicesObj = Object.fromEntries(this.paramHistoryIndices)
      localStorage.setItem(this.PARAM_HISTORY_INDICES_KEY, JSON.stringify(indicesObj))
      console.log('[ParameterHistoryStore] Saved parameter history indices to localStorage')
    } catch (error) {
      console.warn('[ParameterHistoryStore] Failed to save to localStorage:', error)
    }
  }

  /**
   * Get parameter history for a specific tool
   */
  getHistory(planTemplateId: string): Array<Record<string, string>> | undefined {
    return this.parameterHistory.get(planTemplateId)
  }

  /**
   * Check if a parameter has history available
   */
  hasParameterHistory(planTemplateId: string, paramName: string): boolean {
    const history = this.parameterHistory.get(planTemplateId)
    if (!history || history.length === 0) {
      return false
    }
    // Check if any history entry has this parameter
    return history.some(entry => entry[paramName] !== undefined)
  }

  /**
   * Save parameter set to history
   */
  saveParameterSet(planTemplateId: string, paramSet: Record<string, string>): void {
    if (!planTemplateId || Object.keys(paramSet).length === 0) {
      return
    }

    // Check for duplicates
    if (this.isDuplicate(planTemplateId, paramSet)) {
      console.log('[ParameterHistoryStore] Parameter set is duplicate, not saving')
      return
    }

    // Get or create history for this tool
    if (!this.parameterHistory.has(planTemplateId)) {
      this.parameterHistory.set(planTemplateId, [])
    }

    const history = this.parameterHistory.get(planTemplateId)!

    // Add to history (most recent first)
    history.unshift(paramSet)

    // Limit to MAX_HISTORY_SIZE
    if (history.length > this.MAX_HISTORY_SIZE) {
      history.splice(this.MAX_HISTORY_SIZE)
    }

    // Update the map (trigger reactivity)
    this.parameterHistory.set(planTemplateId, [...history])

    // Save to localStorage
    this.saveToStorage()

    console.log('[ParameterHistoryStore] Saved parameter set to history for', planTemplateId)
  }

  /**
   * Check if a parameter set is duplicate
   */
  private isDuplicate(planTemplateId: string, paramSet: Record<string, string>): boolean {
    const history = this.parameterHistory.get(planTemplateId)
    if (!history || history.length === 0) {
      return false
    }

    return history.some(existingSet => this.areParameterSetsEqual(existingSet, paramSet))
  }

  /**
   * Check if two parameter sets are equal
   */
  private areParameterSetsEqual(
    set1: Record<string, string>,
    set2: Record<string, string>
  ): boolean {
    const keys1 = Object.keys(set1).sort()
    const keys2 = Object.keys(set2).sort()

    if (keys1.length !== keys2.length) {
      return false
    }

    return keys1.every(key => set1[key] === set2[key])
  }

  /**
   * Get current history index for a parameter
   */
  getParamHistoryIndex(paramName: string): number {
    return this.paramHistoryIndices.get(paramName) ?? -1
  }

  /**
   * Set history index for a parameter
   */
  setParamHistoryIndex(paramName: string, index: number): void {
    this.paramHistoryIndices.set(paramName, index)
    // Save to localStorage
    this.saveToStorage()
  }

  /**
   * Reset all parameter navigation indices
   */
  resetParamHistoryNavigation(): void {
    this.paramHistoryIndices.clear()
    // Save to localStorage
    this.saveToStorage()
    console.log('[ParameterHistoryStore] Reset all parameter history navigation indices')
  }

  /**
   * Get parameter value from history at specific index
   */
  getParamValueFromHistory(
    planTemplateId: string,
    paramName: string,
    historyIndex: number
  ): string | undefined {
    const history = this.parameterHistory.get(planTemplateId)
    if (!history || historyIndex < 0 || historyIndex >= history.length) {
      return undefined
    }

    const historyEntry = history[historyIndex]
    return historyEntry?.[paramName]
  }

  /**
   * Clear history for a specific tool
   */
  clearHistory(planTemplateId: string): void {
    this.parameterHistory.delete(planTemplateId)
    // Save to localStorage
    this.saveToStorage()
    console.log('[ParameterHistoryStore] Cleared history for', planTemplateId)
  }

  /**
   * Clear all history
   */
  clearAllHistory(): void {
    this.parameterHistory.clear()
    this.paramHistoryIndices.clear()
    // Save to localStorage
    this.saveToStorage()
    console.log('[ParameterHistoryStore] Cleared all history')
  }
}

// Create store instance
const storeInstance = new ParameterHistoryStore()

// Create reactive store
export const parameterHistoryStore = reactive({
  // Methods that expose store functionality
  getHistory: (planTemplateId: string) => storeInstance.getHistory(planTemplateId),
  hasParameterHistory: (planTemplateId: string, paramName: string) =>
    storeInstance.hasParameterHistory(planTemplateId, paramName),
  saveParameterSet: (planTemplateId: string, paramSet: Record<string, string>) =>
    storeInstance.saveParameterSet(planTemplateId, paramSet),
  getParamHistoryIndex: (paramName: string) => storeInstance.getParamHistoryIndex(paramName),
  setParamHistoryIndex: (paramName: string, index: number) =>
    storeInstance.setParamHistoryIndex(paramName, index),
  resetParamHistoryNavigation: () => storeInstance.resetParamHistoryNavigation(),
  getParamValueFromHistory: (planTemplateId: string, paramName: string, historyIndex: number) =>
    storeInstance.getParamValueFromHistory(planTemplateId, paramName, historyIndex),
  clearHistory: (planTemplateId: string) => storeInstance.clearHistory(planTemplateId),
  clearAllHistory: () => storeInstance.clearAllHistory(),
  MAX_HISTORY_SIZE: storeInstance.MAX_HISTORY_SIZE,
})

