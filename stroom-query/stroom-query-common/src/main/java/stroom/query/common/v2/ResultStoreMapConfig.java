package stroom.query.common.v2;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ResultStoreMapConfig extends AbstractConfig implements IsStroomConfig {

    private final int trimmedSizeLimit;
    private final int minUntrimmedSize;


    public ResultStoreMapConfig() {
        this(500_000, 100_000);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ResultStoreMapConfig(@JsonProperty("trimmedSizeLimit") final int trimmedSizeLimit,
                                @JsonProperty("minUntrimmedSize") final int minUntrimmedSize) {
        this.trimmedSizeLimit = trimmedSizeLimit;
        this.minUntrimmedSize = minUntrimmedSize;
    }

    @JsonPropertyDescription("The trimmed size of sorted results for on heap result stores.")
    @JsonProperty("trimmedSizeLimit")
    public int getTrimmedSizeLimit() {
        return trimmedSizeLimit;
    }

    @JsonPropertyDescription("The minimum size of sorted results for on heap result stores before they are trimmed.")
    @JsonProperty("minUntrimmedSize")
    public int getMinUntrimmedSize() {
        return minUntrimmedSize;
    }
}
