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

import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.UnknownRefDataValue;

import javax.inject.Inject;
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
        // to correctly deserialise you need to get the typeId from the
        // ValueStoreMetaDb to work out which serde to use. Therefore we deserialise
        // as an UnknownRefDataValue object
        return new UnknownRefDataValue(byteBuffer);
    }


    public RefDataValue deserialize(final ByteBuffer byteBuffer, final int typeId) {
        return refDataValueSerdeFactory.deserialize(byteBuffer, typeId);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {

        // defer to the specific serde associated with the typeId
        RefDataValueSerde serde = refDataValueSerdeFactory.get(refDataValue);

        serde.serialize(byteBuffer, refDataValue);
    }

    @Override
    public ByteBuffer serialize(final Supplier<ByteBuffer> byteBufferSupplier,
                                final RefDataValue refDataValue) {

        // defer to the specific serde associated with the typeId
        RefDataValueSerde serde = refDataValueSerdeFactory.get(refDataValue);
        return serde.serialize(byteBufferSupplier, refDataValue);
    }
}
