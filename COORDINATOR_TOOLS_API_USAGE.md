# Coordinator Tools API Usage Summary

## Base URL
`/api/coordinator-tools`

## API Endpoints Defined

### 1. `GET /api/coordinator-tools`
**Method**: `getAllCoordinatorTools()`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:46`  
**Used in**:
- `ui-vue3/src/components/input/InputArea.vue:163` - Loading inner tools for tool selection

### 2. `GET /api/coordinator-tools/config`
**Method**: `getCoordinatorToolConfig()`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:71`  
**Used in**:
- `ui-vue3/src/components/sidebar/ExecutionController.vue:900` - Loading coordinator tool configuration

### 3. `GET /api/coordinator-tools/endpoints`
**Method**: `getAllEndpoints()`  
**Status**: ❌ **UNUSED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:100`  
**Used in**:
- ❌ Not used in any Vue component
- Only referenced in `ui-vue3/src/components/publish-service-modal/PublishServiceModal.md:399` (markdown documentation file)

### 4. `GET /api/coordinator-tools/by-template/{planTemplateId}`
**Method**: `getCoordinatorToolByTemplate(planTemplateId: string)`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:126`  
**Used in**:
- `ui-vue3/src/components/sidebar/Sidebar.vue:280` - Loading tool info when plan template changes
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:359` - Getting existing tool before publishing
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:523` - Loading coordinator tool data

### 5. `POST /api/coordinator-tools`
**Method**: `createCoordinatorTool(tool: CoordinatorToolVO)`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:221`  
**Used in**:
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:413` - Creating new coordinator tool when publishing

### 6. `PUT /api/coordinator-tools/{id}`
**Method**: `updateCoordinatorTool(id: number, tool: CoordinatorToolVO)`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:285`  
**Used in**:
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:410` - Updating existing coordinator tool when publishing

### 7. `DELETE /api/coordinator-tools/{id}`
**Method**: `deleteCoordinatorTool(id: number)`  
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:349`  
**Used in**:
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:477` - Deleting coordinator tool

## Helper Methods (Not API Calls)

### `createDefaultCoordinatorTool()`
**Status**: ✅ **USED**  
**Location**: `ui-vue3/src/api/coordinator-tool-api-service.ts:201`  
**Note**: This is a local helper method that creates a default VO object, not an API call.  
**Used in**:
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:367` - Creating default tool VO
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue:534` - Creating default tool VO

## Deprecated/Unused Methods

### `getOrNewCoordinatorToolsByTemplate()`
**Status**: ❌ **NOT FOUND IN API SERVICE**  
**Note**: This method is referenced in `PublishServiceModal.md` (markdown file) but doesn't exist in the actual API service. It appears to have been replaced by `getCoordinatorToolByTemplate()` + `createDefaultCoordinatorTool()` pattern.

## Summary

**Total API Endpoints**: 7  
**Currently Used**: 6  
**Unused**: 1 (`getAllEndpoints`)  
**Deprecated/Non-existent**: 1 (`getOrNewCoordinatorToolsByTemplate`)

### Active Endpoints:
1. ✅ `GET /api/coordinator-tools` - Get all tools
2. ✅ `GET /api/coordinator-tools/config` - Get configuration
3. ✅ `GET /api/coordinator-tools/by-template/{id}` - Get tool by template ID
4. ✅ `POST /api/coordinator-tools` - Create tool
5. ✅ `PUT /api/coordinator-tools/{id}` - Update tool
6. ✅ `DELETE /api/coordinator-tools/{id}` - Delete tool

### Unused:
- ❌ `GET /api/coordinator-tools/endpoints` - Not used in any component, only in markdown documentation

