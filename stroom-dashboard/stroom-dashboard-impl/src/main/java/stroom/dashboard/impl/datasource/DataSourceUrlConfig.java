package stroom.dashboard.impl.datasource;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class DataSourceUrlConfig extends AbstractConfig implements IsStroomConfig {

    private final String index;
    private final String elasticIndex;
    private final String solrIndex;
    private final String statisticStore;
    private final String searchable;

    public DataSourceUrlConfig() {
        // These paths must match the paths in the respective resource classes
        // Not ideal having them defined in two places
        index = "/api/stroom-index/v2";
        elasticIndex = "/api/stroom-elastic-index/v2";
        solrIndex = "/api/stroom-solr-index/v2";
        statisticStore = "/api/sqlstatistics/v2";
        searchable = "/api/searchable/v2";
    }

    @JsonCreator
    public DataSourceUrlConfig(@JsonProperty("index") final String index,
                               @JsonProperty("elasticIndex") final String elasticIndex,
                               @JsonProperty("solrIndex") final String solrIndex,
                               @JsonProperty("statisticStore") final String statisticStore,
                               @JsonProperty("searchable") final String searchable) {
        this.index = index;
        this.elasticIndex = elasticIndex;
        this.solrIndex = solrIndex;
        this.statisticStore = statisticStore;
        this.searchable = searchable;
    }

    @JsonPropertyDescription("The URL for the Lucene index search service")
    public String getIndex() {
        return index;
    }

    @JsonPropertyDescription("The URL for the Elastic index search service")
    public String getElasticIndex() {
        return elasticIndex;
    }

    @JsonPropertyDescription("The URL for the Solr index search service")
    public String getSolrIndex() {
        return solrIndex;
    }

    @JsonPropertyDescription("The URL for the SQL based statistics service")
    public String getStatisticStore() {
        return statisticStore;
    }

    @JsonPropertyDescription("The URL for other searchable things")
    public String getSearchable() {
        return searchable;
    }
}
