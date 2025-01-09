package stroom.planb.impl.experiment;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.planb.impl.io.State.Key;
import stroom.planb.impl.io.StateValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.openhft.hashing.LongHashFunction;
import org.lmdbjava.Dbi;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

public class StateWriter2 extends AbstractLmdbWriter2<Key, StateValue> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateWriter2.class);

    public StateWriter2(final Path path,
                        final ByteBufferFactory byteBufferFactory,
                        final boolean keepFirst) {
        super(path, byteBufferFactory, new StateSerde2(byteBufferFactory), keepFirst);
    }

    public <T> T createKeyByteBuffer(final Key key, final Function<ByteBuffer, T> function) {
        final ByteBuffer keyByteBuffer = byteBufferFactory.acquire(Long.BYTES);
        try {
            // Hash the key.
            final long keyHash = LongHashFunction.xx3().hashBytes(key.bytes());
            keyByteBuffer.putLong(keyHash);
            keyByteBuffer.flip();
            return function.apply(keyByteBuffer);
        } finally {
            byteBufferFactory.release(keyByteBuffer);
        }
    }

    public boolean insert(final Key key,
                          final StateValue value) {
        final ByteBuffer keyFingerprintByteBuffer = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);
        final ByteBuffer valueFingerprintByteBuffer = byteBufferFactory.acquire(Long.BYTES + Long.BYTES);

        try {
            final Txn<ByteBuffer> writeTxn = getOrCreateWriteTxn();

            // Insert the key into the key table.
            final ByteBuffer valueByteBuffer1 = byteBufferFactory.acquire(key.bytes().length);
            try {
                valueByteBuffer1.put(key.bytes());
                valueByteBuffer1.flip();

                // Hash the key.
                final long keyHash = LongHashFunction.xx3().hashBytes(key.bytes());

                putUntilUnique(writeTxn, keyDb, keyFingerprintByteBuffer, valueByteBuffer1, keyHash);
            } finally {
                byteBufferFactory.release(valueByteBuffer1);
            }

            // Insert the value into the value table.
            final ByteBuffer valueByteBuffer2 = byteBufferFactory.acquire(Byte.BYTES + value.byteBuffer().limit());
            try {
                valueByteBuffer2.put(value.typeId());
                valueByteBuffer2.put(value.byteBuffer().duplicate());
                valueByteBuffer2.flip();

                // Hash the value.
                final long valueHash = LongHashFunction.xx3().hashBytes(value.byteBuffer());

                putUntilUnique(writeTxn, valueDb, valueFingerprintByteBuffer, valueByteBuffer2, valueHash);
            } finally {
                byteBufferFactory.release(valueByteBuffer2);
            }

            // Now insert the state record.
            if (keepFirst) {
                // If we are keeping the first then don't allow overwrite.
                stateDb.put(writeTxn, keyFingerprintByteBuffer, valueFingerprintByteBuffer, PutFlags.MDB_NOOVERWRITE);
            } else {
                // Put and overwrite any existing key/value.
                stateDb.put(writeTxn, keyFingerprintByteBuffer, valueFingerprintByteBuffer);
            }

        } finally {
            byteBufferFactory.release(keyFingerprintByteBuffer);
            byteBufferFactory.release(valueFingerprintByteBuffer);
        }

        commitCount++;
        if (commitCount > 10000) {
            commit();
            commitCount = 0;
        }

        return true;
    }

    private void putUntilUnique(final Txn<ByteBuffer> writeTxn,
                                final Dbi<ByteBuffer> dbi,
                                final ByteBuffer keyBytebuffer,
                                final ByteBuffer valueByteBuffer,
                                final long valueHash) {
        boolean success = false;
        long id = 0;
        while (!success) {
            keyBytebuffer.putLong(valueHash);
            keyBytebuffer.putLong(id++);
            keyBytebuffer.flip();

            // Try to put.
            if (!dbi.put(writeTxn, keyBytebuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
                // If we couldn't put then it might be because it already exists.
                final ByteBuffer val = dbi.get(writeTxn, keyBytebuffer);
                if (Objects.equals(val, valueByteBuffer)) {
                    LOGGER.debug(() -> "Value match");
                    success = true;
                } else {
                    LOGGER.warn(() -> "Hash clash");
                }
            } else {
                success = true;
            }
        }
    }

//    public boolean insert(final ByteBuffer keyByteBuffer,
//                          final ByteBuffer valueByteBuffer) {
//        final Txn<ByteBuffer> writeTxn = getOrCreateWriteTxn();
//
//        // If we do not prefix values then we can simply put rows.
//
//
//
//        // If the value has no key prefix, i.e. we are not using key hashes then just try to put.
//        if (keepFirst) {
//            // If we are keeping the first then don't allow overwrite.
//            stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE);
//        } else {
//            // Put and overwrite any existing key/value.
//            stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
//        }
////        } else {
////            // Try to put without overwriting existing values.
////            if (!stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer, PutFlags.MDB_NOOVERWRITE)) {
////                serde.createPrefixPredicate(keyByteBuffer, valueByteBuffer, predicate -> {
////                    if (keepFirst) {
////                        if (!exists(writeTxn, keyByteBuffer, predicate)) {
////                            stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
////                        }
////                    } else {
////                        // Delete current value if there is one.
////                        delete(writeTxn, keyByteBuffer, predicate);
////                        // Put new value allowing for duplicate keys as we are only using a hash key.
////                        stateDb.put(writeTxn, keyByteBuffer, valueByteBuffer);
////                    }
////                    return true;
////                });
////            }
////        }
//
//        commitCount++;
//        if (commitCount > 10000) {
//            commit();
//            commitCount = 0;
//        }
//
//        return true;
//    }
}
