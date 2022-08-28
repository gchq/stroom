package stroom.search.elastic.search;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class ElasticSearchConfig extends AbstractConfig implements IsStroomConfig {

    private final StroomDuration scrollDuration;
    private final String storeSize;
    private final Boolean useSuggesters;

    public ElasticSearchConfig() {
        scrollDuration = StroomDuration.ofMinutes(1);
        storeSize = "1000000,100,10,1";
        useSuggesters = true;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ElasticSearchConfig(@JsonProperty("scrollDuration") final StroomDuration scrollDuration,
                               @JsonProperty("storeSize") final String storeSize,
                               @JsonProperty("useSuggesters") final Boolean useSuggesters) {
        this.scrollDuration = scrollDuration;
        this.storeSize = storeSize;
        this.useSuggesters = useSuggesters;
    }

    @JsonPropertyDescription("Amount of time to allow an Elasticsearch scroll request to continue before aborting.")
    public StroomDuration getScrollDuration() {
        return scrollDuration;
    }

    @JsonPropertyDescription("The maximum number of search results to keep in memory at each level.")
    public String getStoreSize() {
        return storeSize;
    }

    @JsonPropertyDescription("Suggest terms in query expressions when using an Elasticsearch index as the data source.")
    @RequiresRestart(RestartScope.UI)
    public Boolean getUseSuggesters() {
        return useSuggesters;
    }

    @Override
    public String toString() {
        return "ElasticSearchConfig{" +
                "scrollDuration='" + scrollDuration + "'" +
                ", storeSize=" + storeSize +
                ", useSuggesters=" + useSuggesters +
                '}';
    }
}
