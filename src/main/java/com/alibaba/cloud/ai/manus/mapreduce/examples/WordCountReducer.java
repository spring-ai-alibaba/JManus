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
package com.alibaba.cloud.ai.manus.mapreduce.examples;

import com.alibaba.cloud.ai.manus.mapreduce.Reducer;
import java.io.IOException;

/**
 * Example Reducer implementation for word count.
 * 
 * <p>This reducer takes words and their counts, then sums up the counts
 * for each word to produce the final word count result.</p>
 */
public class WordCountReducer implements Reducer<String, Integer, String, Integer> {

    @Override
    public void reduce(String key, Iterable<Integer> values, Context<String, Integer> context) 
            throws IOException, InterruptedException {
        
        int sum = 0;
        
        // Sum up all the counts for this word
        for (Integer value : values) {
            sum += value;
        }
        
        // Output the word and its total count
        context.write(key, sum);
    }

    @Override
    public void setup(Context<String, Integer> context) 
            throws IOException, InterruptedException {
        // Initialize any resources needed for the reducer
        System.out.println("WordCountReducer setup completed");
    }

    @Override
    public void cleanup(Context<String, Integer> context) 
            throws IOException, InterruptedException {
        // Clean up any resources used by the reducer
        System.out.println("WordCountReducer cleanup completed");
    }
}
