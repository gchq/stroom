package stroom.statistics.api;

public enum InternalStatisticKey {
    BENCHMARK_CLUSTER("Benchmark-Cluster Test"),
    CPU("CPU"),
    EVENTS_PER_SECOND("EPS"),
    HEAP_HISTOGRAM_BYTES("Heap Histogram Bytes"),
    HEAP_HISTOGRAM_INSTANCES("Heap Histogram Instances"),
    MEMORY("Memory"),
    METADATA_STREAMS_RECEIVED("Meta Data-Streams Received"),
    METADATA_STREAM_SIZE("Meta Data-Stream Size"),
    PIPELINE_STREAM_PROCESSOR("PipelineStreamProcessor"),
    REF_DATA_STORE_ENTRY_COUNT("Reference Data Store Entry Count"),
    REF_DATA_STORE_SIZE("Reference Data Store Size"),
    REF_DATA_STORE_STREAM_COUNT("Reference Data Store Stream Count"),
    SEARCH_RESULTS_STORE_SIZE("Search Results Store Size"),
    SEARCH_RESULTS_STORE_COUNT("Search Results Store Count"),
    STREAM_TASK_QUEUE_SIZE("Stream Task Queue Size"),
    VOLUMES("Volumes");

    private final String keyName;

    InternalStatisticKey(final String keyName) {
        this.keyName = keyName;
    }

    public String getKeyName() {
        return keyName;
    }
}
