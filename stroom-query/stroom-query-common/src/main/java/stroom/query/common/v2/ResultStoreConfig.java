package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public class ResultStoreConfig extends AbstractConfig implements IsStroomConfig {

    private final int maxPutsBeforeCommit;
    private final boolean offHeapResults;

    private final int valueQueueSize;
    private final ByteSize minValueSize;
    private final ByteSize maxValueSize;
    private final ByteSize minPayloadSize;
    private final ByteSize maxPayloadSize;

    private final ResultStoreLmdbConfig lmdbConfig;

    public ResultStoreConfig() {
        maxPutsBeforeCommit = 100_000;
        offHeapResults = true;

        valueQueueSize = 1_000_000;
        minValueSize = ByteSize.ofKibibytes(1);
        maxValueSize = ByteSize.ofMebibytes(1);
        minPayloadSize = ByteSize.ofMebibytes(1);
        maxPayloadSize = ByteSize.ofGibibytes(1);

        lmdbConfig = new ResultStoreLmdbConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ResultStoreConfig(@JsonProperty("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                             @JsonProperty("offHeapResults") final boolean offHeapResults,
                             @JsonProperty("minValueSize") final ByteSize minValueSize,
                             @JsonProperty("maxValueSize") final ByteSize maxValueSize,
                             @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                             @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                             @JsonProperty("valueQueueSize") final int valueQueueSize,
                             @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.offHeapResults = offHeapResults;
        this.minValueSize = minValueSize;
        this.maxValueSize = maxValueSize;
        this.minPayloadSize = minPayloadSize;
        this.maxPayloadSize = maxPayloadSize;
        this.valueQueueSize = valueQueueSize;
        this.lmdbConfig = lmdbConfig;
    }

    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @JsonPropertyDescription("Should search results be stored off heap (experimental feature).")
    @JsonProperty("offHeapResults")
    public boolean isOffHeapResults() {
        return offHeapResults;
    }

    @JsonPropertyDescription("The minimum byte size of a value byte buffer.")
    public ByteSize getMinValueSize() {
        return minValueSize;
    }

    @JsonPropertyDescription("The maximum byte size of a value byte buffer.")
    public ByteSize getMaxValueSize() {
        return maxValueSize;
    }

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    public ByteSize getMinPayloadSize() {
        return minPayloadSize;
    }

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    public ByteSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    @JsonPropertyDescription("The size of the value queue.")
    @JsonProperty("valueQueueSize")
    public int getValueQueueSize() {
        return valueQueueSize;
    }

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @Override
    public String toString() {
        return "ResultStoreConfig{" +
                "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", offHeapResults=" + offHeapResults +
                ", minValueSize=" + minValueSize +
                ", maxValueSize=" + maxValueSize +
                ", minPayloadSize=" + minPayloadSize +
                ", maxPayloadSize=" + maxPayloadSize +
                ", lmdbConfig=" + lmdbConfig +
                '}';
    }
}
