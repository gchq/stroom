/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.stream.LmdbIterable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class UidLookupDb {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UidLookupDb.class);

    private static final ByteBuffer MAX_ID_KEY = ByteBuffer.allocateDirect(1);

    static {
        MAX_ID_KEY.put((byte) 0);
        MAX_ID_KEY.flip();
    }

    private final String name;
    private final ByteBuffers byteBuffers;
    private final Dbi<ByteBuffer> keyToUidDbi;
    private final Dbi<ByteBuffer> uidToKeyDbi;
    private final Dbi<ByteBuffer> infoDbi;
    private long maxId;
    private final UnsignedBytesFactory unsignedBytesFactory;

    public UidLookupDb(final PlanBEnv env,
                       final ByteBuffers byteBuffers,
                       final String name) {
        this(env, byteBuffers, name, new VariableUnsignedBytesFactory());
    }

    public UidLookupDb(final PlanBEnv env,
                       final ByteBuffers byteBuffers,
                       final String name,
                       final UnsignedBytesFactory unsignedBytesFactory) {
        this.name = name;
        this.byteBuffers = byteBuffers;
        this.unsignedBytesFactory = unsignedBytesFactory;
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
            final ByteBuffer valueBuffer = infoDbi.get(txn, MAX_ID_KEY);
            if (valueBuffer != null) {
                id = valueBuffer.getLong();
            }
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
        }
        return id;
    }

    private void writeMaxId(final Txn<ByteBuffer> txn, final long id) {
        byteBuffers.useLong(id, valueByteBuffer -> {
            infoDbi.put(txn, MAX_ID_KEY, valueByteBuffer);
        });
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final ByteBuffer keyByteBuffer) {
        final ByteBuffer value = uidToKeyDbi.get(readTxn, keyByteBuffer);
        if (value == null) {
            final long uid = byteBufferToUid(keyByteBuffer);
            throw new IllegalStateException("Unable to find value for UID: " + uid);
        }
        return value;
    }

    public ByteBuffer getValue(final Txn<ByteBuffer> readTxn,
                               final long uid) {
        final ByteBuffer value = uidToByteBuffer(uid, byteBuffer -> uidToKeyDbi.get(readTxn, byteBuffer));
        if (value == null) {
            throw new IllegalStateException("Unable to find value for UID: " + uid);
        }
        return value;
    }

    public <R> R uidToByteBuffer(final long uid, final Function<ByteBuffer, R> function) {
        final UnsignedBytes unsignedBytes = unsignedBytesFactory.forValue(uid);
        return byteBuffers.use(unsignedBytes.length(), byteBuffer -> {
            unsignedBytes.put(byteBuffer, uid);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        });
    }

    private long byteBufferToUid(final ByteBuffer byteBuffer) {
        final UnsignedBytes unsignedBytes = unsignedBytesFactory.ofLength(byteBuffer.remaining());
        return unsignedBytes.get(byteBuffer);
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
            final UnsignedBytes unsignedBytes = unsignedBytesFactory.forValue(uid);
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
        LmdbIterable.iterate(readTxn, uidToKeyDbi, (key, val) -> keyConsumer.accept(key));
    }

    public void deleteByUid(final Txn<ByteBuffer> writeTxn, final ByteBuffer uid) {
        final ByteBuffer key = uidToKeyDbi.get(writeTxn, uid);
        keyToUidDbi.delete(writeTxn, key);
        uidToKeyDbi.delete(writeTxn, uid);
    }

    public interface UnsignedBytesFactory {

        UnsignedBytes ofLength(int length);

        UnsignedBytes forValue(long value);
    }

    public static class VariableUnsignedBytesFactory implements UnsignedBytesFactory {

        @Override
        public UnsignedBytes ofLength(final int length) {
            return UnsignedBytesInstances.ofLength(length);
        }

        @Override
        public UnsignedBytes forValue(final long value) {
            return UnsignedBytesInstances.forValue(value);
        }
    }

    public static class StaticUnsignedBytesFactory implements UnsignedBytesFactory {

        private final UnsignedBytes unsignedBytes;

        public StaticUnsignedBytesFactory(final UnsignedBytes unsignedBytes) {
            this.unsignedBytes = unsignedBytes;
        }

        @Override
        public UnsignedBytes ofLength(final int length) {
            return unsignedBytes;
        }

        @Override
        public UnsignedBytes forValue(final long value) {
            return unsignedBytes;
        }
    }
}
