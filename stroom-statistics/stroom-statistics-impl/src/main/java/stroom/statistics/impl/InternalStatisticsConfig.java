package stroom.statistics.impl;

import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.validation.IsSubsetOf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class InternalStatisticsConfig extends AbstractConfig {

    private static final String DESCRIPTION_PREFIX = "A list of DocRefs, one for each statistic store that " +
            "events for this internal statistic will be sent to. This internal statistic describes ";

    private static final Map<InternalStatisticKey, Function<InternalStatisticsConfig, List<DocRef>>>
            KEY_TO_DOC_REFS_GETTER_MAP = new EnumMap<>(InternalStatisticKey.class);

    static {
        // Define the links between internal stat keys and the getter to get the docrefs for that key
        populateGetterMap();
    }

    private List<String> enabledStoreTypes;

    private List<DocRef> benchmarkClusterDocRefs;
    private List<DocRef> cpuDocRefs;
    private List<DocRef> eventsPerSecondDocRefs;
    private List<DocRef> heapHistogramBytesDocRefs;
    private List<DocRef> heapHistogramInstancesDocRefs;
    private List<DocRef> memoryDocRefs;
    private List<DocRef> metaDataStreamSizeDocRefs;
    private List<DocRef> metaDataStreamsReceivedDocRefs;
    private List<DocRef> pipelineStreamProcessorDocRefs;
    private List<DocRef> streamTaskQueueSizeDocRefs;
    private List<DocRef> volumesDocRefs;

    public InternalStatisticsConfig() {
        // By Default we only want internal stats to go to SQL stats store
        // as there may not be a kafka available.
        enabledStoreTypes = Collections.singletonList(StatisticStoreDoc.DOCUMENT_TYPE);

        // Set up the associations from internal stat to the doc refs of the stores for that stat
        // These docs are all defined in the stroom-content repo
        benchmarkClusterDocRefs = buildDocRefs(InternalStatisticKey.BENCHMARK_CLUSTER, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "946a88c6-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "2503f703-5ce0-4432-b9d4-e3272178f47e"));

        cpuDocRefs = buildDocRefs(InternalStatisticKey.CPU, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "af08c4a7-ee7c-44e4-8f5e-e9c6be280434",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "1edfd582-5e60-413a-b91c-151bd544da47"));

        eventsPerSecondDocRefs = buildDocRefs(InternalStatisticKey.EVENTS_PER_SECOND, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "a9936548-2572-448b-9d5b-8543052c4d92",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "cde67df0-0f77-45d3-b2c0-ee8bb7b3c9c6"));

        heapHistogramBytesDocRefs = buildDocRefs(InternalStatisticKey.HEAP_HISTOGRAM_BYTES, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "934a1600-b456-49bf-9aea-f1e84025febd",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "b0110ab4-ac25-4b73-b4f6-96f2b50b456a"));

        heapHistogramInstancesDocRefs = buildDocRefs(InternalStatisticKey.HEAP_HISTOGRAM_INSTANCES, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "e4f243b8-2c70-4d6e-9d5a-16466bf8764f",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "bdd933a4-4309-47fd-98f6-1bc2eb555f20"));

        memoryDocRefs = buildDocRefs(InternalStatisticKey.MEMORY, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "77c09ccb-e251-4ca5-bca0-56a842654397",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "d8a7da4f-ef6d-47e0-b16a-af26367a2798"));

        metaDataStreamSizeDocRefs = buildDocRefs(InternalStatisticKey.METADATA_STREAM_SIZE, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "946a8814-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "3b25d63b-5472-44d0-80e8-8eea94f40f14"));

        metaDataStreamsReceivedDocRefs = buildDocRefs(InternalStatisticKey.METADATA_STREAMS_RECEIVED, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "946a87bc-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "5535f493-29ae-4ee6-bba6-735aa3104136"));

        pipelineStreamProcessorDocRefs = buildDocRefs(InternalStatisticKey.PIPELINE_STREAM_PROCESSOR, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "946a80fc-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "efd9bad4-0bab-460f-ae98-79e9717deeaf"));

        streamTaskQueueSizeDocRefs = buildDocRefs(InternalStatisticKey.STREAM_TASK_QUEUE_SIZE, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "946a7f0f-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "4ce8d6e7-94be-40e1-8294-bf29dd089962"));

        volumesDocRefs = buildDocRefs(InternalStatisticKey.VOLUMES, Map.of(
                StatisticStoreDoc.DOCUMENT_TYPE, "ac4d8d10-6f75-4946-9708-18b8cb42a5a3",
                StroomStatsStoreDoc.DOCUMENT_TYPE, "60f4f5f0-4cc3-42d6-8fe7-21a7cec30f8e"));
    }

    @JsonPropertyDescription("Determines which statistic store(s) internal statistic events will be sent to. " +
            "Valid values are \"" + StatisticStoreDoc.DOCUMENT_TYPE + "\" and \"" + StroomStatsStoreDoc.DOCUMENT_TYPE +
            "\". An empty list means all internal statistic events will be dropped.")
    @JsonProperty("enabledStoreTypes")
    @IsSubsetOf(allowedValues = {
            StatisticStoreDoc.DOCUMENT_TYPE,
            StroomStatsStoreDoc.DOCUMENT_TYPE})
    public List<String> getEnabledStoreTypes() {
        return enabledStoreTypes;
    }

    @SuppressWarnings("unused")
    public void setEnabledStoreTypes(List<String> enabledStoreTypes) {
        this.enabledStoreTypes = enabledStoreTypes;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the statistics produced by the Benchmark Cluster Test job.")
    @JsonProperty("benchmarkCluster")
    public List<DocRef> getBenchmarkClusterDocRefs() {
        return benchmarkClusterDocRefs;
    }

    @SuppressWarnings("unused")
    public void setBenchmarkClusterDocRefs(final List<DocRef> benchmarkClusterDocRefs) {
        this.benchmarkClusterDocRefs = benchmarkClusterDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "various statistic values relating to the CPU load on the node.")
    @JsonProperty("cpu")
    public List<DocRef> getCpuDocRefs() {
        return cpuDocRefs;
    }

    @SuppressWarnings("unused")
    public void setCpuDocRefs(final List<DocRef> cpuDocRefs) {
        this.cpuDocRefs = cpuDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of events processed per second by a node.")
    @JsonProperty("eventsPerSecond")
    public List<DocRef> getEventsPerSecondDocRefs() {
        return eventsPerSecondDocRefs;
    }

    @SuppressWarnings("unused")
    public void setEventsPerSecondDocRefs(final List<DocRef> eventsPerSecondDocRefs) {
        this.eventsPerSecondDocRefs = eventsPerSecondDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "values from a Java heap histogram based on the number of bytes used by each class.")
    @JsonProperty("heapHistogramBytes")
    public List<DocRef> getHeapHistogramBytesDocRefs() {
        return heapHistogramBytesDocRefs;
    }

    @SuppressWarnings("unused")
    public void setHeapHistogramBytesDocRefs(final List<DocRef> heapHistogramBytesDocRefs) {
        this.heapHistogramBytesDocRefs = heapHistogramBytesDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "values from a Java heap histogram based on the number of instances used by each class.")
    @JsonProperty("heapHistogramInstances")
    public List<DocRef> getHeapHistogramInstancesDocRefs() {
        return heapHistogramInstancesDocRefs;
    }

    @SuppressWarnings("unused")
    public void setHeapHistogramInstancesDocRefs(final List<DocRef> heapHistogramInstancesDocRefs) {
        this.heapHistogramInstancesDocRefs = heapHistogramInstancesDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "various values relating to the memory use on a node.")
    @JsonProperty("memory")
    public List<DocRef> getMemoryDocRefs() {
        return memoryDocRefs;
    }

    @SuppressWarnings("unused")
    public void setMemoryDocRefs(final List<DocRef> memoryDocRefs) {
        this.memoryDocRefs = memoryDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the volume of data (in bytes) received by a feed.")
    @JsonProperty("metaDataStreamSize")
    public List<DocRef> getMetaDataStreamSizeDocRefs() {
        return metaDataStreamSizeDocRefs;
    }

    @SuppressWarnings("unused")
    public void setMetaDataStreamSizeDocRefs(final List<DocRef> metaDataStreamSizeDocRefs) {
        this.metaDataStreamSizeDocRefs = metaDataStreamSizeDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of streams received by a feed.")
    @JsonProperty("metaDataStreamsReceived")
    public List<DocRef> getMetaDataStreamsReceivedDocRefs() {
        return metaDataStreamsReceivedDocRefs;
    }

    @SuppressWarnings("unused")
    public void setMetaDataStreamsReceivedDocRefs(final List<DocRef> metaDataStreamsReceivedDocRefs) {
        this.metaDataStreamsReceivedDocRefs = metaDataStreamsReceivedDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of streams processed by a pipeline.")
    @JsonProperty("pipelineStreamProcessor")
    public List<DocRef> getPipelineStreamProcessorDocRefs() {
        return pipelineStreamProcessorDocRefs;
    }

    @SuppressWarnings("unused")
    public void setPipelineStreamProcessorDocRefs(final List<DocRef> pipelineStreamProcessorDocRefs) {
        this.pipelineStreamProcessorDocRefs = pipelineStreamProcessorDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of items on the stream task queue.")
    @JsonProperty("streamTaskQueueSize")
    public List<DocRef> getStreamTaskQueueSizeDocRefs() {
        return streamTaskQueueSizeDocRefs;
    }

    @SuppressWarnings("unused")
    public void setStreamTaskQueueSizeDocRefs(final List<DocRef> streamTaskQueueSizeDocRefs) {
        this.streamTaskQueueSizeDocRefs = streamTaskQueueSizeDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of items on the stream task queue.")
    @JsonProperty("volumes")
    public List<DocRef> getVolumesDocRefs() {
        return volumesDocRefs;
    }

    @SuppressWarnings("unused")
    public void setVolumesDocRefs(final List<DocRef> volumesDocRefs) {
        this.volumesDocRefs = volumesDocRefs;
    }

//    /**
//     * Get the list of DocRefs that correspond to a particular key
//     */
//    @JsonIgnore
//    public List<DocRef> getDocRefs(final InternalStatisticKey internalStatisticKey) {
//        Function<InternalStatisticsConfig, List<DocRef>> func = KEY_TO_DOC_REFS_GETTER_MAP.get(internalStatisticKey);
//        Objects.requireNonNull(func, () -> LogUtil.message("Key {} is not known"));
//        return func.apply(this);
//    }

    /**
     * Get the list of DocRefs that correspond to a particular key, where the type of the {@link DocRef}
     * is in the list of enabledStoreTypes.
     */
    @JsonIgnore
    public List<DocRef> getEnabledDocRefs(final InternalStatisticKey internalStatisticKey) {
        Function<InternalStatisticsConfig, List<DocRef>> func = KEY_TO_DOC_REFS_GETTER_MAP.get(internalStatisticKey);
        Objects.requireNonNull(func, () -> LogUtil.message("Key {} is not known"));
        return func.apply(this).stream()
                .filter(docRef -> enabledStoreTypes.contains(docRef.getType()))
                .collect(Collectors.toList());
   }

    @Override
    public String toString() {
        return "InternalStatisticsConfig{" +
                "benchmarkClusterDocRefs=" + benchmarkClusterDocRefs +
                ", cpuDocRefs=" + cpuDocRefs +
                ", eventsPerSecondDocRefs=" + eventsPerSecondDocRefs +
                ", heapHistogramBytesDocRefs=" + heapHistogramBytesDocRefs +
                ", heapHistogramInstancesDocRefs=" + heapHistogramInstancesDocRefs +
                ", memoryDocRefs=" + memoryDocRefs +
                ", metaDataStreamSizeDocRefs=" + metaDataStreamSizeDocRefs +
                ", metaDataStreamsReceivedDocRefs=" + metaDataStreamsReceivedDocRefs +
                ", pipelineStreamProcessorDocRefs=" + pipelineStreamProcessorDocRefs +
                ", streamTaskQueueSizeDocRefs=" + streamTaskQueueSizeDocRefs +
                ", volumesDocRefs=" + volumesDocRefs +
                '}';
    }

    /**
     * Create a list of {@link DocRef} objects that are associated with a single {@link InternalStatisticKey}
     */
    @JsonIgnore
    private static List<DocRef> buildDocRefs(final InternalStatisticKey internalStatisticKey,
                                             final Map<String, String> storeTypeToUuidMap) {
        return storeTypeToUuidMap.entrySet()
                .stream()
                .map(entry -> {
                    final String type = entry.getKey();
                    final String uuid = entry.getValue();
                    return new DocRef(type, uuid, internalStatisticKey.getKeyName());
                })
                .sorted(Comparator.comparing(DocRef::getType)) // sort so yaml is consistent
                .collect(Collectors.toList());
    }

    @JsonIgnore
    private static void populateGetterMap() {
        // A set of mappings from the internal stat key to the getter to get the docRefs for it.
        // Each InternalStatisticKey must have a corresponding getter in this class
        // i.e "Stream Task Queue Size" => getStreamTaskQueueSizeDocRefs()
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.BENCHMARK_CLUSTER,
                InternalStatisticsConfig::getBenchmarkClusterDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.CPU,
                InternalStatisticsConfig::getCpuDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.EVENTS_PER_SECOND,
                InternalStatisticsConfig::getEventsPerSecondDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.HEAP_HISTOGRAM_BYTES,
                InternalStatisticsConfig::getHeapHistogramBytesDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.HEAP_HISTOGRAM_INSTANCES,
                InternalStatisticsConfig::getHeapHistogramInstancesDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.MEMORY,
                InternalStatisticsConfig::getMemoryDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.METADATA_STREAM_SIZE,
                InternalStatisticsConfig::getMetaDataStreamSizeDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.METADATA_STREAMS_RECEIVED,
                InternalStatisticsConfig::getMetaDataStreamsReceivedDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.PIPELINE_STREAM_PROCESSOR,
                InternalStatisticsConfig::getPipelineStreamProcessorDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.STREAM_TASK_QUEUE_SIZE,
                InternalStatisticsConfig::getStreamTaskQueueSizeDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.VOLUMES,
                InternalStatisticsConfig::getVolumesDocRefs);
    }
}

