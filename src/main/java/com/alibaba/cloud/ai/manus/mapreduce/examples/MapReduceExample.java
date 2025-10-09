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

import com.alibaba.cloud.ai.manus.mapreduce.Job;
import java.io.IOException;

/**
 * Example demonstrating how to use the MapReduce interface framework.
 * 
 * <p>This example shows how to create and configure a word count job
 * using the MapReduce interfaces.</p>
 */
public class MapReduceExample {

    public static void main(String[] args) {
        try {
            // Create a word count job
            runWordCountExample();
        } catch (Exception e) {
            System.err.println("Error running MapReduce example: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demonstrates how to create and run a word count job.
     */
    private static void runWordCountExample() throws IOException, InterruptedException {
        System.out.println("=== MapReduce Word Count Example ===");
        
        // Create a new job
        Job<Object, String, String, Integer> job = new WordCountJob();
        
        // Configure the job
        job.setJobName("WordCountExample")
           .setMapper(WordCountMapper.class)
           .setReducer(WordCountReducer.class)
           .setInputFormat(TextInputFormat.class)
           .setOutputFormat(StringIntegerTextOutputFormat.class)
           .setNumReduceTasks(1)
           .setProperty("mapreduce.input.path", "/path/to/input.txt")
           .setProperty("mapreduce.output.path", "/path/to/output.txt");
        
        System.out.println("Job configured:");
        System.out.println("- Job Name: " + job.getJobName());
        System.out.println("- Mapper: " + job.getProperty("mapreduce.mapper.class"));
        System.out.println("- Reducer: " + job.getProperty("mapreduce.reducer.class"));
        System.out.println("- Input Format: " + job.getProperty("mapreduce.input.format.class"));
        System.out.println("- Output Format: " + job.getProperty("mapreduce.output.format.class"));
        System.out.println("- Reduce Tasks: " + job.getProperty("mapreduce.reduce.tasks"));
        
        // Submit the job
        System.out.println("\nSubmitting job...");
        if (job.submit()) {
            System.out.println("Job submitted successfully!");
            
            // Wait for completion
            System.out.println("Waiting for job completion...");
            boolean success = job.waitForCompletion();
            
            if (success) {
                System.out.println("Job completed successfully!");
                System.out.println("Final status: " + job.getStatus());
            } else {
                System.err.println("Job failed!");
                System.err.println("Final status: " + job.getStatus());
            }
        } else {
            System.err.println("Failed to submit job!");
        }
    }
}
