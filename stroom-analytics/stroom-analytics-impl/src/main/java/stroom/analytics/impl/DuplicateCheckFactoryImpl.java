package stroom.analytics.impl;

import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.query.language.functions.Values;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.concurrent.Executor;

@Singleton
public class DuplicateCheckFactoryImpl implements DuplicateCheckFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckFactoryImpl.class);

    private final DuplicateCheckStoreConfig analyticResultStoreConfig;
    private final DuplicateCheckStorePool<String, DuplicateCheckStore> pool;

    @Inject
    public DuplicateCheckFactoryImpl(final DuplicateCheckDirs duplicateCheckDirs,
                                     final ByteBufferFactory byteBufferFactory,
                                     final ByteBuffers byteBuffers,
                                     final DuplicateCheckStoreConfig duplicateCheckStoreConfig,
                                     final DuplicateCheckRowSerde duplicateCheckRowSerde,
                                     final Provider<Executor> executorProvider) {
        this.analyticResultStoreConfig = duplicateCheckStoreConfig;

        pool = new DuplicateCheckStorePool<>(k -> new DuplicateCheckStore(
                duplicateCheckDirs,
                byteBufferFactory,
                byteBuffers,
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
        try {
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
                public boolean check(final Values values) {
                    final DuplicateCheckRow duplicateCheckRow = duplicateCheckRowFactory.createDuplicateCheckRow(values);
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
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Error creating duplicate check for {}",
                    RuleUtil.getRuleIdentity(analyticRuleDoc)), e);
            throw e;
        }
    }

    public synchronized DuplicateCheckRows fetchData(final FindDuplicateCheckCriteria criteria) {
        return pool.use(criteria.getAnalyticDocUuid(), store -> store.fetchData(criteria));
    }

    public synchronized Boolean delete(final DeleteDuplicateCheckRequest request) {
        return pool.use(request.getAnalyticDocUuid(), store -> store.delete(request));
    }


    // --------------------------------------------------------------------------------


    private static class NoOpDuplicateCheck implements DuplicateCheck {

        @Override
        public boolean check(final Values values) {
            return true;
        }

        @Override
        public void close() {
            // Ignore
        }
    }
}
