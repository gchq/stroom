package stroom.query.common.v2;

import stroom.util.io.ByteSize;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.Min;

@JsonPropertyOrder(alphabetic = true)
public interface ResultStoreConfig {

    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    int getMaxPutsBeforeCommit();

    @JsonPropertyDescription("Should search results be stored off heap (experimental feature).")
    @JsonProperty("offHeapResults")
    boolean isOffHeapResults();

    @JsonPropertyDescription("The minimum byte size of a payload buffer.")
    ByteSize getMinPayloadSize();

    @JsonPropertyDescription("The maximum byte size of a payload buffer.")
    ByteSize getMaxPayloadSize();

    @JsonPropertyDescription("The maximum length of a string field value. Longer strings will be truncated.")
    int getMaxStringFieldLength();

    @JsonPropertyDescription("The size of the value queue.")
    @JsonProperty("valueQueueSize")
    int getValueQueueSize();

    @JsonProperty("lmdb")
    ResultStoreLmdbConfig getLmdbConfig();

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    String getStoreSize();
}
