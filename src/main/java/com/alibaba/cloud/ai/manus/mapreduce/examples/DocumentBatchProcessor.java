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
import com.alibaba.cloud.ai.manus.mapreduce.Reducer;
import com.alibaba.cloud.ai.manus.mapreduce.InputFormat;
import com.alibaba.cloud.ai.manus.mapreduce.OutputFormat;
import com.alibaba.cloud.ai.manus.mapreduce.Job;
import com.alibaba.cloud.ai.manus.mapreduce.JobConfiguration;
import com.alibaba.cloud.ai.manus.mapreduce.JobContext;
import com.alibaba.cloud.ai.manus.mapreduce.TaskContext;
import java.io.IOException;
import java.util.*;

/**
 * Document Batch Processor for processing large markdown files.
 * 
 * <p>This processor:
 * 1. Splits a large .md file into 500-character batches
 * 2. Uses LLM to extract key-value pairs from each batch
 * 3. In reduce phase, sorts values by original batch order
 * 4. Uses LLM to summarize all values for each key
 * </p>
 */
public class DocumentBatchProcessor {

    /**
     * Input format for document batches.
     * Key: batch index (Integer), Value: batch content (String)
     */
    public static class DocumentBatchInputFormat implements InputFormat<Integer, String> {
        
        private static final int BATCH_SIZE = 500;
        
        @Override
        public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
            List<InputSplit> splits = new ArrayList<>();
            String inputPath = jobContext.getConfiguration().get("document.input.path", "");
            
            if (inputPath.isEmpty()) {
                throw new IOException("Document input path not specified");
            }
            
            // Create a single split for the entire document
            // The actual batching will be done in the RecordReader
            splits.add(new DocumentInputSplit(inputPath));
            return splits;
        }
        
        @Override
        public RecordReader<Integer, String> createRecordReader(InputSplit split, TaskContext taskContext) 
                throws IOException, InterruptedException {
            return new DocumentBatchRecordReader((DocumentInputSplit) split);
        }
        
        private static class DocumentInputSplit implements InputSplit {
            private final String filePath;
            
            public DocumentInputSplit(String filePath) {
                this.filePath = filePath;
            }
            
            public String getFilePath() {
                return filePath;
            }
            
            @Override
            public long getLength() throws IOException, InterruptedException {
                return 1; // Single document
            }
            
            @Override
            public String[] getLocations() throws IOException, InterruptedException {
                return new String[]{"localhost"};
            }
        }
        
        private static class DocumentBatchRecordReader implements RecordReader<Integer, String> {
            private final String filePath;
            private final int batchSize = BATCH_SIZE;
            private String documentContent;
            private int currentBatchIndex = 0;
            private List<String> batches;
            private boolean initialized = false;
            
            public DocumentBatchRecordReader(DocumentInputSplit split) {
                this.filePath = split.getFilePath();
            }
            
            @Override
            public void initialize(InputSplit split, TaskContext context) throws IOException, InterruptedException {
                if (!initialized) {
                    // Read the entire document
                    documentContent = readDocument(filePath);
                    // Split into batches
                    batches = splitIntoBatches(documentContent, batchSize);
                    initialized = true;
                }
            }
            
            @Override
            public boolean nextKeyValue() throws IOException, InterruptedException {
                if (batches == null || currentBatchIndex >= batches.size()) {
                    return false;
                }
                currentBatchIndex++;
                return currentBatchIndex <= batches.size();
            }
            
            @Override
            public Integer getCurrentKey() throws IOException, InterruptedException {
                return currentBatchIndex - 1; // 0-based index
            }
            
            @Override
            public String getCurrentValue() throws IOException, InterruptedException {
                return batches.get(currentBatchIndex - 1);
            }
            
            @Override
            public float getProgress() throws IOException, InterruptedException {
                if (batches == null) return 0.0f;
                return (float) currentBatchIndex / batches.size();
            }
            
            @Override
            public void close() throws IOException {
                // Cleanup if needed
            }
            
            private String readDocument(String filePath) throws IOException {
                // In real implementation, read from file system
                // For demo purposes, return sample content
                return "This is a sample markdown document content that will be split into batches. " +
                       "Each batch should contain approximately 500 characters. " +
                       "The content will be processed by LLM to extract key-value pairs. " +
                       "The key-value pairs will then be sorted and summarized. " +
                       "This is a long document that needs to be processed in chunks. " +
                       "The MapReduce framework will handle the parallel processing. " +
                       "Each batch will be processed independently by different mappers. " +
                       "The results will be collected and sorted in the reduce phase. " +
                       "Finally, all values for each key will be summarized using LLM.";
            }
            
            private List<String> splitIntoBatches(String content, int batchSize) {
                List<String> batches = new ArrayList<>();
                int start = 0;
                
                while (start < content.length()) {
                    int end = Math.min(start + batchSize, content.length());
                    
                    // Try to break at word boundary
                    if (end < content.length()) {
                        int lastSpace = content.lastIndexOf(' ', end);
                        if (lastSpace > start) {
                            end = lastSpace;
                        }
                    }
                    
                    String batch = content.substring(start, end).trim();
                    if (!batch.isEmpty()) {
                        batches.add(batch);
                    }
                    
                    start = end;
                }
                
                return batches;
            }
        }
    }
    
    /**
     * Mapper that processes document batches and extracts key-value pairs using LLM.
     */
    public static class DocumentBatchMapper implements Mapper<Integer, String, String, String> {
        
        @Override
        public void map(Integer batchIndex, String batchContent, Context<String, String> context) 
                throws IOException, InterruptedException {
            
            // Increment counter for processed batches
            context.incrementCounter("DOCUMENT", "BATCHES_PROCESSED", 1);
            
            // Simulate LLM processing to extract key-value pairs
            List<KeyValuePair> kvPairs = extractKeyValuePairsWithLLM(batchContent, batchIndex);
            
            // Output each key-value pair with batch index encoded in value
            for (KeyValuePair kv : kvPairs) {
                String encodedValue = batchIndex + ":" + kv.value;
                context.write(kv.key, encodedValue);
                context.incrementCounter("DOCUMENT", "KV_PAIRS_EXTRACTED", 1);
            }
        }
        
        private List<KeyValuePair> extractKeyValuePairsWithLLM(String content, int batchIndex) {
            // Simulate LLM processing
            // In real implementation, this would call an actual LLM service
            List<KeyValuePair> pairs = new ArrayList<>();
            
            // Simulate extracting different types of information
            if (content.contains("document")) {
                pairs.add(new KeyValuePair("document_type", "markdown", batchIndex));
            }
            if (content.contains("MapReduce")) {
                pairs.add(new KeyValuePair("technology", "MapReduce", batchIndex));
            }
            if (content.contains("LLM")) {
                pairs.add(new KeyValuePair("technology", "LLM", batchIndex));
            }
            if (content.contains("batch")) {
                pairs.add(new KeyValuePair("processing_method", "batch_processing", batchIndex));
            }
            if (content.contains("parallel")) {
                pairs.add(new KeyValuePair("processing_method", "parallel_processing", batchIndex));
            }
            
            // Add batch-specific information
            pairs.add(new KeyValuePair("batch_info", "batch_" + batchIndex, batchIndex));
            
            return pairs;
        }
    }
    
    /**
     * Reducer that sorts values by batch order and summarizes using LLM.
     */
    public static class DocumentBatchReducer implements Reducer<String, String, String, String> {
        
        @Override
        public void reduce(String key, Iterable<String> values, Context<String, String> context) 
                throws IOException, InterruptedException {
            
            // Collect all values and sort by batch index
            List<EncodedValue> encodedValues = new ArrayList<>();
            for (String encodedValue : values) {
                String[] parts = encodedValue.split(":", 2);
                if (parts.length == 2) {
                    int batchIndex = Integer.parseInt(parts[0]);
                    String value = parts[1];
                    encodedValues.add(new EncodedValue(value, batchIndex));
                }
            }
            
            // Sort by batch index to maintain original order
            encodedValues.sort(Comparator.comparingInt(EncodedValue::getBatchIndex));
            
            // Increment counter for processed keys
            context.incrementCounter("DOCUMENT", "KEYS_PROCESSED", 1);
            context.incrementCounter("DOCUMENT", "TOTAL_VALUES", encodedValues.size());
            
            // Extract values in order
            List<String> orderedValues = new ArrayList<>();
            for (EncodedValue encodedValue : encodedValues) {
                orderedValues.add(encodedValue.getValue());
            }
            
            // Use LLM to summarize the ordered values
            String summary = summarizeWithLLM(key, orderedValues);
            
            // Output the summarized result
            context.write(key, summary);
        }
        
        private String summarizeWithLLM(String key, List<String> values) {
            // Simulate LLM summarization
            // In real implementation, this would call an actual LLM service
            
            StringBuilder summary = new StringBuilder();
            summary.append("Key: ").append(key).append("\n");
            summary.append("Values: ").append(String.join(", ", values)).append("\n");
            summary.append("Count: ").append(values.size()).append("\n");
            summary.append("Summary: This key appears ").append(values.size())
                   .append(" times across different batches with values: ")
                   .append(String.join(", ", values));
            
            return summary.toString();
        }
    }
    
    /**
     * Output format for document processing results.
     */
    public static class DocumentOutputFormat implements OutputFormat<String, String> {
        
        @Override
        public RecordWriter<String, String> getRecordWriter(TaskContext context) 
                throws IOException, InterruptedException {
            return new DocumentRecordWriter(context);
        }
        
        @Override
        public void checkOutputSpecs(JobContext context) throws IOException, InterruptedException {
            String outputPath = context.getConfiguration().get("document.output.path", "");
            if (outputPath.isEmpty()) {
                throw new IOException("Document output path not specified");
            }
        }
        
        @Override
        public OutputCommitter getOutputCommitter(TaskContext context) 
                throws IOException, InterruptedException {
            return new DocumentOutputCommitter();
        }
        
        private static class DocumentRecordWriter implements RecordWriter<String, String> {
            private final String outputPath;
            
            public DocumentRecordWriter(TaskContext context) throws IOException {
                this.outputPath = context.getConfiguration().get("document.output.path", "");
            }
            
            @Override
            public void write(String key, String value) throws IOException, InterruptedException {
                // In real implementation, write to file system
                System.out.println("=== Document Processing Result ===");
                System.out.println("Key: " + key);
                System.out.println("Summary: " + value);
                System.out.println("---");
            }
            
            @Override
            public void close(TaskContext context) throws IOException, InterruptedException {
                System.out.println("Document processing completed. Results written to: " + outputPath);
            }
        }
        
        private static class DocumentOutputCommitter implements OutputCommitter {
            
            @Override
            public void setupJob(JobContext context) throws IOException {
                System.out.println("Document processing job setup completed");
            }
            
            @Override
            public void commitJob(JobContext context) throws IOException {
                System.out.println("Document processing job committed successfully");
            }
            
            @Override
            public void abortJob(JobContext context, Job.JobStatus status) throws IOException {
                System.out.println("Document processing job aborted with status: " + status);
            }
            
            @Override
            public void setupTask(TaskContext context) throws IOException {
                System.out.println("Document processing task setup completed");
            }
            
            @Override
            public void commitTask(TaskContext context) throws IOException {
                System.out.println("Document processing task committed successfully");
            }
            
            @Override
            public void abortTask(TaskContext context) throws IOException {
                System.out.println("Document processing task aborted");
            }
            
            @Override
            public boolean needsTaskCommit(TaskContext context) throws IOException {
                return true;
            }
        }
    }
    
    /**
     * Document value class that includes batch index for ordering.
     */
    public static class DocumentValue {
        private final String value;
        private final int batchIndex;
        
        public DocumentValue(String value, int batchIndex) {
            this.value = value;
            this.batchIndex = batchIndex;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getBatchIndex() {
            return batchIndex;
        }
        
        @Override
        public String toString() {
            return "DocumentValue{value='" + value + "', batchIndex=" + batchIndex + "}";
        }
    }
    
    /**
     * Key-value pair class for LLM extraction results.
     */
    public static class KeyValuePair {
        private final String key;
        private final String value;
        private final int batchIndex;
        
        public KeyValuePair(String key, String value, int batchIndex) {
            this.key = key;
            this.value = value;
            this.batchIndex = batchIndex;
        }
        
        public String getKey() {
            return key;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getBatchIndex() {
            return batchIndex;
        }
    }
    
    /**
     * Encoded value class that includes batch index for ordering.
     */
    public static class EncodedValue {
        private final String value;
        private final int batchIndex;
        
        public EncodedValue(String value, int batchIndex) {
            this.value = value;
            this.batchIndex = batchIndex;
        }
        
        public String getValue() {
            return value;
        }
        
        public int getBatchIndex() {
            return batchIndex;
        }
    }
    
    /**
     * Job implementation for document batch processing.
     */
    public static class DocumentBatchJob implements Job<Integer, String, String, String> {
        
        private final JobConfiguration configuration;
        private Job.JobStatus status = Job.JobStatus.PREPARING;
        private String jobName = "DocumentBatchJob";
        private String jobId = "doc_job_" + System.currentTimeMillis();
        
        public DocumentBatchJob() {
            this.configuration = new SimpleJobConfiguration();
        }
        
        @Override
        public Job<Integer, String, String, String> setMapper(Class<? extends Mapper<Integer, String, String, String>> mapperClass) {
            configuration.set("mapreduce.mapper.class", mapperClass.getName());
            return this;
        }
        
        @Override
        public Job<Integer, String, String, String> setReducer(Class<? extends Reducer<String, String, String, String>> reducerClass) {
            configuration.set("mapreduce.reducer.class", reducerClass.getName());
            return this;
        }
        
        @Override
        public Job<Integer, String, String, String> setInputFormat(Class<? extends InputFormat<Integer, String>> inputFormatClass) {
            configuration.set("mapreduce.input.format.class", inputFormatClass.getName());
            return this;
        }
        
        @Override
        public Job<Integer, String, String, String> setOutputFormat(Class<? extends OutputFormat<String, String>> outputFormatClass) {
            configuration.set("mapreduce.output.format.class", outputFormatClass.getName());
            return this;
        }
        
        @Override
        public Job<Integer, String, String, String> setNumReduceTasks(int numReduceTasks) {
            configuration.setInt("mapreduce.reduce.tasks", numReduceTasks);
            return this;
        }
        
        @Override
        public Job<Integer, String, String, String> setProperty(String name, String value) {
            configuration.set(name, value);
            return this;
        }
        
        @Override
        public String getProperty(String name) {
            return configuration.get(name);
        }
        
        @Override
        public String getProperty(String name, String defaultValue) {
            return configuration.get(name, defaultValue);
        }
        
        @Override
        public Job<Integer, String, String, String> setJobName(String jobName) {
            this.jobName = jobName;
            return this;
        }
        
        @Override
        public String getJobName() {
            return jobName;
        }
        
        @Override
        public boolean submit() throws IOException, InterruptedException {
            try {
                status = Job.JobStatus.RUNNING;
                System.out.println("Document batch processing job submitted: " + jobName + " (ID: " + jobId + ")");
                return true;
            } catch (Exception e) {
                status = Job.JobStatus.FAILED;
                throw new IOException("Failed to submit document processing job", e);
            }
        }
        
        @Override
        public boolean waitForCompletion() throws IOException, InterruptedException {
            try {
                System.out.println("Starting document batch processing: " + jobName);
                status = Job.JobStatus.RUNNING;
                
                // Step 1: Create InputFormat and get splits
                System.out.println("Step 1: Creating input splits...");
                DocumentBatchInputFormat inputFormat = new DocumentBatchInputFormat();
                List<InputFormat.InputSplit> splits = inputFormat.getSplits(createJobContext());
                System.out.println("Created " + splits.size() + " input splits");
                
                // Step 2: Execute Mapper phase
                System.out.println("Step 2: Executing Mapper phase...");
                Map<String, List<String>> mapperOutput = new HashMap<>();
                DocumentBatchMapper mapper = new DocumentBatchMapper();
                
                for (int i = 0; i < splits.size(); i++) {
                    InputFormat.InputSplit split = splits.get(i);
                    System.out.println("Processing split " + i + ": " + split);
                    
                    // Create RecordReader for this split
                    InputFormat.RecordReader<Integer, String> recordReader = 
                        inputFormat.createRecordReader(split, createTaskContext(i, "MAP"));
                    
                    // Initialize record reader
                    recordReader.initialize(split, createTaskContext(i, "MAP"));
                    
                    // Process all records in this split
                    while (recordReader.nextKeyValue()) {
                        Integer key = recordReader.getCurrentKey();
                        String value = recordReader.getCurrentValue();
                        
                        System.out.println("  Mapper input: (" + key + ", \"" + 
                            (value.length() > 50 ? value.substring(0, 50) + "..." : value) + "\")");
                        
                        // Create a mock context to collect mapper output
                        MockMapperContext mapperContext = new MockMapperContext();
                        mapper.map(key, value, mapperContext);
                        
                        // Collect mapper output
                        for (Map.Entry<String, String> entry : mapperContext.getOutput().entrySet()) {
                            mapperOutput.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                      .add(entry.getValue());
                        }
                    }
                    
                    recordReader.close();
                }
                
                System.out.println("Mapper phase completed. Generated " + mapperOutput.size() + " unique keys");
                
                // Step 3: Shuffle phase (group by key)
                System.out.println("Step 3: Shuffling data by key...");
                for (Map.Entry<String, List<String>> entry : mapperOutput.entrySet()) {
                    System.out.println("  Key: " + entry.getKey() + " -> " + entry.getValue().size() + " values");
                }
                
                // Step 4: Execute Reducer phase
                System.out.println("Step 4: Executing Reducer phase...");
                DocumentBatchReducer reducer = new DocumentBatchReducer();
                Map<String, String> finalOutput = new HashMap<>();
                
                for (Map.Entry<String, List<String>> entry : mapperOutput.entrySet()) {
                    String key = entry.getKey();
                    List<String> values = entry.getValue();
                    
                    System.out.println("  Reducing key: " + key + " with " + values.size() + " values");
                    
                    // Create a mock context to collect reducer output
                    MockReducerContext reducerContext = new MockReducerContext();
                    reducer.reduce(key, values, reducerContext);
                    
                    // Collect reducer output
                    finalOutput.putAll(reducerContext.getOutput());
                }
                
                // Step 5: Write output using OutputFormat
                System.out.println("Step 5: Writing final output...");
                DocumentOutputFormat outputFormat = new DocumentOutputFormat();
                TaskContext outputContext = createTaskContext(0, "OUTPUT");
                OutputFormat.RecordWriter<String, String> recordWriter = 
                    outputFormat.getRecordWriter(outputContext);
                
                for (Map.Entry<String, String> entry : finalOutput.entrySet()) {
                    recordWriter.write(entry.getKey(), entry.getValue());
                }
                recordWriter.close(outputContext);
                
                status = Job.JobStatus.COMPLETED;
                System.out.println("Document batch processing completed successfully: " + jobName);
                System.out.println("Final output contains " + finalOutput.size() + " results");
                return true;
                
            } catch (Exception e) {
                status = Job.JobStatus.FAILED;
                System.err.println("Document batch processing failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }
        
        /**
         * Create a mock JobContext for the job.
         */
        private JobContext createJobContext() {
            return new JobContext() {
                @Override
                public JobConfiguration getConfiguration() {
                    return configuration;
                }
                
                @Override
                public String getJobName() {
                    return jobName;
                }
                
                @Override
                public String getJobId() {
                    return jobId;
                }
                
                @Override
                public Job.JobStatus getJobStatus() {
                    return status;
                }
                
                @Override
                public void setJobStatus(Job.JobStatus status) {
                    DocumentBatchJob.this.status = status;
                }
                
                @Override
                public long getCounter(String group, String name) {
                    return 0; // Mock implementation
                }
                
                @Override
                public void incrementCounter(String group, String name, long value) {
                    // Mock implementation
                }
            };
        }
        
        /**
         * Create a mock TaskContext for a task.
         */
        private TaskContext createTaskContext(int taskId, String taskType) {
            return new TaskContext() {
                @Override
                public JobConfiguration getConfiguration() {
                    return configuration;
                }
                
                @Override
                public String getJobName() {
                    return jobName;
                }
                
                @Override
                public String getJobId() {
                    return jobId;
                }
                
                @Override
                public Job.JobStatus getJobStatus() {
                    return status;
                }
                
                @Override
                public void setJobStatus(Job.JobStatus status) {
                    DocumentBatchJob.this.status = status;
                }
                
                @Override
                public long getCounter(String group, String name) {
                    return 0; // Mock implementation
                }
                
                @Override
                public void incrementCounter(String group, String name, long value) {
                    // Mock implementation
                }
                
                @Override
                public String getTaskId() {
                    return taskType + "_" + taskId;
                }
                
                @Override
                public TaskType getTaskType() {
                    return "MAP".equals(taskType) ? TaskType.MAP : TaskType.REDUCE;
                }
                
                @Override
                public TaskStatus getTaskStatus() {
                    return TaskStatus.RUNNING;
                }
                
                @Override
                public void setTaskStatus(TaskStatus status) {
                    // Mock implementation
                }
                
                @Override
                public InputFormat.InputSplit getInputSplit() {
                    return null; // Mock implementation
                }
                
                @Override
                public void setInputSplit(InputFormat.InputSplit inputSplit) {
                    // Mock implementation
                }
            };
        }
        
        /**
         * Mock MapperContext to collect mapper output.
         */
        private static class MockMapperContext implements Mapper.Context<String, String> {
            private final Map<String, String> output = new HashMap<>();
            
            @Override
            public void write(String key, String value) throws IOException, InterruptedException {
                output.put(key, value);
            }
            
            @Override
            public long getCounter(String group, String name) {
                return 0;
            }
            
            @Override
            public void incrementCounter(String group, String name, long value) {
                // Mock implementation
            }
            
            @Override
            public Object getCurrentKey() {
                return null;
            }
            
            @Override
            public Object getCurrentValue() {
                return null;
            }
            
            public Map<String, String> getOutput() {
                return output;
            }
        }
        
        /**
         * Mock ReducerContext to collect reducer output.
         */
        private static class MockReducerContext implements Reducer.Context<String, String> {
            private final Map<String, String> output = new HashMap<>();
            
            @Override
            public void write(String key, String value) throws IOException, InterruptedException {
                output.put(key, value);
            }
            
            @Override
            public long getCounter(String group, String name) {
                return 0;
            }
            
            @Override
            public void incrementCounter(String group, String name, long value) {
                // Mock implementation
            }
            
            @Override
            public Object getCurrentKey() {
                return null;
            }
            
            @Override
            public Object getCurrentValue() {
                return null;
            }
            
            public Map<String, String> getOutput() {
                return output;
            }
        }
        
        @Override
        public Job.JobStatus getStatus() {
            return status;
        }
        
        @Override
        public JobConfiguration getConfiguration() {
            return configuration;
        }
        
        /**
         * Simple implementation of JobConfiguration.
         */
        private static class SimpleJobConfiguration implements JobConfiguration {
            private final Map<String, String> properties = new HashMap<>();
            
            @Override
            public void set(String name, String value) {
                properties.put(name, value);
            }
            
            @Override
            public String get(String name) {
                return properties.get(name);
            }
            
            @Override
            public String get(String name, String defaultValue) {
                return properties.getOrDefault(name, defaultValue);
            }
            
            @Override
            public int getInt(String name, int defaultValue) {
                String value = get(name);
                if (value == null) {
                    return defaultValue;
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            
            @Override
            public long getLong(String name, long defaultValue) {
                String value = get(name);
                if (value == null) {
                    return defaultValue;
                }
                try {
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            
            @Override
            public boolean getBoolean(String name, boolean defaultValue) {
                String value = get(name);
                if (value == null) {
                    return defaultValue;
                }
                return Boolean.parseBoolean(value);
            }
            
            @Override
            public void setInt(String name, int value) {
                set(name, String.valueOf(value));
            }
            
            @Override
            public void setLong(String name, long value) {
                set(name, String.valueOf(value));
            }
            
            @Override
            public void setBoolean(String name, boolean value) {
                set(name, String.valueOf(value));
            }
            
            @Override
            public Map<String, String> getAll() {
                return new HashMap<>(properties);
            }
            
            @Override
            public void setAll(Map<String, String> properties) {
                this.properties.putAll(properties);
            }
            
            @Override
            public void setAll(java.util.Properties properties) {
                properties.forEach((key, value) -> set(key.toString(), value.toString()));
            }
            
            @Override
            public String remove(String name) {
                return properties.remove(name);
            }
            
            @Override
            public boolean contains(String name) {
                return properties.containsKey(name);
            }
            
            @Override
            public void clear() {
                properties.clear();
            }
            
            @Override
            public int size() {
                return properties.size();
            }
            
            @Override
            public boolean isEmpty() {
                return properties.isEmpty();
            }
        }
    }
}
