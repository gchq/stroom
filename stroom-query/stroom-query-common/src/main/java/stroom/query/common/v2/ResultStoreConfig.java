package stroom.query.common.v2;

import stroom.util.io.ByteSize;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ResultStoreConfig extends AbstractResultStoreConfig implements IsStroomConfig {
    public ResultStoreConfig() {
        super(100_000,
                true,
                ByteSize.ofMebibytes(1),
                ByteSize.ofGibibytes(1),
                1000,
                100_000,
                ResultStoreLmdbConfig.builder().localDir("search_results").build(),
                "1000000,100,10,1");
    }

    @JsonCreator
    public ResultStoreConfig(@JsonProperty("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                               @JsonProperty("offHeapResults") final boolean offHeapResults,
                               @JsonProperty("minPayloadSize") final ByteSize minPayloadSize,
                               @JsonProperty("maxPayloadSize") final ByteSize maxPayloadSize,
                               @JsonProperty("maxStringFieldLength") final int maxStringFieldLength,
                               @JsonProperty("valueQueueSize") final int valueQueueSize,
                               @JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig,
                               @JsonProperty("storeSize") final String storeSize) {
        super(maxPutsBeforeCommit,
                offHeapResults,
                minPayloadSize,
                maxPayloadSize,
                maxStringFieldLength,
                valueQueueSize,
                lmdbConfig,
                storeSize);
    }
}
