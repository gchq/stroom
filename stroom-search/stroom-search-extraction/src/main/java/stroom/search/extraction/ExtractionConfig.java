package stroom.search.extraction;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@NotInjectableConfig
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

    private final int maxStoredDataQueueSize;
    // TODO 01/12/2021 AT: Make final
    private int maxThreads;
    private final int maxThreadsPerTask;
    private final int maxStreamEventMapSize;

    public ExtractionConfig() {
        maxStoredDataQueueSize = DEFAULT_MAX_STORED_DATA_QUEUE_SIZE;
        maxThreads = DEFAULT_MAX_THREADS;
        maxThreadsPerTask = DEFAULT_MAX_THREADS_PER_TASK;
        maxStreamEventMapSize = DEFAULT_MAX_STREAM_EVENT_MAP_SIZE;
    }

    @JsonCreator
    public ExtractionConfig(@JsonProperty("maxStoredDataQueueSize") final int maxStoredDataQueueSize,
                            @JsonProperty("maxThreads") final int maxThreads,
                            @JsonProperty("maxThreadsPerTask") final int maxThreadsPerTask,
                            @JsonProperty("maxStreamEventMapSize") final int maxStreamEventMapSize) {
        this.maxStoredDataQueueSize = maxStoredDataQueueSize;
        this.maxThreads = maxThreads;
        this.maxThreadsPerTask = maxThreadsPerTask;
        this.maxStreamEventMapSize = maxStreamEventMapSize;
    }

    @JsonPropertyDescription("The maximum number documents that will have stored data retrieved from the index " +
            "shard and queued prior to further processing")
    public int getMaxStoredDataQueueSize() {
        return maxStoredDataQueueSize;
    }

    @JsonPropertyDescription("The absolute maximum number of threads per node, used to extract search results " +
            "from streams using a pipeline")
    public int getMaxThreads() {
        return maxThreads;
    }

    @Deprecated(forRemoval = true)
    public void setMaxThreads(final int maxThreads) {
        this.maxThreads = maxThreads;
    }

    @JsonPropertyDescription("The maximum number of threads per search, per node, used to extract search results " +
            "from streams using a pipeline")
    public int getMaxThreadsPerTask() {
        return maxThreadsPerTask;
    }

    @JsonPropertyDescription("The maximum size of the stream event map used to queue events prior to extraction")
    public int getMaxStreamEventMapSize() {
        return maxStreamEventMapSize;
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
