/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { reactive } from 'vue'

export interface MemoryEmits {
  (e: 'memory-selected'): void
}

export class MemoryStore {
  // Basic state
  isCollapsed = false
  conversationId: string | null = null
  loadMessages = () => {}
  intervalId: number | undefined = undefined

  // LocalStorage key for persisting conversationId
  private readonly CONVERSATION_ID_KEY = 'currentConversationId'

  constructor() {
    // Load conversation ID from localStorage on initialization
    this.loadConversationIdFromStorage()
  }

  /**
   * Load conversation ID from localStorage
   */
  private loadConversationIdFromStorage() {
    try {
      const savedConversationId = localStorage.getItem(this.CONVERSATION_ID_KEY)
      if (savedConversationId) {
        this.conversationId = savedConversationId
        console.log('[MemoryStore] Restored conversationId from localStorage:', savedConversationId)
      }
    } catch (error) {
      console.warn('[MemoryStore] Failed to load conversationId from localStorage:', error)
    }
  }

  /**
   * Save conversation ID to localStorage
   */
  private saveConversationIdToStorage() {
    try {
      if (this.conversationId) {
        localStorage.setItem(this.CONVERSATION_ID_KEY, this.conversationId)
        console.log('[MemoryStore] Saved conversationId to localStorage:', this.conversationId)
      } else {
        localStorage.removeItem(this.CONVERSATION_ID_KEY)
        console.log('[MemoryStore] Removed conversationId from localStorage')
      }
    } catch (error) {
      console.warn('[MemoryStore] Failed to save conversationId to localStorage:', error)
    }
  }

  toggleSidebar() {
    this.isCollapsed = !this.isCollapsed
    if (this.isCollapsed) {
      this.loadMessages()
      this.intervalId = window.setInterval(() => {
        this.loadMessages()
      }, 3000)
    } else {
      clearInterval(this.intervalId)
    }
  }

  /**
   * Select a conversation (replaces selectMemory)
   * @param conversationId The conversation ID to select
   */
  selectConversation(conversationId: string) {
    this.toggleSidebar()
    this.conversationId = conversationId
    this.saveConversationIdToStorage()
  }

  /**
   * Clear the selected conversation (replaces clearMemoryId)
   */
  clearSelectedConversation() {
    this.conversationId = null
    this.saveConversationIdToStorage()
  }

  setConversationId(conversationId: string | null) {
    this.conversationId = conversationId
    // Persist to localStorage whenever conversationId changes
    this.saveConversationIdToStorage()
  }

  clearConversationId() {
    this.conversationId = null
    // Clear from localStorage
    this.saveConversationIdToStorage()
  }

  /**
   * Get the current conversation ID (from memory or localStorage)
   */
  getConversationId(): string | null {
    return this.conversationId
  }

  generateRandomId(): string {
    return Math.random().toString(36).substring(2, 10)
  }

  setLoadMessages(messages: () => void) {
    this.loadMessages = messages
  }
}

export const memoryStore = reactive(new MemoryStore())
