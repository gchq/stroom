package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class AnalyticResultStoreConfig extends AbstractResultStoreConfig implements IsStroomConfig {

    public AnalyticResultStoreConfig() {
        this(10_000,
                true,
                ByteSize.ofMebibytes(1),
                ByteSize.ofGibibytes(1),
                1000,
                10_000,
                500_000,
                ResultStoreLmdbConfig.builder().localDir("lmdb/analytic_store").build());
    }

    @JsonCreator
    public AnalyticResultStoreConfig(@JsonProperty("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                                     @JsonProperty("offHeapResults") final boolean offHeapResults,
                                     @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                                     @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                                     @JsonProperty("maxStringFieldLength") final int maxStringFieldLength,
                                     @JsonProperty("valueQueueSize") final int valueQueueSize,
                                     @JsonProperty("maxSortedItems") final int maxSortedItems,
                                     @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig) {
        super(maxPutsBeforeCommit,
                offHeapResults,
                minPayloadSize,
                maxPayloadSize,
                maxStringFieldLength,
                valueQueueSize,
                maxSortedItems,
                lmdbConfig);
    }
}
