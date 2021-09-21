package stroom.pipeline.refdata.store;


import stroom.util.date.DateUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Map;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ProcessingInfoResponse {

    @JsonProperty
    private final RefStreamDefinition refStreamDefinition;
    @JsonProperty
    private final Map<String, EntryCounts> maps;
    @JsonProperty
    private final String createTime;
    @JsonProperty
    private final String lastAccessedTime;
    @JsonProperty
    private final String effectiveTime;
    @JsonProperty
    private final ProcessingState processingState;

    @JsonCreator
    public ProcessingInfoResponse(
            @JsonProperty("refStreamDefinition") final RefStreamDefinition refStreamDefinition,
            @JsonProperty("maps") final Map<String, EntryCounts> maps,
            @JsonProperty("createTime") final String createTime,
            @JsonProperty("lastAccessedTime") final String lastAccessedTime,
            @JsonProperty("effectiveTime") final String effectiveTime,
            @JsonProperty("processingState") final ProcessingState processingState) {
        this.refStreamDefinition = Objects.requireNonNull(refStreamDefinition);
        this.maps = Objects.requireNonNull(maps);
        this.createTime = Objects.requireNonNull(createTime);
        this.lastAccessedTime = Objects.requireNonNull(lastAccessedTime);
        this.effectiveTime = Objects.requireNonNull(effectiveTime);
        this.processingState = Objects.requireNonNull(processingState);
    }

    @JsonIgnore
    public ProcessingInfoResponse(final RefStreamDefinition refStreamDefinition,
                                  final RefDataProcessingInfo refDataProcessingInfo,
                                  final Map<String, EntryCounts> maps) {

        this(
                refStreamDefinition,
                maps,
                DateUtil.createNormalDateTimeString(refDataProcessingInfo.getCreateTimeEpochMs()),
                DateUtil.createNormalDateTimeString(refDataProcessingInfo.getLastAccessedTimeEpochMs()),
                DateUtil.createNormalDateTimeString(refDataProcessingInfo.getEffectiveTimeEpochMs()),
                refDataProcessingInfo.getProcessingState());
    }

    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    public Map<String, EntryCounts> getMaps() {
        return maps;
    }

    public String getCreateTime() {
        return createTime;
    }

    public String getLastAccessedTime() {
        return lastAccessedTime;
    }

    public String getEffectiveTime() {
        return effectiveTime;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessingInfoResponse that = (ProcessingInfoResponse) o;
        return refStreamDefinition.equals(that.refStreamDefinition)
                && maps.equals(that.maps)
                && createTime.equals(that.createTime)
                && lastAccessedTime.equals(that.lastAccessedTime)
                && effectiveTime.equals(that.effectiveTime)
                && processingState == that.processingState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(refStreamDefinition,
                maps,
                createTime,
                lastAccessedTime,
                effectiveTime,
                processingState);
    }

    @Override
    public String toString() {
        return "ProcessingInfoResponse{" +
                "refStreamDefinition=" + refStreamDefinition +
                ", mapNames=" + maps +
                ", createTime='" + createTime + '\'' +
                ", lastAccessedTime='" + lastAccessedTime + '\'' +
                ", effectiveTime='" + effectiveTime + '\'' +
                ", processingState=" + processingState +
                '}';
    }

    public static class EntryCounts {

        @JsonProperty
        private final long keyValueCount;
        @JsonProperty
        private final long rangeValueCount;

        @JsonCreator
        public EntryCounts(@JsonProperty("keyValueCount") final long keyValueCount,
                           @JsonProperty("rangeValueCount") final long rangeValueCount) {
            this.keyValueCount = keyValueCount;
            this.rangeValueCount = rangeValueCount;
        }

        public long getKeyValueCount() {
            return keyValueCount;
        }

        public long getRangeValueCount() {
            return rangeValueCount;
        }

        @Override
        public String toString() {
            return "EntryCounts{" +
                    "keyValueCount=" + keyValueCount +
                    ", rangeValueCount=" + rangeValueCount +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final EntryCounts that = (EntryCounts) o;
            return keyValueCount == that.keyValueCount && rangeValueCount == that.rangeValueCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyValueCount, rangeValueCount);
        }
    }
}
