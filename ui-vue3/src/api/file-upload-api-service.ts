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

/**
 * File information interface matching FileUploadResult.FileInfo from Java
 */
export interface FileInfo {
  originalName: string
  size: number
  type: string
  uploadTime: string
  success: boolean
  error?: string
}

/**
 * File upload result interface matching FileUploadResult from Java
 */
export interface FileUploadResult {
  success: boolean
  message: string
  uploadKey: string
  uploadedFiles: FileInfo[]
  totalFiles: number
  successfulFiles: number
  failedFiles: number
}

/**
 * Get uploaded files response interface
 */
export interface GetUploadedFilesResponse {
  success: boolean
  uploadKey: string
  files: FileInfo[]
  totalCount: number
}

/**
 * Delete file response interface
 */
export interface DeleteFileResponse {
  success: boolean
  message?: string
  error?: string
  uploadKey: string
  fileName: string
}

/**
 * Upload configuration interface
 */
export interface UploadConfig {
  success: boolean
  maxFileSize: string
  maxFiles: number
  allowedTypes: string[]
}

export class FileUploadApiService {
  /**
   * Upload files
   * @param files Files to upload
   * @returns FileUploadResult with upload information
   */
  public static async uploadFiles(files: File[]): Promise<FileUploadResult> {
    try {
      console.log('[FileUploadApiService] Uploading files:', files.length)

      const formData = new FormData()
      files.forEach(file => {
        formData.append('files', file)
      })

      const response = await fetch('/api/file-upload/upload', {
        method: 'POST',
        body: formData,
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result: FileUploadResult = await response.json()
      console.log(
        '[FileUploadApiService] Files uploaded successfully:',
        result.successfulFiles,
        'uploadKey:',
        result.uploadKey
      )
      return result
    } catch (error) {
      console.error('[FileUploadApiService] Error uploading files:', error)
      throw error
    }
  }

  /**
   * Get uploaded files for a specific upload key
   * @param uploadKey Upload key to get files for
   * @returns GetUploadedFilesResponse with file information
   */
  public static async getUploadedFiles(uploadKey: string): Promise<GetUploadedFilesResponse> {
    try {
      console.log('[FileUploadApiService] Getting uploaded files for uploadKey:', uploadKey)

      const response = await fetch(`/api/file-upload/files/${encodeURIComponent(uploadKey)}`)

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result: GetUploadedFilesResponse = await response.json()
      console.log(
        '[FileUploadApiService] Got uploaded files:',
        result.totalCount,
        'for uploadKey:',
        uploadKey
      )
      return result
    } catch (error) {
      console.error('[FileUploadApiService] Error getting uploaded files:', error)
      throw error
    }
  }

  /**
   * Delete an uploaded file from a specific upload key directory
   * @param uploadKey Upload key directory
   * @param fileName File name to delete
   * @returns DeleteFileResponse with deletion result
   */
  public static async deleteFile(uploadKey: string, fileName: string): Promise<DeleteFileResponse> {
    try {
      console.log('[FileUploadApiService] Deleting file:', fileName, 'from uploadKey:', uploadKey)

      const response = await fetch(
        `/api/file-upload/files/${encodeURIComponent(uploadKey)}/${encodeURIComponent(fileName)}`,
        {
          method: 'DELETE',
          headers: {
            'Content-Type': 'application/json',
          },
        }
      )

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result: DeleteFileResponse = await response.json()
      console.log('[FileUploadApiService] File deleted successfully:', result.success)
      return result
    } catch (error) {
      console.error('[FileUploadApiService] Error deleting file:', error)
      throw error
    }
  }

  /**
   * Get upload configuration and limits
   * @returns UploadConfig with configuration information
   */
  public static async getUploadConfig(): Promise<UploadConfig> {
    try {
      console.log('[FileUploadApiService] Getting upload configuration')

      const response = await fetch('/api/file-upload/config')

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const result: UploadConfig = await response.json()
      console.log('[FileUploadApiService] Got upload configuration:', result)
      return result
    } catch (error) {
      console.error('[FileUploadApiService] Error getting upload configuration:', error)
      throw error
    }
  }
}
