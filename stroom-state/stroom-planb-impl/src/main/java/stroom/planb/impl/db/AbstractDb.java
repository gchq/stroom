package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.BBKV;
import stroom.lmdb2.KV;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbKeySequence;
import stroom.query.api.Column;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValArrayFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasPrimitiveValue;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractDb<K, V> implements Db<K, V> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDb.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private static final byte[] NAME = "db".getBytes(UTF_8);
    private static final byte[] INFO_NAME = "info_db".getBytes(UTF_8);
    private static final int CONCURRENT_READERS = 10;

    private final Semaphore concurrentReaderSemaphore;

    private final LmdbEnvDir lmdbEnvDir;
    private final LmdbKeySequence lmdbKeySequence;
    protected final Serde<K, V> serde;
    protected final ByteBuffers byteBuffers;
    protected final Env<ByteBuffer> env;
    protected final Dbi<ByteBuffer> dbi;
    protected final Dbi<ByteBuffer> infoDbi;
    private final DBWriter dbWriter;
    private final ReentrantLock writeTxnLock = new ReentrantLock();
    private final ReentrantLock dbCommitLock = new ReentrantLock();
    private final boolean readOnly;

    private final HashClashCommitRunnable commitRunnable = new HashClashCommitRunnable();

    public AbstractDb(final Path path,
                      final ByteBuffers byteBuffers,
                      final Serde<K, V> serde,
                      final Long mapSize,
                      final Boolean overwrite,
                      final boolean readOnly) {
        this.lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBuffers = byteBuffers;
        this.serde = serde;
        this.readOnly = readOnly;
        concurrentReaderSemaphore = new Semaphore(CONCURRENT_READERS);
        lmdbKeySequence = new LmdbKeySequence(byteBuffers);

        if (readOnly) {
            LOGGER.info(() -> "Opening: " + path);
        } else {
            LOGGER.info(() -> "Creating: " + path);
        }

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(mapSize == null
                        ? LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes()
                        : mapSize)
                .setMaxDbs(2)
                .setMaxReaders(CONCURRENT_READERS);

        if (readOnly) {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV);
        } else {
            env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                    EnvFlags.MDB_NOTLS);
        }
        dbi = env.openDbi(NAME, DbiFlags.MDB_CREATE);

        if (readOnly) {
            dbWriter = null;

            // Read schema version.
            infoDbi = env.openDbi(INFO_NAME, DbiFlags.MDB_CREATE);
            try (final Txn<ByteBuffer> txn = env.txnRead()) {
                final int schemaVersion = readSchemaVersion(txn);
                LOGGER.debug("Read schema version {}", schemaVersion);
            }

        } else {
            // Read and write schema version.
            infoDbi = env.openDbi(INFO_NAME, DbiFlags.MDB_CREATE);
            try (final Txn<ByteBuffer> txn = env.txnWrite()) {
                final int schemaVersion = readSchemaVersion(txn);
                LOGGER.debug("Read schema version {}", schemaVersion);
                writeSchemaVersion(txn, CURRENT_SCHEMA_VERSION);
            }

            // If we do not prefix values then we can simply put rows.
            if (!serde.hasPrefix()) {
                // If the value has no key prefix, i.e. we are not using key hashes then just try to put.
                if (overwrite == null || overwrite) {
                    // Put and overwrite any existing key/value.
                    dbWriter = dbi::put;
                } else {
                    // Put but do not overwrite any existing key/value.
                    dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) ->
                            dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE);
                }
            } else {
                if (overwrite == null || overwrite) {
                    dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) -> {
                        // First try to put without overwriting existing values.
                        if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                            // We didn't manage to put so see if we can find the existing KV pair.
                            serde.createPrefixPredicate(new BBKV(keyByteBuffer, valueByteBuffer),
                                    predicate -> lmdbKeySequence.find(
                                            dbi,
                                            writeTxn,
                                            keyByteBuffer,
                                            valueByteBuffer,
                                            null,
                                            match -> {
                                                final ByteBuffer foundKey = match.foundKey();
                                                if (foundKey != null) {
                                                    // We need to copy the buffer to use it after delete.
                                                    byteBuffers.useCopy(foundKey, copy -> {
                                                        dbi.delete(writeTxn, copy);
                                                        if (!dbi.put(writeTxn,
                                                                copy,
                                                                valueByteBuffer,
                                                                PutFlags.MDB_NOOVERWRITE)) {
                                                            throw new RuntimeException("Unable to put after delete");
                                                        }
                                                    });

                                                } else {
                                                    // If we didn't find the item then insert it with a new sequence
                                                    // number.
                                                    putAtNewSequenceNumber(
                                                            writeTxn,
                                                            keyByteBuffer,
                                                            valueByteBuffer,
                                                            commitRunnable,
                                                            match.nextSequenceNumber());
                                                }
                                                return null;
                                            }));
                        }
                    };

                } else {
                    dbWriter = (writeTxn, keyByteBuffer, valueByteBuffer) -> {
                        // First try to put without overwriting existing values.
                        if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                            // We didn't manage to put so see if we can find the existing KV pair.
                            serde.createPrefixPredicate(new BBKV(keyByteBuffer, valueByteBuffer),
                                    predicate -> lmdbKeySequence.find(
                                            dbi,
                                            writeTxn,
                                            keyByteBuffer,
                                            valueByteBuffer,
                                            null,
                                            match -> {
                                                // If we didn't find the item then insert it with a new sequence number.
                                                if (match.foundKey() == null) {
                                                    putAtNewSequenceNumber(
                                                            writeTxn,
                                                            keyByteBuffer,
                                                            valueByteBuffer,
                                                            commitRunnable,
                                                            match.nextSequenceNumber());
                                                }
                                                return null;
                                            }));
                        }
                    };
                }
            }
        }
    }

    private void putAtNewSequenceNumber(final Txn<ByteBuffer> writeTxn,
                                        final ByteBuffer keyByteBuffer,
                                        final ByteBuffer valueByteBuffer,
                                        final HashClashCommitRunnable hashClashCommitRunnable,
                                        final long sequenceNumber) {
        // We must have had a hash clash here because we didn't find a row for the key even
        // though the db contains the key hash.
        hashClashCommitRunnable.increment();

        lmdbKeySequence.addSequenceNumber(keyByteBuffer, serde.getKeyLength(), sequenceNumber, sequenceKeyBuffer -> {
            if (!dbi.put(writeTxn, sequenceKeyBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                throw new RuntimeException("Unable to put at sequence " + sequenceNumber);
            }
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        write(writer -> {
            final Env.Builder<ByteBuffer> builder = Env.create()
                    .setMaxDbs(1)
                    .setMaxReaders(1);
            try (final Env<ByteBuffer> sourceEnv = builder.open(source.toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV)) {
                final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(NAME);
                try (final Txn<ByteBuffer> readTxn = sourceEnv.txnRead()) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
                        for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                            ByteBuffer key = keyVal.key();
                            ByteBuffer value = keyVal.val();
                            if (key.limit() > serde.getKeyLength()) {
                                key = key.slice(0, serde.getKeyLength());
                            }
                            insert(writer, key, value);
                        }
                    }
                }
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    @Override
    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        // Don't condense by default.
    }

    @Override
    public void insert(final LmdbWriter writer,
                       final KV<K, V> kv) {
        insert(writer, kv.key(), kv.val());
    }

    public void insert(final LmdbWriter writer,
                       final K key,
                       final V value) {
        serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createValueByteBuffer(key, value, valueByteBuffer ->
                        insert(writer, keyByteBuffer, valueByteBuffer)));
    }

    private boolean insert(final LmdbWriter writer,
                           final ByteBuffer keyByteBuffer,
                           final ByteBuffer valueByteBuffer) {
        dbWriter.write(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer);
        writer.tryCommit();
        return true;
    }

    @Override
    public final LmdbWriter createWriter() {
        return new LmdbWriter(env, dbCommitLock, commitRunnable, writeTxnLock);
    }

    public final <T> T write(final Function<LmdbWriter, T> function) {
        try (final LmdbWriter writer = new LmdbWriter(env, dbCommitLock, commitRunnable, writeTxnLock)) {
            return function.apply(writer);
        }
    }

    public final void write(final Consumer<LmdbWriter> consumer) {
        try (final LmdbWriter writer = new LmdbWriter(env, dbCommitLock, commitRunnable, writeTxnLock)) {
            consumer.accept(writer);
        }
    }

    @Override
    public final void lock(final Runnable runnable) {
        dbCommitLock.lock();
        try {
            runnable.run();
        } finally {
            dbCommitLock.unlock();
        }
    }

    public final <R> R read(final Function<Txn<ByteBuffer>, R> function) {
        try {
            concurrentReaderSemaphore.acquire();
            try {
                try (final Txn<ByteBuffer> readTxn = env.txnRead()) {
                    return function.apply(readTxn);
                }
            } finally {
                concurrentReaderSemaphore.release();
            }
        } catch (final InterruptedException e) {
            LOGGER.debug(e::getMessage, e);
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

    @Override
    public V get(final K key) {
        return read(readTxn -> get(readTxn, key));
    }

    private V get(final Txn<ByteBuffer> readTxn, final K key) {
        return serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createPrefixPredicate(key, predicate -> {
                    // Just try to get directly first without the overhead of a cursor.
                    final V v = getDirect(readTxn, keyByteBuffer, predicate);
                    if (v != null) {
                        return v;
                    }

                    // We tried directly so now try looking beyond the provided key to see if there are any sequence
                    // appended keys.
                    return getWithCursor(readTxn, keyByteBuffer, predicate);
                }));
    }

    /**
     * Direct lookup for exact key, assuming that there are no sequence rows.
     */
    private V getDirect(final Txn<ByteBuffer> readTxn,
                        final ByteBuffer keyByteBuffer,
                        final Predicate<BBKV> predicate) {
        final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
        final BBKV kv = new BBKV(keyByteBuffer, valueByteBuffer);
        if (predicate.test(kv)) {
            return serde.getVal(kv);
        }
        return null;
    }

    /**
     * After trying and failing to get a value directly by exact key, iterate over any subsequent sequence rows that
     * may exist.
     */
    private V getWithCursor(final Txn<ByteBuffer> readTxn,
                            final ByteBuffer keyByteBuffer,
                            final Predicate<BBKV> predicate) {
        final KeyRange<ByteBuffer> keyRange =
                new KeyRange<>(KeyRangeType.FORWARD_GREATER_THAN, keyByteBuffer, keyByteBuffer);
        try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
            while (iterator.hasNext()
                   && !Thread.currentThread().isInterrupted()) {
                final BBKV kv = BBKV.create(iterator.next());

                // Stop iterating if we go beyond the prefix.
                if (!ByteBufferUtils.containsPrefix(kv.key(), keyByteBuffer)) {
                    return null;
                }

                if (predicate.test(kv)) {
                    return serde.getVal(kv);
                }
            }
        }
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        // Ensure we have fields for all expression criteria.
        final List<String> fields = ExpressionUtil.fields(criteria.getExpression());
        fields.forEach(fieldIndex::create);

        final ValueFunctionFactories<Val[]> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                .createOptional(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
        final Predicate<Val[]> predicate = optionalPredicate.orElse(vals -> true);
        final Function<KeyVal<ByteBuffer>, Val>[] valExtractors = serde.getValExtractors(fieldIndex);

        // TODO : It would be faster if we limit the iteration to keys based on the criteria.
        read(readTxn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final Val[] vals = new Val[valExtractors.length];
                    for (int i = 0; i < vals.length; i++) {
                        vals[i] = valExtractors[i].apply(keyVal);
                    }
                    if (predicate.test(vals)) {
                        consumer.accept(vals);
                    }
                }
            }
            return null;
        });
    }

    ValueFunctionFactories<Val[]> createValueFunctionFactories(final FieldIndex fieldIndex) {
        return fieldName -> {
            final Integer index = fieldIndex.getPos(fieldName);
            if (index == null) {
                throw new RuntimeException("Unexpected field: " + fieldName);
            }
            return new ValArrayFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    @Override
    public final long count() {
        return read(readTxn -> dbi.stat(readTxn).entries);
    }

    @Override
    public final boolean isReadOnly() {
        return readOnly;
    }

    private int readSchemaVersion(final Txn<ByteBuffer> txn) {
        int version = -1;
        try {
            final ByteBuffer valueBuffer = infoDbi.get(txn, InfoKey.SCHEMA_VERSION.getByteBuffer());
            if (valueBuffer != null) {
                version = valueBuffer.getInt();
            }

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return version;
    }

    private void writeSchemaVersion(final Txn<ByteBuffer> txn, final int schemaVersion) {
        byteBuffers.useInt(schemaVersion, byteBuffer -> {
            infoDbi.put(txn, InfoKey.SCHEMA_VERSION.getByteBuffer(), byteBuffer);
        });
    }

    @Override
    public final void close() {
        env.close();
    }

    private enum InfoKey implements HasPrimitiveValue {
        SCHEMA_VERSION(0),
        HASH_CLASHES(1);

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

    @Override
    public final String getInfo() {
        try {
            final Inf inf = read(txn -> {
                try {
                    final List<String> dbNames = env
                            .getDbiNames()
                            .stream()
                            .map(String::new)
                            .sorted()
                            .toList();
                    final EnvInf envInf = new EnvInf(env.stat(), env.info(), env.getMaxKeySize(), dbNames);

                    final Stat stat = read(dbi::stat);
                    final DbInf dbInf = new DbInf("db", stat);

                    return new Inf(envInf, Collections.singletonList(dbInf), isReadOnly(), readSchemaVersion(txn));
                } catch (final Exception e) {
                    LOGGER.debug(e::getMessage, e);
                }
                return null;
            });
            return JsonUtil.writeValueAsString(inf);
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }

    private record Inf(EnvInf env, List<DbInf> db, boolean readOnly, int schemaVersion) {

    }

    private record EnvInf(Stat stat, EnvInfo envInfo, int maxKeySize, List<String> dbNames) {

    }

    private record DbInf(String name, Stat stat) {

    }
}
