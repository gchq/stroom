package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb2.LmdbKeySequence;
import stroom.planb.impl.serde.hash.Hash;
import stroom.planb.impl.serde.hash.HashClashCount;
import stroom.planb.impl.serde.hash.HashFactory;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class HashLookupDb {

    private final String name;
    private final ByteBuffers byteBuffers;
    private final Dbi<ByteBuffer> dbi;
    private final LmdbKeySequence lmdbKeySequence;
    private final HashFactory hashFactory;
    private final HashClashCount hashClashCount;

    public HashLookupDb(final PlanBEnv env,
                        final ByteBuffers byteBuffers,
                        final HashFactory hashFactory,
                        final HashClashCount hashClashCount,
                        final String name) {
        this.name = name;
        this.byteBuffers = byteBuffers;
        this.hashFactory = hashFactory;
        this.hashClashCount = hashClashCount;
        lmdbKeySequence = new LmdbKeySequence(byteBuffers);
        dbi = env.openDbi(name + "-hash", DbiFlags.MDB_CREATE);
    }

    public String getName() {
        return name;
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final ByteBuffer keyByteBuffer) {
        final ByteBuffer value = dbi.get(readTxn, keyByteBuffer);
        if (value == null) {
            final Hash hash = hashFactory.create(keyByteBuffer);
            throw new IllegalStateException("Unable to find value for hash: " + hash);
        }
        return value;
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
            final Predicate<ByteBuffer> matchPredicate = val ->
                    Objects.equals(val, valueByteBuffer);
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
        // See if we can find the existing KV pair.
        final Predicate<ByteBuffer> matchPredicate = val -> Objects.equals(val, valueByteBuffer);
        return lmdbKeySequence.find(
                dbi,
                writeTxn,
                keyByteBuffer,
                valueByteBuffer,
                matchPredicate,
                match -> {
                    final ByteBuffer foundKey = match.foundKey();
                    if (foundKey == null) {
                        // If there is 0 sequence number then just put.
                        if (match.nextSequenceNumber() == 0) {
                            if (!dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                                throw new RuntimeException("Unable to put");
                            }
                            return idConsumer.apply(keyByteBuffer);
                        }

                        // If we didn't find the item then insert it with a new sequence number.
                        return putAtNewSequenceNumber(
                                writeTxn,
                                keyByteBuffer,
                                valueByteBuffer,
                                match.nextSequenceNumber(),
                                idConsumer);
                    }

                    return idConsumer.apply(foundKey);
                });
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

    public void forEachHash(final Txn<ByteBuffer> readTxn, final Consumer<ByteBuffer> keyConsumer) {
        LmdbIterable.iterate(readTxn, dbi, (key, val) -> keyConsumer.accept(key));
    }

    public void deleteByHash(final Txn<ByteBuffer> writeTxn, final ByteBuffer key) {
        dbi.delete(writeTxn, key);
    }
}
