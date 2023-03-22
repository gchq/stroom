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


import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreMetaSerde extends AbstractSerdeTest<ValueStoreMeta, ValueStoreMetaSerde> {

    @Test
    void testInitialRefCount() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(FastInfosetValue.TYPE_ID);
        assertThat(valueStoreMeta.getReferenceCount())
                .isEqualTo(1);
    }

    @Test
    void testSerializeDeserialize() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(FastInfosetValue.TYPE_ID, 123);

        doSerialisationDeserialisationTest(valueStoreMeta, ValueStoreMetaSerde::new);
    }

    @Test
    void testExtractTypeId() {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractTypeId, ValueStoreMeta::getTypeId);
    }

    @Test
    void testExtractReferenceCount() {

        ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractReferenceCount, ValueStoreMeta::getReferenceCount);
    }

    @Test
    void testIncrementRefCount() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(20);

        assertThat(valueStoreMeta.getReferenceCount())
                .isEqualTo(123);

        getSerde().serialize(byteBuffer1, valueStoreMeta);


        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(20);

        getSerde().cloneAndIncrementRefCount(byteBuffer1, byteBuffer2);

        final ValueStoreMeta valueStoreMeta2 = getSerde().deserialize(byteBuffer2);

        assertThat(valueStoreMeta2.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() + 1);


        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(20);

        getSerde().cloneAndIncrementRefCount(byteBuffer2, byteBuffer3);

        final ValueStoreMeta valueStoreMeta3 = getSerde().deserialize(byteBuffer3);

        assertThat(valueStoreMeta3.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() + 2)
                .isEqualTo(valueStoreMeta2.getReferenceCount() + 1);

    }

    @Test
    void testDecrementRefCount() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(20);

        assertThat(valueStoreMeta.getReferenceCount())
                .isEqualTo(123);

        getSerde().serialize(byteBuffer1, valueStoreMeta);


        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(20);

        getSerde().cloneAndDecrementRefCount(byteBuffer1, byteBuffer2);

        final ValueStoreMeta valueStoreMeta2 = getSerde().deserialize(byteBuffer2);

        assertThat(valueStoreMeta2.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() - 1);


        final ByteBuffer byteBuffer3 = ByteBuffer.allocate(20);

        getSerde().cloneAndDecrementRefCount(byteBuffer2, byteBuffer3);

        final ValueStoreMeta valueStoreMeta3 = getSerde().deserialize(byteBuffer3);

        assertThat(valueStoreMeta3.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() - 2)
                .isEqualTo(valueStoreMeta2.getReferenceCount() - 1);
    }

    @Override
    Class<ValueStoreMetaSerde> getSerdeType() {
        return ValueStoreMetaSerde.class;
    }
}
