package stroom.statistics.internal;

public enum InternalStatisticKey {

    BENCHMARK_CLUSTER("Benchmark-Cluster Test"),
    CPU("CPU"),
    EVENTS_PER_SECOND("EPS"),
    HEAP_HISTOGRAM_BYTES("Heap Histogram Bytes"),
    HEAP_HISTOGRAM_INSTANCES("Heap Histogram Instances"),
    MEMORY("Memory"),
    METADATA_STREAMS_RECEIVED("metaDataStreamsReceived"),
    METADATA_STREAM_SIZE("metaDataStreamSize"),
    PIPELINE_STREAM_PROCESSOR("PipelineStreamProcessor"),
    STREAM_TASK_QUEUE_SIZE("Stream Task Queue Size"),
    VOLUMES("Volumes");

    private final String name;

    InternalStatisticKey(final String name) {
        this.name = name;
    }
}
