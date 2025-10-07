package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.LmdbEntry;
import stroom.lmdb.LmdbIterableSupport;
import stroom.lmdb.LmdbIterableSupport.LmdbIterable;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbKeySequence;
import stroom.lmdb2.LmdbWriter;
import stroom.lmdb2.ReadTxn;
import stroom.lmdb2.WriteTxn;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.query.common.v2.LmdbKV;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import jakarta.inject.Provider;
import org.lmdbjava.Cursor;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class DuplicateCheckStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckStore.class);

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final ByteBufferFactory byteBufferFactory;
    private final ByteBuffers byteBuffers;
    private final DuplicateCheckRowSerde duplicateCheckRowSerde;
    private final LmdbEnv lmdbEnv;
    private final LmdbDb db;
    private final LmdbDb infoDb;
    private final LmdbWriter writer;
    private final LmdbKeySequence lmdbKeySequence;
    private final int maxPutsBeforeCommit = 100;
    private long uncommittedCount = 0;

    DuplicateCheckStore(final DuplicateCheckDirs duplicateCheckDirs,
                        final ByteBufferFactory byteBufferFactory,
                        final ByteBuffers byteBuffers,
                        final DuplicateCheckStoreConfig duplicateCheckStoreConfig,
                        final DuplicateCheckRowSerde duplicateCheckRowSerde,
                        final Provider<Executor> executorProvider,
                        final String analyticRuleUUID) {
        this.byteBufferFactory = byteBufferFactory;
        this.byteBuffers = byteBuffers;
        this.duplicateCheckRowSerde = duplicateCheckRowSerde;
        lmdbKeySequence = new LmdbKeySequence(byteBuffers);
        final LmdbEnvDir lmdbEnvDir = duplicateCheckDirs.getDir(analyticRuleUUID);

        // See if the DB dir already exists.
        if (lmdbEnvDir.dbExists()) {
            // Find out the current schema version if any.
            final int schemaVersion = readSchemaVersion(lmdbEnvDir, duplicateCheckStoreConfig);
            // If there is no schema then delete and start again with a new DB.
            if (schemaVersion == -1) {
                lmdbEnvDir.delete();
            }
        }

        this.lmdbEnv = LmdbEnv
                .builder()
                .config(duplicateCheckStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir)
                .maxDbs(2)
                .maxReaders(1)
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();

        this.db = lmdbEnv.openDb("duplicate-check", DbiFlags.MDB_CREATE);
        this.infoDb = lmdbEnv.openDb("info", DbiFlags.MDB_CREATE);
        writer = new LmdbWriter(executorProvider, lmdbEnv);
        writeSchemaVersion();
    }

    private int readSchemaVersion(final LmdbEnvDir lmdbEnvDir,
                                  final DuplicateCheckStoreConfig duplicateCheckStoreConfig) {
        int version = -1;
        try {
            final LmdbEnv lmdbEnv = LmdbEnv
                    .builder()
                    .config(duplicateCheckStoreConfig.getLmdbConfig())
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbs(2)
                    .maxReaders(1)
                    .addEnvFlag(EnvFlags.MDB_NOTLS)
                    .build();
            final LmdbDb info = lmdbEnv.openDb("info");
            final ByteBuffer valueBuffer = info.get(lmdbEnv.readTxn(), InfoKey.SCHEMA_VERSION.getByteBuffer());
            version = valueBuffer.getInt();

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return version;
    }

    private synchronized void writeSchemaVersion() {
        writer.write(writeTxn -> writeSchemaVersion(writeTxn, CURRENT_SCHEMA_VERSION));
    }

    private void writeSchemaVersion(final WriteTxn txn, final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDb.put(txn, InfoKey.SCHEMA_VERSION.getByteBuffer(), byteBuffer);
        });
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
        final boolean didPut = lmdbKeySequence.find(
                db.getDbi(),
                writeTxn.get(),
                lmdbKV.key(),
                lmdbKV.val(),
                val -> val.equals(lmdbKV.val()),
                match -> {
                    if (match.foundKey() == null) {
                        // If there is 0 sequence number then just put.
                        if (match.nextSequenceNumber() == 0) {
                            final boolean success = db.put(writeTxn,
                                    lmdbKV.key(),
                                    lmdbKV.val(),
                                    PutFlags.MDB_NOOVERWRITE);
                            if (!success) {
                                throw new RuntimeException("Expected to put value but failed");
                            }
                            return true;
                        }

                        LOGGER.debug("Didn't find row {}", duplicateCheckRow);
                        return lmdbKeySequence.addSequenceNumber(
                                lmdbKV.key(),
                                duplicateCheckRowSerde.getKeyLength(),
                                match.nextSequenceNumber(),
                                sequenceKeyBuffer -> {
                                    final boolean success = db.put(writeTxn,
                                            sequenceKeyBuffer,
                                            lmdbKV.val(),
                                            PutFlags.MDB_NOOVERWRITE);
                                    if (!success) {
                                        throw new RuntimeException("Expected to put value but failed");
                                    }
                                    uncommittedCount++;
                                    return true;
                                });
                    }

                    return false;
                });

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

            long count = 0;
            try (final LmdbIterable iterable = LmdbIterableSupport.builder(txn.get(), db.getDbi()).create()) {
                for (final LmdbEntry entry : iterable) {
                    if (count >= pageRequest.getOffset()) {
                        final ByteBuffer valBuffer = entry.getVal();
                        results.add(duplicateCheckRowSerde.createDuplicateCheckRow(valBuffer));
                    }
                    count++;

                    // Once we have enough results break.
                    if (results.size() >= pageRequest.getLength()) {
                        break;
                    }
                }
            }
            totalSize.set(db.count(txn));
        });

        final ResultPage<DuplicateCheckRow> resultPage = ResultPage
                .createCriterialBasedList(results, criteria, totalSize.get());
        return new DuplicateCheckRows(columnNames, resultPage);
    }

    private void writeColumnNames(final WriteTxn txn, final List<String> columnNames) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, 128, -1)) {
            columnNames.forEach(output::writeString);
            output.flush();
            final ByteBuffer byteBuffer = output.getByteBuffer();
            byteBuffer.flip();
            try {
                infoDb.put(txn, InfoKey.COLUMN_NAMES.getByteBuffer(), byteBuffer);
            } finally {
                byteBufferFactory.release(byteBuffer);
            }
        }
    }

    private void readColumnNames(final ReadTxn txn, final List<String> columnNames) {
        final ByteBuffer state = infoDb.get(txn, InfoKey.COLUMN_NAMES.getByteBuffer());
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
            try {
                lmdbKeySequence.delete(
                        db.getDbi(),
                        writeTxn.get(),
                        lmdbKV.key(),
                        cursorValue -> cursorValue.equals(lmdbKV.val()));
                LOGGER.debug("Finished delete iterator");
            } catch (final Exception e) {
                LOGGER.error("Error deleting lmdbKV {}", lmdbKV, e);
                throw e;
            }
        });
    }

    private void releaseLmdbKv(final LmdbKV lmdbKV) {
        if (lmdbKV != null) {
            byteBufferFactory.release(lmdbKV.key());
            byteBufferFactory.release(lmdbKV.val());
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
                   + NullSafe.get(lmdbKV.key(), ByteBufferUtils::byteBufferInfoAsLong)
                   + "}, Value: {"
                   + NullSafe.get(lmdbKV.val(), ByteBufferUtils::byteBufferInfo)
                   + "}";
        }
    }

    private enum InfoKey implements HasPrimitiveValue {
        SCHEMA_VERSION(0),
        COLUMN_NAMES(1);

        private final byte primitiveValue;
        private final ByteBuffer byteBuffer;

        InfoKey(final int primitiveValue) {
            this.primitiveValue = (byte) primitiveValue;
            this.byteBuffer = ByteBuffer.allocateDirect(1);
            byteBuffer.put((byte) primitiveValue);
            byteBuffer.flip();
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer.duplicate();
        }
    }
}
