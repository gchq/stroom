package stroom.planb.impl.io;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.LmdbEnvDir;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Format;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ValFunctionFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractLmdbReader<K, V> implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractLmdbReader.class);

    private static final byte[] NAME = "db".getBytes(UTF_8);
    private static final int CONCURRENT_READERS = 10;

    private final Semaphore concurrentReaderSemaphore;

    final ByteBufferFactory byteBufferFactory;
    private final Env<ByteBuffer> env;
    final Dbi<ByteBuffer> dbi;
    final Serde<K, V> serde;

    public AbstractLmdbReader(final Path path,
                              final ByteBufferFactory byteBufferFactory,
                              final Serde<K, V> serde) {
        final LmdbEnvDir lmdbEnvDir = new LmdbEnvDir(path, true);
        this.byteBufferFactory = byteBufferFactory;
        this.serde = serde;
        LOGGER.info(() -> "Opening: " + path);

        final Env.Builder<ByteBuffer> builder = Env.create()
                .setMapSize(LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes())
                .setMaxDbs(1)
                .setMaxReaders(CONCURRENT_READERS);

        env = builder.open(lmdbEnvDir.getEnvDir().toFile(),
                EnvFlags.MDB_NOTLS,
                EnvFlags.MDB_NOLOCK,
                EnvFlags.MDB_RDONLY_ENV);
        dbi = env.openDbi(NAME);
        concurrentReaderSemaphore = new Semaphore(CONCURRENT_READERS);
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
            LOGGER.error(e::getMessage, e);
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
                    final KeyRange<ByteBuffer> keyRange = KeyRange.closed(keyByteBuffer, keyByteBuffer);
                    try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                        while (iterator.hasNext()
                               && !Thread.currentThread().isInterrupted()) {
                            final KeyVal<ByteBuffer> keyVal = iterator.next();
                            if (predicate.test(keyVal)) {
                                return Optional.of(serde.getVal(keyVal));
                            }
                        }
                    }
                    return Optional.empty();
                }));
    }

    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        final ValueFunctionFactories<Val[]> valueFunctionFactories = createValueFunctionFactories(fieldIndex);
        final Optional<Predicate<Val[]>> optionalPredicate = expressionPredicateFactory
                .create(criteria.getExpression(), valueFunctionFactories, dateTimeSettings);
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
            return new ValFunctionFactory(Column.builder().format(Format.TEXT).build(), index);
        };
    }

    public long count() {
        return read(readTxn -> dbi.stat(readTxn).entries);
    }

    @Override
    public void close() {
        env.close();
    }
}
