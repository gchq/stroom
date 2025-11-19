package stroom.search.elastic.search;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import java.util.HashMap;
import java.util.Map;

public class ElasticQueryParams {

    private Query query;
    private final Map<String, String> knnFieldQueries;

    public ElasticQueryParams() {
        this.knnFieldQueries = new HashMap<>();
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(final Query query) {
        this.query = query;
    }

    public Map<String, String> getKnnFieldQueries() {
        return knnFieldQueries;
    }

    public void addKnnFieldQuery(final String fieldName, final String query) {
        this.knnFieldQueries.put(fieldName, query);
    }
}
