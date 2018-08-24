package stroom.search.shard;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class IndexShardSearchConfig {
    private static final int DEFAULT_MAX_THREADS = 4;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 2;

    private int maxDocIdQueueSize = 1000000;
    private int maxThreads = DEFAULT_MAX_THREADS;
    private int maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;

    @JsonPropertyDescription("The maximum number of doc ids that will be queued ready for stored data to be retrieved from the index shard")
    public int getMaxDocIdQueueSize() {
        return maxDocIdQueueSize;
    }

    public void setMaxDocIdQueueSize(final int maxDocIdQueueSize) {
        this.maxDocIdQueueSize = maxDocIdQueueSize;
    }

    @JsonPropertyDescription("The absolute maximum number of threads per node, used to search Lucene index shards across all searches")
    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(final int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to search Lucene index shards")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    public void setMaxThreadsPerTask(final int maxThreadsPerTask) {
        this.maxThreadsPerTask = maxThreadsPerTask;
    }
}
