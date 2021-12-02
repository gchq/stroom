package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.validation.constraints.Min;

public class ResultStoreConfig extends AbstractConfig {

    private final int maxPutsBeforeCommit;
    private final boolean offHeapResults;
    private final ByteSize payloadLimit;

    private final ByteSize minValueSize;
    private final ByteSize maxValueSize;
    private final ByteSize minPayloadSize;
    private final ByteSize maxPayloadSize;

    private final ResultStoreLmdbConfig lmdbConfig;

    public ResultStoreConfig() {
        maxPutsBeforeCommit = 100_000;
        offHeapResults = true;
        payloadLimit = ByteSize.ofMebibytes(0);

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
                             @JsonProperty("payloadLimit") final ByteSize payloadLimit,
                             @JsonProperty("minValueSize") final ByteSize minValueSize,
                             @JsonProperty("maxValueSize") final ByteSize maxValueSize,
                             @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                             @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                             @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.offHeapResults = offHeapResults;
        this.payloadLimit = payloadLimit;
        this.minValueSize = minValueSize;
        this.maxValueSize = maxValueSize;
        this.minPayloadSize = minPayloadSize;
        this.maxPayloadSize = maxPayloadSize;
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

    @JsonPropertyDescription("Do we want to limit the size of payloads (0 by default means no limit).")
    @JsonProperty("payloadLimit")
    public ByteSize getPayloadLimit() {
        return payloadLimit;
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

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @Override
    public String toString() {
        return "ResultStoreConfig{" +
                "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", offHeapResults=" + offHeapResults +
                ", payloadLimit=" + payloadLimit +
                ", minValueSize=" + minValueSize +
                ", maxValueSize=" + maxValueSize +
                ", minPayloadSize=" + minPayloadSize +
                ", maxPayloadSize=" + maxPayloadSize +
                ", lmdbConfig=" + lmdbConfig +
                '}';
    }
}
