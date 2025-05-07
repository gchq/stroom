package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.BBKV;
import stroom.lmdb2.LmdbKeySequence;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.hash.Hash;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.state.StateSearchHelper.Context;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

class HashedKeySupport {

    private final PlanBEnv env;
    private final Dbi<ByteBuffer> dbi;
    private final ByteBuffers byteBuffers;
    private final HashFactory hashFactory;
    private final boolean overwrite;
    private final LmdbKeySequence lmdbKeySequence;
    private final HashClashCount hashClashCount;
    private final StateValueSerde stateValueSerde;

    public HashedKeySupport(final PlanBEnv env,
                            final Dbi<ByteBuffer> dbi,
                            final ByteBuffers byteBuffers,
                            final HashFactory hashFactory,
                            final boolean overwrite,
                            final HashClashCount hashClashCount,
                            final StateValueSerde stateValueSerde) {
        this.env = env;
        this.dbi = dbi;
        this.byteBuffers = byteBuffers;
        this.hashFactory = hashFactory;
        this.overwrite = overwrite;
        this.lmdbKeySequence = new LmdbKeySequence(byteBuffers);
        this.hashClashCount = hashClashCount;
        this.stateValueSerde = stateValueSerde;
    }

    public void insert(final LmdbWriter writer, final String key, final StateValue val) {
        // Split byte hash writing.
        // We've been told that we need to use a key hash so try that.
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        final Hash hash = hashFactory.create(bytes);
        byteBuffers.use(hash.len(), keyByteBuffer -> {
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();

            byteBuffers.use(Integer.BYTES + bytes.length + Byte.BYTES + val.getByteBuffer().limit(),
                    valueByteBuffer -> {
                        valueByteBuffer.putInt(bytes.length);
                        valueByteBuffer.put(bytes);
                        valueByteBuffer.put(val.getTypeId());
                        valueByteBuffer.put(val.getByteBuffer());
                        valueByteBuffer.flip();

                        hashedKeyPut(writer, keyByteBuffer, valueByteBuffer);
                    });
        });
    }

    private void hashedKeyPut(final LmdbWriter writer,
                              final ByteBuffer keyByteBuffer,
                              final ByteBuffer valueByteBuffer) {
        if (overwrite) {
            putAndOverwrite(writer, keyByteBuffer, valueByteBuffer);
        } else {
            put(writer, keyByteBuffer, valueByteBuffer);
        }
    }

    private void putAndOverwrite(final LmdbWriter writer,
                                 final ByteBuffer keyByteBuffer,
                                 final ByteBuffer valueByteBuffer) {
        // First try to put without overwriting existing values.
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
            // We didn't manage to put so see if we can find the existing KV pair.
            createPrefixPredicate(new BBKV(keyByteBuffer, valueByteBuffer),
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
                                    byteBuffers.use(foundKey.limit(), copy -> {
                                        copy.put(foundKey);
                                        copy.flip();

                                        dbi.delete(writeTxn, copy);
                                        if (dbi.put(writeTxn,
                                                copy,
                                                valueByteBuffer,
                                                PutFlags.MDB_NOOVERWRITE)) {
                                            writer.tryCommit();
                                        } else {
                                            throw new RuntimeException("Unable to put after delete");
                                        }
                                    });

                                } else {
                                    // If we didn't find the item then insert it with a new sequence
                                    // number.
                                    putAtNewSequenceNumber(
                                            writer,
                                            keyByteBuffer,
                                            valueByteBuffer,
                                            match.nextSequenceNumber());
                                }
                                return null;
                            }));
        }
    }

    private void put(final LmdbWriter writer,
                     final ByteBuffer keyByteBuffer,
                     final ByteBuffer valueByteBuffer) {
        // First try to put without overwriting existing values.
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
            // We didn't manage to put so see if we can find the existing KV pair.
            createPrefixPredicate(new BBKV(keyByteBuffer, valueByteBuffer),
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
                                            writer,
                                            keyByteBuffer,
                                            valueByteBuffer,
                                            match.nextSequenceNumber());
                                }
                                return null;
                            }));
        }
    }

    private void putAtNewSequenceNumber(final LmdbWriter writer,
                                        final ByteBuffer keyByteBuffer,
                                        final ByteBuffer valueByteBuffer,
                                        final long sequenceNumber) {
        // We must have had a hash clash here because we didn't find a row for the key even
        // though the db contains the key hash.
        hashClashCount.increment();

        lmdbKeySequence.addSequenceNumber(
                keyByteBuffer,
                hashFactory.hashLength(),
                sequenceNumber,
                sequenceKeyBuffer -> {
                    if (dbi.put(writer.getWriteTxn(), sequenceKeyBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                        writer.tryCommit();
                    } else {
                        throw new RuntimeException("Unable to put at sequence " + sequenceNumber);
                    }
                    return null;
                });
    }

    private void createPrefixPredicate(final BBKV kv, final Consumer<Predicate<BBKV>> consumer) {
        final int keyLength = kv.val().getInt(0);
        final ByteBuffer slice = kv.val().slice(0, Integer.BYTES + keyLength);
        consumer.accept(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), slice));
    }

    public StateValue get(final String key) {
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return env.read(readTxn -> get(readTxn, bytes));
    }

    private StateValue get(final Txn<ByteBuffer> readTxn, final byte[] key) {
        return createKeyByteBuffer(key, keyByteBuffer ->
                createPrefixPredicate(key, predicate -> {
                    // Just try to get directly first without the overhead of a cursor.
                    StateValue v = getDirect(readTxn, keyByteBuffer, predicate);
                    if (v != null) {
                        return v;
                    }

                    // We tried directly so now try looking beyond the provided key to see if there are any sequence
                    // appended keys.
                    return getWithCursor(readTxn, keyByteBuffer, predicate);
                }));
    }

    public <T> T createKeyByteBuffer(final byte[] key, final Function<ByteBuffer, T> function) {
        final Hash hash = hashFactory.create(key);
        return byteBuffers.use(hash.len(), keyByteBuffer -> {
            // Hash the key.
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();
            return function.apply(keyByteBuffer);
        });
    }

    public <R> R createPrefixPredicate(final byte[] key, final Function<Predicate<BBKV>, R> function) {
        return byteBuffers.use(Integer.BYTES + key.length, prefixByteBuffer -> {
            putPrefix(prefixByteBuffer, key);
            prefixByteBuffer.flip();

            return function.apply(keyVal -> ByteBufferUtils.containsPrefix(keyVal.val(), prefixByteBuffer));
        });
    }

    private void putPrefix(final ByteBuffer byteBuffer, final byte[] keyBytes) {
        byteBuffer.putInt(keyBytes.length);
        byteBuffer.put(keyBytes);
    }

    /**
     * Direct lookup for exact key, assuming that there are no sequence rows.
     */
    private StateValue getDirect(final Txn<ByteBuffer> readTxn,
                                 final ByteBuffer keyByteBuffer,
                                 final Predicate<BBKV> predicate) {
        final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
        final BBKV kv = new BBKV(keyByteBuffer, valueByteBuffer);
        if (predicate.test(kv)) {
            return getVal(kv);
        }
        return null;
    }

    /**
     * After trying and failing to get a value directly by exact key, iterate over any subsequent sequence rows that
     * may exist.
     */
    private StateValue getWithCursor(final Txn<ByteBuffer> readTxn,
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
                    return getVal(kv);
                }
            }
        }
        return null;
    }

    public StateValue getVal(final BBKV kv) {
        final ByteBuffer byteBuffer = kv.val();
        final int keyLength = byteBuffer.getInt(0);
        final byte typeId = byteBuffer.get(Integer.BYTES + keyLength);
        final int valueStart = Integer.BYTES + keyLength + Byte.BYTES;
        final ByteBuffer slice = byteBuffer.slice(valueStart, byteBuffer.limit() - valueStart);
        final byte[] valueBytes = ByteBufferUtils.toBytes(slice);
        return new StateValue(typeId, ByteBuffer.wrap(valueBytes));
    }

    public void merge(final Path source) {
        env.write(writer -> {
            final Env.Builder<ByteBuffer> builder = Env.create()
                    .setMaxDbs(1)
                    .setMaxReaders(1);
            try (final Env<ByteBuffer> sourceEnv = builder.open(source.toFile(),
                    EnvFlags.MDB_NOTLS,
                    EnvFlags.MDB_NOLOCK,
                    EnvFlags.MDB_RDONLY_ENV)) {
                final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(dbi.getName());
                try (final Txn<ByteBuffer> readTxn = sourceEnv.txnRead()) {
                    try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
                        for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                            merge(writer, keyVal);
                        }
                    }
                }
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    public void merge(final LmdbWriter writer,
                      final KeyVal<ByteBuffer> keyVal) {
        ByteBuffer key = keyVal.key();
        ByteBuffer value = keyVal.val();

        // Trim off any sequence number from the end of the hash.
        if (key.limit() > hashFactory.hashLength()) {
            key = key.slice(0, hashFactory.hashLength());
        }

        hashedKeyPut(writer, key, value);
    }

    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        final ValuesExtractor valuesExtractor = StateSearchHelper.createValuesExtractor(
                fieldIndex,
                getKeyExtractionFunction(),
                getStateValueExtractionFunction());
        StateSearchHelper.search(
                criteria,
                fieldIndex,
                dateTimeSettings,
                expressionPredicateFactory,
                consumer,
                valuesExtractor,
                env,
                dbi);
    }

    private Function<Context, Val> getKeyExtractionFunction() {
        return context -> {
            final ByteBuffer byteBuffer = context.kv().val();
            final int keyLength = byteBuffer.getInt(0);
            return ValString.create(ByteBufferUtils.toString(byteBuffer.slice(Integer.BYTES, keyLength)));
        };
    }

    private Function<Context, StateValue> getStateValueExtractionFunction() {
        return context -> {
            final ByteBuffer byteBuffer = context.kv().val();
            final int keyLength = byteBuffer.getInt(0);
            final int valueStart = Integer.BYTES + keyLength;
            return stateValueSerde.read(byteBuffer.slice(valueStart, byteBuffer.limit() - valueStart));
        };
    }
}
