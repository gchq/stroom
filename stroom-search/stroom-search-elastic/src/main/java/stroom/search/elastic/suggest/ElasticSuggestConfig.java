package stroom.search.elastic.suggest;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSuggestConfig extends AbstractConfig implements IsStroomConfig {

    private final Boolean enabled;

    public ElasticSuggestConfig() {
        enabled = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSuggestConfig(@JsonProperty("enabled") final Boolean enabled) {
        this.enabled = enabled;
    }

    @JsonPropertyDescription("Suggest terms in query expressions when using an Elasticsearch index as the data source.")
    public Boolean getEnabled() {
        return enabled;
    }
}
