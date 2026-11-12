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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
    // A buffer for the hash a lookup reads under, one for each thread that looks rows up.
    private static final ThreadLocal<ByteBuffer> PREFIX_BUFFER = ThreadLocal.withInitial(() ->
            ByteBuffer.allocateDirect(Long.BYTES));

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
    private long byteCount;
    private long uncommittedRows;
    private volatile boolean complete;

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
        if (complete) {
            throw new IllegalStateException("The join table for '" + name + "' is already complete");
        }
        if (key == null) {
            return;
        }
        if (row.length > MAX_FIELDS) {
            throw new IllegalArgumentException("A join can use at most " + MAX_FIELDS + " fields of '" + name + "'");
        }

        output.reset();
        output.writeString(key);
        final KryoDataWriter writer = new KryoDataWriter(output);
        writer.writeByteUnsigned(row.length);
        for (final Val val : row) {
            // A field the row has no value for is held as a null value.
            ValSerialiser.write(writer, val == null
                    ? ValNull.INSTANCE
                    : val);
        }
        final int valueLength = output.position();

        if (rowCount + 1 > limits.maxRows() || byteCount + KEY_LENGTH + valueLength > limits.maxBytes()) {
            throw new JoinTableFullException(name, limits);
        }
        rowCount++;
        byteCount += KEY_LENGTH + valueLength;

        keyBuffer.clear();
        keyBuffer.putLong(hash(key));
        keyBuffer.putLong(rowCount);
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
        return readMatches(key, Integer.MAX_VALUE, consumer);
    }

    /// Finds whether any row is held under the key, reading only the keys held, not the rows.
    ///
    /// @param key The join key of the row looking for a match, or `null` if it has none, in which case
    ///            nothing matches.
    /// @return True if at least one row is held under the key.
    public boolean hasMatch(final String key) {
        return readMatches(key, 1, null) > 0;
    }

    // Reads up to the given number of rows held under the key, passing each to the consumer as it is
    // read, or only counting them if there is no consumer. The rows under the key's hash are read in
    // one read transaction; those of another key with the same hash are skipped. Any failure is
    // thrown, as a failed lookup must not look like a key with no matches.
    private int readMatches(final String key, final int limit, final Consumer<Val[]> consumer) {
        if (!complete) {
            throw new IllegalStateException("The join table for '" + name + "' is not complete yet");
        }
        if (key == null) {
            return 0;
        }
        final ByteBuffer prefix = PREFIX_BUFFER.get();
        prefix.clear();
        prefix.putLong(hash(key));
        prefix.flip();
        final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefix).build();
        int count = 0;
        try (final ReadTxn readTxn = env.readTxn();
                final LmdbIterable iterable = LmdbIterable.create(readTxn.get(), db.getDbi(), keyRange)) {
            final Iterator<LmdbEntry> iterator = iterable.iterator();
            while (count < limit && iterator.hasNext()) {
                final ByteBuffer value = iterator.next().getVal();
                try (final KryoDataReader reader = new KryoDataReader(new ByteBufferInput(value))) {
                    if (key.equals(reader.readString())) {
                        count++;
                        if (consumer != null) {
                            consumer.accept(ValSerialiser.readArray(reader));
                        }
                    }
                }
            }
        }
        return count;
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
}
