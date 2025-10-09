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
package com.alibaba.cloud.ai.manus.mapreduce.mapreduceTask;

import com.alibaba.cloud.ai.manus.mapreduce.InputFormat;
import java.io.IOException;
import java.util.List;

/**
 * Interface for managing input format and splits creation
 * 
 * This interface handles Step 1 of the MapReduce process:
 * - Creating InputFormat instances
 * - Generating input splits from the input data
 * 
 * @param <K> The type of input keys
 * @param <V> The type of input values
 */
public interface MManager<K, V> {
    
    /**
     * Create InputFormat and generate input splits
     * 
     * This method handles the complete Step 1 process:
     * - Creates an InputFormat instance for the given job context
     * - Generates input splits from the input data
     * - Returns both the InputFormat and splits for further processing
     * 
     * @param jobContext The job context containing configuration and metadata
     * @return InputSplitResult containing both InputFormat and splits
     * @throws IOException if there's an error creating InputFormat or generating splits
     */
    InputSplitResult<K, V> createInputFormatAndSplits(String filePath,SplitStrategy splitStragety) throws IOException;
    
    


    /**
     * Result containing InputFormat and generated splits
     */
    class InputSplitResult<K, V> {
        private final InputFormat<K, V> inputFormat;
        private final List<InputFormat.InputSplit> splits;
        
        public InputSplitResult(InputFormat<K, V> inputFormat, List<InputFormat.InputSplit> splits) {
            this.inputFormat = inputFormat;
            this.splits = splits;
        }
        
        public InputFormat<K, V> getInputFormat() { return inputFormat; }
        public List<InputFormat.InputSplit> getSplits() { return splits; }
        public int getSplitCount() { return splits.size(); }
    }
    
        /**
         * Split strategy enumeration
         */
        public enum SplitStrategy {
            CONTENT_SIZE_BASED,  // Split based on content size (e.g., 500 characters)
            LINE_BASED          // Split based on line count (e.g., 100 lines)
        }
    
}
