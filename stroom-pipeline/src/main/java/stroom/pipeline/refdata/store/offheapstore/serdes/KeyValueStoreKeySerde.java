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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Serde;
import stroom.pipeline.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;

public class KeyValueStoreKeySerde implements Serde<KeyValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreKeySerde.class);

    private static final int UID_OFFSET = 0;
    private static final int KEY_OFFSET = UID_OFFSET + UID.UID_ARRAY_LENGTH;

    @Override
    public KeyValueStoreKey deserialize(final ByteBuffer byteBuffer) {

        // This advances the position to aftet the UID
        final UID uid = UIDSerde.getUid(byteBuffer);

        try (final Input input = new Input(new ByteBufferInputStream(byteBuffer))) {
            final String key = input.readString();
            byteBuffer.flip();
            return new KeyValueStoreKey(uid, key);
        }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final KeyValueStoreKey keyValueStoreKey) {

        final ByteBuffer uidBuffer = keyValueStoreKey.getMapUid().getBackingBuffer();

        byteBuffer.put(uidBuffer);
        uidBuffer.rewind();

        try (final Output output = new Output(new ByteBufferOutputStream(byteBuffer))) {
            output.writeString(keyValueStoreKey.getKey());
        }
        byteBuffer.flip();
    }


    public void serializeWithoutKeyPart(final ByteBuffer byteBuffer, final KeyValueStoreKey key) {

        final int startPos = byteBuffer.position();

        serialize(byteBuffer, key);

        // set the limit to just after the UID part
        byteBuffer.limit(startPos + UID.UID_ARRAY_LENGTH);
    }

    /**
     * The returned UID is just a wrapper onto the passed {@link ByteBuffer}. If you need to use it outside
     * a txn/cursor then you will need to copy it.
     */
    public UID extractUid(final ByteBuffer byteBuffer) {
        return UIDSerde.extractUid(byteBuffer, UID_OFFSET);
    }

    /**
     * Copy the contents of sourceByteBuffer into destByteBuffer but with the supplied UID.
     */
    public static void copyWithNewUid(final ByteBuffer sourceByteBuffer,
                                      final ByteBuffer destByteBuffer,
                                      final UID newUid) {
        Objects.requireNonNull(sourceByteBuffer);
        Objects.requireNonNull(destByteBuffer);
        Objects.requireNonNull(newUid);

        if (destByteBuffer.remaining() < sourceByteBuffer.remaining()) {
            throw new RuntimeException(LogUtil.message("Insufficient remaining,\nsource: {},\ndest: {}",
                    ByteBufferUtils.byteBufferInfo(sourceByteBuffer),
                    ByteBufferUtils.byteBufferInfo(destByteBuffer)));
        }

        destByteBuffer.put(newUid.getBackingBuffer());
        final ByteBuffer keyPartBuffer = sourceByteBuffer.slice(
                KEY_OFFSET,
                sourceByteBuffer.remaining() - UID.UID_ARRAY_LENGTH);
        destByteBuffer.put(keyPartBuffer);
        destByteBuffer.flip();
        sourceByteBuffer.rewind();
    }
}
