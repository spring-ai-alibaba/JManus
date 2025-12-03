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

import type { ChatMessage } from '@/types/message-dialog'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js'
import { marked } from 'marked'
import { computed } from 'vue'

// Configure marked options
marked.setOptions({
  breaks: true, // Convert line breaks to <br>
  gfm: true, // Enable GitHub Flavored Markdown
})

// Configure marked extension for code blocks with syntax highlighting
// In marked v16, we use extensions to customize rendering
marked.use({
  renderer: {
    code(token: { text?: string; raw?: string; code?: string; lang?: string; language?: string }) {
      try {
        // Handle different token formats from marked v16
        let codeText = ''
        let language: string | undefined

        // In marked v16, token is an object with 'text' and 'lang' properties
        if (token && typeof token === 'object') {
          codeText = token.text || token.raw || token.code || ''
          language = token.lang || token.language
        } else if (typeof token === 'string') {
          // Fallback: if token is a string (shouldn't happen in v16, but handle it)
          codeText = token
        } else {
          // Last resort: convert to string
          console.warn('[useMessageFormatting] Unexpected token format:', typeof token, token)
          codeText = String(token || '')
        }

        // Ensure codeText is a string
        if (typeof codeText !== 'string') {
          console.warn(
            '[useMessageFormatting] Code text is not a string:',
            typeof codeText,
            codeText
          )
          codeText = String(codeText || '')
        }

        // If language is specified, try to highlight it
        if (language && typeof language === 'string' && hljs.getLanguage(language)) {
          try {
            const highlighted = hljs.highlight(codeText, { language }).value
            return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
          } catch (err) {
            console.warn('[useMessageFormatting] Highlight.js error:', err)
          }
        }
        // Fallback: escape HTML and wrap in pre/code
        const escaped = codeText
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;')
        return `<pre><code>${escaped}</code></pre>`
      } catch (error) {
        console.error('[useMessageFormatting] Error in code renderer:', error, token)
        // Ultimate fallback - escape all HTML entities properly
        // Note: & must be escaped first to avoid double-encoding
        const safeText = String(token?.text || token?.raw || token || '')
        const escaped = safeText
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;')
        return `<pre><code>${escaped}</code></pre>`
      }
    },
  },
})

/**
 * Message formatting utilities
 */
export function useMessageFormatting() {
  /**
   * Format response text with markdown and code highlighting
   */
  const formatResponseText = (text: string): string => {
    if (!text) return ''

    try {
      // Convert markdown to HTML using marked
      const html = marked.parse(text) as string

      // Sanitize HTML to prevent XSS attacks
      const sanitized = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
          'p',
          'br',
          'strong',
          'em',
          'u',
          's',
          'code',
          'pre',
          'h1',
          'h2',
          'h3',
          'h4',
          'h5',
          'h6',
          'ul',
          'ol',
          'li',
          'blockquote',
          'a',
          'img',
          'table',
          'thead',
          'tbody',
          'tr',
          'th',
          'td',
          'hr',
          'div',
          'span',
        ],
        ALLOWED_ATTR: ['href', 'title', 'alt', 'src', 'class', 'target', 'rel'],
        ALLOW_DATA_ATTR: false,
      })

      return sanitized
    } catch (error) {
      console.error('[useMessageFormatting] Error formatting markdown:', error)
      // Fallback to plain text with escaped HTML
      return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\n/g, '<br>')
    }
  }

  /**
   * Format timestamp for display
   */
  const formatTimestamp = (timestamp: Date): string => {
    // Handle invalid dates
    if (!timestamp || isNaN(timestamp.getTime())) {
      return ''
    }

    const now = new Date()
    const diff = now.getTime() - timestamp.getTime()

    // Less than 1 minute
    if (diff < 60000) {
      return 'Just now'
    }

    // Less than 1 hour
    if (diff < 3600000) {
      const minutes = Math.floor(diff / 60000)
      return `${minutes} minutes ago`
    }

    // Less than 1 day
    if (diff < 86400000) {
      const hours = Math.floor(diff / 3600000)
      return `${hours} hours ago`
    }

    // More than 1 day
    try {
      return timestamp.toLocaleDateString('zh-CN', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    } catch (error) {
      console.warn('[useMessageFormatting] Error formatting timestamp:', timestamp, error)
      return ''
    }
  }

  /**
   * Get message CSS classes
   */
  const getMessageClasses = (message: ChatMessage) => {
    return computed(() => ({
      user: message.type === 'user',
      assistant: message.type === 'assistant',
      streaming: message.isStreaming,
      'has-error': !!message.error,
      'has-thinking': !!message.thinking,
      'has-execution': !!message.planExecution,
    }))
  }

  /**
   * Format file size for attachments
   */
  const formatFileSize = (bytes: number): string => {
    if (bytes === 0) return '0 B'

    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))

    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  /**
   * Truncate long text with ellipsis
   */
  const truncateText = (text: string, maxLength: number = 100): string => {
    if (!text || text.length <= maxLength) return text
    return text.substring(0, maxLength) + '...'
  }

  /**
   * Extract plain text from HTML
   */
  const stripHtml = (html: string): string => {
    return html.replace(/<[^>]*>/g, '')
  }

  /**
   * Check if message has content to display
   */
  const hasDisplayableContent = (message: ChatMessage): boolean => {
    return !!(
      message.content ||
      (message.thinking ?? false) ||
      (message.planExecution ?? false) ||
      message.error
    )
  }

  /**
   * Get message status text
   */
  const getMessageStatus = (message: ChatMessage): string => {
    if (message.error) return 'Send failed'
    if (message.isStreaming) return 'Typing...'
    if (message.type === 'assistant' && !message.content && !message.thinking)
      return 'Waiting for response'
    return ''
  }

  return {
    formatResponseText,
    formatTimestamp,
    getMessageClasses,
    formatFileSize,
    truncateText,
    stripHtml,
    hasDisplayableContent,
    getMessageStatus,
  }
}
