package stroom.statistics.impl.sql.entity;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.security.api.SecurityContext;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StatisticStoreStoreImpl
        extends AbstractDocumentStore<StatisticStoreDoc>
        implements StatisticStoreStore {

    @Inject
    public StatisticStoreStoreImpl(final StoreFactory storeFactory,
                                   final SecurityContext securityContext,
                                   final StatisticStoreSerialiser serialiser) {
        super(storeFactory,
                securityContext,
                serialiser,
                StatisticStoreDoc.TYPE,
                StatisticStoreDoc::builder,
                StatisticStoreDoc::copy);
    }
}
