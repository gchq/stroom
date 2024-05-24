package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbWriter;
import stroom.lmdb2.WriteTxn;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.query.common.v2.LmdbKV;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Provider;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

class DuplicateCheckStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckStore.class);

    private final ByteBufferFactory byteBufferFactory;
    private final DuplicateCheckRowSerde duplicateCheckRowSerde;
    private final LmdbEnv lmdbEnv;
    private final LmdbDb db;
    private final LmdbWriter writer;
    private final int maxPutsBeforeCommit = 100;
    private long uncommittedCount = 0;

    DuplicateCheckStore(final DuplicateCheckDirs duplicateCheckDirs,
                        final ByteBufferFactory byteBufferFactory,
                        final DuplicateCheckStoreConfig duplicateCheckStoreConfig,
                        final DuplicateCheckRowSerde duplicateCheckRowSerde,
                        final Provider<Executor> executorProvider,
                        final String analyticRuleUUID) {
        this.byteBufferFactory = byteBufferFactory;
        this.duplicateCheckRowSerde = duplicateCheckRowSerde;
        final LmdbEnvDir lmdbEnvDir = duplicateCheckDirs.getDir(analyticRuleUUID);
        this.lmdbEnv = LmdbEnv
                .builder()
                .config(duplicateCheckStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir)
                .maxDbs(1)
                .maxReaders(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        this.db = lmdbEnv.openDb("duplicate-check", DbiFlags.MDB_CREATE, DbiFlags.MDB_DUPSORT);
        writer = new LmdbWriter(executorProvider, lmdbEnv);
    }

    synchronized boolean tryInsert(final DuplicateCheckRow duplicateCheckRow) {
        final LmdbKV lmdbKV = duplicateCheckRowSerde.createLmdbKV(duplicateCheckRow);
        final AtomicBoolean res = new AtomicBoolean();

        writer.write(writeTxn -> {
            try {
                try {
                    boolean result = db.put(writeTxn,
                            lmdbKV.getRowKey(),
                            lmdbKV.getRowValue(),
                            PutFlags.MDB_NODUPDATA);
                    res.set(result);
                    if (result) {
                        LOGGER.debug(() -> "New row (row=" +
                                duplicateCheckRow +
                                ", lmdbKv=" +
                                lmdbKV +
                                ", lmdbEnvDir=" +
                                lmdbEnv.getDir() +
                                ")");
                        uncommittedCount++;
                    } else {
                        LOGGER.debug(() -> "Duplicate row (row=" +
                                duplicateCheckRow +
                                ", lmdbKv=" +
                                lmdbKV +
                                ", lmdbEnvDir=" +
                                lmdbEnv.getDir() +
                                ")");
                    }
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
        });

        return res.get();
    }

    synchronized void flush() {
        writer.flush();

        LOGGER.debug(() -> "flush called");
        LOGGER.trace(() -> "flush()", new RuntimeException("flush"));
    }

    synchronized void close() {
        writer.close();

        LOGGER.debug(() -> "close called");
        LOGGER.trace(() -> "close()", new RuntimeException("close"));
        try {
            lmdbEnv.close();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public synchronized ResultPage<DuplicateCheckRow> fetchData(final FindDuplicateCheckCriteria criteria) {
        final List<DuplicateCheckRow> results = new ArrayList<>();

        lmdbEnv.read(txn -> {
            final PageRequest pageRequest = criteria.getPageRequest();
            db.iterate(txn, cursorIterable -> {
                long count = 0;

                for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                    if (count >= pageRequest.getOffset()) {
                        final ByteBuffer keyBuffer = kv.key();
                        final ByteBuffer valBuffer = kv.val();
                        results.add(duplicateCheckRowSerde.createDuplicateCheckRow(valBuffer));
                    }
                    count++;
                    if (results.size() >= pageRequest.getLength()) {
                        break;
                    }
                }
            });
        });

        return ResultPage.createCriterialBasedList(results, criteria);
    }

    public boolean delete(final DeleteDuplicateCheckRequest request,
                          final ByteBufferFactory byteBufferFactory) {
        request.getRows().forEach(row -> {
            final LmdbKV lmdbKV = duplicateCheckRowSerde.createLmdbKV(row);
            try {
                delete(lmdbKV);
            } finally {
                byteBufferFactory.release(lmdbKV.getRowKey());
                byteBufferFactory.release(lmdbKV.getRowValue());
            }
        });
        commit();

        return true;
    }

    private synchronized void delete(LmdbKV lmdbKV) {
        writer.write(writeTxn -> db.delete(writeTxn,
                lmdbKV.getRowKey(),
                lmdbKV.getRowValue()));
    }

    private synchronized void commit() {
        writer.write(WriteTxn::commit);
    }
}
