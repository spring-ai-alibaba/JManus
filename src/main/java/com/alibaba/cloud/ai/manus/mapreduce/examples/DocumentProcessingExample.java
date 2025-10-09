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
 * Example demonstrating document batch processing with MapReduce.
 * 
 * <p>This example shows how to:
 * 1. Split a large .md file into 500-character batches
 * 2. Use LLM to extract key-value pairs from each batch
 * 3. Sort values by original batch order in reduce phase
 * 4. Use LLM to summarize all values for each key
 * </p>
 */
public class DocumentProcessingExample {

    public static void main(String[] args) {
        try {
            runDocumentProcessingExample();
        } catch (Exception e) {
            System.err.println("Error running document processing example: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Demonstrates document batch processing workflow.
     */
    private static void runDocumentProcessingExample() throws IOException, InterruptedException {
        System.out.println("=== Document Batch Processing Example ===");
        System.out.println("Processing workflow:");
        System.out.println("1. Split large .md file into 500-character batches");
        System.out.println("2. Extract key-value pairs using LLM from each batch");
        System.out.println("3. Sort values by original batch order in reduce phase");
        System.out.println("4. Summarize all values for each key using LLM");
        System.out.println();
        
        // Create a document batch processing job
        Job<Integer, String, String, String> job = new DocumentBatchProcessor.DocumentBatchJob();
        
        // Configure the job
        job.setJobName("DocumentBatchProcessing")
           .setMapper(DocumentBatchProcessor.DocumentBatchMapper.class)
           .setReducer(DocumentBatchProcessor.DocumentBatchReducer.class)
           .setInputFormat(DocumentBatchProcessor.DocumentBatchInputFormat.class)
           .setOutputFormat(DocumentBatchProcessor.DocumentOutputFormat.class)
           .setNumReduceTasks(1)
           .setProperty("document.input.path", "/path/to/input.md")
           .setProperty("document.output.path", "/path/to/output/");
        
        System.out.println("Job configured:");
        System.out.println("- Job Name: " + job.getJobName());
        System.out.println("- Mapper: " + job.getProperty("mapreduce.mapper.class"));
        System.out.println("- Reducer: " + job.getProperty("mapreduce.reducer.class"));
        System.out.println("- Input Format: " + job.getProperty("mapreduce.input.format.class"));
        System.out.println("- Output Format: " + job.getProperty("mapreduce.output.format.class"));
        System.out.println("- Reduce Tasks: " + job.getProperty("mapreduce.reduce.tasks"));
        System.out.println("- Input Path: " + job.getProperty("document.input.path"));
        System.out.println("- Output Path: " + job.getProperty("document.output.path"));
        
        // Submit the job
        System.out.println("\nSubmitting document processing job...");
        if (job.submit()) {
            System.out.println("Document processing job submitted successfully!");
            
            // Wait for completion
            System.out.println("Waiting for job completion...");
            boolean success = job.waitForCompletion();
            
            if (success) {
                System.out.println("Document processing job completed successfully!");
                System.out.println("Final status: " + job.getStatus());
                System.out.println("\nExpected output format:");
                System.out.println("Key: technology");
                System.out.println("Values: MapReduce, LLM");
                System.out.println("Count: 2");
                System.out.println("Summary: This key appears 2 times across different batches with values: MapReduce, LLM");
            } else {
                System.err.println("Document processing job failed!");
                System.err.println("Final status: " + job.getStatus());
            }
        } else {
            System.err.println("Failed to submit document processing job!");
        }
    }
}
