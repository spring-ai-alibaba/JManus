# Spring AI Alibaba Lynxe 开发者快速入门

本项目是阿里巴巴 AI 计划执行与管理系统，采用 Spring Boot 架构，支持计划生成、异步执行、状态跟踪、配置管理等功能。以下为开发者调试和理解系统的核心入口与接口说明。

## 1. 核心模块接口入口

- 所有核心 REST API 均在 `src/main/java/com/alibaba/cloud/ai/lynxe/runtime/controller/` 及 `config/` 目录下。
- 推荐从 `LynxeController.java`、`PlanTemplateController.java`、`ConfigController.java` 入手，快速定位主要业务流程。

## 2. 前置条件：创建并发布 Func-Agent

**重要提示**：在使用同步/异步执行接口之前，必须先完成以下步骤：

1. **创建 Func-Agent**
   - 在系统界面中创建并配置一个 Func-Agent（计划模板）
   - 定义计划步骤、代理配置、工具选择等

2. **发布为工具**
   - 在 Func-Agent 配置界面中，点击"发布服务"按钮
   - 选择"发布为内部工具调用"（Internal Method Call）
   - 设置工具名称（toolName）、工具描述、参数定义等

3. **发布为 HTTP 服务**（可选但推荐）
   - 在发布服务界面中，选择"发布为 HTTP 服务"（HTTP Service）
   - 系统将生成对应的 HTTP API 调用示例

**只有完成上述步骤后，才能通过 `executeByToolNameAsync` 或 `executeByToolNameSync` 接口调用该 Func-Agent。**

参考前端实现：
- `ui-vue3/src/components/sidebar/ExecutionController.vue`: 执行控制器，包含发布服务按钮
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue`: 发布服务弹窗

## 3. 任务发起与执行

### 异步执行（推荐）

- **接口文件**: `LynxeController.java`
- **主要入口**: `POST /api/executor/executeByToolNameAsync`
- **请求参数**:
  ```json
  {
    "toolName": "my-tool",
    "serviceGroup": "research",  // 必选
    "replacementParams": {       // 可选，用于替换计划模板中的 <<param>> 占位符
      "param1": "value1",
      "param2": "value2"
    },
    "uploadedFiles": ["file1.pdf", "file2.txt"],  // 可选，上传的文件名列表
    "uploadKey": "upload-1234567890_1234_1",      // 可选，文件上传会话的唯一标识符
    "conversationId": "xxx"                       // 可选，会话ID（用于记忆管理）
  }
  ```

- **文件上传参数说明**:
  - `uploadedFiles`: 字符串数组，包含已上传文件的原始文件名列表
    - 通过 `POST /api/file-upload/upload` 接口上传文件后获得
    - 后端会将文件名附加到每个执行步骤的 `stepRequirement` 中，供 LLM 使用
    - 示例: `["report.pdf", "data.csv"]`
  
  - `uploadKey`: 字符串，文件上传会话的唯一标识符
    - 上传文件时，后端自动生成并返回（格式: `upload-{timestamp}_{random}_{threadId}`）
    - 用于标识同一批上传的文件，并在执行时将文件同步到计划目录
    - 后端使用 `uploadKey` 调用 `syncUploadedFilesToPlan()` 将文件复制到计划工作目录
    - 示例: `"upload-1703123456789_5678_42"`
  
  - **参数使用说明**:
    - **推荐同时使用** `uploadedFiles` 和 `uploadKey`：
      - `uploadedFiles` 让 LLM 知道有哪些文件可用（通过 stepRequirement）
      - `uploadKey` 确保文件被同步到计划工作目录，代理可以实际访问这些文件
      - 两者配合使用可获得最佳效果
    - 也可以单独使用：
      - 仅 `uploadedFiles`：LLM 会知道文件名，但文件可能不在计划目录中
      - 仅 `uploadKey`：文件会被同步到计划目录，但 LLM 可能不知道有哪些文件
  
  - **文件上传流程**:
    1. 调用 `POST /api/file-upload/upload` 上传文件（使用 FormData，字段名: `files`）
    2. 后端返回 `FileUploadResult`，包含:
       - `uploadKey`: 上传会话标识符
       - `uploadedFiles`: 文件信息数组（每个包含 `originalName`、`size`、`type` 等）
    3. 在调用执行接口时，**同时传递** `uploadKey` 和 `uploadedFiles`（文件名列表）
    4. 后端执行时会自动将文件同步到计划目录，代理可以访问这些文件
- **响应示例**:
  ```json
  {
    "planId": "plan-123456",
    "status": "processing",
    "message": "Task submitted, processing",
    "conversationId": "conv-123",
    "toolName": "my-tool",
    "planTemplateId": "template-456"
  }
  ```
- **功能**: 异步执行计划任务，立即返回 `planId`，任务在后台执行。适用于长时间运行的任务。

### 同步执行

- **接口文件**: `LynxeController.java`
- **主要入口**: `POST /api/executor/executeByToolNameSync`
- **请求参数**: 与异步执行相同
- **响应**: 直接返回执行结果（等待任务完成）
- **功能**: 同步执行计划任务，等待执行完成后返回结果。适用于快速任务。

## 4. 任务状态跟踪

### 获取执行详情（核心接口）

- **接口文件**: `LynxeController.java`
- **主要入口**: `GET /api/executor/details/{planId}`
- **功能**: 获取指定 planId 的完整执行记录及状态
- **响应结构**: `PlanExecutionRecord` 对象，包含：
  - `currentPlanId`: 当前计划ID
  - `rootPlanId`: 根计划ID（用于子计划）
  - `title`: 计划标题
  - `status`: 执行状态（pending/running/completed/failed）
  - `completed`: 是否完成
  - `summary`: 执行摘要
  - `agentExecutionSequence`: 代理执行序列（核心数据）
    - 每个元素包含：`agentName`、`agentRequest`、`result`、`status`、`stepId` 等
  - `userInputWaitState`: 用户输入等待状态（如任务需要用户填写表单）
  - `structureResult`: 结构化结果（任务完成时提取的最后工具调用结果）
  - `subPlanExecutionRecords`: 子计划执行记录（支持嵌套）

- **使用场景**:
  - 前端通过轮询此接口跟踪任务进度
  - 获取完整的执行历史记录
  - 检查是否需要用户输入

### 获取步骤详情

- **接口文件**: `LynxeController.java`
- **主要入口**: `GET /api/executor/agent-execution/{stepId}`
- **功能**: 获取指定 stepId 的详细步骤信息
- **响应结构**: `AgentExecutionRecord` 对象，包含：
  - `agentName`: 代理名称
  - `agentRequest`: 代理请求内容
  - `agentDescription`: 代理描述
  - `result`: 执行结果
  - `errorMessage`: 错误信息
  - `status`: 状态（RUNNING/FINISHED/IDLE）
  - `thinkActRecords`: 思考-行动记录列表
    - 每个记录包含：`thinkInput`、`thinkOutput`、`inputCharCount`、`outputCharCount`、`actToolInfoList` 等

- **使用场景**:
  - 在右侧面板显示详细的步骤执行信息
  - 查看每个步骤的思考过程和工具调用

### 辅助接口

- **删除执行记录**: `DELETE /api/executor/details/{planId}`
  - 功能: 删除指定 planId 的执行记录（实际不删除数据库记录，仅标记）

- **提交用户输入**: `POST /api/executor/submit-input/{planId}`
  - 功能: 当任务等待用户输入时（如表单填写），提交用户输入数据
  - 请求体: `{ "field1": "value1", "field2": "value2" }`

## 5. 前端实现参考

### 执行流程

参考 `ui-vue3/src/components/sidebar/ExecutionController.vue`:
- 调用 `executeByToolNameAsync` 发起任务
- 获取返回的 `planId`
- 使用 `planId` 进行状态跟踪

### 状态跟踪

参考 `ui-vue3/src/composables/usePlanExecution.ts`:
- 使用 `CommonApiService.getDetails(planId)` 轮询执行状态
- 默认轮询间隔：1秒
- 自动处理重试和错误恢复

### 详情显示

参考 `ui-vue3/src/components/chat/ExecutionDetails.vue`:
- 显示 `PlanExecutionRecord` 的完整结构
- 支持嵌套子计划显示
- 支持步骤选择和详情查看

参考 `ui-vue3/src/composables/useRightPanel.ts`:
- 处理步骤选择事件
- 调用 `fetchAgentExecutionDetail(stepId)` 获取步骤详情
- 在右侧面板显示详细执行信息

## 6. 配置模块

- **接口文件**: `ConfigController.java`
- **主要入口**:
  - `GET /api/config/group/{groupName}`: 按分组获取配置项
  - `POST /api/config/batch-update`: 批量更新配置项

- **配置实体**: `ConfigEntity`
- **业务服务**: `ConfigService`

## 7. Plan-Act 模式核心接口

- **接口文件**: `PlanTemplateController.java`
- **主要入口**:
  - `POST /api/plan-template/generate`: 生成新的计划模板
  - `POST /api/plan-template/executePlanByTemplateId`: 按模板ID执行计划
  - `POST /api/plan-template/save`: 保存计划版本
  - `POST /api/plan-template/versions`: 获取计划版本历史
  - `POST /api/plan-template/get-version`: 获取指定版本计划
  - `GET /api/plan-template/list`: 获取所有计划模板列表
  - `POST /api/plan-template/update`: 更新计划模板
  - `POST /api/plan-template/delete`: 删除计划模板

- 计划生成、版本管理、执行均通过 `PlanTemplateService`、`PlanIdDispatcher`、`PlanningFactory` 协作完成。

## 8. 数据模型说明

### PlanExecutionRecord（执行记录）

这是系统的核心数据结构，所有执行反馈都通过此对象返回，简化集成操作。主要字段：

- **标识信息**: `currentPlanId`、`rootPlanId`、`parentPlanId`
- **执行状态**: `status`、`completed`、`currentStepIndex`
- **执行内容**: `agentExecutionSequence`（代理执行序列）
- **用户交互**: `userInputWaitState`（等待用户输入状态）
- **结果数据**: `summary`、`structureResult`（结构化结果）

详细字段说明请参考 `src/main/java/com/alibaba/cloud/ai/lynxe/recorder/entity/vo/PlanExecutionRecord.java` 源码注释。

### AgentExecutionRecord（代理执行记录）

单个代理的执行记录，包含：

- **基本信息**: `agentName`、`agentRequest`、`agentDescription`
- **执行结果**: `result`、`errorMessage`
- **状态信息**: `status`、`stepId`
- **子计划**: `subPlanExecutionRecords`（支持嵌套子计划）
- **思考-行动记录**: `thinkActRecords`（通过 `/api/executor/agent-execution/{stepId}` 获取）

## 9. 快速调试建议

1. **启动服务后，使用 Postman 或 curl 测试接口**:
   ```bash
   # 1. 上传文件（可选）
   curl -X POST http://localhost:8080/api/file-upload/upload \
     -F "files=@/path/to/file1.pdf" \
     -F "files=@/path/to/file2.txt"
   # 返回: {"uploadKey": "upload-xxx", "uploadedFiles": [{"originalName": "file1.pdf", ...}, ...]}
   
   # 2. 异步执行（使用上传的文件）
   curl -X POST http://localhost:8080/api/executor/executeByToolNameAsync \
     -H "Content-Type: application/json" \
     -d '{
       "toolName": "my-tool",
       "serviceGroup": "research",
       "replacementParams": {"param1": "value1"},
       "uploadedFiles": ["file1.pdf", "file2.txt"],
       "uploadKey": "upload-xxx"
     }'
   
   # 3. 获取执行详情（使用返回的 planId）
   curl http://localhost:8080/api/executor/details/{planId}
   
   # 4. 获取步骤详情
   curl http://localhost:8080/api/executor/agent-execution/{stepId}
   ```

2. **通过 `/api/executor/details/{planId}` 跟踪异步任务进度**:
   - 前端默认每1秒轮询一次
   - 可以手动调用查看当前状态
   - 返回完整的执行树结构（包括子计划）

3. **修改配置使用 `/api/config/batch-update`，无需重启服务**

4. **计划模板相关操作通过 `/api/plan-template/*` 系列接口完成**

5. **查看前端实现**:
   - `ExecutionController.vue`: 任务发起示例
   - `ExecutionDetails.vue`: 执行详情显示
   - `usePlanExecution.ts`: 状态轮询实现
   - `useRightPanel.ts`: 步骤详情获取

---

如需更详细的接口参数、返回结构或业务流程，请参考对应 Controller 源码及 Service 层实现。
