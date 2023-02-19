package stroom.search.elastic.search;

import stroom.search.elastic.suggest.ElasticSuggestConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSearchConfig extends AbstractConfig implements IsStroomConfig {

    private final boolean highlight;
    private final StroomDuration scrollDuration;
    private final String storeSize;
    private final ElasticSuggestConfig suggestConfig;

    public ElasticSearchConfig() {
        highlight = true;
        scrollDuration = StroomDuration.ofMinutes(1);
        storeSize = "1000000,100,10,1";
        suggestConfig = new ElasticSuggestConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSearchConfig(@JsonProperty("highlight") final boolean highlight,
                               @JsonProperty("scrollDuration") final StroomDuration scrollDuration,
                               @JsonProperty("storeSize") final String storeSize,
                               @JsonProperty("suggestions") final ElasticSuggestConfig suggestConfig) {
        this.highlight = highlight;
        this.scrollDuration = scrollDuration;
        this.storeSize = storeSize;
        this.suggestConfig = suggestConfig;
    }

    @JsonPropertyDescription("Highlight matched terms in the source document.")
    public boolean getHighlight() {
        return highlight;
    }

    @JsonPropertyDescription("Amount of time to allow an Elasticsearch scroll request to continue before aborting.")
    public StroomDuration getScrollDuration() {
        return scrollDuration;
    }

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    @JsonProperty("suggestions")
    public ElasticSuggestConfig getSuggestConfig() {
        return suggestConfig;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "highlight=" + highlight +
                ", scrollDuration='" + scrollDuration + "'" +
                ", storeSize=" + storeSize +
                ", suggestConfig=" + suggestConfig +
                '}';
    }
}
