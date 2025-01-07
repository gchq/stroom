package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.DuplicateCheckStoreConfig;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;

@Singleton
public class DuplicateCheckFactoryImpl implements DuplicateCheckFactory {

    private final ByteBufferFactory byteBufferFactory;
    private final DuplicateCheckStoreConfig analyticResultStoreConfig;
    private final DuplicateCheckStorePool<String, DuplicateCheckStore> pool;

    @Inject
    public DuplicateCheckFactoryImpl(final DuplicateCheckDirs duplicateCheckDirs,
                                     final ByteBufferFactory byteBufferFactory,
                                     final DuplicateCheckStoreConfig duplicateCheckStoreConfig,
                                     final DuplicateCheckRowSerde duplicateCheckRowSerde,
                                     final Provider<Executor> executorProvider) {
        this.byteBufferFactory = byteBufferFactory;
        this.analyticResultStoreConfig = duplicateCheckStoreConfig;

        pool = new DuplicateCheckStorePool<>(k -> new DuplicateCheckStore(
                duplicateCheckDirs,
                byteBufferFactory,
                analyticResultStoreConfig,
                duplicateCheckRowSerde,
                executorProvider,
                k),
                null,
                DuplicateCheckStore::flush,
                DuplicateCheckStore::close);
    }

    @Override
    public DuplicateCheck create(final AbstractAnalyticRuleDoc analyticRuleDoc,
                                 final CompiledColumns compiledColumns) {
        final DuplicateNotificationConfig duplicateNotificationConfig =
                analyticRuleDoc.getDuplicateNotificationConfig();
        if (!duplicateNotificationConfig.isRememberNotifications() &&
            !duplicateNotificationConfig.isSuppressDuplicateNotifications()) {
            return new NoOpDuplicateCheck();
        }

        final DuplicateCheckStore store = pool.borrow(analyticRuleDoc.getUuid());
        final DuplicateCheckRowFactory duplicateCheckRowFactory =
                new DuplicateCheckRowFactory(duplicateNotificationConfig, compiledColumns);
        store.writeColumnNames(duplicateCheckRowFactory.getColumnNames());

        return new DuplicateCheck() {
            @Override
            public boolean check(final Row row) {
                final DuplicateCheckRow duplicateCheckRow = duplicateCheckRowFactory.createDuplicateCheckRow(row);
                final boolean success = store.tryInsert(duplicateCheckRow);
                if (duplicateNotificationConfig.isSuppressDuplicateNotifications()) {
                    return success;
                } else {
                    return true;
                }
            }

            @Override
            public void close() {
                pool.release(analyticRuleDoc.getUuid());
            }
        };
    }

    public synchronized DuplicateCheckRows fetchData(final FindDuplicateCheckCriteria criteria) {
        return pool.use(criteria.getAnalyticDocUuid(), store -> store.fetchData(criteria));
    }

    public synchronized Boolean delete(final DeleteDuplicateCheckRequest request) {
        return pool.use(request.getAnalyticDocUuid(), store -> store.delete(request, byteBufferFactory));
    }

    private static class NoOpDuplicateCheck implements DuplicateCheck {

        @Override
        public boolean check(final Row row) {
            return true;
        }

        @Override
        public void close() {
            // Ignore
        }
    }
}
