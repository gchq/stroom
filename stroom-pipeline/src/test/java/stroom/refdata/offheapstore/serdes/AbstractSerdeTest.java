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

import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.ByteArrayUtils;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

class AbstractSerdeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSerdeTest.class);

    private static final int BYTE_BUFFER_SIZE = 10_000;

    <T> void doByteBufferModificationTest(final T inputObject,
                                          final T expectedOutputObject,
                                          final Supplier<Serde<T>> serdeSupplier,
                                          final BiConsumer<Serde<T>, ByteBuffer> byteBufferModifier) {

        // use two serde instances to be sure ser and de-ser are independent
        final Serde<T> serde1 = serdeSupplier.get();
        final Serde<T> serde2 = serdeSupplier.get();

        // allocate a buffer size bigger than we need
        final ByteBuffer byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);

        serde1.serialize(byteBuffer, inputObject);

        // modify the bytebuffer
        byteBufferModifier.accept(serde1, byteBuffer);

        T outputObject = serde2.deserialize(byteBuffer);

        LOGGER.debug("inputObject [{}]", inputObject);
        LOGGER.debug("expectedOutputObject [{}]", expectedOutputObject);
        LOGGER.debug("outputObject [{}]", outputObject);

        Assertions.assertThat(outputObject).isEqualTo(expectedOutputObject);

        T outputObject2 = serde2.deserialize(byteBuffer);

        // re-run the deser to ennsure the buffer is in the right position to be read from again
        Assertions.assertThat(outputObject2).isEqualTo(expectedOutputObject);
    }

    <T> void doSerialisationDeserialisationTest(T object, Supplier<Serde<T>> serdeSupplier) {
        // use two serde instances to be sure ser and de-ser are independent
        final Serde<T> serde1 = serdeSupplier.get();
        final Serde<T> serde2 = serdeSupplier.get();

        // allocate a buffer size bigger than we need
        final ByteBuffer byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);

        serde1.serialize(byteBuffer, object);

        LOGGER.debug(ByteArrayUtils.byteBufferInfo(byteBuffer));

        T object2 = serde2.deserialize(byteBuffer);

        LOGGER.debug("Object 1 [{}]", object);
        LOGGER.debug("Object 2 [{}]", object2);

        Assertions.assertThat(object2).isEqualTo(object);

        T object3 = serde2.deserialize(byteBuffer);

        // re-run the deser to ennsure the buffer is in the right position to be read from again
        Assertions.assertThat(object3).isEqualTo(object);

    }
}
