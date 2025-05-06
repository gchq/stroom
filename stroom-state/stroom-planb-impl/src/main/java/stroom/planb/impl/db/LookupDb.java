package stroom.planb.impl.db;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb2.BBKV;
import stroom.lmdb2.LmdbKeySequence;
import stroom.planb.impl.db.hash.Hash;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.state.PlanBEnv;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public class LookupDb {

    private final ByteBuffers byteBuffers;
    private final Dbi<ByteBuffer> dbi;
    private final LmdbKeySequence lmdbKeySequence;
    private final HashFactory hashFactory;
    private final HashClashCount hashClashCount;
    private final boolean overwrite;

    public LookupDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final HashFactory hashFactory,
                    final HashClashCount hashClashCount,
                    final String name,
                    final boolean overwrite) {
        this.byteBuffers = byteBuffers;
        this.hashFactory = hashFactory;
        this.hashClashCount = hashClashCount;
        this.overwrite = overwrite;
        lmdbKeySequence = new LmdbKeySequence(byteBuffers);
        dbi = env.openDbi(name, DbiFlags.MDB_CREATE);
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final ByteBuffer keyByteBuffer) {
        return dbi.get(readTxn, keyByteBuffer);
    }

    public <R> R get(final Txn<ByteBuffer> readTxn,
                     final byte[] bytes,
                     final Function<Optional<ByteBuffer>, R> idConsumer) {
        final Hash hash = hashFactory.create(bytes);
        return byteBuffers.use(hash.len(), keyByteBuffer -> {
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();

            return byteBuffers.useBytes(bytes, valueByteBuffer -> {
                return get(readTxn, keyByteBuffer, valueByteBuffer, idConsumer);
            });
        });
    }

    public <R> R get(final Txn<ByteBuffer> readTxn,
                     final ByteBuffer byteBuffer,
                     final Function<Optional<ByteBuffer>, R> idConsumer) {
        final Hash hash = hashFactory.create(byteBuffer);
        return byteBuffers.use(hash.len(), keyByteBuffer -> {
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();

            return get(readTxn, keyByteBuffer, byteBuffer, idConsumer);
        });
    }

    private <R> R get(final Txn<ByteBuffer> readTxn,
                      final ByteBuffer keyByteBuffer,
                      final ByteBuffer valueByteBuffer,
                      final Function<Optional<ByteBuffer>, R> idConsumer) {
        // First try to get direct.
        final ByteBuffer byteBuffer = dbi.get(readTxn, keyByteBuffer);
        if (byteBuffer != null && byteBuffer.equals(valueByteBuffer)) {
            return idConsumer.apply(Optional.of(keyByteBuffer));
        } else {
            // We didn't manage to get directly then search for the key.
            final Predicate<BBKV> matchPredicate = bbkv ->
                    ByteBufferUtils.containsPrefix(bbkv.key(), keyByteBuffer) &&
                    Objects.equals(bbkv.val(), valueByteBuffer);
            return lmdbKeySequence.find(
                    dbi,
                    readTxn,
                    keyByteBuffer,
                    valueByteBuffer,
                    matchPredicate,
                    match -> idConsumer.apply(Optional.ofNullable(match.foundKey())));
        }
    }

    public <R> R put(final Txn<ByteBuffer> writeTxn,
                     final byte[] bytes,
                     final Function<ByteBuffer, R> idConsumer) {
        final Hash hash = hashFactory.create(bytes);
        return byteBuffers.use(hash.len(), keyByteBuffer -> {
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();

            return byteBuffers.useBytes(bytes, valueByteBuffer -> {
                return put(writeTxn, keyByteBuffer, valueByteBuffer, idConsumer);
            });
        });
    }

    public <R> R put(final Txn<ByteBuffer> writeTxn,
                     final ByteBuffer byteBuffer,
                     final Function<ByteBuffer, R> idConsumer) {
        final Hash hash = hashFactory.create(byteBuffer);
        return byteBuffers.use(hash.len(), keyByteBuffer -> {
            hash.write(keyByteBuffer);
            keyByteBuffer.flip();

            return put(writeTxn, keyByteBuffer, byteBuffer, idConsumer);
        });
    }

    private <R> R put(final Txn<ByteBuffer> writeTxn,
                      final ByteBuffer keyByteBuffer,
                      final ByteBuffer valueByteBuffer,
                      final Function<ByteBuffer, R> idConsumer) {
        // First try to put without overwriting existing values.
        if (dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
            return idConsumer.apply(keyByteBuffer);

        } else {
            final Predicate<BBKV> matchPredicate = bbkv ->
                    ByteBufferUtils.containsPrefix(bbkv.key(), keyByteBuffer) &&
                    Objects.equals(bbkv.val(), valueByteBuffer);

            // We didn't manage to put so see if we can find the existing KV pair.
            return lmdbKeySequence.find(
                    dbi,
                    writeTxn,
                    keyByteBuffer,
                    valueByteBuffer,
                    matchPredicate,
                    match -> {
                        final ByteBuffer foundKey = match.foundKey();
                        if (foundKey == null) {
                            // If we didn't find the item then insert it with a new sequence
                            // number.
                            return putAtNewSequenceNumber(
                                    writeTxn,
                                    keyByteBuffer,
                                    valueByteBuffer,
                                    match.nextSequenceNumber(),
                                    idConsumer);

                        } else {
                            if (overwrite) {
                                // We need to copy the buffer to use it after delete.
                                return byteBuffers.use(foundKey.limit(), copy -> {
                                    copy.put(foundKey);
                                    copy.flip();
                                    dbi.delete(writeTxn, copy);
                                    if (!dbi.put(writeTxn,
                                            copy,
                                            valueByteBuffer,
                                            PutFlags.MDB_NOOVERWRITE)) {
                                        throw new RuntimeException("Unable to put after delete");
                                    }
                                    return idConsumer.apply(copy);
                                });
                            } else {
                                return idConsumer.apply(foundKey);
                            }
                        }
                    });
        }
    }

    private <R> R putAtNewSequenceNumber(final Txn<ByteBuffer> writeTxn,
                                         final ByteBuffer keyByteBuffer,
                                         final ByteBuffer valueByteBuffer,
                                         final long sequenceNumber,
                                         final Function<ByteBuffer, R> idConsumer) {
        // We must have had a hash clash here because we didn't find a row for the key even
        // though the db contains the key hash.
        hashClashCount.increment();

        return lmdbKeySequence.addSequenceNumber(
                keyByteBuffer,
                hashFactory.hashLength(),
                sequenceNumber,
                sequenceKeyBuffer -> {
                    if (!dbi.put(writeTxn, sequenceKeyBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                        throw new RuntimeException("Unable to put at sequence " + sequenceNumber);
                    }
                    return idConsumer.apply(sequenceKeyBuffer);
                });
    }
}
