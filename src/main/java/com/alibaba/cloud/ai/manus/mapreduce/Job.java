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

import java.io.IOException;

/**
 * A MapReduce job configuration and execution interface.
 * 
 * <p>This interface defines the contract for configuring and executing
 * MapReduce jobs. It provides methods to set up mappers, reducers, input/output
 * formats, and other job parameters.</p>
 *
 * @param <KEYIN> the input key type
 * @param <VALUEIN> the input value type
 * @param <KEYOUT> the output key type
 * @param <VALUEOUT> the output value type
 */
public interface Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {

    /**
     * Sets the mapper class for this job.
     *
     * @param mapperClass the mapper class
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setMapper(Class<? extends Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT>> mapperClass);

    /**
     * Sets the reducer class for this job.
     *
     * @param reducerClass the reducer class
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setReducer(Class<? extends Reducer<KEYOUT, VALUEOUT, KEYOUT, VALUEOUT>> reducerClass);

    /**
     * Sets the input format for this job.
     *
     * @param inputFormatClass the input format class
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setInputFormat(Class<? extends InputFormat<KEYIN, VALUEIN>> inputFormatClass);

    /**
     * Sets the output format for this job.
     *
     * @param outputFormatClass the output format class
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setOutputFormat(Class<? extends OutputFormat<KEYOUT, VALUEOUT>> outputFormatClass);

    /**
     * Sets the number of reduce tasks for this job.
     *
     * @param numReduceTasks the number of reduce tasks
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setNumReduceTasks(int numReduceTasks);

    /**
     * Sets a job property.
     *
     * @param name the property name
     * @param value the property value
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setProperty(String name, String value);

    /**
     * Gets a job property.
     *
     * @param name the property name
     * @return the property value, or null if not set
     */
    String getProperty(String name);

    /**
     * Gets a job property with a default value.
     *
     * @param name the property name
     * @param defaultValue the default value
     * @return the property value, or the default value if not set
     */
    String getProperty(String name, String defaultValue);

    /**
     * Sets the job name.
     *
     * @param jobName the job name
     * @return this job instance for method chaining
     */
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setJobName(String jobName);

    /**
     * Gets the job name.
     *
     * @return the job name
     */
    String getJobName();

    /**
     * Submits the job for execution.
     *
     * @return true if the job was submitted successfully
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    boolean submit() throws IOException, InterruptedException;

    /**
     * Waits for the job to complete.
     *
     * @return true if the job completed successfully
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    boolean waitForCompletion() throws IOException, InterruptedException;

    /**
     * Gets the job status.
     *
     * @return the current job status
     */
    JobStatus getStatus();

    /**
     * Gets the job configuration.
     *
     * @return the job configuration
     */
    JobConfiguration getConfiguration();

    /**
     * Job status enumeration.
     */
    enum JobStatus {
        PREPARING,
        RUNNING,
        COMPLETED,
        FAILED,
        KILLED
    }
}
