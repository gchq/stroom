package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
public class ResultStoreConfig extends AbstractConfig {

    private int maxPutsBeforeCommit = 100_000;
    private boolean offHeapResults = true;
    private ByteSize payloadLimit = ByteSize.ofMebibytes(0);

    private ByteSize minValueSize = ByteSize.ofKibibytes(1);
    private ByteSize maxValueSize = ByteSize.ofMebibytes(1);
    private ByteSize minPayloadSize = ByteSize.ofMebibytes(1);
    private ByteSize maxPayloadSize = ByteSize.ofGibibytes(1);

    private ResultStoreLmdbConfig lmdbConfig = new ResultStoreLmdbConfig();

    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @SuppressWarnings("unused")
    public void setMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
    }

    @JsonPropertyDescription("Should search results be stored off heap (experimental feature).")
    @JsonProperty("offHeapResults")
    public boolean isOffHeapResults() {
        return offHeapResults;
    }

    @SuppressWarnings("unused")
    public void setOffHeapResults(final boolean offHeapResults) {
        this.offHeapResults = offHeapResults;
    }

    @JsonPropertyDescription("Do we want to limit the size of payloads (0 by default means no limit).")
    @JsonProperty("payloadLimit")
    public ByteSize getPayloadLimit() {
        return payloadLimit;
    }

    @SuppressWarnings("unused")
    public void setPayloadLimit(final ByteSize payloadLimit) {
        this.payloadLimit = payloadLimit;
    }

    @JsonPropertyDescription("The minimum byte size of a value byte buffer.")
    public ByteSize getMinValueSize() {
        return minValueSize;
    }

    @SuppressWarnings("unused")
    public void setMinValueSize(final ByteSize minValueSize) {
        this.minValueSize = minValueSize;
    }

    @JsonPropertyDescription("The maximum byte size of a value byte buffer.")
    public ByteSize getMaxValueSize() {
        return maxValueSize;
    }

    @SuppressWarnings("unused")
    public void setMaxValueSize(final ByteSize maxValueSize) {
        this.maxValueSize = maxValueSize;
    }

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    public ByteSize getMinPayloadSize() {
        return minPayloadSize;
    }

    @SuppressWarnings("unused")
    public void setMinPayloadSize(final ByteSize minPayloadSize) {
        this.minPayloadSize = minPayloadSize;
    }

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    public ByteSize getMaxPayloadSize() {
        return maxPayloadSize;
    }

    @SuppressWarnings("unused")
    public void setMaxPayloadSize(final ByteSize maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @SuppressWarnings("unused")
    public void setLmdbConfig(final ResultStoreLmdbConfig lmdbConfig) {
        this.lmdbConfig = lmdbConfig;
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
