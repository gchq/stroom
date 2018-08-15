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

package stroom.refdata.offheapstore.serdes;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.KeyValueStoreKey;
import stroom.refdata.offheapstore.UID;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;

public class KeyValueStoreKeySerde implements Serde<KeyValueStoreKey> {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyValueStoreKeySerde.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(KeyValueStoreKeySerde.class);

    @Override
    public KeyValueStoreKey deserialize(final ByteBuffer byteBuffer) {
        ByteBuffer dupBuffer = byteBuffer.duplicate();

        dupBuffer.limit(byteBuffer.position() + UID.UID_ARRAY_LENGTH);

        UID uid = UID.wrap(dupBuffer);
        // advance the position now we have a dup of the UID portion
        byteBuffer.position(byteBuffer.position() + UID.UID_ARRAY_LENGTH);

        try (Input input = new Input(new ByteBufferInputStream(byteBuffer))) {
            String key = input.readString();
            byteBuffer.flip();
            return new KeyValueStoreKey(uid, key);
        }
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final KeyValueStoreKey keyValueStoreKey) {

        final ByteBuffer uidBuffer = keyValueStoreKey.getMapUid().getBackingBuffer();

        byteBuffer.put(uidBuffer);
        uidBuffer.rewind();

        try (Output output = new Output(new ByteBufferOutputStream(byteBuffer))) {
            output.writeString(keyValueStoreKey.getKey());
        }
        byteBuffer.flip();
    }


    public void serializeWithoutKeyPart(final ByteBuffer byteBuffer, final KeyValueStoreKey key) {

        int startPos = byteBuffer.position();

        serialize(byteBuffer, key);

        // set the limit to just after the UID part
        byteBuffer.limit(startPos + UID.UID_ARRAY_LENGTH);
    }
}
