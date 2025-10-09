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

import com.alibaba.cloud.ai.manus.mapreduce.Mapper;
import java.io.IOException;

/**
 * Example Mapper implementation for word count.
 * 
 * <p>This mapper takes text input and outputs each word as a key with a count of 1.
 * It demonstrates the basic pattern of a MapReduce mapper.</p>
 */
public class WordCountMapper implements Mapper<Object, String, String, Integer> {

    @Override
    public void map(Object key, String value, Context<String, Integer> context) 
            throws IOException, InterruptedException {
        
        // Split the input text into words
        String[] words = value.toLowerCase().split("\\s+");
        
        // Output each word with a count of 1
        for (String word : words) {
            if (!word.isEmpty()) {
                context.write(word, 1);
            }
        }
    }

    @Override
    public void setup(Context<String, Integer> context) 
            throws IOException, InterruptedException {
        // Initialize any resources needed for the mapper
        System.out.println("WordCountMapper setup completed");
    }

    @Override
    public void cleanup(Context<String, Integer> context) 
            throws IOException, InterruptedException {
        // Clean up any resources used by the mapper
        System.out.println("WordCountMapper cleanup completed");
    }
}
