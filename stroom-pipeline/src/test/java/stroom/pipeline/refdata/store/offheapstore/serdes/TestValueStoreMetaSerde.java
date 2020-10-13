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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

class TestValueStoreMetaSerde extends AbstractSerdeTest<ValueStoreMeta, ValueStoreMetaSerde> {

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
        final ByteBuffer byteBuffer = ByteBuffer.allocate(20);

        getSerde().serialize(byteBuffer, valueStoreMeta);

        final ByteBuffer newBuffer = ByteBuffer.allocate(20);

        getSerde().cloneAndIncrementRefCount(byteBuffer, newBuffer);

        final ValueStoreMeta valueStoreMeta2 = getSerde().deserialize(newBuffer);

        Assertions.assertThat(valueStoreMeta2.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() + 1);
    }

    @Test
    void testDecrementRefCount() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(20);

        getSerde().serialize(byteBuffer, valueStoreMeta);

        final ByteBuffer newBuffer = ByteBuffer.allocate(20);

        getSerde().cloneAndDecrementRefCount(byteBuffer, newBuffer);

        final ValueStoreMeta valueStoreMeta2 = getSerde().deserialize(newBuffer);

        Assertions.assertThat(valueStoreMeta2.getReferenceCount())
                .isEqualTo(valueStoreMeta.getReferenceCount() - 1);
    }

    @Override
    Class<ValueStoreMetaSerde> getSerdeType() {
        return ValueStoreMetaSerde.class;
    }
}