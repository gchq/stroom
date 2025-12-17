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

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Make sure T implements equals as {@link AbstractSerdeTest#doSerialisationDeserialisationTest(Object)} will do an
 * equality check. If your serde doesn't have a no-args constructor then implement
 * {@link AbstractSerdeTest#getSerdeSupplier()}.
 *
 * @param <T> The type of the object being (de-)serialised.
 * @param <S> The type of the serde.
 */
abstract class AbstractSerdeTest<T, S extends Serde<T>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSerdeTest.class);

    private static final int BYTE_BUFFER_SIZE = 10_000;

    private S serde = null;

    @BeforeEach
    void before() {
        // serde is kept for the life of the test
        serde = null;
    }

    /**
     * Assumes a no-arg ctor, override if serde doesn't have one.
     */
    Supplier<S> getSerdeSupplier() {
        return () -> {
            try {
                return (S) getSerdeType().getRawType().getConstructor().newInstance();
            } catch (final NoSuchMethodException
                           | InvocationTargetException
                           | InstantiationException
                           | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        };
    }

    abstract TypeLiteral<S> getSerdeType();

    S getSerde() {
        // serde is kept for the life of the test
        if (serde == null) {
            serde = getSerdeSupplier().get();
        }
        return serde;
    }

    ByteBuffer serialize(final T object) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(getSerde().getBufferCapacity());
        getSerde().serialize(byteBuffer, object);
        return byteBuffer;
    }

    T deserialize(final ByteBuffer byteBuffer) {
        return getSerde().deserialize(byteBuffer);
    }

    void doByteBufferModificationTest(final T inputObject,
                                      final T expectedOutputObject,
                                      final BiConsumer<S, ByteBuffer> byteBufferModifier) {
        doByteBufferModificationTest(inputObject, expectedOutputObject, this::getSerde, byteBufferModifier);

    }

    void doByteBufferModificationTest(final T inputObject,
                                      final T expectedOutputObject,
                                      final Supplier<S> serdeSupplier,
                                      final BiConsumer<S, ByteBuffer> byteBufferModifier) {

        // use two serde instances to be sure ser and de-ser are independent
        final S serde1 = serdeSupplier.get();
        final S serde2 = serdeSupplier.get();

        // allocate a buffer size bigger than we need
        final ByteBuffer byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);

        serde1.serialize(byteBuffer, inputObject);

        // modify the bytebuffer
        byteBufferModifier.accept(serde1, byteBuffer);

        final T outputObject = serde2.deserialize(byteBuffer);

        LOGGER.debug("inputObject [{}]", inputObject);
        LOGGER.debug("expectedOutputObject [{}]", expectedOutputObject);
        LOGGER.debug("outputObject [{}]", outputObject);

        assertThat(outputObject).isEqualTo(expectedOutputObject);

        final T outputObject2 = serde2.deserialize(byteBuffer);

        // re-run the deser to ennsure the buffer is in the right position to be read from again
        assertThat(outputObject2).isEqualTo(expectedOutputObject);
    }

    T doSerialisationDeserialisationTest(final T object) {
        return doSerialisationDeserialisationTest(object, this::getSerde);
    }

    T doSerialisationDeserialisationTest(final T object, final Supplier<Serde<T>> serdeSupplier) {
        // use two serde instances to be sure ser and de-ser are independent
        final Serde<T> serde1 = serdeSupplier.get();
        final Serde<T> serde2 = serdeSupplier.get();

        // allocate a buffer size bigger than we need
        final ByteBuffer byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE);

        serde1.serialize(byteBuffer, object);

        LOGGER.debug(ByteBufferUtils.byteBufferInfo(byteBuffer));

        final T object2 = serde2.deserialize(byteBuffer);

        LOGGER.debug("Object   [{}]", object);
        LOGGER.debug("Object 2 [{}]", object2);

        assertThat(object2).isEqualTo(object);

        final T object3 = serde2.deserialize(byteBuffer);

        // re-run the deser to ensure the buffer is in the right position to be read from again
        assertThat(object3).isEqualTo(object);

        // ensure hashcode work across ser-deser
        assertThat(object.hashCode()).isEqualTo(object2.hashCode());
        assertThat(object.hashCode()).isEqualTo(object3.hashCode());

        return object2;
    }


    /**
     * Used for testing the extraction of a single value from part of the serialised form.
     *
     * @param object            The object to be serialised
     * @param extractionFunc    The extraction method on the serde to use
     * @param expectedValueFunc The method on the object that gets the value being tested
     */
    <V> void doExtractionTest(final T object,
                              final Function<ByteBuffer, V> extractionFunc,
                              final Function<T, V> expectedValueFunc) {
        final ByteBuffer byteBuffer = serialize(object);
        final ByteBuffer byteBufferClone = byteBuffer.asReadOnlyBuffer();

        final V actualExtractedValue = extractionFunc.apply(byteBuffer);

        assertThat(actualExtractedValue).isEqualTo(expectedValueFunc.apply(object));

        // ensure bytebuffer has not been mutated in the process
        assertThat(byteBuffer).isEqualTo(byteBufferClone);
    }
}
