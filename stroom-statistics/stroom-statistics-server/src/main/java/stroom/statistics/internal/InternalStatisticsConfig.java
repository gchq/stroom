package stroom.statistics.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import stroom.docref.DocRef;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

//public class InternalStatisticsConfig extends HashMap<String, List<DocRef>> {
@Singleton
public class InternalStatisticsConfig {

    private static final Map<InternalStatisticKey, Function<InternalStatisticsConfig, List<DocRef>>>
            KEY_TO_DOC_REFS_GETTER_MAP = new EnumMap<>(InternalStatisticKey.class);

    static {
        populateGetterMap();
    }

    private static void populateGetterMap() {
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.BENCHMARK_CLUSTER, InternalStatisticsConfig::getBenchmarkClusterDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.CPU, InternalStatisticsConfig::getCpuDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.EVENTS_PER_SECOND, InternalStatisticsConfig::getEventsPerSecondDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.HEAP_HISTOGRAM_BYTES, InternalStatisticsConfig::getHeapHistogramBytesDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.HEAP_HISTOGRAM_INSTANCES, InternalStatisticsConfig::getHeapHistogramInstancesDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.MEMORY, InternalStatisticsConfig::getMemoryDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.METADATA_STREAM_SIZE, InternalStatisticsConfig::getMetaDataStreamSizeDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.METADATA_STREAMS_RECEIVED, InternalStatisticsConfig::getMetaDataStreamsReceivedDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.PIPELINE_STREAM_PROCESSOR, InternalStatisticsConfig::getPipelineStreamProcessorDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.STREAM_TASK_QUEUE_SIZE, InternalStatisticsConfig::getStreamTaskQueueSizeDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.VOLUMES, InternalStatisticsConfig::getVolumesDocRefs);
    }

    private List<DocRef> benchmarkClusterDocRefs = Collections.emptyList();
    private List<DocRef> cpuDocRefs = Collections.emptyList();
    private List<DocRef> eventsPerSecondDocRefs = Collections.emptyList();
    private List<DocRef> heapHistogramBytesDocRefs = Collections.emptyList();
    private List<DocRef> heapHistogramInstancesDocRefs = Collections.emptyList();
    private List<DocRef> memoryDocRefs = Collections.emptyList();
    private List<DocRef> metaDataStreamSizeDocRefs = Collections.emptyList();
    private List<DocRef> metaDataStreamsReceivedDocRefs = Collections.emptyList();
    private List<DocRef> pipelineStreamProcessorDocRefs = Collections.emptyList();
    private List<DocRef> streamTaskQueueSizeDocRefs = Collections.emptyList();
    private List<DocRef> volumesDocRefs = Collections.emptyList();

    @JsonIgnore
    public List<DocRef> get(final InternalStatisticKey internalStatisticKey) {
        Function<InternalStatisticsConfig, List<DocRef>> func = KEY_TO_DOC_REFS_GETTER_MAP.get(internalStatisticKey);
        Preconditions.checkNotNull(func, "Key %s is not known", internalStatisticKey);
        return func.apply(this);
    }

    public InternalStatisticsConfig() {
//        put("benchmarkCluster", Arrays.asList(
//                new DocRef("StatisticStore", "946a88c6-a59a-11e6-bdc4-0242ac110002", "Benchmark-Cluster Test"),
//                new DocRef("StroomStatsStore", "2503f703-5ce0-4432-b9d4-e3272178f47e", "Benchmark-Cluster Test")));
//
//        put("pipelineStreamProcessor", Arrays.asList(
//                new DocRef("StatisticStore", "946a80fc-a59a-11e6-bdc4-0242ac110002", "PipelineStreamProcessor"),
//                new DocRef("StroomStatsStore", "efd9bad4-0bab-460f-ae98-79e9717deeaf", "PipelineStreamProcessor")));
//
//        put("metaDataStreamSize", Arrays.asList(
//                new DocRef("StatisticStore", "946a8814-a59a-11e6-bdc4-0242ac110002", "Meta Data-Stream Size"),
//                new DocRef("StroomStatsStore", "3b25d63b-5472-44d0-80e8-8eea94f40f14", "Meta Data-Stream Size")));
//
//        put("eventsPerSecond", Arrays.asList(
//                new DocRef("StatisticStore", "a9936548-2572-448b-9d5b-8543052c4d92", "EPS"),
//                new DocRef("StroomStatsStore", "cde67df0-0f77-45d3-b2c0-ee8bb7b3c9c6", "EPS")));
//
//        put("cpu", Arrays.asList(
//                new DocRef("StatisticStore", "af08c4a7-ee7c-44e4-8f5e-e9c6be280434", "CPU"),
//                new DocRef("StroomStatsStore", "1edfd582-5e60-413a-b91c-151bd544da47", "CPU")));
//
//        put("metaDataStreamsReceived", Arrays.asList(
//                new DocRef("StatisticStore", "946a87bc-a59a-11e6-bdc4-0242ac110002", "Meta Data-Streams Received"),
//                new DocRef("StroomStatsStore", "5535f493-29ae-4ee6-bba6-735aa3104136", "Meta Data-Streams Received")));
//
//        put("streamTaskQueueSize", Arrays.asList(
//                new DocRef("StatisticStore", "946a7f0f-a59a-11e6-bdc4-0242ac110002", "Stream Task Queue Size"),
//                new DocRef("StroomStatsStore", "4ce8d6e7-94be-40e1-8294-bf29dd089962", "Stream Task Queue Size")));
//
//        put("volumes", Arrays.asList(
//                new DocRef("StatisticStore", "ac4d8d10-6f75-4946-9708-18b8cb42a5a3", "Volumes"),
//                new DocRef("StroomStatsStore", "60f4f5f0-4cc3-42d6-8fe7-21a7cec30f8e", "Volumes")));
//
//        put("memory", Arrays.asList(
//                new DocRef("StatisticStore", "77c09ccb-e251-4ca5-bca0-56a842654397", "Memory"),
//                new DocRef("StroomStatsStore", "d8a7da4f-ef6d-47e0-b16a-af26367a2798", "Memory")));
//
//        put("heapHistogramInstances", Arrays.asList(
//                new DocRef("StatisticStore", "e4f243b8-2c70-4d6e-9d5a-16466bf8764f", "Heap Histogram Instances"),
//                new DocRef("StroomStatsStore", "bdd933a4-4309-47fd-98f6-1bc2eb555f20", "Heap Histogram Instances")));
//
//        put("heapHistogramBytes", Arrays.asList(
//                new DocRef("StatisticStore", "934a1600-b456-49bf-9aea-f1e84025febd", "Heap Histogram Bytes"),
//                new DocRef("StroomStatsStore", "b0110ab4-ac25-4b73-b4f6-96f2b50b456a", "Heap Histogram Bytes")));
    }

    @JsonProperty("benchmarkCluster")
    public List<DocRef> getBenchmarkClusterDocRefs() {
        return benchmarkClusterDocRefs;
    }

    public void setBenchmarkClusterDocRefs(final List<DocRef> benchmarkClusterDocRefs) {
        this.benchmarkClusterDocRefs = benchmarkClusterDocRefs;
    }

    @JsonProperty("cpu")
    public List<DocRef> getCpuDocRefs() {
        return cpuDocRefs;
    }

    public void setCpuDocRefs(final List<DocRef> cpuDocRefs) {
        this.cpuDocRefs = cpuDocRefs;
    }

    @JsonProperty("eventsPerSecond")
    public List<DocRef> getEventsPerSecondDocRefs() {
        return eventsPerSecondDocRefs;
    }

    public void setEventsPerSecondDocRefs(final List<DocRef> eventsPerSecondDocRefs) {
        this.eventsPerSecondDocRefs = eventsPerSecondDocRefs;
    }

    @JsonProperty("heapHistogramBytes")
    public List<DocRef> getHeapHistogramBytesDocRefs() {
        return heapHistogramBytesDocRefs;
    }

    public void setHeapHistogramBytesDocRefs(final List<DocRef> heapHistogramBytesDocRefs) {
        this.heapHistogramBytesDocRefs = heapHistogramBytesDocRefs;
    }

    @JsonProperty("heapHistogramInstances")
    public List<DocRef> getHeapHistogramInstancesDocRefs() {
        return heapHistogramInstancesDocRefs;
    }

    public void setHeapHistogramInstancesDocRefs(final List<DocRef> heapHistogramInstancesDocRefs) {
        this.heapHistogramInstancesDocRefs = heapHistogramInstancesDocRefs;
    }

    @JsonProperty("memory")
    public List<DocRef> getMemoryDocRefs() {
        return memoryDocRefs;
    }

    public void setMemoryDocRefs(final List<DocRef> memoryDocRefs) {
        this.memoryDocRefs = memoryDocRefs;
    }

    @JsonProperty("metaDataStreamSize")
    public List<DocRef> getMetaDataStreamSizeDocRefs() {
        return metaDataStreamSizeDocRefs;
    }

    public void setMetaDataStreamSizeDocRefs(final List<DocRef> metaDataStreamSizeDocRefs) {
        this.metaDataStreamSizeDocRefs = metaDataStreamSizeDocRefs;
    }

    @JsonProperty("metaDataStreamsReceived")
    public List<DocRef> getMetaDataStreamsReceivedDocRefs() {
        return metaDataStreamsReceivedDocRefs;
    }

    public void setMetaDataStreamsReceivedDocRefs(final List<DocRef> metaDataStreamsReceivedDocRefs) {
        this.metaDataStreamsReceivedDocRefs = metaDataStreamsReceivedDocRefs;
    }

    @JsonProperty("pipelineStreamProcessor")
    public List<DocRef> getPipelineStreamProcessorDocRefs() {
        return pipelineStreamProcessorDocRefs;
    }

    public void setPipelineStreamProcessorDocRefs(final List<DocRef> pipelineStreamProcessorDocRefs) {
        this.pipelineStreamProcessorDocRefs = pipelineStreamProcessorDocRefs;
    }

    @JsonProperty("streamTaskQueueSize")
    public List<DocRef> getStreamTaskQueueSizeDocRefs() {
        return streamTaskQueueSizeDocRefs;
    }

    public void setStreamTaskQueueSizeDocRefs(final List<DocRef> streamTaskQueueSizeDocRefs) {
        this.streamTaskQueueSizeDocRefs = streamTaskQueueSizeDocRefs;
    }

    @JsonProperty("volumes")
    public List<DocRef> getVolumesDocRefs() {
        return volumesDocRefs;
    }

    public void setVolumesDocRefs(final List<DocRef> volumesDocRefs) {
        this.volumesDocRefs = volumesDocRefs;
    }
}

