package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.LmdbEnv2;
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
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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


    private static class DuplicateCheckImpl implements DuplicateCheck {

        private final Thread thread;
        private final LmdbEnv2 lmdbEnv;
        private final Dbi<ByteBuffer> dbi;
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
            this.lmdbEnv = LmdbEnv2
                    .builder()
                    .config(analyticResultStoreConfig.getLmdbConfig())
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbCount(1)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();

            this.dbi = lmdbEnv.openDbi("duplicate-check", DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
            this.byteBufferFactory = byteBufferFactory;
            writeTxn = lmdbEnv.txnWrite();
        }

        private List<String> getDBNames() {
            final List<String> names = new ArrayList<>();
            try {
                final Dbi<ByteBuffer> root = lmdbEnv.openDbi(null);
                try {
                    try (final Txn<ByteBuffer> txn = lmdbEnv.txnRead()) {
                        try (final CursorIterable<ByteBuffer> cursorIterable = root.iterate(txn)) {
                            for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                                final String name = new String(
                                        ByteBufferUtils.toBytes(keyVal.key()),
                                        StandardCharsets.UTF_8);
                                names.add(name);
                            }
                        }
                    }
                } finally {
                    root.close();
                }
            } catch (final Exception e) {
                LOGGER.error(e::getMessage, e);
            }
            return names;
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
                    result = dbi.put(writeTxn.get(),
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
                dbi.close();
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