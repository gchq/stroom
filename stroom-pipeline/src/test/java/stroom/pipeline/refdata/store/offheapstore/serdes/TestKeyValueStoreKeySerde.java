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

package stroom.pipeline.refdata.store.offheapstore.serdes;

import stroom.pipeline.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.util.ByteBufferUtils;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyValueStoreKeySerde extends AbstractSerdeTest<KeyValueStoreKey, KeyValueStoreKeySerde> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestKeyValueStoreKeySerde.class);

    @Test
    void serializeDeserialize() {
        final UID uid = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 1, 2, 3);
        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(
                uid,
                "myKey");

        doSerialisationDeserialisationTest(keyValueStoreKey);
    }

    @Test
    void serializeDeserialize_emptyString() {
        final UID uid = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 1, 2, 3);
        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(
                uid,
                "");

        doSerialisationDeserialisationTest(keyValueStoreKey);
    }


    @Test
    void testOutput() {

        // verify that we can directly use Output and Input classes to manage our own (de)ser
        ByteBuffer byteBuffer = ByteBuffer.allocate(20);
        LOGGER.info("{}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        Output output = new Output(new ByteBufferOutputStream(byteBuffer));
        LOGGER.info("{}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        output.writeString("MyTestString");
        output.flush();
        LOGGER.info("{}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        output.writeInt(1, true);
        output.flush();
        LOGGER.info("{}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        byteBuffer.flip();
        LOGGER.info("{}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        Input input = new Input(new ByteBufferInputStream(byteBuffer));
        String str = input.readString();
        int i = input.readInt(true);
        assertThat(str).isEqualTo("MyTestString");
        assertThat(i).isEqualTo(1);
    }

    @Test
    void testSerialiseWithoutKeyPart() {
        final ByteBuffer keyValueStoreKeyBuffer1 = ByteBuffer.allocate(20);
        final ByteBuffer keyValueStoreKeyBuffer2 = ByteBuffer.allocate(20);
        final ByteBuffer keyValueStoreKeyBuffer3 = ByteBuffer.allocate(20);

        final UID uid = UID.of(ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH), 0, 1, 2, 3);
        final KeyValueStoreKey keyValueStoreKey1 = new KeyValueStoreKey(uid, "key1");
        final KeyValueStoreKey keyValueStoreKey2 = new KeyValueStoreKey(uid, "key2");


        final KeyValueStoreKeySerde keyValueStoreKeySerde = new KeyValueStoreKeySerde();

        keyValueStoreKeySerde.serializeWithoutKeyPart(keyValueStoreKeyBuffer1, keyValueStoreKey1);
        keyValueStoreKeySerde.serializeWithoutKeyPart(keyValueStoreKeyBuffer2, keyValueStoreKey2);

        LOGGER.info("keyValueStoreKeyBuffer1 {}", ByteBufferUtils.byteBufferInfo(keyValueStoreKeyBuffer1));
        LOGGER.info("keyValueStoreKeyBuffer2 {}", ByteBufferUtils.byteBufferInfo(keyValueStoreKeyBuffer2));

        Assertions.assertThat(keyValueStoreKeyBuffer2)
                .isEqualByComparingTo(keyValueStoreKeyBuffer1);

        keyValueStoreKeySerde.serialize(keyValueStoreKeyBuffer3, keyValueStoreKey1);

        LOGGER.info("keyValueStoreKeyBuffer3 {}", ByteBufferUtils.byteBufferInfo(keyValueStoreKeyBuffer3));

        Assertions.assertThat(keyValueStoreKeyBuffer3)
                .isNotEqualByComparingTo(keyValueStoreKeyBuffer1);
    }

    @Override
    Class<KeyValueStoreKeySerde> getSerdeType() {
        return KeyValueStoreKeySerde.class;
    }
}