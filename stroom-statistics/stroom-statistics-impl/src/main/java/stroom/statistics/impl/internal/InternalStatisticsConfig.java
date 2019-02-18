package stroom.statistics.impl.internal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import stroom.docref.DocRef;
import stroom.statistics.api.InternalStatisticKey;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class InternalStatisticsConfig implements IsConfig {

    private static final Map<InternalStatisticKey, Function<InternalStatisticsConfig, List<DocRef>>>
            KEY_TO_DOC_REFS_GETTER_MAP = new EnumMap<>(InternalStatisticKey.class);

    static {
        populateGetterMap();
    }

    private static void populateGetterMap() {
        // A set of mappings from the internal stat key to the getter to get the docRefs for it.
        // Each InternalStatisticKey must have a corresponding getter in this class
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
}

