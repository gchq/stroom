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


import stroom.pipeline.refdata.store.offheapstore.ValueStoreKey;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreKeySerde extends AbstractSerdeTest<ValueStoreKey, ValueStoreKeySerde> {

    @Test
    void testSerializeDeserialize() {
        final ValueStoreKey valueStoreKey = new ValueStoreKey(
                123456789L,
                (short) 1);

        doSerialisationDeserialisationTest(valueStoreKey);
    }

    @Test
    void testIncrementId() {
        final ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, (short) 123);

        final ByteBuffer byteBuffer = serialize(originalValueStoreKey);

        ValueStoreKeySerde.incrementId(byteBuffer);

        final ValueStoreKey newValueStoreKey = deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) (originalValueStoreKey.getUniqueId() + 1));
    }

    @Test
    void testUpdateId() {
        final ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, (short) 123);

        final ValueStoreKeySerde serde = new ValueStoreKeySerde();

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getBufferCapacity());

        serde.serialize(byteBuffer, originalValueStoreKey);

        ValueStoreKeySerde.updateId(byteBuffer, (short) 456);

        final ValueStoreKey newValueStoreKey = serde.deserialize(byteBuffer);

        assertThat(newValueStoreKey.getValueHashCode()).isEqualTo(originalValueStoreKey.getValueHashCode());
        assertThat(newValueStoreKey.getUniqueId()).isEqualTo((short) 456);
    }

    @Test
    void testExtractId() {
        final short id = 123;
        final ValueStoreKey originalValueStoreKey = new ValueStoreKey(1234567L, id);

        final ValueStoreKeySerde serde = new ValueStoreKeySerde();

        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(serde.getBufferCapacity());
        serde.serialize(byteBuffer, originalValueStoreKey);

        final short extractedId = ValueStoreKeySerde.extractId(byteBuffer);

        assertThat(extractedId).isEqualTo(id);
    }

    @Override
    TypeLiteral<ValueStoreKeySerde> getSerdeType() {
        return new TypeLiteral<ValueStoreKeySerde>(){};
    }
}
