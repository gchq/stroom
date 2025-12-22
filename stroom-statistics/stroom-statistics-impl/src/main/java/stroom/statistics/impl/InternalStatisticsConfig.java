/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.impl;

import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.IsSubsetOf;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@JsonPropertyOrder(alphabetic = true)
public class InternalStatisticsConfig extends AbstractConfig implements IsStroomConfig {

    private static final String DESCRIPTION_PREFIX = "A list of DocRefs, one for each statistic store that " +
                                                     "events for this internal statistic will be sent to. " +
                                                     "This internal statistic describes ";

    private static final Map<InternalStatisticKey, Function<InternalStatisticsConfig, List<DocRef>>>
            KEY_TO_DOC_REFS_GETTER_MAP = new EnumMap<>(InternalStatisticKey.class);

    static {
        // Define the links between internal stat keys and the getter to get the docrefs for that key
        populateGetterMap();
    }

    // TODO 02/12/2021 AT: Make final
    private List<String> enabledStoreTypes;

    private final List<DocRef> benchmarkClusterDocRefs;
    private final List<DocRef> cpuDocRefs;
    private final List<DocRef> eventsPerSecondDocRefs;
    private final List<DocRef> heapHistogramBytesDocRefs;
    private final List<DocRef> heapHistogramInstancesDocRefs;
    private final List<DocRef> memoryDocRefs;
    private final List<DocRef> metaDataStreamSizeDocRefs;
    private final List<DocRef> metaDataStreamsReceivedDocRefs;
    private final List<DocRef> refDataStoreEntryCount;
    private final List<DocRef> refDataStoreStreamCount;
    private final List<DocRef> refDataStoreSize;
    private final List<DocRef> pipelineStreamProcessorDocRefs;
    private final List<DocRef> searchResultsStoreSize;
    private final List<DocRef> searchResultsStoreCount;
    private final List<DocRef> streamTaskQueueSizeDocRefs;
    private final List<DocRef> volumesDocRefs;

    public InternalStatisticsConfig() {
        // By Default we only want internal stats to go to SQL stats store
        // as there may not be a kafka available.
        enabledStoreTypes = Collections.singletonList(StatisticStoreDoc.TYPE);

        // Set up the associations from internal stat to the doc refs of the stores for that stat
        // These docs are all defined in the stroom-content repo
        benchmarkClusterDocRefs = buildDocRefs(InternalStatisticKey.BENCHMARK_CLUSTER, Map.of(
                StatisticStoreDoc.TYPE, "946a88c6-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.TYPE, "2503f703-5ce0-4432-b9d4-e3272178f47e"));

        cpuDocRefs = buildDocRefs(InternalStatisticKey.CPU, Map.of(
                StatisticStoreDoc.TYPE, "af08c4a7-ee7c-44e4-8f5e-e9c6be280434",
                StroomStatsStoreDoc.TYPE, "1edfd582-5e60-413a-b91c-151bd544da47"));

        eventsPerSecondDocRefs = buildDocRefs(InternalStatisticKey.EVENTS_PER_SECOND, Map.of(
                StatisticStoreDoc.TYPE, "a9936548-2572-448b-9d5b-8543052c4d92",
                StroomStatsStoreDoc.TYPE, "cde67df0-0f77-45d3-b2c0-ee8bb7b3c9c6"));

        heapHistogramBytesDocRefs = buildDocRefs(InternalStatisticKey.HEAP_HISTOGRAM_BYTES, Map.of(
                StatisticStoreDoc.TYPE, "934a1600-b456-49bf-9aea-f1e84025febd",
                StroomStatsStoreDoc.TYPE, "b0110ab4-ac25-4b73-b4f6-96f2b50b456a"));

        heapHistogramInstancesDocRefs = buildDocRefs(InternalStatisticKey.HEAP_HISTOGRAM_INSTANCES, Map.of(
                StatisticStoreDoc.TYPE, "e4f243b8-2c70-4d6e-9d5a-16466bf8764f",
                StroomStatsStoreDoc.TYPE, "bdd933a4-4309-47fd-98f6-1bc2eb555f20"));

        memoryDocRefs = buildDocRefs(InternalStatisticKey.MEMORY, Map.of(
                StatisticStoreDoc.TYPE, "77c09ccb-e251-4ca5-bca0-56a842654397",
                StroomStatsStoreDoc.TYPE, "d8a7da4f-ef6d-47e0-b16a-af26367a2798"));

        metaDataStreamSizeDocRefs = buildDocRefs(InternalStatisticKey.METADATA_STREAM_SIZE, Map.of(
                StatisticStoreDoc.TYPE, "946a8814-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.TYPE, "3b25d63b-5472-44d0-80e8-8eea94f40f14"));

        metaDataStreamsReceivedDocRefs = buildDocRefs(InternalStatisticKey.METADATA_STREAMS_RECEIVED, Map.of(
                StatisticStoreDoc.TYPE, "946a87bc-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.TYPE, "5535f493-29ae-4ee6-bba6-735aa3104136"));

        refDataStoreEntryCount = buildDocRefs(InternalStatisticKey.REF_DATA_STORE_ENTRY_COUNT, Map.of(
                StatisticStoreDoc.TYPE, "f1587262-9cbc-46b4-80eb-51deb011b2c1",
                StroomStatsStoreDoc.TYPE, "TODO"));

        refDataStoreSize = buildDocRefs(InternalStatisticKey.REF_DATA_STORE_SIZE, Map.of(
                StatisticStoreDoc.TYPE, "e57959bf-0b2d-4008-98a7-ffcae4bbc4bb",
                StroomStatsStoreDoc.TYPE, "TODO"));

        refDataStoreStreamCount = buildDocRefs(InternalStatisticKey.REF_DATA_STORE_STREAM_COUNT, Map.of(
                StatisticStoreDoc.TYPE, "0dfd4e00-e068-4667-9c60-d3f6163a6c04",
                StroomStatsStoreDoc.TYPE, "TODO"));

        pipelineStreamProcessorDocRefs = buildDocRefs(InternalStatisticKey.PIPELINE_STREAM_PROCESSOR, Map.of(
                StatisticStoreDoc.TYPE, "946a80fc-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.TYPE, "efd9bad4-0bab-460f-ae98-79e9717deeaf"));

        searchResultsStoreSize = buildDocRefs(InternalStatisticKey.SEARCH_RESULTS_STORE_SIZE, Map.of(
                StatisticStoreDoc.TYPE, "de5b831d-3b7e-4bb5-836f-2f438ec30568",
                StroomStatsStoreDoc.TYPE, "TODO"));

        searchResultsStoreCount = buildDocRefs(InternalStatisticKey.SEARCH_RESULTS_STORE_COUNT, Map.of(
                StatisticStoreDoc.TYPE, "35d60e7d-f11a-45c9-981d-16d8ddda081e",
                StroomStatsStoreDoc.TYPE, "TODO"));

        streamTaskQueueSizeDocRefs = buildDocRefs(InternalStatisticKey.STREAM_TASK_QUEUE_SIZE, Map.of(
                StatisticStoreDoc.TYPE, "946a7f0f-a59a-11e6-bdc4-0242ac110002",
                StroomStatsStoreDoc.TYPE, "4ce8d6e7-94be-40e1-8294-bf29dd089962"));

        volumesDocRefs = buildDocRefs(InternalStatisticKey.VOLUMES, Map.of(
                StatisticStoreDoc.TYPE, "ac4d8d10-6f75-4946-9708-18b8cb42a5a3",
                StroomStatsStoreDoc.TYPE, "60f4f5f0-4cc3-42d6-8fe7-21a7cec30f8e"));
    }

    @JsonCreator
    public InternalStatisticsConfig(
            @JsonProperty("enabledStoreTypes") final List<String> enabledStoreTypes,
            @JsonProperty("benchmarkCluster") final List<DocRef> benchmarkClusterDocRefs,
            @JsonProperty("cpu") final List<DocRef> cpuDocRefs,
            @JsonProperty("eventsPerSecond") final List<DocRef> eventsPerSecondDocRefs,
            @JsonProperty("heapHistogramBytes") final List<DocRef> heapHistogramBytesDocRefs,
            @JsonProperty("heapHistogramInstances") final List<DocRef> heapHistogramInstancesDocRefs,
            @JsonProperty("memory") final List<DocRef> memoryDocRefs,
            @JsonProperty("metaDataStreamSize") final List<DocRef> metaDataStreamSizeDocRefs,
            @JsonProperty("metaDataStreamsReceived") final List<DocRef> metaDataStreamsReceivedDocRefs,
            @JsonProperty("refDataStoreEntryCount") final List<DocRef> refDataStoreEntryCount,
            @JsonProperty("refDataStoreSize") final List<DocRef> refDataStoreSize,
            @JsonProperty("refDataStoreStreamCount") final List<DocRef> refDataStoreStreamCount,
            @JsonProperty("pipelineStreamProcessor") final List<DocRef> pipelineStreamProcessorDocRefs,
            @JsonProperty("searchResultsStoreSize") final List<DocRef> searchResultsStoreSize,
            @JsonProperty("searchResultsStoreCount") final List<DocRef> searchResultsStoreCount,
            @JsonProperty("streamTaskQueueSize") final List<DocRef> streamTaskQueueSizeDocRefs,
            @JsonProperty("volumes") final List<DocRef> volumesDocRefs) {

        this.enabledStoreTypes = enabledStoreTypes;
        this.benchmarkClusterDocRefs = benchmarkClusterDocRefs;
        this.cpuDocRefs = cpuDocRefs;
        this.eventsPerSecondDocRefs = eventsPerSecondDocRefs;
        this.heapHistogramBytesDocRefs = heapHistogramBytesDocRefs;
        this.heapHistogramInstancesDocRefs = heapHistogramInstancesDocRefs;
        this.memoryDocRefs = memoryDocRefs;
        this.metaDataStreamSizeDocRefs = metaDataStreamSizeDocRefs;
        this.metaDataStreamsReceivedDocRefs = metaDataStreamsReceivedDocRefs;
        this.refDataStoreEntryCount = refDataStoreEntryCount;
        this.refDataStoreSize = refDataStoreSize;
        this.refDataStoreStreamCount = refDataStoreStreamCount;
        this.pipelineStreamProcessorDocRefs = pipelineStreamProcessorDocRefs;
        this.searchResultsStoreSize = searchResultsStoreSize;
        this.searchResultsStoreCount = searchResultsStoreCount;
        this.streamTaskQueueSizeDocRefs = streamTaskQueueSizeDocRefs;
        this.volumesDocRefs = volumesDocRefs;
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
                InternalStatisticKey.REF_DATA_STORE_ENTRY_COUNT,
                InternalStatisticsConfig::getRefDataStoreEntryCount);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.REF_DATA_STORE_SIZE,
                InternalStatisticsConfig::getRefDataStoreSize);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.REF_DATA_STORE_STREAM_COUNT,
                InternalStatisticsConfig::getRefDataStoreStreamCount);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.PIPELINE_STREAM_PROCESSOR,
                InternalStatisticsConfig::getPipelineStreamProcessorDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.SEARCH_RESULTS_STORE_SIZE,
                InternalStatisticsConfig::getSearchResultsStoreSize);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.SEARCH_RESULTS_STORE_COUNT,
                InternalStatisticsConfig::getSearchResultsStoreCount);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.STREAM_TASK_QUEUE_SIZE,
                InternalStatisticsConfig::getStreamTaskQueueSizeDocRefs);
        KEY_TO_DOC_REFS_GETTER_MAP.put(
                InternalStatisticKey.VOLUMES,
                InternalStatisticsConfig::getVolumesDocRefs);
    }

    @JsonPropertyDescription("Determines which statistic store(s) internal statistic events will be sent to. " +
                             "Valid values are \"" + StatisticStoreDoc.TYPE + "\" and \"" + StroomStatsStoreDoc.TYPE +
                             "\". An empty list means all internal statistic events will be dropped.")
    @JsonProperty("enabledStoreTypes")
    @IsSubsetOf(allowedValues = {
            StatisticStoreDoc.TYPE,
            StroomStatsStoreDoc.TYPE})
    public List<String> getEnabledStoreTypes() {
        return enabledStoreTypes;
    }

    @Deprecated(forRemoval = true)
    public void setEnabledStoreTypes(final List<String> enabledStoreTypes) {
        this.enabledStoreTypes = enabledStoreTypes;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the statistics produced by the Benchmark Cluster Test job.")
    @JsonProperty("benchmarkCluster")
    public List<DocRef> getBenchmarkClusterDocRefs() {
        return benchmarkClusterDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "various statistic values relating to the CPU load on the node.")
    @JsonProperty("cpu")
    public List<DocRef> getCpuDocRefs() {
        return cpuDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of events processed per second by a node.")
    @JsonProperty("eventsPerSecond")
    public List<DocRef> getEventsPerSecondDocRefs() {
        return eventsPerSecondDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "values from a Java heap histogram based on the number of " +
                             "bytes used by each class.")
    @JsonProperty("heapHistogramBytes")
    public List<DocRef> getHeapHistogramBytesDocRefs() {
        return heapHistogramBytesDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "values from a Java heap histogram based on the number of " +
                             "instances used by each class.")
    @JsonProperty("heapHistogramInstances")
    public List<DocRef> getHeapHistogramInstancesDocRefs() {
        return heapHistogramInstancesDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "various values relating to the memory use on a node.")
    @JsonProperty("memory")
    public List<DocRef> getMemoryDocRefs() {
        return memoryDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the volume of data (in bytes) received by a feed.")
    @JsonProperty("metaDataStreamSize")
    public List<DocRef> getMetaDataStreamSizeDocRefs() {
        return metaDataStreamSizeDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of streams received by a feed.")
    @JsonProperty("metaDataStreamsReceived")
    public List<DocRef> getMetaDataStreamsReceivedDocRefs() {
        return metaDataStreamsReceivedDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX
                             + "the total number of map entries in the off heap reference data store.")
    @JsonProperty("refDataStoreEntryCount")
    public List<DocRef> getRefDataStoreEntryCount() {
        return refDataStoreEntryCount;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the total size in bytes of the off heap reference data store.")
    @JsonProperty("refDataStoreSize")
    public List<DocRef> getRefDataStoreSize() {
        return refDataStoreSize;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX
                             + "the total number of streams in the off heap reference data store.")
    @JsonProperty("refDataStoreStreamCount")
    public List<DocRef> getRefDataStoreStreamCount() {
        return refDataStoreStreamCount;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the total size in bytes of the off heap search results stores.")
    @JsonProperty("searchResultsStoreSize")
    public List<DocRef> getSearchResultsStoreSize() {
        return searchResultsStoreSize;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the total number of off heap search results stores.")
    @JsonProperty("searchResultsStoreCount")
    public List<DocRef> getSearchResultsStoreCount() {
        return searchResultsStoreCount;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of streams processed by a pipeline.")
    @JsonProperty("pipelineStreamProcessor")
    public List<DocRef> getPipelineStreamProcessorDocRefs() {
        return pipelineStreamProcessorDocRefs;
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

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of items on the stream task queue.")
    @JsonProperty("streamTaskQueueSize")
    public List<DocRef> getStreamTaskQueueSizeDocRefs() {
        return streamTaskQueueSizeDocRefs;
    }

    @JsonPropertyDescription(DESCRIPTION_PREFIX + "the number of items on the stream task queue.")
    @JsonProperty("volumes")
    public List<DocRef> getVolumesDocRefs() {
        return volumesDocRefs;
    }

    /**
     * Get the list of DocRefs that correspond to a particular key, where the type of the {@link DocRef}
     * is in the list of enabledStoreTypes.
     */
    @JsonIgnore
    public List<DocRef> getEnabledDocRefs(final InternalStatisticKey internalStatisticKey) {
        final Function<InternalStatisticsConfig, List<DocRef>> func = KEY_TO_DOC_REFS_GETTER_MAP.get(
                internalStatisticKey);
        Objects.requireNonNull(func, () -> LogUtil.message(
                "Key {} is not known. You need to added it to KEY_TO_DOC_REFS_GETTER_MAP"));

        return func.apply(this).stream()
                .filter(docRef -> enabledStoreTypes.contains(docRef.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "InternalStatisticsConfig{" +
               "enabledStoreTypes=" + enabledStoreTypes +
               ", benchmarkClusterDocRefs=" + benchmarkClusterDocRefs +
               ", cpuDocRefs=" + cpuDocRefs +
               ", eventsPerSecondDocRefs=" + eventsPerSecondDocRefs +
               ", heapHistogramBytesDocRefs=" + heapHistogramBytesDocRefs +
               ", heapHistogramInstancesDocRefs=" + heapHistogramInstancesDocRefs +
               ", memoryDocRefs=" + memoryDocRefs +
               ", metaDataStreamSizeDocRefs=" + metaDataStreamSizeDocRefs +
               ", metaDataStreamsReceivedDocRefs=" + metaDataStreamsReceivedDocRefs +
               ", refDataStoreSize=" + refDataStoreSize +
               ", pipelineStreamProcessorDocRefs=" + pipelineStreamProcessorDocRefs +
               ", searchResultsStoreSize=" + searchResultsStoreSize +
               ", searchResultsStoreCount=" + searchResultsStoreCount +
               ", streamTaskQueueSizeDocRefs=" + streamTaskQueueSizeDocRefs +
               ", volumesDocRefs=" + volumesDocRefs +
               '}';
    }
}

