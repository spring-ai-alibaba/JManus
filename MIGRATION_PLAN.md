# Migration Plan: Coordinator Tools API to usePlanTemplateConfig

## Analysis

Based on the current usage patterns, here are the methods that should be added to `usePlanTemplateConfig.ts` to centralize coordinator tools API calls:

## Methods to Add

### 1. **Load/Sync Coordinator Tool Data**
**Purpose**: Load coordinator tool data for the current plan template and sync it with toolConfig

```typescript
/**
 * Load coordinator tool by plan template ID and sync with toolConfig
 * This ensures toolConfig is always in sync with the actual coordinator tool
 * @param planTemplateId Plan template ID (optional, uses current if not provided)
 * @returns CoordinatorToolVO | null
 */
const loadCoordinatorTool = async (planTemplateId?: string): Promise<CoordinatorToolVO | null>
```

**Used in**:
- `Sidebar.vue:280` - Loading tool info when plan template changes
- `PublishServiceModal.vue:359, 523` - Loading coordinator tool data

**Implementation**:
- Call `CoordinatorToolApiService.getCoordinatorToolByTemplate()`
- If found, update `config.toolConfig` with the data
- Return the CoordinatorToolVO

---

### 2. **Create Coordinator Tool**
**Purpose**: Create a new coordinator tool for the current plan template

```typescript
/**
 * Create coordinator tool for the current plan template
 * Updates toolConfig after creation
 * @param toolData Partial CoordinatorToolVO (will merge with current toolConfig)
 * @returns Created CoordinatorToolVO
 */
const createCoordinatorTool = async (toolData?: Partial<CoordinatorToolVO>): Promise<CoordinatorToolVO>
```

**Used in**:
- `PublishServiceModal.vue:413` - Creating new coordinator tool when publishing

**Implementation**:
- Merge `toolData` with current `config.toolConfig`
- Call `CoordinatorToolApiService.createCoordinatorTool()`
- Update `config.toolConfig` with the response
- Return the created tool

---

### 3. **Update Coordinator Tool**
**Purpose**: Update existing coordinator tool for the current plan template

```typescript
/**
 * Update coordinator tool for the current plan template
 * Updates toolConfig after update
 * @param toolId Coordinator tool ID
 * @param toolData Partial CoordinatorToolVO (will merge with current toolConfig)
 * @returns Updated CoordinatorToolVO
 */
const updateCoordinatorTool = async (
  toolId: number,
  toolData?: Partial<CoordinatorToolVO>
): Promise<CoordinatorToolVO>
```

**Used in**:
- `PublishServiceModal.vue:410` - Updating existing coordinator tool when publishing

**Implementation**:
- Merge `toolData` with current `config.toolConfig`
- Call `CoordinatorToolApiService.updateCoordinatorTool()`
- Update `config.toolConfig` with the response
- Return the updated tool

---

### 4. **Delete Coordinator Tool**
**Purpose**: Delete coordinator tool for the current plan template

```typescript
/**
 * Delete coordinator tool for the current plan template
 * Clears toolConfig after deletion
 * @param toolId Coordinator tool ID
 * @returns Success result
 */
const deleteCoordinatorTool = async (toolId: number): Promise<{ success: boolean; message: string }>
```

**Used in**:
- `PublishServiceModal.vue:477` - Deleting coordinator tool

**Implementation**:
- Call `CoordinatorToolApiService.deleteCoordinatorTool()`
- Clear `config.toolConfig` or set it to undefined
- Return the result

---

### 5. **Get All Coordinator Tools**
**Purpose**: Get all coordinator tools (for tool selection, not tied to specific template)

```typescript
/**
 * Get all coordinator tools
 * This is a general query, not tied to a specific plan template
 * @returns List of all coordinator tools
 */
const getAllCoordinatorTools = async (): Promise<CoordinatorToolVO[]>
```

**Used in**:
- `InputArea.vue:163` - Loading inner tools for tool selection

**Implementation**:
- Call `CoordinatorToolApiService.getAllCoordinatorTools()`
- Return the list (no need to update config since it's not template-specific)

---

### 6. **Get Coordinator Tool Config**
**Purpose**: Get coordinator tool configuration (enabled/disabled status)

```typescript
/**
 * Get coordinator tool configuration (enabled/disabled status)
 * @returns CoordinatorToolConfig
 */
const getCoordinatorToolConfig = async (): Promise<CoordinatorToolConfig>
```

**Used in**:
- `ExecutionController.vue:900` - Loading coordinator tool configuration

**Implementation**:
- Call `CoordinatorToolApiService.getCoordinatorToolConfig()`
- Return the config (this is system-level config, not template-specific)

---

### 7. **Get or Create Default Coordinator Tool**
**Purpose**: Helper method to get existing tool or create default VO (not saved)

```typescript
/**
 * Get coordinator tool by template ID, or create default VO if not found
 * This is a convenience method that combines getCoordinatorToolByTemplate + createDefaultCoordinatorTool
 * @param planTemplateId Plan template ID (optional, uses current if not provided)
 * @returns CoordinatorToolVO (existing or default)
 */
const getOrCreateDefaultCoordinatorTool = async (
  planTemplateId?: string
): Promise<CoordinatorToolVO>
```

**Used in**:
- `PublishServiceModal.vue:367, 534` - Getting or creating default tool VO

**Implementation**:
- Try to load coordinator tool
- If not found, create default using `CoordinatorToolApiService.createDefaultCoordinatorTool()`
- Return the tool (default is not saved to database)

---

## Additional Considerations

### State Management
- Add a reactive state for the current coordinator tool (separate from toolConfig):
  ```typescript
  const currentCoordinatorTool = ref<CoordinatorToolVO | null>(null)
  ```

### Integration with Existing Methods
- Modify `load()` method to also call `loadCoordinatorTool()` after loading plan template
- Modify `save()` method to ensure coordinator tool is synced (if needed)

### Helper Methods
- Keep `createDefaultCoordinatorTool()` as a helper (it's not an API call, just creates a VO object)

## Migration Priority

1. **High Priority** (Core functionality):
   - `loadCoordinatorTool()` - Used in multiple places
   - `createCoordinatorTool()` - Used in publish workflow
   - `updateCoordinatorTool()` - Used in publish workflow
   - `deleteCoordinatorTool()` - Used in publish workflow

2. **Medium Priority** (Utility methods):
   - `getOrCreateDefaultCoordinatorTool()` - Convenience method
   - `getAllCoordinatorTools()` - Used in InputArea
   - `getCoordinatorToolConfig()` - Used in ExecutionController

## Benefits

1. **Centralized Management**: All coordinator tool operations go through one composable
2. **Automatic Sync**: toolConfig stays in sync with actual coordinator tool data
3. **Consistency**: Single source of truth for coordinator tool state
4. **Easier Testing**: All coordinator tool logic in one place
5. **Better Type Safety**: TypeScript can better track coordinator tool state

