/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb2.LmdbDb;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.ReadTxn;
import stroom.lmdb2.WriteTxn;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.KryoDataReader;
import stroom.query.language.functions.ref.KryoDataWriter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Output;
import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.EnvFlags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/// The rows of the bounded side of a join, held in LMDB by join key so that each row of the other
/// side can find its matches.
///
/// Rows are added by one thread while the table is built, then [#complete()] is called, after which
/// any number of threads may look rows up. Each row is held under the 64 bit hash of its join key
/// followed by a sequence number, so a key can have any number of rows and keys of any length can
/// be held. The join key itself is held with each row, so rows of a different key with the same
/// hash are skipped.
///
/// The table holds at most [JoinLimits#maxRows()] rows and [JoinLimits#maxBytes()] bytes. Adding
/// more fails with a [JoinTableFullException].
///
/// For a right or full join, the table also notes which of its rows have matched, so the rows that
/// never did can be given once the other side has been searched, and holds the rows with no key,
/// which never match.
public final class JoinTable implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JoinTable.class);

    private static final String DB_NAME = "join";
    private static final int KEY_LENGTH = Long.BYTES + Long.BYTES;
    // Rows are committed in batches while the table is built.
    private static final int ROWS_PER_COMMIT = 10_000;
    // Enough reader slots for every thread that probes the table at once.
    private static final int MAX_READERS = 256;
    // The number of values in a row is held in one byte.
    private static final int MAX_FIELDS = 255;
    // The rows read at a time when every row is read.
    private static final int ROWS_PER_BATCH = 1_000;
    // A buffer for the hash a lookup reads under, one for each thread that looks rows up.
    private static final ThreadLocal<ByteBuffer> PREFIX_BUFFER = ThreadLocal.withInitial(() ->
            ByteBuffer.allocateDirect(Long.BYTES));
    // The key held with a row that has none. No row looking for matches has it, as a key made from
    // values is never empty, so these rows are only read by forEachRow.
    private static final String NO_KEY = "";

    private final String name;
    private final List<String> fields;
    private final JoinLimits limits;
    private final LmdbEnv env;
    private final LmdbDb db;
    private final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(KEY_LENGTH);
    private final Output output = new Output(1024, -1);
    private ByteBuffer valueBuffer = ByteBuffer.allocateDirect(1024);
    private WriteTxn writeTxn;
    private long rowCount;
    // The highest row number held. Rows copied from another node keep the numbers they had there.
    private long maxRowNumber;
    private long byteCount;
    // One bit for each row number, set once the row has matched, made when a right or full join first
    // uses it.
    private volatile AtomicLongArray matched;
    private long uncommittedRows;
    private volatile boolean complete;
    // Reading a closed LMDB environment can crash the JVM, so lookups hold the read lock and close
    // takes the write lock: it waits for lookups under way, and later lookups find the table closed.
    private final ReentrantReadWriteLock closeLock = new ReentrantReadWriteLock();
    private boolean closed;

    /// Creates an empty join table.
    ///
    /// @param name           The name of the joined source, for messages, e.g. its alias.
    /// @param fields         The joined source's fields the table holds, in the order of each row's
    ///                       values, without the alias prefix.
    /// @param lmdbEnvBuilder A builder for the LMDB environment that holds the table, with its
    ///                       directory set. The table owns the environment and deletes it on close.
    /// @param limits         The most rows and bytes the table may hold.
    public JoinTable(final String name,
                     final List<String> fields,
                     final LmdbEnv.Builder lmdbEnvBuilder,
                     final JoinLimits limits) {
        this.name = Objects.requireNonNull(name);
        this.fields = List.copyOf(fields);
        this.limits = Objects.requireNonNull(limits);
        this.env = lmdbEnvBuilder
                .maxDbs(1)
                .maxReaders(MAX_READERS)
                // Probes from many threads each open their own read transaction.
                .addEnvFlag(EnvFlags.MDB_NOTLS)
                .build();
        this.db = env.openDb(DB_NAME);
    }

    /// Adds a row of the bounded side. A row with no key, as [JoinKeys#getKey(Val[])] gives for a
    /// blank or null key value, can never match, so it is not held.
    ///
    /// @param key The row's join key, or `null` if it has none.
    /// @param row The row's values.
    /// @throws JoinTableFullException If the table would hold more rows or bytes than its limits.
    public void add(final String key, final Val[] row) {
        if (key != null) {
            add(rowCount + 1, key, row);
        }
    }

    /// Adds a row of the bounded side that has no key, which can never match, but which a right or
    /// full join still gives once the other side has been searched.
    ///
    /// @param row The row's values.
    /// @throws JoinTableFullException If the table would hold more rows or bytes than its limits.
    void addWithoutKey(final Val[] row) {
        add(rowCount + 1, NO_KEY, row);
    }

    /// Adds a row under the number it has in the table it is copied from, so that which rows have
    /// matched can be told between the tables.
    ///
    /// @param rowNumber The row's number, unique in the table.
    /// @param key       The row's join key, empty for a row with none.
    /// @param row       The row's values.
    /// @throws JoinTableFullException If the table would hold more rows or bytes than its limits.
    void add(final long rowNumber, final String key, final Val[] row) {
        if (complete) {
            throw new IllegalStateException("The join table for '" + name + "' is already complete");
        }
        if (row.length > MAX_FIELDS) {
            throw new IllegalArgumentException("A join can use at most " + MAX_FIELDS + " fields of '" + name + "'");
        }

        output.reset();
        output.writeString(key);
        writeRow(new KryoDataWriter(output), row);
        final int valueLength = output.position();

        if (rowCount + 1 > limits.maxRows() || byteCount + KEY_LENGTH + valueLength > limits.maxBytes()) {
            throw new JoinTableFullException(name, limits);
        }
        rowCount++;
        maxRowNumber = Math.max(maxRowNumber, rowNumber);
        byteCount += KEY_LENGTH + valueLength;

        keyBuffer.clear();
        keyBuffer.putLong(hash(key));
        keyBuffer.putLong(rowNumber);
        keyBuffer.flip();
        if (valueBuffer.capacity() < valueLength) {
            valueBuffer = ByteBuffer.allocateDirect(Math.max(valueLength, valueBuffer.capacity() * 2));
        }
        valueBuffer.clear();
        valueBuffer.put(output.getBuffer(), 0, valueLength);
        valueBuffer.flip();

        if (writeTxn == null) {
            writeTxn = env.writeTxn();
        }
        if (!db.put(writeTxn, keyBuffer, valueBuffer)) {
            throw new IllegalStateException("Unable to add a row to the join table for '" + name + "'");
        }
        uncommittedRows++;
        if (uncommittedRows >= ROWS_PER_COMMIT) {
            commit();
        }
    }

    /// Ends the building of the table. After this, rows can be looked up and no more can be added.
    public void complete() {
        commit();
        complete = true;
        LOGGER.debug("Join table for '{}' complete with {} rows, {} bytes", name, rowCount, byteCount);
    }

    /// Calls the consumer with each row held under the key, in the order they were added.
    ///
    /// @param key      The join key of the row looking for matches, or `null` if it has none, in which
    ///                 case nothing matches.
    /// @param consumer Takes each matching row.
    /// @return The number of matching rows.
    public int forEachMatch(final String key, final Consumer<Val[]> consumer) {
        return readMatches(key, Integer.MAX_VALUE, consumer, false);
    }

    /// Calls the consumer with each row held under the key, as [#forEachMatch(String, Consumer)]
    /// does, and notes that each of them has matched.
    ///
    /// @param key      The join key of the row looking for matches, or `null` if it has none.
    /// @param consumer Takes each matching row.
    /// @return The number of matching rows.
    public int forEachMatchNotingMatched(final String key, final Consumer<Val[]> consumer) {
        return readMatches(key, Integer.MAX_VALUE, consumer, true);
    }

    /// Finds whether any row is held under the key, reading only the keys held, not the rows.
    ///
    /// @param key The join key of the row looking for a match, or `null` if it has none, in which case
    ///            nothing matches.
    /// @return True if at least one row is held under the key.
    public boolean hasMatch(final String key) {
        return readMatches(key, 1, null, false) > 0;
    }

    // Reads up to the given number of rows held under the key, passing each to the consumer as it is
    // read, or only counting them if there is no consumer, and noting that they matched if asked. The
    // rows under the key's hash are read in one read transaction; those of another key with the same
    // hash are skipped. Any failure is thrown, as a failed lookup must not look like a key with no
    // matches.
    private int readMatches(final String key,
                            final int limit,
                            final Consumer<Val[]> consumer,
                            final boolean noteMatched) {
        checkComplete();
        if (key == null || NO_KEY.equals(key) || limit <= 0) {
            return 0;
        }
        final ByteBuffer prefix = PREFIX_BUFFER.get();
        prefix.clear();
        prefix.putLong(hash(key));
        prefix.flip();
        final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefix).build();
        final int[] count = {0};
        read(keyRange, entry -> {
            try (final KryoDataReader reader = new KryoDataReader(new ByteBufferInput(entry.getVal()))) {
                if (key.equals(reader.readString())) {
                    count[0]++;
                    if (noteMatched) {
                        noteMatched(getRowNumber(entry.getKey()));
                    }
                    if (consumer != null) {
                        consumer.accept(ValSerialiser.readArray(reader));
                    }
                }
            }
            return count[0] < limit;
        });
        return count[0];
    }

    // Reads the entries in the key range, in key order, in one read transaction, passing each to the
    // reader until it returns false or there are no more. The table can't be closed while they are read.
    private void read(final LmdbKeyRange keyRange, final Predicate<LmdbEntry> reader) {
        closeLock.readLock().lock();
        try {
            checkNotClosed();
            try (final ReadTxn readTxn = env.readTxn();
                    final LmdbIterable iterable = LmdbIterable.create(readTxn.get(), db.getDbi(), keyRange)) {
                final Iterator<LmdbEntry> iterator = iterable.iterator();
                boolean more = true;
                while (more && iterator.hasNext()) {
                    more = reader.test(iterator.next());
                }
            }
        } finally {
            closeLock.readLock().unlock();
        }
    }

    private void checkComplete() {
        if (!complete) {
            throw new IllegalStateException("The join table for '" + name + "' is not complete yet");
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("The join table for '" + name + "' is closed, as its search has "
                                            + "ended");
        }
    }

    /// Calls the consumer with every row held and its key, as for copying the table to another node.
    ///
    /// @param consumer Takes each row's join key and values.
    public void forEachRow(final BiConsumer<String, Val[]> consumer) {
        forEachNumberedRow((rowNumber, key, row) -> consumer.accept(key, row));
    }

    /// Calls the consumer with every row held that has not matched, as a right or full join gives
    /// them once the other side has been searched.
    ///
    /// @param consumer Takes each row's values.
    public void forEachUnmatchedRow(final Consumer<Val[]> consumer) {
        forEachNumberedRow((rowNumber, key, row) -> {
            if (!isMatched(rowNumber)) {
                consumer.accept(row);
            }
        });
    }

    /// Gets which rows have matched so far, as another node sends them to the node that gives the
    /// rows that never matched.
    ///
    /// @return One bit for each row number, set if the row has matched.
    public long[] getMatched() {
        final AtomicLongArray matched = getMatchedArray();
        final long[] words = new long[matched.length()];
        for (int i = 0; i < words.length; i++) {
            words[i] = matched.get(i);
        }
        return words;
    }

    /// Notes rows another node's copy of the table found matched.
    ///
    /// @param wordIndex The index of a word of [#getMatched()].
    /// @param word      The word, whose set bits are rows that matched.
    public void addMatched(final int wordIndex, final long word) {
        final AtomicLongArray matched = getMatchedArray();
        if (wordIndex < 0 || wordIndex >= matched.length()) {
            throw new IllegalArgumentException("The matched rows of '" + name + "' are for a different table");
        }
        matched.accumulateAndGet(wordIndex, word, (a, b) -> a | b);
    }

    private void noteMatched(final long rowNumber) {
        getMatchedArray().accumulateAndGet(
                (int) (rowNumber / Long.SIZE), 1L << (rowNumber % Long.SIZE), (a, b) -> a | b);
    }

    private boolean isMatched(final long rowNumber) {
        return (getMatchedArray().get((int) (rowNumber / Long.SIZE)) & (1L << (rowNumber % Long.SIZE))) != 0;
    }

    private AtomicLongArray getMatchedArray() {
        checkComplete();
        AtomicLongArray matched = this.matched;
        if (matched == null) {
            synchronized (this) {
                matched = this.matched;
                if (matched == null) {
                    matched = new AtomicLongArray(Math.toIntExact(maxRowNumber / Long.SIZE + 1));
                    this.matched = matched;
                }
            }
        }
        return matched;
    }

    // The row number held after the key's hash in an LMDB key.
    private static long getRowNumber(final ByteBuffer lmdbKey) {
        return lmdbKey.getLong(lmdbKey.position() + Long.BYTES);
    }

    /// Calls the consumer with every row held, its number and its key, as for copying the table to
    /// another node.
    ///
    /// @param consumer Takes each row's number, join key and values.
    public void forEachNumberedRow(final RowConsumer consumer) {
        checkComplete();
        // Read in batches, so the table is only held open while a batch is read, not while the
        // consumer, which may be writing to a slow node, takes the rows.
        ByteBuffer after = null;
        while (true) {
            final List<RowEntry> batch = readBatch(after);
            batch.forEach(entry -> consumer.accept(getRowNumber(entry.lmdbKey()), entry.key(), entry.row()));
            if (batch.size() < ROWS_PER_BATCH) {
                return;
            }
            after = batch.getLast().lmdbKey();
        }
    }

    // Reads up to a batch of rows, in key order, after the given LMDB key, or from the start.
    private List<RowEntry> readBatch(final ByteBuffer after) {
        final LmdbKeyRange keyRange = after == null
                ? LmdbKeyRange.all()
                : LmdbKeyRange.builder().start(after, false).build();
        final List<RowEntry> batch = new ArrayList<>(ROWS_PER_BATCH);
        read(keyRange, entry -> {
            // LMDB's buffers are only valid in the transaction, so the key is copied.
            final ByteBuffer lmdbKey = ByteBuffer.allocateDirect(KEY_LENGTH);
            lmdbKey.put(entry.getKey().duplicate());
            lmdbKey.flip();
            try (final KryoDataReader reader = new KryoDataReader(new ByteBufferInput(entry.getVal()))) {
                final String key = reader.readString();
                batch.add(new RowEntry(lmdbKey, key, ValSerialiser.readArray(reader)));
            }
            return batch.size() < ROWS_PER_BATCH;
        });
        return batch;
    }

    /// Writes a row's values, as a join table holds them: a field with no value is held as a null
    /// value.
    ///
    /// @param writer The writer.
    /// @param row    The row's values, at most 255 of them.
    static void writeRow(final KryoDataWriter writer, final Val[] row) {
        // The count is written as one byte, so a longer row would be read back wrongly.
        if (row.length > MAX_FIELDS) {
            throw new IllegalArgumentException("A join table row can hold at most " + MAX_FIELDS + " values");
        }
        writer.writeByteUnsigned(row.length);
        for (final Val val : row) {
            ValSerialiser.write(writer, val == null
                    ? ValNull.INSTANCE
                    : val);
        }
    }

    /// Gets the name of the joined source the table holds the rows of.
    ///
    /// @return The name, e.g. the join line's alias.
    public String getName() {
        return name;
    }

    /// Gets the joined source's fields the table holds, in the order of each row's values.
    ///
    /// @return The fields, without the alias prefix.
    public List<String> getFields() {
        return fields;
    }

    /// Gets the number of rows held.
    ///
    /// @return The number of rows.
    public long getRowCount() {
        return rowCount;
    }

    /// Gets the number of bytes held, as counted against the byte limit.
    ///
    /// @return The number of bytes.
    public long getByteCount() {
        return byteCount;
    }

    /// Closes the table and deletes its LMDB environment.
    @Override
    public void close() {
        closeLock.writeLock().lock();
        try {
            if (!closed) {
                closed = true;
                try {
                    if (writeTxn != null) {
                        writeTxn.close();
                        writeTxn = null;
                    }
                } finally {
                    env.close();
                    env.delete();
                }
            }
        } finally {
            closeLock.writeLock().unlock();
        }
    }

    private void commit() {
        if (writeTxn != null) {
            writeTxn.commit();
            writeTxn.close();
            writeTxn = null;
        }
        uncommittedRows = 0;
    }

    private static long hash(final String key) {
        return LongHashFunction.xx3().hashBytes(key.getBytes(StandardCharsets.UTF_8));
    }


    // --------------------------------------------------------------------------------


    // A row read from the table: its LMDB key, for reading on after it, its join key and its values.
    private record RowEntry(ByteBuffer lmdbKey, String key, Val[] row) {

    }


    // --------------------------------------------------------------------------------


    /// Takes a row of a join table with its number and key.
    @FunctionalInterface
    public interface RowConsumer {

        /// Takes a row.
        ///
        /// @param rowNumber The row's number, unique in the table.
        /// @param key       The row's join key, empty for a row with none.
        /// @param row       The row's values.
        void accept(long rowNumber, String key, Val[] row);
    }
}
