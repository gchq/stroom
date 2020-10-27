package stroom.search.extraction;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ExtractionConfig extends AbstractConfig {
    /**
     * We don't want to collect more than 10k doc's data into the queue by
     * default. When the queue is full the index shard data tasks will pause
     * until the docs are drained from the queue.
     */
    private static final int DEFAULT_MAX_STORED_DATA_QUEUE_SIZE = 1000;

    private static final int DEFAULT_MAX_THREADS = 4;
    private static final int DEFAULT_MAX_THREADS_PER_TASK = 2;
    private static final int DEFAULT_MAX_STREAM_EVENT_MAP_SIZE = 1000000;

    private int maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
    private int maxThreads = DEFAULT_MAX_THREADS;
    private int maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;
    private int maxStreamEventMapSize = DEFAULT_MAX_STREAM_EVENT_MAP_SIZE;

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    public void setMaxStoredDataQueueSize(final int maxStoredDataQueueSize) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The absolute maximum number of threads per node, used to extract search results from streams using a pipeline")
    public int getMaxThreads() {
        return maxThreads;
    }

    public void setMaxThreads(final int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to extract search results from streams using a pipeline")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    public void setMaxThreadsPerTask(final int maxThreadsPerTask) {
        this.maxThreadsPerTask = maxThreadsPerTask;
    }

    @JsonPropertyDescription("The maximum size of the stream event map used to queue events prior to extraction")
    public int getMaxStreamEventMapSize() {
        return maxStreamEventMapSize;
    }

    public void setMaxStreamEventMapSize(final int maxStreamEventMapSize) {
        this.maxStreamEventMapSize = maxStreamEventMapSize;
    }

    @Override
    public String toString() {
        return "ExtractionConfig{" +
                "maxStoredDataQueueSize=" + maxStoredDataQueueSize +
                ", maxThreads=" + maxThreads +
                ", maxThreadsPerTask=" + maxThreadsPerTask +
                ", maxStreamEventMapSize=" + maxStreamEventMapSize +
                '}';
    }
}
