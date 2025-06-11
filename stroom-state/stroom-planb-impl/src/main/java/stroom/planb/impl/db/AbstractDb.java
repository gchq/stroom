package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.BBKV;
import stroom.lmdb2.KV;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbKeySequence;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionUtil;
import stroom.query.api.v2.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValArrayFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.HasPrimitiveValue;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractDb<K, V> implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDb.class);
    private static final int CURRENT_SCHEMA_VERSION = 1;

    private static final byte[] NAME = "db".getBytes(UTF_8);
    private static final byte[] INFO_NAME = "info_db".getBytes(UTF_8);
    private static final int CONCURRENT_READERS = 10;

    private final Semaphore concurrentReaderSemaphore;

    private final LmdbKeySequence lmdbKeySequence;
    final Serde<K, V> serde;
    final ByteBufferFactory byteBufferFactory;
    final Env<ByteBuffer> env;
    final Dbi<ByteBuffer> dbi;
    final Dbi<ByteBuffer> infoDbi;
    private final DBWriter dbWriter;
    private final ReentrantLock writeTxnLock = new ReentrantLock();
    private final ReentrantLock dbCommitLock = new ReentrantLock();
    private final boolean readOnly;

    private final Runnable commitRunnable;

    public AbstractDb(final Path path,
                      final ByteBufferFactory byteBufferFactory,
                      final Serde<K, V> serde,
                      final Long mapSize,
                      final Boolean overwrite,
                      final boolean readOnly) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBufferFactory = byteBufferFactory;
        this.serde = serde;
        this.readOnly = readOnly;
        concurrentReaderSemaphore = new Semaphore(CONCURRENT_READERS);
        lmdbKeySequence = new LmdbKeySequence(byteBufferFactory);

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
            this.commitRunnable = null;

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
                // Do nothing special on commit.
                this.commitRunnable = () -> {
                };

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
                // Warn on hash clashes.
                final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
                this.commitRunnable = hashClashCommitRunnable;

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
                                            predicate,
                                            match -> {
                                                final ByteBuffer foundKey = match.foundKey();
                                                if (foundKey != null) {
                                                    // We need to copy the buffer to use it after delete.
                                                    final ByteBuffer copy = byteBufferFactory.acquire(foundKey.limit());
                                                    try {
                                                        copy.put(foundKey);
                                                        copy.flip();

                                                        dbi.delete(writeTxn, copy);
                                                        if (!dbi.put(writeTxn,
                                                                copy,
                                                                valueByteBuffer,
                                                                PutFlags.MDB_NOOVERWRITE)) {
                                                            throw new RuntimeException("Unable to put after delete");
                                                        }

                                                    } finally {
                                                        byteBufferFactory.release(copy);
                                                    }

                                                } else {
                                                    // If we didn't find the item then insert it with a new sequence
                                                    // number.
                                                    putAtNewSequenceNumber(
                                                            writeTxn,
                                                            keyByteBuffer,
                                                            valueByteBuffer,
                                                            hashClashCommitRunnable,
                                                            match.nextSequenceNumber());
                                                }
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
                                            predicate,
                                            match -> {
                                                // If we didn't find the item then insert it with a new sequence number.
                                                if (match.foundKey() == null) {
                                                    putAtNewSequenceNumber(
                                                            writeTxn,
                                                            keyByteBuffer,
                                                            valueByteBuffer,
                                                            hashClashCommitRunnable,
                                                            match.nextSequenceNumber());
                                                }
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
        });
    }

    private static class HashClashCommitRunnable implements Runnable {

        private int hashClashes;

        public void increment() {
            // We must have had a hash clash here because we didn't find a row for the key even
            // though the db contains the key hash.
            hashClashes++;
        }

        @Override
        public void run() {
            if (hashClashes > 0) {
                // We prob don't want to warn but will keep for now until we know how big the issue is.
                LOGGER.warn(() -> "We had " + hashClashes + " hash clashes since last commit");
                hashClashes = 0;
            }
        }
    }

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

    public void condense(final long condenseBeforeMs,
                         final long deleteBeforeMs) {
        // Don't condense by default.
    }

    boolean insert(final Writer writer,
                   final KV<K, V> kv) {
        return insert(writer, kv.key(), kv.val());
    }

    boolean insert(final Writer writer,
                   final K key,
                   final V value) {
        return serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createValueByteBuffer(key, value, valueByteBuffer ->
                        insert(writer, keyByteBuffer, valueByteBuffer)));
    }

    private boolean insert(final Writer writer,
                           final ByteBuffer keyByteBuffer,
                           final ByteBuffer valueByteBuffer) {
        dbWriter.write(writer.getWriteTxn(), keyByteBuffer, valueByteBuffer);
        writer.tryCommit();
        return true;
    }

    public static class Writer implements AutoCloseable {

        private final Env<ByteBuffer> env;
        private final ReentrantLock dbCommitLock;
        private final Runnable commitListener;
        private final ReentrantLock writeTxnLock;
        private Txn<ByteBuffer> writeTxn;
        private int commitCount = 0;

        public Writer(final Env<ByteBuffer> env,
                      final ReentrantLock dbCommitLock,
                      final Runnable commitListener,
                      final ReentrantLock writeTxnLock) {
            this.env = env;
            this.dbCommitLock = dbCommitLock;
            this.commitListener = commitListener;
            this.writeTxnLock = writeTxnLock;

            // We are only allowed a single write txn and we can only write with a single thread so ensure this is the
            // case.
            writeTxnLock.lock();
        }

        Txn<ByteBuffer> getWriteTxn() {
            if (writeTxn == null) {
                writeTxn = env.txnWrite();
            }
            return writeTxn;
        }

        void tryCommit() {
            commitCount++;
            if (commitCount > 10000) {
                commit();
            }
        }

        void commit() {
            dbCommitLock.lock();
            try {
                if (writeTxn != null) {
                    try {
                        writeTxn.commit();
                    } finally {
                        try {
                            writeTxn.close();
                        } finally {
                            writeTxn = null;
                        }
                    }
                }

                commitCount = 0;
                commitListener.run();
            } finally {
                dbCommitLock.unlock();
            }
        }

        @Override
        public void close() {
            try {
                commit();
            } finally {
                writeTxnLock.unlock();
            }
        }
    }

    Writer createWriter() {
        return new Writer(env, dbCommitLock, commitRunnable, writeTxnLock);
    }

    <T> T write(final Function<Writer, T> function) {
        try (final Writer writer = new Writer(env, dbCommitLock, commitRunnable, writeTxnLock)) {
            return function.apply(writer);
        }
    }

    void write(final Consumer<Writer> consumer) {
        try (final Writer writer = new Writer(env, dbCommitLock, commitRunnable, writeTxnLock)) {
            consumer.accept(writer);
        }
    }

    public void lockCommits(final Runnable runnable) {
        dbCommitLock.lock();
        try {
            runnable.run();
        } finally {
            dbCommitLock.unlock();
        }
    }

    <R> R read(final Function<Txn<ByteBuffer>, R> function) {
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

    public Optional<V> get(final K key) {
        return read(readTxn -> get(readTxn, key));
    }

    private Optional<V> get(final Txn<ByteBuffer> readTxn, final K key) {
        return serde.createKeyByteBuffer(key, keyByteBuffer ->
                serde.createPrefixPredicate(key, predicate -> {
                    // Just try to get directly first without the overhead of a cursor.
                    Optional<V> optional = getDirect(readTxn, keyByteBuffer, predicate);
                    if (optional.isPresent()) {
                        return optional;
                    }

                    // We tried directly so now try looking beyond the provided key to see if there are any sequence
                    // appended keys.
                    return getWithCursor(readTxn, keyByteBuffer, predicate);
                }));
    }

    /**
     * Direct lookup for exact key, assuming that there are no sequence rows.
     */
    private Optional<V> getDirect(final Txn<ByteBuffer> readTxn,
                                  final ByteBuffer keyByteBuffer,
                                  final Predicate<BBKV> predicate) {
        final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
        final BBKV kv = new BBKV(keyByteBuffer, valueByteBuffer);
        if (predicate.test(kv)) {
            return Optional.of(serde.getVal(kv));
        }
        return Optional.empty();
    }

    /**
     * After trying and failing to get a value directly by exact key, iterate over any subsequent sequence rows that
     * may exist.
     */
    private Optional<V> getWithCursor(final Txn<ByteBuffer> readTxn,
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
                    return Optional.empty();
                }

                if (predicate.test(kv)) {
                    return Optional.of(serde.getVal(kv));
                }
            }
        }
        return Optional.empty();
    }

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

    public long count() {
        return read(readTxn -> dbi.stat(readTxn).entries);
    }

    public boolean isReadOnly() {
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
        final ByteBuffer byteBuffer = byteBufferFactory.acquire(Integer.BYTES);
        try {
            byteBuffer.putInt(schemaVersion);
            byteBuffer.flip();
            infoDbi.put(txn, InfoKey.SCHEMA_VERSION.getByteBuffer(), byteBuffer);
        } finally {
            byteBufferFactory.release(byteBuffer);
        }
    }

    @Override
    public void close() {
        env.close();
    }

    private interface DBWriter {

        void write(Txn<ByteBuffer> writeTxn,
                   ByteBuffer keyByteBuffer,
                   ByteBuffer valueByteBuffer);
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
}
