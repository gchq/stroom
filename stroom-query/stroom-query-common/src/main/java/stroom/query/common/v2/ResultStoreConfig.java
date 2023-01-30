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
    private final ByteSize minPayloadSize;
    private final ByteSize maxPayloadSize;
    private final int maxStringFieldLength;

    private final ResultStoreLmdbConfig lmdbConfig;
    private final String storeSize;

    public ResultStoreConfig() {
        maxPutsBeforeCommit = 100_000;
        offHeapResults = true;

        valueQueueSize = 1_000_000;
        minPayloadSize = ByteSize.ofMebibytes(1);
        maxPayloadSize = ByteSize.ofGibibytes(1);
        maxStringFieldLength = 1000;

        lmdbConfig = new ResultStoreLmdbConfig();
        storeSize = "1000000,100,10,1";
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ResultStoreConfig(@JsonProperty("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                             @JsonProperty("offHeapResults") final boolean offHeapResults,
                             @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                             @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                             @JsonProperty("maxStringFieldLength") final int maxStringFieldLength,
                             @JsonProperty("valueQueueSize") final int valueQueueSize,
                             @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig,
                             @JsonProperty("storeSize") final String storeSize) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.offHeapResults = offHeapResults;
        this.minPayloadSize = minPayloadSize;
        this.maxPayloadSize = maxPayloadSize;
        this.maxStringFieldLength = maxStringFieldLength;
        this.valueQueueSize = valueQueueSize;
        this.lmdbConfig = lmdbConfig;
        this.storeSize = storeSize;
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

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    public ByteSize getMinPayloadSize() {
        return minPayloadSize;
    }

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    public ByteSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    @JsonPropertyDescription("The maximum length of a string field value. Longer strings will be truncated.")
    public int getMaxStringFieldLength() {
        return maxStringFieldLength;
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

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    @Override
    public String toString() {
        return "ResultStoreConfig{" +
                "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", offHeapResults=" + offHeapResults +
                ", valueQueueSize=" + valueQueueSize +
                ", minPayloadSize=" + minPayloadSize +
                ", maxPayloadSize=" + maxPayloadSize +
                ", maxStringFieldLength=" + maxStringFieldLength +
                ", lmdbConfig=" + lmdbConfig +
                ", storeSize='" + storeSize + '\'' +
                '}';
    }
}
