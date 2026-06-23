package stroom.statistics.impl.sql.entity;

import stroom.docstore.api.AbstractDocumentStore;
import stroom.docstore.api.StoreFactory;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StatisticStoreStoreImpl
        extends AbstractDocumentStore<StatisticStoreDoc>
        implements StatisticStoreStore {

    @Inject
    public StatisticStoreStoreImpl(final StoreFactory storeFactory,
                                   final StatisticStoreSerialiser serialiser) {
        super(storeFactory,
                serialiser,
                StatisticStoreDoc.TYPE,
                StatisticStoreDoc::builder,
                StatisticStoreDoc::copy);
    }
}
