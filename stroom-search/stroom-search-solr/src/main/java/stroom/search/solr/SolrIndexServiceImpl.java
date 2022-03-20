package stroom.search.solr;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.search.solr.search.SolrSearchStoreFactory;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;

public class SolrIndexServiceImpl implements SolrIndexService {

    private final SolrIndexStore solrIndexStore;
    private final SecurityContext securityContext;
    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SolrSearchStoreFactory storeFactory;

    @Inject
    SolrIndexServiceImpl(final SolrIndexStore solrIndexStore,
                         final SecurityContext securityContext,
                         final SearchResponseCreatorManager searchResponseCreatorManager,
                         final SolrSearchStoreFactory storeFactory) {
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.storeFactory = storeFactory;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
            return new DataSource(SolrIndexDataSourceFieldUtil.getDataSourceFields(index));
        });
    }

    @Override
    public SearchResponse search(final SearchRequest request) {
        return searchResponseCreatorManager.search(storeFactory, request);
    }

    @Override
    public Boolean keepAlive(final QueryKey queryKey) {
        return searchResponseCreatorManager.keepAlive(queryKey);
    }

    @Override
    public Boolean destroy(final QueryKey queryKey) {
        return searchResponseCreatorManager.remove(queryKey);
    }

    @Override
    public String getType() {
        return SolrIndexDoc.DOCUMENT_TYPE;
    }
}
