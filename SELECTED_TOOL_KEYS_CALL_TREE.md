# Tool Call Tree: Displaying selectedToolKeys in JSON

## Overview
This diagram shows the call flow for how `selectedToolKeys` is displayed in JSON format in the frontend, including both frontend components and backend API calls.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FRONTEND LAYER                                   │
└─────────────────────────────────────────────────────────────────────────┘

1. User Interaction
   └─> JsonEditorV2.vue (UI Component)
       │
       ├─> [User clicks "Show JSON" button]
       │   └─> showJsonPreview = true
       │
       └─> [Component renders JSON preview]
           └─> <pre class="json-code">{{ generatedJsonOutput }}</pre>

2. JSON Generation (Computed Property)
   └─> generatedJsonOutput (computed)
       │
       └─> templateConfig.generateJsonString()
           │
           └─> usePlanTemplateConfig.ts::generateJsonString()
               │
               └─> Returns JSON string with selectedToolKeys:
                   {
                     "steps": [
                       {
                         "selectedToolKeys": ["tool1", "tool2", ...]
                       }
                     ]
                   }

3. Data Source (Reactive State)
   └─> templateConfig.config.steps[].selectedToolKeys
       │
       ├─> [Initial Load] ────────────────────────────────────────────────┐
       │                                                                   │
       │   └─> usePlanTemplateConfig.ts::load(planTemplateId)            │
       │       │                                                           │
       │       └─> PlanTemplateApiService.getPlanTemplateConfigVO()        │
       │           │                                                       │
       │           └─> HTTP GET /api/plan-template/{planTemplateId}/config│
       │                                                                   │
       └─> [User Selection] ──────────────────────────────────────────────┐
           │                                                               │
           └─> JsonEditorV2.vue::handleToolSelectionConfirm()             │
               │                                                           │
               └─> displayData.steps[index].selectedToolKeys = [...]      │
                   │                                                       │
                   └─> [Updates reactive state]                           │

4. Tool Selection UI
   └─> AssignedTools.vue
       │
       ├─> Props: selectedToolIds (bound to step.selectedToolKeys)
       │
       └─> [User clicks "Add/Remove Tools"]
           │
           └─> ToolSelectionModal.vue
               │
               ├─> [Loads available tools] ───────────────────────────────┐
               │                                                           │
               │   └─> useAvailableTools.ts::loadAvailableTools()          │
               │       │                                                   │
               │       └─> ToolApiService.getAvailableTools()              │
               │           │                                               │
               │           └─> HTTP GET /api/tools                         │
               │                                                           │
               └─> [User selects tools]
                   │
                   └─> @confirm event
                       │
                       └─> handleToolSelectionConfirm(selectedToolIds)
                           │
                           └─> Updates step.selectedToolKeys


┌─────────────────────────────────────────────────────────────────────────┐
│                         BACKEND LAYER                                    │
└─────────────────────────────────────────────────────────────────────────┘

5. Plan Template API Endpoint
   └─> PlanTemplateController.java
       │
       └─> @GetMapping("/{planTemplateId}/config")
           │
           └─> getPlanTemplateConfigVO(planTemplateId)
               │
               ├─> planTemplateConfigService.getPlanTemplate(planTemplateId)
               │   └─> [Loads PlanTemplate from database]
               │
               ├─> planTemplateService.getLatestPlanVersion(planTemplateId)
               │   └─> [Gets latest plan JSON from database]
               │
               └─> [Parses plan JSON to PlanInterface]
                   │
                   └─> Converts ExecutionStep → StepConfig
                       │
                       └─> stepConfig.setSelectedToolKeys(
                               step.getSelectedToolKeys()
                           )
                           │
                           └─> [Returns PlanTemplateConfigVO with selectedToolKeys]

6. Tool API Endpoint
   └─> ToolController.java
       │
       └─> @GetMapping("/api/tools")
           │
           └─> getAvailableTools()
               │
               └─> planningFactory.toolCallbackMap()
                   │
                   └─> [Returns List<Tool> with tool keys]
                       │
                       └─> Tool.key = "serviceGroup.toolName"
                           │
                           └─> [Used for tool selection in frontend]

7. Data Model (Backend)
   └─> ExecutionStep.java
       │
       └─> private List<String> selectedToolKeys
           │
           └─> [Stored in plan JSON in database]
               │
               └─> PlanInterface.getAllSteps()
                   │
                   └─> Each ExecutionStep contains selectedToolKeys


┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA FLOW SUMMARY                                │
└─────────────────────────────────────────────────────────────────────────┘

Database (H2)
    │
    ├─> PlanTemplate table
    │   └─> planTemplateId, title, serviceGroup, ...
    │
    └─> PlanVersion table
        └─> planJson (JSON string containing ExecutionStep[])
            └─> ExecutionStep.selectedToolKeys: List<String>

        │
        ▼

Backend API
    │
    ├─> GET /api/plan-template/{id}/config
    │   └─> Returns PlanTemplateConfigVO
    │       └─> steps[].selectedToolKeys: List<String>
    │
    └─> GET /api/tools
        └─> Returns List<Tool>
            └─> Tool.key: String (used for selection)

        │
        ▼

Frontend State
    │
    ├─> usePlanTemplateConfig.ts
    │   └─> config.steps[].selectedToolKeys: string[]
    │
    └─> JsonEditorV2.vue
        └─> displayData.steps[].selectedToolKeys: string[]

        │
        ▼

UI Display
    │
    ├─> AssignedTools.vue
    │   └─> Displays selected tools as chips/badges
    │
    └─> JSON Preview
        └─> generatedJsonOutput (computed)
            └─> JSON.stringify() with selectedToolKeys array


┌─────────────────────────────────────────────────────────────────────────┐
│                         KEY FILES & METHODS                              │
└─────────────────────────────────────────────────────────────────────────┘

Frontend:
  • ui-vue3/src/components/sidebar/JsonEditorV2.vue
    - Line 246: :selected-tool-ids="step.selectedToolKeys"
    - Line 279: {{ generatedJsonOutput }}
    - Line 429-430: generatedJsonOutput computed property
    - Line 897: Updates selectedToolKeys on tool selection

  • ui-vue3/src/composables/usePlanTemplateConfig.ts
    - Line 188-209: generateJsonString() - generates JSON with selectedToolKeys
    - Line 139-143: setConfig() - preserves selectedToolKeys when loading
    - Line 197: Ensures selectedToolKeys is always an array

  • ui-vue3/src/api/plan-template-with-tool-api-service.ts
    - Line 82-90: getPlanTemplateConfigVO() - fetches from backend

  • ui-vue3/src/api/tool-api-service.ts
    - Line 42-51: getAvailableTools() - fetches available tools

Backend:
  • src/main/java/.../planning/controller/PlanTemplateController.java
    - Line 584-633: getPlanTemplateConfigVO() - returns config with selectedToolKeys
    - Line 499: stepConfig.setSelectedToolKeys(step.getSelectedToolKeys())

  • src/main/java/.../planning/model/vo/PlanTemplateConfigVO.java
    - Line 205: private List<String> selectedToolKeys
    - Line 247-252: getter/setter methods

  • src/main/java/.../runtime/entity/vo/ExecutionStep.java
    - Line 53: private List<String> selectedToolKeys
    - Line 140-145: getter/setter methods

  • src/main/java/.../tool/controller/ToolController.java
    - Line 58-102: getAvailableTools() - returns available tools

