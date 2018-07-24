/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.ByteBufferUtils;
import stroom.refdata.offheapstore.KeyValueStoreKey;
import stroom.refdata.offheapstore.PooledByteBuffer;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;

public class KeyValueStoreDb extends AbstractLmdbDb<KeyValueStoreKey, ValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreDb.class);


    private static final String DB_NAME = "KeyValueStore";

    private final KeyValueStoreKeySerde keySerde;
    private final ValueStoreKeySerde valueSerde;

    @Inject
    KeyValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                    final ByteBufferPool byteBufferPool,
                    final KeyValueStoreKeySerde keySerde,
                    final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public void forEachEntry(final Txn<ByteBuffer> txn,
                             final UID mapUid,
                             final BiConsumer<ByteBuffer, ByteBuffer> entryConsumer) {

        try (PooledByteBuffer startKeyIncPooledBuffer = byteBufferPool.getBufferAsResource(lmdbEnvironment.getMaxKeySize());
             PooledByteBuffer endKeyExcPooledBuffer = byteBufferPool.getBufferAsResource(lmdbEnvironment.getMaxKeySize())) {

            final KeyRange<ByteBuffer> singleMapUidKeyRange = buildSingleMapUidKeyRange(
                    mapUid, startKeyIncPooledBuffer.getByteBuffer(), endKeyExcPooledBuffer.getByteBuffer());

            try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, singleMapUidKeyRange)) {
                for (final CursorIterator.KeyVal<ByteBuffer> keyVal : cursorIterator.iterable()) {

                    LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Found entry {} {}",
                            ByteBufferUtils.byteBufferInfo(keyVal.key()),
                            ByteBufferUtils.byteBufferInfo(keyVal.val())));

                    // pass the found kv pair from this entry to the consumer
                    entryConsumer.accept(keyVal.key(), keyVal.val());
                }
            }
        }
    }

    private KeyRange<ByteBuffer> buildSingleMapUidKeyRange(final UID mapUid,
                                                           final ByteBuffer startKeyIncBuffer,
                                                           final ByteBuffer endKeyExcBuffer) {
        final KeyValueStoreKey startKeyInc = new KeyValueStoreKey(mapUid, "");

        keySerde.serializeWithoutKeyPart(startKeyIncBuffer, startKeyInc);

        UID nextMapUid = mapUid.nextUid();
        final KeyValueStoreKey endKeyExc = new KeyValueStoreKey(nextMapUid, "");

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("Using range {} (inc) {} (exc)",
                ByteBufferUtils.byteBufferInfo(startKeyIncBuffer),
                ByteBufferUtils.byteBufferInfo(endKeyExcBuffer)));

        keySerde.serializeWithoutKeyPart(endKeyExcBuffer, endKeyExc);

        return KeyRange.closedOpen(startKeyIncBuffer, endKeyExcBuffer);
    }

    public interface Factory {
        KeyValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
