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

import com.alibaba.cloud.ai.manus.mapreduce.JobContext;
import com.alibaba.cloud.ai.manus.mapreduce.OutputFormat;
import com.alibaba.cloud.ai.manus.mapreduce.TaskContext;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example OutputFormat implementation for text files.
 * 
 * <p>This OutputFormat writes key-value pairs to text files in the format
 * "key\tvalue" (tab-separated).</p>
 */
public class TextOutputFormat<K, V> implements OutputFormat<K, V> {

    @Override
    public RecordWriter<K, V> getRecordWriter(TaskContext context) throws IOException, InterruptedException {
        return new TextRecordWriter<>(context);
    }

    @Override
    public void checkOutputSpecs(JobContext context) throws IOException, InterruptedException {
        String outputPath = context.getConfiguration().get("mapreduce.output.path", "");
        if (outputPath.isEmpty()) {
            throw new IOException("Output path not specified");
        }
        
        Path path = Paths.get(outputPath);
        if (Files.exists(path)) {
            throw new IOException("Output path already exists: " + outputPath);
        }
    }

    @Override
    public OutputCommitter getOutputCommitter(TaskContext context) throws IOException, InterruptedException {
        return new TextOutputCommitter();
    }

    /**
     * RecordWriter implementation for text output.
     */
    private static class TextRecordWriter<K, V> implements RecordWriter<K, V> {
        private final BufferedWriter writer;

        public TextRecordWriter(TaskContext context) throws IOException {
            String outputPath = context.getConfiguration().get("mapreduce.output.path", "");
            
            // Create output directory if it doesn't exist
            Path path = Paths.get(outputPath);
            Files.createDirectories(path.getParent());
            
            this.writer = new BufferedWriter(new FileWriter(outputPath, true));
        }

        @Override
        public void write(K key, V value) throws IOException, InterruptedException {
            writer.write(key.toString() + "\t" + value.toString() + "\n");
        }

        @Override
        public void close(TaskContext context) throws IOException, InterruptedException {
            if (writer != null) {
                writer.close();
            }
        }
    }

    /**
     * OutputCommitter implementation for text output.
     */
    private static class TextOutputCommitter implements OutputCommitter {

        @Override
        public void setupJob(JobContext context) throws IOException {
            // Setup job-level resources if needed
            System.out.println("TextOutputCommitter: Job setup completed");
        }

        @Override
        public void commitJob(JobContext context) throws IOException {
            // Commit job-level changes if needed
            System.out.println("TextOutputCommitter: Job committed successfully");
        }

        @Override
        public void abortJob(JobContext context, com.alibaba.cloud.ai.manus.mapreduce.Job.JobStatus status) throws IOException {
            // Clean up job-level resources on failure
            System.out.println("TextOutputCommitter: Job aborted with status: " + status);
        }

        @Override
        public void setupTask(TaskContext context) throws IOException {
            // Setup task-level resources if needed
            System.out.println("TextOutputCommitter: Task setup completed");
        }

        @Override
        public void commitTask(TaskContext context) throws IOException {
            // Commit task-level changes if needed
            System.out.println("TextOutputCommitter: Task committed successfully");
        }

        @Override
        public void abortTask(TaskContext context) throws IOException {
            // Clean up task-level resources on failure
            System.out.println("TextOutputCommitter: Task aborted");
        }

        @Override
        public boolean needsTaskCommit(TaskContext context) throws IOException {
            // Return true if task needs commit, false otherwise
            return true;
        }
    }
}
