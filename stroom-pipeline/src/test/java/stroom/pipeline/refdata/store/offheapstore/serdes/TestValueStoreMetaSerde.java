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


import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.ValueStoreMeta;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestValueStoreMetaSerde extends AbstractSerdeTest<ValueStoreMeta, ValueStoreMetaSerde> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestValueStoreMetaSerde.class);

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

        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractTypeId, ValueStoreMeta::getTypeId);
    }

    @Test
    void testExtractReferenceCount() {

        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(StringValue.TYPE_ID, 123);
        doExtractionTest(valueStoreMeta, getSerde()::extractReferenceCount, ValueStoreMeta::getReferenceCount);
    }

    @TestFactory
    Stream<DynamicTest> testIsLastReference() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(int.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(
                            StringValue.TYPE_ID,
                            testCase.getInput());
                    final ByteBuffer byteBuffer = serialize(valueStoreMeta);
                    return getSerde().isLastReference(byteBuffer);
                })
                .withSimpleEqualityAssertion()
                .addCase(0, true)
                .addCase(1, true)
                .addCase(2, false)
                .addCase(3, false)
                .addCase(8, false)
                .addCase(10, false)
                .addCase(100, false)
                .addCase(1000, false)
                .addCase(10000, false)
                .addCase(16_000_000, false)
                .build();
    }

    @Disabled // Manual perf test
    @Test
    void testIsLastReference_perf() {
        final ValueStoreMeta valueStoreMeta = new ValueStoreMeta(
                StringValue.TYPE_ID, 2);
        final ByteBuffer byteBuffer = serialize(valueStoreMeta);
        final ValueStoreMetaSerde serde = getSerde();
        final int count = 100_000_000;

        LOGGER.logDurationIfInfoEnabled(() -> {
            for (int i = 0; i < count; i++) {
                final boolean lastReference = serde.isLastReference(byteBuffer);
            }
        }, "isLastReference");

        LOGGER.logDurationIfInfoEnabled(() -> {
            for (int i = 0; i < count; i++) {
                final int refCount = serde.extractReferenceCount(byteBuffer);
                final boolean lastReference = refCount <= 1;
            }
        }, "extractReferenceCount");
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
    TypeLiteral<ValueStoreMetaSerde> getSerdeType() {
        return new TypeLiteral<ValueStoreMetaSerde>() {
        };
    }
}
