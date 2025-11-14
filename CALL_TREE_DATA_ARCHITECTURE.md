# Call Tree Data Architecture - Optimal Communication Strategy

## Overview
This document defines the optimal data architecture for efficient communication between major components in the call tree.

---

## Core Principle: Single Source of Truth

Each piece of data should have **one authoritative source**. Other components should **reference** or **subscribe** to that source, not duplicate it.

---

## Component Data Ownership

### 1. `usePlanExecution` - Plan Execution State Manager

**Owns (Single Source of Truth):**
```typescript
{
  // Core execution state
  trackedPlanIds: Set<string>              // All planIds being polled
  planExecutionRecords: Map<string, PlanExecutionRecord>  // Complete execution records by planId
  
  // Polling state
  isPolling: boolean
  pollTimer: number | null
}
```

**Responsibilities:**
- ✅ Track which plans are being executed/polled
- ✅ Store complete `PlanExecutionRecord` data for each planId
- ✅ Manage polling lifecycle (start/stop)
- ✅ Provide reactive updates when records change

**Why this design:**
- `planExecutionRecords` is the **single source of truth** for all plan execution data
- All components that need plan status should read from here
- No duplication of plan execution state

---

### 2. `useMessageDialog` - UI State Manager

**Owns (Single Source of Truth):**
```typescript
{
  // UI state
  dialogList: MessageDialog[]              // All dialogs
  activeDialogId: string | null             // Currently active dialog
  
  // UI loading state
  isLoading: boolean
  error: string | null
  streamingMessageId: string | null
  inputPlaceholder: string | null
}
```

**Each MessageDialog contains:**
```typescript
{
  id: string
  title: string
  messages: ChatMessage[]                   // UI messages
  planId?: string                          // Reference to planId (not owned!)
  conversationId?: string                   // Reference to conversationId
  createdAt: Date
  updatedAt: Date
  isActive: boolean
}
```

**Each ChatMessage contains:**
```typescript
{
  id: string
  type: 'user' | 'assistant'
  content: string
  planExecution?: PlanExecutionRecord       // Reference (not owned!)
  // ... other UI fields
}
```

**Responsibilities:**
- ✅ Manage dialog list and active dialog
- ✅ Manage message list for each dialog
- ✅ Handle UI loading/error states
- ✅ **Reference** planIds (not own them)

**Why this design:**
- UI state is separate from execution state
- `dialog.planId` and `message.planExecution` are **references**, not owned data
- UI updates by watching `usePlanExecution.planExecutionRecords`

---

### 3. `useConversationHistory` - History Manager

**Owns:**
```typescript
{
  // No persistent state - uses other composables
}
```

**Responsibilities:**
- ✅ Load history from API
- ✅ Convert API records to UI format
- ✅ Delegate to `useMessageDialog` for adding messages
- ✅ Delegate to `usePlanExecution` for caching records

**Why this design:**
- Pure orchestration layer
- No state duplication
- Uses existing composables

---

## Communication Patterns

### Pattern 1: Reactive Watch with watchEffect (Recommended - Vue 3 Idiomatic)

**useMessageDialog watches usePlanExecution using watchEffect:**

```typescript
// In useMessageDialog.ts
watchEffect(() => {
  const records = planExecution.planExecutionRecords

  // Process all dialogs that have associated planIds
  for (const dialog of dialogList.value) {
    if (!dialog.planId) continue

    const record = records.get(dialog.planId)
    if (!record) continue

    // Find the assistant message with this planId
    const message = dialog.messages.find(
      m => m.planExecution?.rootPlanId === dialog.planId
    )
    if (!message) continue

    // Update message with latest plan execution record
    updateMessageWithPlanRecord(dialog, message, record)
  }
})
```

**Why watchEffect is better:**
- ✅ **Automatic dependency tracking** - Vue automatically tracks which reactive values are accessed
- ✅ **No deep watch needed** - Map is already reactive, no need for `{ deep: true }`
- ✅ **More performant** - Only re-runs when actually accessed dependencies change
- ✅ **Cleaner code** - No need to specify what to watch explicitly
- ✅ **Processes all dialogs** - Not limited to just active dialog

**Benefits:**
- ✅ Automatic updates when plan execution changes
- ✅ No manual synchronization needed
- ✅ Single source of truth
- ✅ Better performance (no unnecessary deep watching)
- ✅ More Vue 3 idiomatic approach

---

### Pattern 2: Direct Reference (For Lookups)

**useMessageDialog references usePlanExecution directly:**

**Current implementation - Plan completion check:**

The logic for checking if a plan is running and preventing new requests is implemented in two places:

**1. Business Logic Check (in useMessageDialog.ts):**
```typescript
// In sendMessage() and executePlan() methods
if (activeRootPlanId.value) {
  const activeRecord = planExecution.planExecutionRecords.get(activeRootPlanId.value)
  if (activeRecord && !activeRecord.completed) {
    const errorMsg = 'Please wait for the current task to complete before starting a new one'
    error.value = errorMsg
    return { success: false, error: errorMsg }
  }
}
```

**2. UI Disable State (in InputArea.vue and ExecutionController.vue):**

**InputArea.vue:**
```typescript
// Input is disabled when messageDialog.isLoading is true
const isDisabled = computed(() => messageDialog.isLoading.value)

// Send button is also disabled
:disabled="!currentInput.trim() || isDisabled"
```

**ExecutionController.vue:**
```typescript
// Execute button is disabled when messageDialog.isLoading is true (same validation)
const isDisabled = computed(() => messageDialog.isLoading.value)

const canExecute = computed(() => {
  // Disable if messageDialog is loading (same validation as InputArea)
  if (isDisabled.value) {
    return false
  }
  // ... other validation checks
})

// Execute button uses canExecute
:disabled="!canExecute"
```

**How it works:**
- ✅ **Pre-request check**: Before sending a new message/plan, checks if `activeRootPlanId` exists and its record is not completed
- ✅ **UI feedback**: Input area is disabled when `isLoading` is true (set during message sending)
- ✅ **Reactive updates**: When plan completes, `isLoading` is set to false, enabling input again
- ✅ **Direct reference**: Uses `planExecution.planExecutionRecords.get()` to check plan status

**Optional helper functions (could be added for cleaner code):**
```typescript
// Helper function example - could be added if needed
const isPlanRunning = (planId: string): boolean => {
  const record = planExecution.planExecutionRecords.get(planId)
  return record ? !record.completed : false
}

// Helper function example - could be added if needed
const getPlanStatus = (planId: string) => {
  return planExecution.planExecutionRecords.get(planId)?.status
}
```

**Benefits:**
- ✅ Direct access to authoritative data
- ✅ No state duplication
- ✅ Always up-to-date
- ✅ Two-layer protection: business logic + UI state

---

### Pattern 3: Event-Driven (For Actions)

**useMessageDialog triggers usePlanExecution:**

```typescript
// When starting a new plan
const sendMessage = async (query) => {
  // ... send API request ...
  
  if (response.planId) {
    // Store reference in dialog
    targetDialog.planId = response.planId
    
    // Trigger execution tracking
    planExecution.handlePlanExecutionRequested(response.planId)
  }
}
```

**Benefits:**
- ✅ Clear action flow
- ✅ Separation of concerns
- ✅ usePlanExecution manages its own state

---

## Current Issues & Recommendations

### Issue 1: `activeRootPlanId` in useMessageDialog ✅ FIXED

**Previous (Problematic):**
```typescript
// useMessageDialog.ts
const activeRootPlanId = ref<string | null>(null)
```

**Problems:**
- ❌ Duplicated information that can be derived from `dialogList`
- ❌ Created synchronization complexity
- ❌ Required manual updates when dialog changed

**Current Solution (Implemented):**
```typescript
// useMessageDialog.ts
const activeRootPlanId = computed(() => {
  return activeDialog.value?.planId || null
})
```

**Benefits:**
- ✅ No duplicate state
- ✅ Always in sync automatically
- ✅ Derived from single source of truth (`activeDialog`)
- ✅ No manual synchronization needed
- ✅ More reactive and Vue 3 idiomatic

---

### Issue 2: Dialog-Plan Mapping

**Current:**
```typescript
// Finding dialog by planId
const dialog = dialogList.value.find(d => d.planId === activeRootPlanId.value)
```

**Recommended Enhancement:**
```typescript
// Add helper method in useMessageDialog
const getDialogByPlanId = (planId: string): MessageDialog | null => {
  return dialogList.value.find(d => d.planId === planId) || null
}

// Or create reverse index (if needed for performance)
const planIdToDialogId = computed(() => {
  const map = new Map<string, string>()
  dialogList.value.forEach(dialog => {
    if (dialog.planId) {
      map.set(dialog.planId, dialog.id)
    }
  })
  return map
})
```

---

### Issue 3: Message-Plan Mapping

**Current:**
```typescript
// Finding message by planId
const message = dialog.messages.find(
  m => m.planExecution?.rootPlanId === activeRootPlanId.value
)
```

**Recommended:**
```typescript
// Add helper method
const getMessageByPlanId = (dialog: MessageDialog, planId: string): ChatMessage | undefined => {
  return dialog.messages.find(m => m.planExecution?.rootPlanId === planId)
}
```

---

## Optimal Data Flow

### Flow 1: Starting a New Plan

```
1. User sends message
   ↓
2. useMessageDialog.sendMessage()
   ├── Creates dialog
   ├── Adds user message
   ├── Calls API
   └── Receives planId
   ↓
3. useMessageDialog
   ├── Stores planId reference: dialog.planId = planId
   ├── Adds assistant message with planExecution reference
   └── Triggers: planExecution.handlePlanExecutionRequested(planId)
   ↓
4. usePlanExecution
   ├── Adds planId to trackedPlanIds
   ├── Starts polling
   └── Updates planExecutionRecords[planId]
   ↓
5. useMessageDialog (via watch)
   ├── Detects planExecutionRecords change
   ├── Finds dialog by planId
   ├── Finds message by planId
   └── Updates message.planExecution
```

**Key Points:**
- ✅ planId flows: API → useMessageDialog → usePlanExecution
- ✅ Execution data flows: usePlanExecution → useMessageDialog (via watch)
- ✅ No circular dependencies

---

### Flow 2: Polling Updates

```
1. usePlanExecution.pollPlanStatus()
   ├── Fetches latest PlanExecutionRecord
   └── Updates planExecutionRecords[planId]
   ↓
2. Reactive watch triggers in useMessageDialog
   ├── Detects change in planExecutionRecords
   ├── Finds dialog with matching planId
   ├── Finds message with matching rootPlanId
   └── Updates message.planExecution
   ↓
3. UI automatically re-renders (Vue reactivity)
```

**Key Points:**
- ✅ Single update point: `planExecutionRecords`
- ✅ Automatic propagation via reactive watch
- ✅ No manual synchronization

---

### Flow 3: Plan Completion

```
1. usePlanExecution detects completion
   ├── planExecutionRecords[planId].completed = true
   └── Removes from trackedPlanIds (after delay)
   ↓
2. useMessageDialog watch detects completion
   ├── Updates message.planExecution
   ├── Sets isStreaming = false
   └── Clears activeRootPlanId (if using computed, auto-clears)
   ↓
3. UI shows final result
```

---

## Summary: What Each Component Should Hold

### ✅ usePlanExecution
- **Owns:** `trackedPlanIds`, `planExecutionRecords`, polling state
- **Provides:** Reactive plan execution data
- **Does NOT own:** Dialog state, message state, UI state

### ✅ useMessageDialog
- **Owns:** `dialogList`, `activeDialogId`, UI loading state
- **References:** `planId` in dialogs, `planExecution` in messages
- **Watches:** `planExecution.planExecutionRecords` for updates
- **Does NOT own:** Plan execution records (only references)

### ✅ useConversationHistory
- **Owns:** Nothing (stateless)
- **Orchestrates:** Calls useMessageDialog and usePlanExecution
- **Does NOT own:** Any state

---

## Communication Efficiency Rules

### Rule 1: Data Ownership
- **One component owns, others reference**
- Never duplicate the same data in multiple places

### Rule 2: Reactive Updates
- Use Vue's reactive system (`watchEffect`, `watch`, `computed`)
- Prefer `watchEffect` for automatic dependency tracking
- Avoid manual synchronization

### Rule 3: Clear Data Flow
- **Downward:** Actions flow down (useMessageDialog → usePlanExecution)
- **Upward:** State flows up via reactivity (usePlanExecution → useMessageDialog)

### Rule 4: Minimal State
- Only store what you truly own
- Derive everything else from single source of truth

---

## Recommended Refactoring

1. **✅ Remove `activeRootPlanId` from useMessageDialog** - DONE
   - ✅ Replaced with computed property derived from `activeDialog.planId`
   - Now automatically stays in sync

2. **✅ Simplify watch logic** - DONE
   - ✅ Using `watchEffect` for automatic dependency tracking
   - ✅ Processes all dialogs with planIds, not just active one
   - ✅ No deep watch needed (Map is already reactive)

3. **Add helper methods** (Optional future enhancement)
   - `getDialogByPlanId(planId)`
   - `getMessageByPlanId(dialog, planId)`
   - `getActivePlanId()` (already implemented as computed)

4. **✅ Clear separation** - DONE
   - ✅ usePlanExecution: Execution state only
   - ✅ useMessageDialog: UI state + references to execution state
   - ✅ No overlap, no duplication

---

## Performance Considerations

### Efficient Lookups
- Use `Map` for O(1) lookups (already done in `planExecutionRecords`)
- Consider reverse index if needed: `Map<planId, dialogId>`

### Watch Optimization
- ✅ **Use `watchEffect`** for automatic dependency tracking (more efficient)
- ✅ **No deep watch needed** - Reactive Map/Set are already reactive
- Use `watch` with specific keys when you need explicit control
- Avoid `{ deep: true }` unnecessarily - it watches entire object tree

### Memory Management
- Remove completed plans from `planExecutionRecords` after delay (already implemented)
- Clean up dialogs when not needed

---

## Conclusion

**Best Practice:**
- `usePlanExecution` = Single source of truth for plan execution
- `useMessageDialog` = UI state + references to plan execution
- Communication = Reactive watches + direct references
- No duplication = Highest efficiency

**Key Insight:**
The most efficient architecture is one where:
1. Each piece of data has exactly one owner
2. Other components reference (don't copy) that data
3. Updates flow automatically via reactivity
4. No manual synchronization needed

