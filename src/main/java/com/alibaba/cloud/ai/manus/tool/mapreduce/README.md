# ParallelExecutionManager

A powerful tool for managing parallel execution of multiple functions in the Manus AI system. This manager allows you to batch register executable functions and execute them in parallel, with comprehensive status tracking and monitoring capabilities.

## Features

1. **Batch Registration**: Register multiple executable functions at once
2. **Parallel Execution**: Execute all registered functions simultaneously using a 'start' function
3. **Status Tracking**: Monitor function execution status (REGISTERED, RUNNING, COMPLETED, FAILED, CANCELLED)
4. **Function Querying**: Get pending functions (not yet started)
5. **Thread Pool Management**: Uses a configurable thread pool for efficient parallel execution

## Usage

### 1. Register Functions

#### Batch Function Registration

```java
Map<String, Object> batchInput = new HashMap<>();
batchInput.put("action", "registerBatch");

List<Map<String, Object>> functions = new ArrayList<>();

// Function 1: Data processing
Map<String, Object> func1 = new HashMap<>();
func1.put("toolName", "data_processor");
func1.put("toolDescription", "Process and analyze data");
func1.put("planTemplateId", "template_data_processing");
func1.put("serviceGroup", "data_services");
func1.put("toolCallId", "call_001");
func1.put("input", Map.of("dataSource", "database", "operation", "analyze"));
functions.add(func1);

// Function 2: Report generation
Map<String, Object> func2 = new HashMap<>();
func2.put("toolName", "report_generator");
func2.put("toolDescription", "Generate reports from processed data");
func2.put("planTemplateId", "template_report_generation");
func2.put("serviceGroup", "report_services");
func2.put("toolCallId", "call_002");
func2.put("input", Map.of("format", "PDF", "template", "standard"));
functions.add(func2);

batchInput.put("functions", functions);
ToolExecuteResult result = parallelManager.run(batchInput);
```

### 2. Execute Functions

#### Start All Registered Functions

```java
Map<String, Object> input = new HashMap<>();
input.put("action", "start");
ToolExecuteResult result = parallelManager.run(input);
```

#### Start Specific Functions

```java
Map<String, Object> input = new HashMap<>();
input.put("action", "start");
input.put("functionIds", Arrays.asList("func_1", "func_2"));
ToolExecuteResult result = parallelManager.run(input);
```

### 3. Monitor Status

#### Get Pending Functions

```java
Map<String, Object> input = new HashMap<>();
input.put("action", "getPending");
ToolExecuteResult result = parallelManager.run(input);
```

## Function Status States

- **REGISTERED**: Function is registered but not started
- **RUNNING**: Function is currently executing
- **COMPLETED**: Function execution completed successfully
- **FAILED**: Function execution failed
- **CANCELLED**: Function execution was cancelled

## Input Parameters

### Required Parameters

- `action`: The action to perform ("registerBatch", "start", "getPending")

### For Registration Actions

- `functions`: Array of function objects to register
- Each function object requires:
  - `toolName`: Name of the tool/function
  - `planTemplateId`: ID of the plan template to execute
  - `toolCallId`: Unique identifier for the tool call

### Optional Parameters

- `toolDescription`: Description of the tool
- `serviceGroup`: Service group for the tool
- `input`: Input parameters for the function
- `functionIds`: List of specific function IDs to start (for start action)

## Example Workflow

```java
// 1. Register multiple functions
registerMultipleFunctions();

// 2. Check pending functions
getPendingFunctions();

// 3. Start parallel execution
startParallelExecution();

// 4. Monitor status
getPendingFunctions(); // Check again after execution
```

## Thread Pool Configuration

The ParallelExecutionManager uses a fixed thread pool with 10 threads by default. You can modify this in the constructor if needed:

```java
this.executorService = Executors.newFixedThreadPool(10);
```

## Error Handling

The manager includes comprehensive error handling:

- Validates required parameters
- Handles execution failures gracefully
- Provides detailed error messages
- Maintains function status even when errors occur

## Cleanup

Always call `shutdown()` when done to properly close the thread pool:

```java
parallelManager.shutdown();
```

## Integration with SubplanToolWrapper

The ParallelExecutionManager is designed to work with SubplanToolWrapper instances, allowing you to execute subplans in parallel. It handles the complexity of managing multiple subplan executions while providing a simple interface for registration and execution.

## Thread Safety

The ParallelExecutionManager is thread-safe and can be used concurrently from multiple threads. All internal state is protected using concurrent data structures and atomic operations.

## Available Actions

The ParallelExecutionManager supports the following actions:

1. **registerBatch**: Register multiple functions at once
2. **start**: Execute registered functions in parallel
3. **getPending**: Get all functions that are registered but not yet started
