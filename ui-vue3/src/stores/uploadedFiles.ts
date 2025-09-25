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

import { ref } from 'vue'

export interface UploadedFile {
  name: string
  size: number
  type: string
  planId?: string
  relativePath?: string
}

// Global state for uploaded files
const uploadedFilesState = ref<UploadedFile[]>([])

// Global state for uploaded files planId
const uploadedFilesPlanId = ref<string | null>(null)

// Helper functions
export const setUploadedFiles = (files: UploadedFile[]) => {
  uploadedFilesState.value = [...files]
}

export const addUploadedFiles = (files: UploadedFile[]) => {
  uploadedFilesState.value = [...uploadedFilesState.value, ...files]
}

export const clearUploadedFiles = () => {
  uploadedFilesState.value = []
  // clean planId
  uploadedFilesPlanId.value = null
}

export const getUploadedFiles = () => {
  return uploadedFilesState.value
}

export const hasUploadedFiles = () => {
  return uploadedFilesState.value.length > 0
}

export const setUploadedFilesPlanId = (planId: string) => {
  uploadedFilesPlanId.value = planId
}

export const getUploadedFilesPlanId = (): string | undefined => {
  return uploadedFilesPlanId.value || undefined
}

export const hasUploadedFilesPlanId = () => {
  return uploadedFilesPlanId.value !== null && uploadedFilesPlanId.value.trim() !== ''
}

export const clearUploadedFilesPlanId = () => {
  uploadedFilesPlanId.value = null
}
