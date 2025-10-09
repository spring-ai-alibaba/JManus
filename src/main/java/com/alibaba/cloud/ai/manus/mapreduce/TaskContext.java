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
package com.alibaba.cloud.ai.manus.mapreduce;

/**
 * TaskContext is the interface for task-specific information.
 * 
 * <p>TaskContext provides access to configuration and other task-specific 
 * information for the current task.</p>
 */
public interface TaskContext extends JobContext {

    /**
     * Gets the task ID.
     *
     * @return the task ID
     */
    String getTaskId();

    /**
     * Gets the task type (MAP or REDUCE).
     *
     * @return the task type
     */
    TaskType getTaskType();

    /**
     * Gets the current task status.
     *
     * @return the current task status
     */
    TaskStatus getTaskStatus();

    /**
     * Sets the task status.
     *
     * @param status the new task status
     */
    void setTaskStatus(TaskStatus status);

    /**
     * Gets the input split for this task.
     *
     * @return the input split
     */
    InputFormat.InputSplit getInputSplit();

    /**
     * Sets the input split for this task.
     *
     * @param inputSplit the input split
     */
    void setInputSplit(InputFormat.InputSplit inputSplit);

    /**
     * Task type enumeration.
     */
    enum TaskType {
        MAP,
        REDUCE
    }

    /**
     * Task status enumeration.
     */
    enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        KILLED
    }
}
