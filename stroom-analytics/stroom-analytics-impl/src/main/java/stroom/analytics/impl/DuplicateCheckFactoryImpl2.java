package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.WriteTxn;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.LmdbKV;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;

@Singleton
public class DuplicateCheckFactoryImpl2 implements DuplicateCheckFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckFactoryImpl2.class);

    private final DuplicateCheckDirs duplicateCheckDirs;
    private final ByteBufferFactory byteBufferFactory;
    private final AnalyticResultStoreConfig analyticResultStoreConfig;

    @Inject
    public DuplicateCheckFactoryImpl2(final DuplicateCheckDirs duplicateCheckDirs,
                                      final ByteBufferFactory byteBufferFactory,
                                      final AnalyticResultStoreConfig analyticResultStoreConfig) {
        this.duplicateCheckDirs = duplicateCheckDirs;
        this.byteBufferFactory = byteBufferFactory;
        this.analyticResultStoreConfig = analyticResultStoreConfig;
    }

    @Override
    public DuplicateCheck create(final AnalyticRuleDoc analyticRuleDoc, final CompiledColumns compiledColumns) {
        return new DuplicateCheckImpl(
                duplicateCheckDirs,
                byteBufferFactory,
                analyticResultStoreConfig,
                analyticRuleDoc,
                compiledColumns);
    }


    // --------------------------------------------------------------------------------


    private static class DuplicateCheckImpl implements DuplicateCheck {

        private final Thread thread;
        private final LmdbEnv lmdbEnv;
        private final LmdbDb db;
        private final ByteBufferFactory byteBufferFactory;
        private final DuplicateKeyFactory2 duplicateKeyFactory;
        private final WriteTxn writeTxn;
        private final int maxPutsBeforeCommit = 100;
        private long uncommittedCount = 0;

        public DuplicateCheckImpl(final DuplicateCheckDirs duplicateCheckDirs,
                                  final ByteBufferFactory byteBufferFactory,
                                  final AnalyticResultStoreConfig analyticResultStoreConfig,
                                  final AnalyticRuleDoc analyticRuleDoc,
                                  final CompiledColumns compiledColumns) {
            thread = Thread.currentThread();

            this.duplicateKeyFactory = new DuplicateKeyFactory2(
                    byteBufferFactory,
                    compiledColumns);
            final LmdbEnvDir lmdbEnvDir = duplicateCheckDirs.getDir(analyticRuleDoc);
            this.lmdbEnv = LmdbEnv
                    .builder()
                    .config(analyticResultStoreConfig.getLmdbConfig())
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbs(1)
                    .maxReaders(1)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();

            this.db = lmdbEnv.openDb("duplicate-check", DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
            this.byteBufferFactory = byteBufferFactory;
            writeTxn = lmdbEnv.writeTxn();
        }

        @Override
        public boolean check(final Row row) {
            if (thread != Thread.currentThread()) {
                throw new RuntimeException("Unexpected thread used. This will break LMDB.");
            }

            final LmdbKV lmdbKV = duplicateKeyFactory.createRow(row);
            boolean result = false;

            try {
                try {
                    result = db.put(writeTxn,
                            lmdbKV.getRowKey(),
                            lmdbKV.getRowValue(),
                            PutFlags.MDB_NODUPDATA);
                    uncommittedCount++;
                } finally {
                    byteBufferFactory.release(lmdbKV.getRowKey());
                    byteBufferFactory.release(lmdbKV.getRowValue());
                }

                if (uncommittedCount > 0) {
                    final long count = uncommittedCount;
                    if (count >= maxPutsBeforeCommit) {
                        // Commit
                        LOGGER.trace(() -> "Committing for max puts " + maxPutsBeforeCommit);
                        writeTxn.commit();
                        uncommittedCount = 0;
                    }
                }
            } catch (final Throwable e) {
                LOGGER.error(e::getMessage, e);
            }

            return result;
        }

        @Override
        public void close() {
            if (thread != Thread.currentThread()) {
                throw new RuntimeException("Unexpected thread used. This will break LMDB.");
            }

            LOGGER.debug(() -> "close called");
            LOGGER.trace(() -> "close()", new RuntimeException("close"));
            try {
                // Final commit.
                writeTxn.commit();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }

            try {
                lmdbEnv.close();
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }
}
