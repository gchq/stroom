package stroom.search.solr;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;

public interface SolrIndexService {
    DataSource getDataSource(final DocRef docRef);
}
