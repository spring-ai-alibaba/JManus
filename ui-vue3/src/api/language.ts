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

export interface LanguageResponse {
  language: 'zh' | 'en'
}

export interface SetLanguageRequest {
  language: 'zh' | 'en'
}

export interface SetLanguageResponse {
  success: boolean
  language: 'zh' | 'en'
  error?: string
}

/**
 * Get current language preference from backend
 * @returns Language preference ("zh" or "en")
 */
export const getLanguage = async (): Promise<'zh' | 'en'> => {
  try {
    const response = await fetch('/api/language', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    })

    if (!response.ok) {
      throw new Error(`Failed to get language: ${response.statusText}`)
    }

    const data: LanguageResponse = await response.json()
    return data.language || 'zh'
  } catch (error) {
    console.error('Failed to get language from backend:', error)
    throw error
  }
}

/**
 * Set language preference in backend
 * @param language Language to set ("zh" or "en")
 * @returns Success response with updated language
 */
export const setLanguage = async (language: 'zh' | 'en'): Promise<SetLanguageResponse> => {
  try {
    const response = await fetch('/api/language', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ language }),
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({ error: response.statusText }))
      throw new Error(errorData.error || `Failed to set language: ${response.statusText}`)
    }

    const data: SetLanguageResponse = await response.json()
    return data
  } catch (error) {
    console.error('Failed to set language in backend:', error)
    throw error
  }
}
