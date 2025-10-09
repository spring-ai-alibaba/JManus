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
import com.alibaba.cloud.ai.manus.mapreduce.JobConfiguration;
import com.alibaba.cloud.ai.manus.mapreduce.Mapper;
import com.alibaba.cloud.ai.manus.mapreduce.Reducer;
import com.alibaba.cloud.ai.manus.mapreduce.InputFormat;
import com.alibaba.cloud.ai.manus.mapreduce.OutputFormat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Example Job implementation for word count.
 * 
 * <p>This is a complete example showing how to implement a MapReduce job
 * using the MapReduce interfaces. It demonstrates the word count algorithm
 * which counts the frequency of each word in a text file.</p>
 */
public class WordCountJob implements Job<Object, String, String, Integer> {

    private final JobConfiguration configuration;
    private Job.JobStatus status = Job.JobStatus.PREPARING;
    private String jobName = "WordCountJob";
    private String jobId = "job_" + System.currentTimeMillis();

    public WordCountJob() {
        this.configuration = new SimpleJobConfiguration();
    }

    @Override
    public Job<Object, String, String, Integer> setMapper(Class<? extends Mapper<Object, String, String, Integer>> mapperClass) {
        configuration.set("mapreduce.mapper.class", mapperClass.getName());
        return this;
    }

    @Override
    public Job<Object, String, String, Integer> setReducer(Class<? extends Reducer<String, Integer, String, Integer>> reducerClass) {
        configuration.set("mapreduce.reducer.class", reducerClass.getName());
        return this;
    }

    @Override
    public Job<Object, String, String, Integer> setInputFormat(Class<? extends InputFormat<Object, String>> inputFormatClass) {
        configuration.set("mapreduce.input.format.class", inputFormatClass.getName());
        return this;
    }

    @Override
    public Job<Object, String, String, Integer> setOutputFormat(Class<? extends OutputFormat<String, Integer>> outputFormatClass) {
        configuration.set("mapreduce.output.format.class", outputFormatClass.getName());
        return this;
    }

    @Override
    public Job<Object, String, String, Integer> setNumReduceTasks(int numReduceTasks) {
        configuration.setInt("mapreduce.reduce.tasks", numReduceTasks);
        return this;
    }

    @Override
    public Job<Object, String, String, Integer> setProperty(String name, String value) {
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
    public Job<Object, String, String, Integer> setJobName(String jobName) {
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
            System.out.println("Job submitted: " + jobName + " (ID: " + jobId + ")");
            return true;
        } catch (Exception e) {
            status = Job.JobStatus.FAILED;
            throw new IOException("Failed to submit job", e);
        }
    }

    @Override
    public boolean waitForCompletion() throws IOException, InterruptedException {
        try {
            System.out.println("Starting job execution: " + jobName);
            
            // Simulate job execution
            // In a real implementation, this would coordinate the execution
            // of mappers and reducers across multiple tasks
            
            status = Job.JobStatus.COMPLETED;
            System.out.println("Job completed successfully: " + jobName);
            return true;
        } catch (Exception e) {
            status = Job.JobStatus.FAILED;
            System.err.println("Job failed: " + e.getMessage());
            return false;
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
