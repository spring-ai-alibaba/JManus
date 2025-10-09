# MapReduce Interface Framework

This package provides a comprehensive MapReduce interface framework for Java applications. It defines the core interfaces and abstractions needed to implement MapReduce-style data processing jobs.

## Overview

The MapReduce programming model is designed to process large datasets in parallel across multiple machines. This framework provides the essential interfaces that allow you to:

- **Map** input data into intermediate key-value pairs
- **Reduce** intermediate data to produce final results
- **Configure** and **execute** MapReduce jobs
- **Handle** input/output formats and data serialization

## Core Interfaces

### 1. Mapper Interface

The `Mapper` interface defines how to transform input data into intermediate key-value pairs.

```java
public interface Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
    void map(KEYIN key, VALUEIN value, Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
    
    default void setup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
    
    default void cleanup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
}
```

**Key Methods:**
- `map()`: Processes each input key-value pair
- `setup()`: Called once at the beginning of the task
- `cleanup()`: Called once at the end of the task

### 2. Reducer Interface

The `Reducer` interface defines how to aggregate intermediate key-value pairs into final results.

```java
public interface Reducer<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
    void reduce(KEYIN key, Iterable<VALUEIN> values, Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
    
    default void setup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
    
    default void cleanup(Context<KEYOUT, VALUEOUT> context) 
            throws IOException, InterruptedException;
}
```

**Key Methods:**
- `reduce()`: Processes all values for a given key
- `setup()`: Called once at the beginning of the task
- `cleanup()`: Called once at the end of the task

### 3. Job Interface

The `Job` interface provides methods to configure and execute MapReduce jobs.

```java
public interface Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setMapper(Class<? extends Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT>> mapperClass);
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setReducer(Class<? extends Reducer<KEYOUT, VALUEOUT, KEYOUT, VALUEOUT>> reducerClass);
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setInputFormat(Class<? extends InputFormat<KEYIN, VALUEIN>> inputFormatClass);
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setOutputFormat(Class<? extends OutputFormat<KEYOUT, VALUEOUT>> outputFormatClass);
    Job<KEYIN, VALUEIN, KEYOUT, VALUEOUT> setNumReduceTasks(int numReduceTasks);
    
    boolean submit() throws IOException, InterruptedException;
    boolean waitForCompletion() throws IOException, InterruptedException;
    JobStatus getStatus();
}
```

### 4. InputFormat Interface

The `InputFormat` interface defines how input data is read and split for processing.

```java
public interface InputFormat<K, V> {
    List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException;
    RecordReader<K, V> createRecordReader(InputSplit split, TaskContext taskContext) 
            throws IOException, InterruptedException;
}
```

### 5. OutputFormat Interface

The `OutputFormat` interface defines how output data is written.

```java
public interface OutputFormat<K, V> {
    RecordWriter<K, V> getRecordWriter(TaskContext context) throws IOException, InterruptedException;
    void checkOutputSpecs(JobContext context) throws IOException, InterruptedException;
    OutputCommitter getOutputCommitter(TaskContext context) throws IOException, InterruptedException;
}
```

## Usage Examples

### Basic Word Count Example

Here's a complete example of implementing a word count job:

#### 1. Create a Mapper

```java
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
}
```

#### 2. Create a Reducer

```java
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
}
```

#### 3. Create Input/Output Formats

```java
// TextInputFormat reads text files line by line
public class TextInputFormat implements InputFormat<Long, String> {
    // Implementation details...
}

// TextOutputFormat writes key-value pairs as tab-separated text
public class TextOutputFormat<K, V> implements OutputFormat<K, V> {
    // Implementation details...
}
```

#### 4. Configure and Run the Job

```java
public class WordCountExample {
    public static void main(String[] args) throws Exception {
        // Create a new job
        Job<Object, String, String, Integer> job = new WordCountJob();
        
        // Configure the job
        job.setJobName("WordCount")
           .setMapper(WordCountMapper.class)
           .setReducer(WordCountReducer.class)
           .setInputFormat(TextInputFormat.class)
           .setOutputFormat(TextOutputFormat.class)
           .setNumReduceTasks(1)
           .setProperty("mapreduce.input.path", "/path/to/input")
           .setProperty("mapreduce.output.path", "/path/to/output");
        
        // Submit and run the job
        if (job.submit()) {
            boolean success = job.waitForCompletion();
            if (success) {
                System.out.println("Job completed successfully!");
            } else {
                System.err.println("Job failed!");
            }
        }
    }
}
```

## Advanced Usage

### Custom Input Format

To create a custom input format for your specific data source:

```java
public class CustomInputFormat implements InputFormat<CustomKey, CustomValue> {
    @Override
    public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
        // Define how to split your input data
        List<InputSplit> splits = new ArrayList<>();
        // Add your custom split logic here
        return splits;
    }
    
    @Override
    public RecordReader<CustomKey, CustomValue> createRecordReader(InputSplit split, TaskContext taskContext) 
            throws IOException, InterruptedException {
        return new CustomRecordReader();
    }
}
```

### Custom Output Format

To create a custom output format:

```java
public class CustomOutputFormat<K, V> implements OutputFormat<K, V> {
    @Override
    public RecordWriter<K, V> getRecordWriter(TaskContext context) throws IOException, InterruptedException {
        return new CustomRecordWriter(context);
    }
    
    @Override
    public void checkOutputSpecs(JobContext context) throws IOException, InterruptedException {
        // Validate output specifications
    }
    
    @Override
    public OutputCommitter getOutputCommitter(TaskContext context) throws IOException, InterruptedException {
        return new CustomOutputCommitter();
    }
}
```

### Job Configuration

You can configure various job parameters:

```java
Job<Object, String, String, Integer> job = new WordCountJob();

// Set basic job properties
job.setJobName("MyCustomJob")
   .setNumReduceTasks(4);

// Set custom properties
job.setProperty("custom.property1", "value1")
   .setProperty("custom.property2", "value2")
   .setProperty("mapreduce.input.path", "/input/path")
   .setProperty("mapreduce.output.path", "/output/path");
```

## Best Practices

### 1. Error Handling

Always implement proper error handling in your mappers and reducers:

```java
@Override
public void map(Object key, String value, Context<String, Integer> context) 
        throws IOException, InterruptedException {
    try {
        // Your mapping logic here
        context.write(key, value);
    } catch (Exception e) {
        // Log the error and increment error counter
        context.incrementCounter("ERRORS", "MAPPING_ERRORS", 1);
        throw new IOException("Mapping failed", e);
    }
}
```

### 2. Resource Management

Use the setup and cleanup methods to manage resources:

```java
public class DatabaseMapper implements Mapper<Object, String, String, Integer> {
    private Connection connection;
    
    @Override
    public void setup(Context<String, Integer> context) throws IOException, InterruptedException {
        // Initialize database connection
        connection = DriverManager.getConnection("jdbc:mysql://localhost/db");
    }
    
    @Override
    public void cleanup(Context<String, Integer> context) throws IOException, InterruptedException {
        // Close database connection
        if (connection != null) {
            connection.close();
        }
    }
}
```

### 3. Performance Optimization

- Use appropriate data types for keys and values
- Minimize object creation in tight loops
- Use counters to track job progress and statistics
- Consider partitioning strategies for large datasets

## Integration with Existing Tools

This MapReduce interface framework can be integrated with the existing MapReduce tools in the project:

- **DataSplitTool**: Can be used as an InputFormat for splitting large files
- **MapOutputTool**: Can be used to collect mapper outputs
- **ReduceOperationTool**: Can be used as a Reducer implementation
- **FinalizeTool**: Can be used as an OutputFormat for finalizing results

## Thread Safety

The interfaces are designed to be thread-safe when implemented correctly. However, you should be careful when:

- Sharing resources between map/reduce tasks
- Using static variables in your implementations
- Accessing external systems (databases, file systems, etc.)

## Conclusion

This MapReduce interface framework provides a solid foundation for implementing distributed data processing jobs in Java. By following the patterns and examples provided, you can create robust, scalable applications that can process large datasets efficiently.

For more advanced usage patterns and integration examples, refer to the example implementations in the `examples` package.
