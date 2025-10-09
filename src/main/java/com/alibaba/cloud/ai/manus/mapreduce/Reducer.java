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
 * Reduces a set of intermediate values which share a key to a smaller set of values.
 * 
 * <p>The number of values associated with each key is reduced to a single value.
 * The framework calls {@link #reduce(Object, Iterable, Context)} for each 
 * key/value pair in the grouped inputs. Applications can override this method 
 * to process the input as required.</p>
 *
 * @param <KEYIN> the input key type
 * @param <VALUEIN> the input value type
 * @param <KEYOUT> the output key type  
 * @param <VALUEOUT> the output value type
 */
public interface Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {

    /**
     * This method is called once for each key. Most applications will define
     * their reduce class by overriding this method. The default implementation
     * is an identity function.
     *
     * @param key the input key
     * @param values the input values
     * @param context the context for writing output
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    void reduce(KEYIN key, Iterable<VALUEIN> values, Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;

    /**
     * Called once at the start of the task.
     *
     * @param context the context for the task
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    default void setup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException {
        // Default implementation does nothing
    }

    /**
     * Called once at the end of the task.
     *
     * @param context the context for the task
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    default void cleanup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException {
        // Default implementation does nothing
    }

    /**
     * Context interface for writing output from the reducer.
     *
     * @param <KEYOUT> the output key type
     * @param <VALUEOUT> the output value type
     */
    interface Context<KEYOUT, VALUEOUT> {
        
        /**
         * Writes a key/value pair to the output.
         *
         * @param key the output key
         * @param value the output value
         * @throws IOException if an I/O error occurs
         * @throws InterruptedException if the thread is interrupted
         */
        void write(KEYOUT key, VALUEOUT value) throws IOException, InterruptedException;

        /**
         * Gets the current counter value for the given counter group and name.
         *
         * @param group the counter group
         * @param name the counter name
         * @return the current counter value
         */
        long getCounter(String group, String name);

        /**
         * Increments the counter for the given group and name by the specified value.
         *
         * @param group the counter group
         * @param name the counter name
         * @param value the value to increment by
         */
        void incrementCounter(String group, String name, long value);

        /**
         * Gets the current input key.
         *
         * @return the current input key
         */
        Object getCurrentKey();

        /**
         * Gets the current input value.
         *
         * @return the current input value
         */
        Object getCurrentValue();
    }
}
