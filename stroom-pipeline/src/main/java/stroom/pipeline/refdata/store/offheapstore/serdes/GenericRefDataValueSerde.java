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

import stroom.bytebuffer.PooledByteBufferOutputStream;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.UnknownRefDataValue;

import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class GenericRefDataValueSerde implements RefDataValueSerde {

    private final RefDataValueSerdeFactory refDataValueSerdeFactory;

    @Inject
    public GenericRefDataValueSerde(final RefDataValueSerdeFactory refDataValueSerdeFactory) {
        this.refDataValueSerdeFactory = refDataValueSerdeFactory;
    }

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {
        // To correctly deserialise you need to get the typeId from the
        // ValueStoreMetaDb to work out which delegate serde to use.
        return new UnknownRefDataValue(byteBuffer);
    }

    public RefDataValue deserialize(final ByteBuffer byteBuffer, final byte typeId) {
        return refDataValueSerdeFactory.deserialize(byteBuffer, typeId);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {

        // defer to the specific serde associated with the typeId
        final RefDataValueSerde serde = refDataValueSerdeFactory.get(refDataValue);
        serde.serialize(byteBuffer, refDataValue);
    }

    @Override
    public ByteBuffer serialize(final Supplier<ByteBuffer> byteBufferSupplier,
                                final RefDataValue refDataValue) {

        // defer to the specific serde associated with the typeId
        final RefDataValueSerde serde = refDataValueSerdeFactory.get(refDataValue);
        return serde.serialize(byteBufferSupplier, refDataValue);
    }

    @Override
    public ByteBuffer serialize(final PooledByteBufferOutputStream pooledByteBufferOutputStream,
                                final RefDataValue refDataValue) {
        // defer to the specific serde associated with the typeId
        final RefDataValueSerde serde = refDataValueSerdeFactory.get(refDataValue);
        return serde.serialize(pooledByteBufferOutputStream, refDataValue);
    }
}
