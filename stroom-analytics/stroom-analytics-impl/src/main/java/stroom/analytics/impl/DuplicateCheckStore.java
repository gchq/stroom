package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbWriter;
import stroom.lmdb2.ReadTxn;
import stroom.lmdb2.WriteTxn;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.query.common.v2.LmdbKV;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import jakarta.inject.Provider;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class DuplicateCheckStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckStore.class);

    private static final int DB_STATE_KEY_LENGTH = 1;
    public static final ByteBuffer DB_STATE_KEY = ByteBuffer.allocateDirect(DB_STATE_KEY_LENGTH);

    static {
        DB_STATE_KEY.put((byte) -1);
        DB_STATE_KEY.flip();
    }

    private final ByteBufferFactory byteBufferFactory;
    private final DuplicateCheckRowSerde duplicateCheckRowSerde;
    private final LmdbEnv lmdbEnv;
    private final LmdbDb db;
    private final LmdbDb columnNamesDb;
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
                .maxDbs(2)
                .maxReaders(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        this.db = lmdbEnv.openDb("duplicate-check", DbiFlags.MDB_CREATE);
        this.columnNamesDb = lmdbEnv.openDb("column-names", DbiFlags.MDB_CREATE);
        writer = new LmdbWriter(executorProvider, lmdbEnv);
    }

    synchronized void writeColumnNames(final List<String> columnNames) {
        writer.write(writeTxn -> writeColumnNames(writeTxn, columnNames));
    }

    synchronized boolean tryInsert(final DuplicateCheckRow duplicateCheckRow) {
        final LmdbKV lmdbKV = duplicateCheckRowSerde.createLmdbKV(duplicateCheckRow);
        final AtomicBoolean res = new AtomicBoolean();

        writer.write(writeTxn -> {
            try {
                try {
                    final boolean didInsert = tryInsert(duplicateCheckRow, writeTxn, lmdbKV);
                    res.set(didInsert);
                } finally {
                    releaseLmdbKv(lmdbKV);
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

    private boolean tryInsert(final DuplicateCheckRow duplicateCheckRow,
                              final WriteTxn writeTxn,
                              final LmdbKV lmdbKV) {
        // try immediate insert first.
        boolean didPut = db.put(writeTxn, lmdbKV.getRowKey(), lmdbKV.getRowValue(), PutFlags.MDB_NOOVERWRITE);
        if (didPut) {
            return true;
        }

        // If we didn't put then check to see if this was because this is an exact duplicate.
        final ByteBuffer valueBuffer = db.get(writeTxn, lmdbKV.getRowKey());
        if (ByteBufferUtils.compare(lmdbKV.getRowValue(), valueBuffer) == 0) {
            // Found our value, job done
            LOGGER.debug("Found row {}", duplicateCheckRow);
            return false;
        }

        // We have a hash clash and didn't find the value we are looking for using the same key. We need to look at
        // multiple keys to see if we can find the value.
        KeyRange<ByteBuffer> singleHashKeyRange = null;
        final long nextSeqNo;
        try {
            // We know that the first key already exists so we will create a range beyond it and up to (not including)
            // hash + 1 to search within the sequence space.
            singleHashKeyRange = duplicateCheckRowSerde.createSequenceKeyRange(lmdbKV);

            // Iterate over all entries with the same hash. Will only be one unless
            // we get a hash clash. Have to use a cursor as entries can be deleted, thus leaving
            // gaps in the seq numbers.
            nextSeqNo = db.iterateResult(writeTxn, singleHashKeyRange, keyValIterator -> {
                long maxSeqNo = 0;

                while (keyValIterator.hasNext()) {
                    final KeyVal<ByteBuffer> cursorKeyVal = keyValIterator.next();
                    final long seqNo = duplicateCheckRowSerde.extractSequenceNumber(cursorKeyVal.key());

                    // See if the value is the same as ours
                    if (ByteBufferUtils.compare(lmdbKV.getRowValue(), cursorKeyVal.val()) == 0) {
                        // Found our value, job done
                        LOGGER.debug("Found row {}", duplicateCheckRow);
                        return -1L;
                    } else {
                        LOGGER.debug(() -> LogUtil.message("Same hash different value, sequenceNo: {}, key {}, val {}",
                                seqNo,
                                ByteBufferUtils.byteBufferInfo(cursorKeyVal.key()),
                                ByteBufferUtils.byteBufferInfo(cursorKeyVal.val())));
                    }

                    // Remember the maximum sequence number.
                    maxSeqNo = Math.max(maxSeqNo, seqNo);
                }

                return maxSeqNo;
            });
        } finally {
            releaseKeyRange(singleHashKeyRange);
        }

        if (nextSeqNo != -1) {
            LOGGER.debug("Didn't find row {}", duplicateCheckRow);
            final long seqNoVal = nextSeqNo + 1;
            duplicateCheckRowSerde.setSequenceNumber(lmdbKV.getRowKey(), seqNoVal);
            didPut = db.put(writeTxn, lmdbKV.getRowKey(), lmdbKV.getRowValue(), PutFlags.MDB_NOOVERWRITE);
            if (!didPut) {
                throw new RuntimeException("Expected to put value but failed");
            }
            uncommittedCount++;
        }

        if (LOGGER.isDebugEnabled()) {
            if (didPut) {
                LOGGER.debug(() -> "New row (row=" + duplicateCheckRow
                                   + ", " + toString(lmdbKV) +
                                   ", lmdbEnvDir=" + lmdbEnv.getDir() + ")");
            } else {
                LOGGER.debug(() -> "Duplicate row (row=" +
                                   duplicateCheckRow +
                                   ", " + toString(lmdbKV) +
                                   ", lmdbEnvDir=" + lmdbEnv.getDir() + ")");
            }
        }
        return didPut;
    }

    synchronized void flush() {
        writer.flush();
        uncommittedCount = 0;

        LOGGER.debug("flush called");
        LOGGER.trace(() -> "flush()", new RuntimeException("flush"));
    }

    synchronized void close() {
        writer.close();

        LOGGER.debug("close called");
        LOGGER.trace(() -> "close()", new RuntimeException("close"));
        try {
            lmdbEnv.close();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    public synchronized DuplicateCheckRows fetchData(final FindDuplicateCheckCriteria criteria) {
        final List<DuplicateCheckRow> results = new ArrayList<>();
        final AtomicLong totalSize = new AtomicLong();
        final List<String> columnNames = new ArrayList<>();

        lmdbEnv.read(txn -> {
            final PageRequest pageRequest = criteria.getPageRequest();
            readColumnNames(txn, columnNames);
            db.iterate(txn, cursorIterable -> {
                long count = 0;

                for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                    if (count >= pageRequest.getOffset()) {
                        final ByteBuffer keyBuffer = kv.key();
                        final ByteBuffer valBuffer = kv.val();
                        results.add(duplicateCheckRowSerde.createDuplicateCheckRow(valBuffer));
                    }
                    count++;

                    // Once we have enough results break.
                    if (results.size() >= pageRequest.getLength()) {
                        break;
                    }
                }
            });
            totalSize.set(db.count(txn));
        });

        final ResultPage<DuplicateCheckRow> resultPage = ResultPage
                .createCriterialBasedList(results, criteria, totalSize.get());
        return new DuplicateCheckRows(columnNames, resultPage);
    }

    private void writeColumnNames(final WriteTxn txn, final List<String> columnNames) {
        final ByteBuffer byteBuffer;
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, 128, -1)) {
            columnNames.forEach(output::writeString);
            output.flush();
            byteBuffer = output.getByteBuffer();
            byteBuffer.flip();
        }
        columnNamesDb.put(txn, DB_STATE_KEY.duplicate(), byteBuffer);
    }

    private void readColumnNames(final ReadTxn txn, final List<String> columnNames) {
        final ByteBuffer state = columnNamesDb.get(txn, DB_STATE_KEY);
        if (state != null) {
            try (final Input input = new UnsafeByteBufferInput(state)) {
                while (!input.end()) {
                    columnNames.add(input.readString());
                }
            }
        }
    }

    public boolean delete(final DeleteDuplicateCheckRequest request) {
        request.getRows().forEach(row -> {
            final LmdbKV lmdbKV = duplicateCheckRowSerde.createLmdbKV(row);
            try {
                delete(lmdbKV);
            } finally {
                releaseLmdbKv(lmdbKV);
            }
        });
        LOGGER.debug("Committing delete");
        commit();
        return true;
    }

    private synchronized void delete(final LmdbKV lmdbKV) {
        writer.write(writeTxn -> {
            // First try to delete directly.
            final boolean success = db.delete(writeTxn, lmdbKV.getRowKey(), lmdbKV.getRowValue());
            if (success) {
                LOGGER.debug("Deleted lmdbKV directly {}", lmdbKV);
                return;
            }

            // If we didn't manage to delete directly then see if we can find the row to delete.
            KeyRange<ByteBuffer> singleHashKeyRange = null;
            try {
                // We tried the first key already so we will create a range beyond it and up to (not including)
                // hash + 1 to search within the sequence space.
                singleHashKeyRange = duplicateCheckRowSerde.createSequenceKeyRange(lmdbKV);

                // Iterate over all entries with the same hash. Will only be one unless
                // we get a hash clash. Delete the one with the matching value.
                db.iterate(writeTxn, singleHashKeyRange, keyValIterator -> {
                    while (keyValIterator.hasNext()) {
                        final KeyVal<ByteBuffer> cursorKeyVal = keyValIterator.next();
                        // See if the value is the same as ours
                        if (ByteBufferUtils.compare(lmdbKV.getRowValue(), cursorKeyVal.val()) == 0) {
                            // Found our value, delete it
                            keyValIterator.remove();
                            LOGGER.debug("Deleted lmdbKV via iterator {}", lmdbKV);
                            break;
                        }
                    }
                });
                LOGGER.debug("Finished delete iterator");
            } catch (final Exception e) {
                LOGGER.error("Error deleting lmdbKV {}", lmdbKV, e);
                throw e;
            } finally {
                releaseKeyRange(singleHashKeyRange);
            }
        });
    }

    private void releaseKeyRange(final KeyRange<ByteBuffer> keyRange) {
        if (keyRange != null) {
            byteBufferFactory.release(keyRange.getStart());
            byteBufferFactory.release(keyRange.getStop());
        }
    }

    private void releaseLmdbKv(final LmdbKV lmdbKV) {
        if (lmdbKV != null) {
            byteBufferFactory.release(lmdbKV.getRowKey());
            byteBufferFactory.release(lmdbKV.getRowValue());
        }
    }

    private synchronized void commit() {
        writer.write(writeTxn -> {
            LOGGER.debug("Committing, uncommittedCount: {}", uncommittedCount);
            writeTxn.commit();
            uncommittedCount = 0;
        });
    }

    private static String toString(final LmdbKV lmdbKV) {
        if (lmdbKV == null) {
            return "null";
        } else {
            return "Key: {"
                   + NullSafe.get(lmdbKV.getRowKey(), ByteBufferUtils::byteBufferInfoAsLong)
                   + "}, Value: {"
                   + NullSafe.get(lmdbKV.getRowValue(), ByteBufferUtils::byteBufferInfo)
                   + "}";
        }
    }
}
