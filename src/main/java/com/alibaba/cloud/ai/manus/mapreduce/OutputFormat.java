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
 * OutputFormat describes the output-specification for a Map-Reduce job.
 * 
 * <p>The Map-Reduce framework relies on the OutputFormat to:
 * <ol>
 *   <li>Validate the output-specification of the job. For e.g. check that the 
 *       output directory doesn't already exist.</li>
 *   <li>Provide the RecordWriter implementation to be used to write the output 
 *       files of the job. Output files are stored in a FileSystem.</li>
 * </ol>
 *
 * @param <K> the output key type
 * @param <V> the output value type
 */
public interface OutputFormat<K, V> {

    /**
     * Get the RecordWriter for the given output.
     *
     * @param context the context of the output file
     * @return a RecordWriter to write the output for the job
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    RecordWriter<K, V> getRecordWriter(TaskContext context) throws IOException, InterruptedException;

    /**
     * Check for validity of the output-specification for the job.
     *
     * @param context information about the job
     * @throws IOException if the output specification is invalid
     * @throws InterruptedException if the thread is interrupted
     */
    void checkOutputSpecs(JobContext context) throws IOException, InterruptedException;

    /**
     * Get the OutputCommitter for this output format. This is responsible 
     * for ensuring the output is committed correctly.
     *
     * @param context the context of the output file
     * @return an OutputCommitter for the job
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    OutputCommitter getOutputCommitter(TaskContext context) throws IOException, InterruptedException;

    /**
     * RecordWriter writes the output key/value pairs to an output file.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    interface RecordWriter<K, V> {
        
        /**
         * Writes a key/value pair.
         *
         * @param key the key to write
         * @param value the value to write
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        void write(K key, V value) throws IOException, InterruptedException;

        /**
         * Close this RecordWriter to free resources.
         *
         * @param context the context of the output file
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        void close(TaskContext context) throws IOException, InterruptedException;
    }

    /**
     * OutputCommitter describes the commit of task output for a Map-Reduce job.
     */
    interface OutputCommitter {
        
        /**
         * For the framework to setup the job during initialization.
         *
         * @param context the context of the job
         * @throws IOException if an I/O error occurs
         */
        void setupJob(JobContext context) throws IOException;

        /**
         * For the framework to cleanup the job during job completion.
         *
         * @param context the context of the job
         * @throws IOException if an I/O error occurs
         */
        void commitJob(JobContext context) throws IOException;

        /**
         * For the framework to abort the job after a failure.
         *
         * @param context the context of the job
         * @throws IOException if an I/O error occurs
         */
        void abortJob(JobContext context, Job.JobStatus status) throws IOException;

        /**
         * For the framework to setup the task during initialization.
         *
         * @param context the context of the task
         * @throws IOException if an I/O error occurs
         */
        void setupTask(TaskContext context) throws IOException;

        /**
         * For the framework to cleanup the task during task completion.
         *
         * @param context the context of the task
         * @throws IOException if an I/O error occurs
         */
        void commitTask(TaskContext context) throws IOException;

        /**
         * For the framework to abort the task after a failure.
         *
         * @param context the context of the task
         * @throws IOException if an I/O error occurs
         */
        void abortTask(TaskContext context) throws IOException;

        /**
         * Check whether task needs a commit.
         *
         * @param context the context of the task
         * @return true if the task needs a commit
         * @throws IOException if an I/O error occurs
         */
        boolean needsTaskCommit(TaskContext context) throws IOException;
    }
}
