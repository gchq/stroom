package stroom.search.solr;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SolrIndexServiceImpl implements SolrIndexService {

    private final SolrIndexStore solrIndexStore;
    private final SecurityContext securityContext;

    @Inject
    SolrIndexServiceImpl(final SolrIndexStore solrIndexStore, final SecurityContext securityContext){
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
    }

    @Override
    public DataSource getDataSource(final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
            return new DataSource(SolrIndexDataSourceFieldUtil.getDataSourceFields(index));
        });
    }
}
