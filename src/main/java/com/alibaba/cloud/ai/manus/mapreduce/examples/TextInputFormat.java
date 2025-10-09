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

import com.alibaba.cloud.ai.manus.mapreduce.InputFormat;
import com.alibaba.cloud.ai.manus.mapreduce.JobContext;
import com.alibaba.cloud.ai.manus.mapreduce.TaskContext;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Example InputFormat implementation for text files.
 * 
 * <p>This InputFormat reads text files line by line, where each line
 * becomes a value and the line number becomes the key.</p>
 */
public class TextInputFormat implements InputFormat<Object, String> {

    @Override
    public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
        List<InputSplit> splits = new ArrayList<>();
        
        // Get input path from job configuration
        String inputPath = jobContext.getConfiguration().get("mapreduce.input.path", "");
        if (inputPath.isEmpty()) {
            throw new IOException("Input path not specified");
        }
        
        Path path = Paths.get(inputPath);
        if (Files.isDirectory(path)) {
            // If it's a directory, create splits for each file
            Files.list(path)
                .filter(Files::isRegularFile)
                .forEach(file -> splits.add(new FileInputSplit(file.toString())));
        } else if (Files.isRegularFile(path)) {
            // If it's a single file, create one split
            splits.add(new FileInputSplit(inputPath));
        } else {
            throw new IOException("Input path does not exist: " + inputPath);
        }
        
        return splits;
    }

    @Override
    public RecordReader<Object, String> createRecordReader(InputSplit split, TaskContext taskContext) 
            throws IOException, InterruptedException {
        return new TextRecordReader();
    }

    /**
     * InputSplit implementation for file-based input.
     */
    private static class FileInputSplit implements InputSplit {
        private final String filePath;
        private final long fileSize;

        public FileInputSplit(String filePath) {
            this.filePath = filePath;
            long size = 0;
            try {
                size = Files.size(Paths.get(filePath));
            } catch (IOException e) {
                size = 0;
            }
            this.fileSize = size;
        }

        @Override
        public long getLength() throws IOException, InterruptedException {
            return fileSize;
        }

        @Override
        public String[] getLocations() throws IOException, InterruptedException {
            return new String[]{"localhost"};
        }

        public String getFilePath() {
            return filePath;
        }
    }

    /**
     * RecordReader implementation for text files.
     */
    private static class TextRecordReader implements RecordReader<Object, String> {
        private BufferedReader reader;
        private long currentKey;
        private String currentValue;
        private boolean hasNext;
        private long fileSize;
        private long bytesRead;

        @Override
        public void initialize(InputSplit split, TaskContext context) throws IOException, InterruptedException {
            FileInputSplit fileSplit = (FileInputSplit) split;
            this.reader = new BufferedReader(new FileReader(fileSplit.getFilePath()));
            this.currentKey = 0;
            this.fileSize = fileSplit.getLength();
            this.bytesRead = 0;
            this.hasNext = true;
        }

        @Override
        public boolean nextKeyValue() throws IOException, InterruptedException {
            if (!hasNext) {
                return false;
            }

            String line = reader.readLine();
            if (line == null) {
                hasNext = false;
                return false;
            }

            currentKey++;
            currentValue = line;
            bytesRead += line.length() + 1; // +1 for newline character
            return true;
        }

        @Override
        public Object getCurrentKey() throws IOException, InterruptedException {
            return currentKey;
        }

        @Override
        public String getCurrentValue() throws IOException, InterruptedException {
            return currentValue;
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            if (fileSize == 0) {
                return 1.0f;
            }
            return Math.min(1.0f, (float) bytesRead / fileSize);
        }

        @Override
        public void close() throws IOException {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
