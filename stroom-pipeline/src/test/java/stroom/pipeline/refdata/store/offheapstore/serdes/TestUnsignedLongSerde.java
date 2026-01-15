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


import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.serde.UnsignedLong;
import stroom.lmdb.serde.UnsignedLongSerde;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class TestUnsignedLongSerde extends AbstractSerdeTest<UnsignedLong, UnsignedLongSerde> {

    private AtomicInteger length = new AtomicInteger(-1);

//    @Test
//    void testSerialisationDeserialisation() {
//        final UnsignedLong unsignedLong = UnsignedLong.of(1L, length.get());
//
//        doSerialisationDeserialisationTest(unsignedLong);
//    }

    @TestFactory
    Stream<DynamicTest> testSerialisationDeserialisation() {
        return IntStream.rangeClosed(1, 8)
                .boxed()
                .map(len ->
                        DynamicTest.dynamicTest("length " + len, () -> {
                            length.set(len);
                            final long val = UnsignedBytesInstances.ofLength(len).getMaxVal();
                            final UnsignedLong unsignedLong = UnsignedLong.of(val, len);

                            doSerialisationDeserialisationTest(unsignedLong);
                        }));
    }

    @Override
    TypeLiteral<UnsignedLongSerde> getSerdeType() {
        return new TypeLiteral<UnsignedLongSerde>(){};
    }

    @Override
    Supplier<UnsignedLongSerde> getSerdeSupplier() {
        return () ->
                new UnsignedLongSerde(length.get());
    }

    @Override
    UnsignedLongSerde getSerde() {
        // As we are using a TestFactory we need a fresh serde on each call to doSerialisationDeserialisationTest
        return getSerdeSupplier().get();
    }
}
