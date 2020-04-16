package stroom.dashboard.impl.datasource;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class DataSourceUrlConfig extends AbstractConfig {
    private String index = "/api/stroom-index/v2";
    private String solrIndex = "/api/stroom-solr-index/v2";
    private String statisticStore = "/api/sqlstatistics/v2";
    private String searchable = "/api/searchable/v2";

    @JsonPropertyDescription("The URL for the Lucene index search service")
    public String getIndex() {
        return index;
    }

    public void setIndex(final String index) {
        this.index = index;
    }

    @JsonPropertyDescription("The URL for the Solr index search service")
    public String getSolrIndex() {
        return solrIndex;
    }

    public void setSolrIndex(final String solrIndex) {
        this.solrIndex = solrIndex;
    }

    @JsonPropertyDescription("The URL for the SQL based statistics service")
    public String getStatisticStore() {
        return statisticStore;
    }

    public void setStatisticStore(final String statisticStore) {
        this.statisticStore = statisticStore;
    }

    @JsonPropertyDescription("The URL for other searchable things")
    public String getSearchable() {
        return searchable;
    }

    public void setSearchable(final String searchable) {
        this.searchable = searchable;
    }
}
