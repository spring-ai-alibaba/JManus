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
import java.util.List;

/**
 * InputFormat describes the input-specification for a Map-Reduce job.
 * 
 * <p>The Map-Reduce framework relies on the InputFormat to:
 * <ol>
 *   <li>Validate the input-specification of the job.</li>
 *   <li>Split-up the input file(s) into logical InputSplits, each of which 
 *       is then assigned to an individual Mapper.</li>
 *   <li>Provide the RecordReader implementation to be used to read the input 
 *       data for the given InputSplit.</li>
 * </ol>
 *
 * @param <K> the input key type
 * @param <V> the input value type
 */
public interface InputFormat<K, V> {

    /**
     * Logically splits the set of input files for the job.
     * 
     * <p>Each InputSplit is then assigned to an individual Mapper for processing.</p>
     *
     * @param jobContext the job context
     * @return a list of InputSplits for the job
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException;

    /**
     * Creates a record reader for a given split. The framework will call
     * RecordReader.initialize(InputSplit, TaskAttemptContext) after
     * the record reader is created.
     *
     * @param split the split to be read
     * @param taskContext the context of the task
     * @return a new record reader
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    RecordReader<K, V> createRecordReader(InputSplit split, TaskContext taskContext) 
            throws IOException, InterruptedException;

    /**
     * InputSplit represents the data to be processed by an individual Mapper.
     */
    interface InputSplit {
        
        /**
         * Gets the size of the split, so that the input splits can be sorted by size.
         *
         * @return the size of the split in bytes
         */
        long getLength() throws IOException, InterruptedException;

        /**
         * Gets the list of nodes by name where the data for the split would be local.
         * The locations do not need to be specified, but they can be helpful for
         * scheduling.
         *
         * @return the list of nodes where the data is local
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        String[] getLocations() throws IOException, InterruptedException;
    }

    /**
     * RecordReader reads key/value pairs from an InputSplit.
     *
     * @param <K> the key type
     * @param <V> the value type
     */
    interface RecordReader<K, V> {
        
        /**
         * Called once at initialization.
         *
         * @param split the split that defines the range of records to read
         * @param context the context of the task
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        void initialize(InputSplit split, TaskContext context) throws IOException, InterruptedException;

        /**
         * Read the next key, value pair in the input for the task.
         *
         * @return true if a key/value pair was read
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        boolean nextKeyValue() throws IOException, InterruptedException;

        /**
         * Get the current key.
         *
         * @return the current key or null if no key has been read
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        K getCurrentKey() throws IOException, InterruptedException;

        /**
         * Get the current value.
         *
         * @return the current value or null if no value has been read
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        V getCurrentValue() throws IOException, InterruptedException;

        /**
         * The current progress of the record reader through its data.
         *
         * @return a number between 0.0 and 1.0 that is the fraction of the data read
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        float getProgress() throws IOException, InterruptedException;

        /**
         * Close the record reader.
         *
         * @throws IOException if an I/O error occurs
         */
        void close() throws IOException;
    }
}
