package stroom.search.elastic.indexing;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticIndexingConfig extends AbstractConfig implements IsStroomConfig {

    private final int maxNestedElementDepth;

    public ElasticIndexingConfig() {
        maxNestedElementDepth = 10;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticIndexingConfig(
            @JsonProperty("maxNestedElementDepth") final int maxNestedElementDepth
    ) {
        this.maxNestedElementDepth = maxNestedElementDepth;
    }

    @JsonPropertyDescription("The maximum allowed depth of JSON XML `array`/`map` elements, that a JSON document " +
            "may have when being sent to Elasticsearch for indexing.")
    public int getMaxNestedElementDepth() {
        return maxNestedElementDepth;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "maxNestedElementDepth=" + maxNestedElementDepth +
                '}';
    }
}
