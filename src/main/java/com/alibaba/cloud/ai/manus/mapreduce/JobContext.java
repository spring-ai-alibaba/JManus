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
 * JobContext is the interface for job-specific information.
 * 
 * <p>JobContext provides access to configuration and other job-specific 
 * information for the job.</p>
 */
public interface JobContext {

    /**
     * Gets the job configuration.
     *
     * @return the job configuration
     */
    JobConfiguration getConfiguration();

    /**
     * Gets the job name.
     *
     * @return the job name
     */
    String getJobName();

    /**
     * Gets the job ID.
     *
     * @return the job ID
     */
    String getJobId();

    /**
     * Gets the current job status.
     *
     * @return the current job status
     */
    Job.JobStatus getJobStatus();

    /**
     * Sets the job status.
     *
     * @param status the new job status
     */
    void setJobStatus(Job.JobStatus status);

    /**
     * Gets a counter value.
     *
     * @param group the counter group
     * @param name the counter name
     * @return the counter value
     */
    long getCounter(String group, String name);

    /**
     * Increments a counter.
     *
     * @param group the counter group
     * @param name the counter name
     * @param value the value to increment by
     */
    void incrementCounter(String group, String name, long value);
}
