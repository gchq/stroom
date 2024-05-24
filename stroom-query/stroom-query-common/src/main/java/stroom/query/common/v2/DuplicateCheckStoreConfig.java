package stroom.query.common.v2;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class DuplicateCheckStoreConfig extends AbstractConfig implements IsStroomConfig {

    private final ResultStoreLmdbConfig lmdbConfig;

    public DuplicateCheckStoreConfig() {
        this(ResultStoreLmdbConfig.builder().localDir("lmdb/duplicate_check").build());
    }

    @JsonCreator
    public DuplicateCheckStoreConfig(@JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig) {
        this.lmdbConfig = lmdbConfig;
    }

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }
}
