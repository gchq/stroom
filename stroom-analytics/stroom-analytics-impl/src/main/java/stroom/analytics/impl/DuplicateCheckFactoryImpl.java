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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

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

        pool = new DuplicateCheckStorePool<>(
                k -> new DuplicateCheckStore(
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

            final DuplicateCheck duplicateCheck;
            if (!duplicateNotificationConfig.isRememberNotifications() &&
                !duplicateNotificationConfig.isSuppressDuplicateNotifications()) {
                duplicateCheck = NoOpDuplicateCheck.INSTANCE;
            } else {
                final DuplicateCheckStore store = pool.borrow(analyticRuleDoc.getUuid());
                final DuplicateCheckRowFactory duplicateCheckRowFactory =
                        new DuplicateCheckRowFactory(duplicateNotificationConfig, compiledColumns);
                store.writeColumnNames(duplicateCheckRowFactory.getColumnNames());

                duplicateCheck = buildDuplicateCheck(
                        analyticRuleDoc,
                        duplicateCheckRowFactory,
                        store,
                        duplicateNotificationConfig);
            }
            return duplicateCheck;
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Error creating duplicate check for {}",
                    RuleUtil.getRuleIdentity(analyticRuleDoc)), e);
            throw e;
        }
    }

    @Override
    public Optional<List<String>> fetchColumnNames(final String analyticUuid) {
        return pool.use(analyticUuid, DuplicateCheckStore::fetchColumnNames);
    }

    @NotNull
    private DuplicateCheck buildDuplicateCheck(final AbstractAnalyticRuleDoc analyticRuleDoc,
                                               final DuplicateCheckRowFactory duplicateCheckRowFactory,
                                               final DuplicateCheckStore store,
                                               final DuplicateNotificationConfig duplicateNotificationConfig) {

        // No point doing this test on every row, so bake it in.
        final Predicate<Boolean> sendNotificationCheck = duplicateNotificationConfig.isSuppressDuplicateNotifications()
                ? this::sendNonDuplicatesOnly
                : this::sendAllNotifications;

        return new DuplicateCheck() {
            @Override
            public boolean check(final Values values) {
                final DuplicateCheckRow duplicateCheckRow = duplicateCheckRowFactory.createDuplicateCheckRow(values);
                // Even if we are not suppressing notifications, we need to store the non-dups because
                // isRememberNotifications was true.
                final boolean isNonDuplicate = store.tryInsert(duplicateCheckRow);
                return sendNotificationCheck.test(isNonDuplicate);
            }

            @Override
            public void close() {
                pool.release(analyticRuleDoc.getUuid());
            }
        };
    }

    private boolean sendAllNotifications(final boolean ignored) {
        return true;
    }

    private boolean sendNonDuplicatesOnly(final boolean isNonDuplicate) {
        return isNonDuplicate;
    }

    public synchronized DuplicateCheckRows fetchData(final FindDuplicateCheckCriteria criteria) {
        return pool.use(criteria.getAnalyticDocUuid(), store -> store.fetchData(criteria));
    }

    public synchronized Boolean delete(final DeleteDuplicateCheckRequest request) {
        return pool.use(request.getAnalyticDocUuid(), store -> store.delete(request));
    }


    // --------------------------------------------------------------------------------


    private static class NoOpDuplicateCheck implements DuplicateCheck {

        public static final NoOpDuplicateCheck INSTANCE = new NoOpDuplicateCheck();

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
