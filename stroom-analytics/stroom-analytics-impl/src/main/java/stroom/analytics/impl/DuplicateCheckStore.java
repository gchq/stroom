package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckRow;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb2.AbstractTxn;
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
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import jakarta.inject.Provider;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class DuplicateCheckStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckStore.class);
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String INFO_DB_NAME = "info";
    static final String DUPLICATE_CHECK_DB_NAME = "duplicate-check";
    static final int MAX_DBS = 2;
    static final int MAX_READERS = 1;
    static final EnvFlags ENV_FLAGS = EnvFlags.MDB_NOTLS;

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
        final LmdbConfig lmdbConfig = duplicateCheckStoreConfig.getLmdbConfig();

        LmdbEnv anLmdbEnv = null;
        // See if the DB dir already exists.
        if (lmdbEnvDir.dbExists()) {
            // Find out the current schema version if any.
            anLmdbEnv = validateSchemaVersion(lmdbEnvDir, lmdbConfig);
            // If there is no schema or it is an old version, then delete and start again with a new DB.
            if (anLmdbEnv == null) {
                lmdbEnvDir.delete();
            }
        }

        if (anLmdbEnv == null) {
            // Either it didn't exist or it was deleted for being not the right version, so
            // re-create the env dir and a new env in it.
            lmdbEnvDir.ensureExists();
            anLmdbEnv = createEnv(duplicateCheckStoreConfig.getLmdbConfig(), lmdbEnvDir);
        }
        this.lmdbEnv = anLmdbEnv;
        this.db = lmdbEnv.openDb(DUPLICATE_CHECK_DB_NAME, DbiFlags.MDB_CREATE);
        this.infoDb = lmdbEnv.openDb(INFO_DB_NAME, DbiFlags.MDB_CREATE);
        this.writer = new LmdbWriter(executorProvider, lmdbEnv);
        writeSchemaVersion();
    }

    /**
     * Caller is responsible for (auto-)closing.
     * Pkg-private for testing
     */
    private LmdbEnv createEnv(final LmdbConfig lmdbConfig, final LmdbEnvDir lmdbEnvDir) {
        try {
            final LmdbEnv lmdbEnv = LmdbEnv
                    .builder()
                    .config(lmdbConfig)
                    .lmdbEnvDir(lmdbEnvDir)
                    .maxDbs(MAX_DBS)
                    .maxReaders(MAX_READERS)
                    .addEnvFlag(ENV_FLAGS)
                    .build();
            LOGGER.debug("Opened/created LmdbEnv in {} with config {}", lmdbEnvDir, lmdbConfig);
            return lmdbEnv;
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message("Error creating/opening LMDB Env in {} with config {} - ",
                    lmdbEnvDir, lmdbConfig, LogUtil.exceptionMessage(e)), e);
        }
    }

    /**
     * Check that the {@link LmdbEnv} has the expected schema version in it.
     *
     * @return The opened {@link LmdbEnv} if the version is as expected, else
     * the {@link LmdbEnv} will be closed and {@code null} will be returned.
     */
    private LmdbEnv validateSchemaVersion(final LmdbEnvDir lmdbEnvDir,
                                          final LmdbConfig lmdbConfig) {
        LmdbEnv lmdbEnv = null;
        boolean isValid = false;
        try {
            lmdbEnv = createEnv(lmdbConfig, lmdbEnvDir);
            if (lmdbEnv.hasDb(INFO_DB_NAME)) {
                final LmdbDb info = lmdbEnv.openDb(INFO_DB_NAME);
                try (final ReadTxn readTxn = lmdbEnv.readTxn()) {
                    final ByteBuffer valueBuffer = info.get(readTxn, InfoKey.SCHEMA_VERSION.getByteBuffer());
                    if (valueBuffer != null) {
                        final int version = valueBuffer.getInt();
                        isValid = version == CURRENT_SCHEMA_VERSION;
                        LOGGER.debug("LmdbEnv {} has version {}, isValid: {}", lmdbEnvDir, version, isValid);
                    } else {
                        LOGGER.debug("No entry for key {} found in {} DB, lmdbEnvDir: {}",
                                InfoKey.SCHEMA_VERSION, INFO_DB_NAME, lmdbEnvDir);
                    }
                }
            } else {
                LOGGER.debug("{} DB not present, lmdbEnvDir: {}", INFO_DB_NAME, lmdbEnvDir);
            }
        } catch (final Exception e) {
            NullSafe.consume(lmdbEnv, LmdbEnv::close);
            LOGGER.error(e::getMessage, e);
            throw e;
        }
        if (!isValid) {
            lmdbEnv.close();
            lmdbEnv = null;
        }
        return lmdbEnv;
    }

    private synchronized void writeSchemaVersion() {
        writer.write(writeTxn -> {
            byteBuffers.useInt(CURRENT_SCHEMA_VERSION, byteBuffer -> {
                infoDb.put(writeTxn, InfoKey.SCHEMA_VERSION.getByteBuffer(), byteBuffer);
            });
        }, true);
    }

//    synchronized void writeColumnNames(final List<String> columnNames) {
//        final Optional<List<String>> optColumnNames = lmdbEnv.readResult(this::fetchColumnNames);
//
//        if (optColumnNames.isPresent()) {
//            final List<String> currentColNames = optColumnNames.get();
//            if (!Objects.equals(currentColNames, columnNames)) {
//                LOGGER.info(() -> LogUtil.message(
//                        """
//                                Columns have changed. All data in duplicate store {} will be deleted.
//                                Old: '{}'
//                                New: '{}'""",
//                        lmdbEnv.getDir(),
//                        String.join(", ", currentColNames),
//                        String.join(", ", columnNames)));
//
//                // Change of columns (added, removed, re-ordered) means any new data won't match the layout
//                // of the existing data, so we have to clear it out.
//                writer.write(writeTxn -> {
//                    LOGGER.debug(() -> LogUtil.message("Deleting all data in {}", lmdbEnv.getDir()));
//                    db.drop(writeTxn);
//                });
//                writer.flush();
//
//                // Write the new columns
//                LOGGER.debug("writeColumnNames() - Writing column names {}", columnNames);
//                writer.write(writeTxn ->
//                                writeColumnNames(writeTxn, columnNames),
//                        true);
//            } else {
//                LOGGER.debug("writeColumnNames() - Column names unchanged {}", columnNames);
//            }
//        } else {
//            // None set so just write what we have and there should be no data to delete.
//            writer.write(writeTxn ->
//                            writeColumnNames(writeTxn, columnNames),
//                    true);
//        }
//    }

    synchronized Optional<List<String>> fetchColumnNames() {
        return lmdbEnv.readResult(this::fetchColumnNames);
    }

    synchronized void writeColumnNames(final List<String> columnNames) {
        writer.write(writeTxn -> {
            final Optional<List<String>> optColumnNames = fetchColumnNames(writeTxn);

            if (optColumnNames.isPresent()) {
                final List<String> currentColNames = optColumnNames.get();
                if (!Objects.equals(currentColNames, columnNames)) {
                    LOGGER.info(() -> LogUtil.message(
                            "Columns have changed. All data in duplicate store {} will be deleted.", lmdbEnv.getDir()));
                    LOGGER.debug(() -> LogUtil.message(
                            """
                                    writeColumnNames() - lmdbEnv: {}
                                    Old: '{}'
                                    New: '{}'""",
                            lmdbEnv.getDir(),
                            String.join(", ", currentColNames),
                            String.join(", ", columnNames)));

                    // Change of columns (added, removed, re-ordered) means any new data won't match the layout
                    // of the existing data, so we have to clear it out.
                    db.drop(writeTxn);

                    // Write the new columns
                    LOGGER.debug("writeColumnNames() - Writing column names {}", columnNames);
                    writeColumnNames(writeTxn, columnNames);
                } else {
                    LOGGER.debug("writeColumnNames() - Column names unchanged {}", columnNames);
                }
            } else {
                // None set so just write what we have and there should be no data to delete.
                writeColumnNames(writeTxn, columnNames);
            }
        }, true);
    }

    /**
     * @param duplicateCheckRow The row to check.
     * @return True if duplicateCheckRow can be inserted, i.e. it is NOT a duplicate of
     * any existing rows.
     */
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
            try (final LmdbIterable iterable = LmdbIterable.create(txn.get(), db.getDbi())) {
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

    private void readColumnNames(final AbstractTxn txn, final List<String> columnNames) {
        final ByteBuffer state = infoDb.get(txn, InfoKey.COLUMN_NAMES.getByteBuffer());
        if (state != null) {
            try (final Input input = new UnsafeByteBufferInput(state)) {
                while (!input.end()) {
                    columnNames.add(input.readString());
                }
            }
        }
    }

    /**
     * Return {@link Optional} so we can distinguish between an uninitialised store and
     * a rule with no columns.
     */
    private Optional<List<String>> fetchColumnNames(final AbstractTxn txn) {
        final ByteBuffer state = infoDb.get(txn, InfoKey.COLUMN_NAMES.getByteBuffer());
        final Optional<List<String>> result;
        if (state != null) {
            final ArrayList<String> columnNames = new ArrayList<>();
            try (final Input input = new UnsafeByteBufferInput(state)) {
                while (!input.end()) {
                    columnNames.add(input.readString());
                }
            }
            if (columnNames.isEmpty()) {
                result = Optional.of(Collections.emptyList());
            } else {
                result = Optional.of(Collections.unmodifiableList(columnNames));
            }
        } else {
            result = Optional.empty();
        }
        LOGGER.debug("fetchColumnNames() - Returning {}", result);
        return result;
    }

    private boolean haveColumnNamesBeenSet(final AbstractTxn txn) {
        // Don't care about the val
        final ByteBuffer val = infoDb.get(txn, InfoKey.COLUMN_NAMES.getByteBuffer());
        return val != null;
    }

    /**
     * @return The number of entries in the main dup check db. I.e. the number of unique items.
     */
    public long size() {
        return db.count();
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

    /**
     * Delete all the data in the main db of the duplicate check store.
     * Does not delete columns or schema info.
     */
    private void deleteAll(final WriteTxn writeTxn) {
        LOGGER.debug(() -> LogUtil.message("Deleting all data in {}", lmdbEnv.getDir()));
        db.drop(writeTxn);
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


    // --------------------------------------------------------------------------------


    /**
     * Pkg-private for testing
     */
    enum InfoKey implements HasPrimitiveValue {
        SCHEMA_VERSION(0),
        COLUMN_NAMES(1);

        private final byte primitiveValue;
        private final ByteBuffer byteBuffer;

        InfoKey(final int primitiveValue) {
            this.primitiveValue = (byte) primitiveValue;
            final ByteBuffer aByteBuffer = ByteBuffer.allocateDirect(1);
            aByteBuffer.put((byte) primitiveValue);
            aByteBuffer.flip();
            this.byteBuffer = aByteBuffer.asReadOnlyBuffer();
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
