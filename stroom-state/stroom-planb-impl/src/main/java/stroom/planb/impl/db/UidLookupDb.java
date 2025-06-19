package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UidLookupDb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UidLookupDb.class);

    private final String name;
    private final ByteBuffers byteBuffers;
    private final Dbi<ByteBuffer> keyToUidDbi;
    private final Dbi<ByteBuffer> uidToKeyDbi;
    private final Dbi<ByteBuffer> infoDbi;
    private long maxId;

    public UidLookupDb(final PlanBEnv env,
                       final ByteBuffers byteBuffers,
                       final String name) {
        this.name = name;
        this.byteBuffers = byteBuffers;
        keyToUidDbi = env.openDbi(name + "-keyToUid", DbiFlags.MDB_CREATE);
        uidToKeyDbi = env.openDbi(name + "-uidToKey", DbiFlags.MDB_CREATE);
        infoDbi = env.openDbi(name + "-info", DbiFlags.MDB_CREATE);
        maxId = env.read(this::readMaxId);
    }

    public String getName() {
        return name;
    }

    private long readMaxId(final Txn<ByteBuffer> txn) {
        long id = 0;
        try {
            id = byteBuffers.useByte((byte) 0, keyByteBuffer -> {
                final ByteBuffer valueBuffer = infoDbi.get(txn, keyByteBuffer);
                if (valueBuffer != null) {
                    return valueBuffer.getLong();
                } else {
                    return 0L;
                }
            });

        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return id;
    }

    private void writeMaxId(final Txn<ByteBuffer> txn, final long id) {
        byteBuffers.useByte((byte) 0, keyByteBuffer -> {
            byteBuffers.useLong(id, valueByteBuffer -> {
                infoDbi.put(txn, keyByteBuffer, valueByteBuffer);
            });
        });
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final ByteBuffer keyByteBuffer) {
        return uidToKeyDbi.get(readTxn, keyByteBuffer);
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final long uid) {
        return uidToByteBuffer(uid, byteBuffer -> uidToKeyDbi.get(readTxn, byteBuffer));
    }

    public <R> R uidToByteBuffer(final long uid, final Function<ByteBuffer, R> function) {
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(uid);
        return byteBuffers.use(unsignedBytes.length(), byteBuffer -> {
            unsignedBytes.put(byteBuffer, uid);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        });
    }

    public <R> R get(final Txn<ByteBuffer> readTxn,
                     final ByteBuffer byteBuffer,
                     final Function<Optional<ByteBuffer>, R> idConsumer) {
        final ByteBuffer uidByteBuffer = keyToUidDbi.get(readTxn, byteBuffer.duplicate());
        return idConsumer.apply(Optional.ofNullable(uidByteBuffer));
    }

    public <R> R put(final Txn<ByteBuffer> writeTxn,
                     final ByteBuffer keyByteBuffer,
                     final Function<ByteBuffer, R> idConsumer) {
        KeyLength.check(keyByteBuffer, Db.MAX_KEY_LENGTH);

        // See if we already have this key.
        final ByteBuffer existingUidByteBuffer = keyToUidDbi.get(writeTxn, keyByteBuffer);
        if (existingUidByteBuffer != null) {
            return idConsumer.apply(existingUidByteBuffer);

        } else {
            final long uid = ++maxId;
            final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(uid);
            return byteBuffers.use(unsignedBytes.length(), uidByteBuffer -> {
                unsignedBytes.put(uidByteBuffer, uid);
                uidByteBuffer.flip();

                keyToUidDbi.put(writeTxn, keyByteBuffer, uidByteBuffer);
                uidToKeyDbi.put(writeTxn, uidByteBuffer, keyByteBuffer);
                writeMaxId(writeTxn, maxId);

                return idConsumer.apply(uidByteBuffer);
            });
        }
    }

    public void forEachUid(final Txn<ByteBuffer> readTxn, final Consumer<ByteBuffer> keyConsumer) {
        try (final CursorIterable<ByteBuffer> cursor = uidToKeyDbi.iterate(readTxn)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
            while (iterator.hasNext()
                   && !Thread.currentThread().isInterrupted()) {
                final KeyVal<ByteBuffer> kv = iterator.next();
                keyConsumer.accept(kv.key());
            }
        }
    }

    public void deleteByUid(final Txn<ByteBuffer> writeTxn, final ByteBuffer uid) {
        final ByteBuffer key = uidToKeyDbi.get(writeTxn, uid);
        keyToUidDbi.delete(writeTxn, key);
        uidToKeyDbi.delete(writeTxn, uid);
    }
}
