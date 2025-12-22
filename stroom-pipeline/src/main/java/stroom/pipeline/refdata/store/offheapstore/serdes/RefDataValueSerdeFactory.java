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
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.StringValue;

import java.nio.ByteBuffer;

public class RefDataValueSerdeFactory {

    private static final RefDataValueSerde[] SERDES;

    static {
        SERDES = new RefDataValueSerde[4];
        SERDES[FastInfosetValue.TYPE_ID] = new FastInfoSetValueSerde();
        SERDES[StringValue.TYPE_ID] = new StringValueSerde();
        SERDES[NullValue.TYPE_ID] = new NullValueSerde();
    }

    public RefDataValueSerde get(final byte typeId) {
        if (typeId < 0 || typeId >= SERDES.length) {
            throw new RuntimeException("Unexpected typeId " + typeId);
        }

        final RefDataValueSerde serde = SERDES[typeId];
        if (serde == null) {
            throw new RuntimeException("Unexpected typeId " + typeId);
        }
        return serde;
    }

    public RefDataValueSerde get(final RefDataValue refDataValue) {
        return get(refDataValue.getTypeId());
    }

    public RefDataValue deserialize(final ByteBuffer byteBuffer, final byte typeId) {
        return get(typeId).deserialize(byteBuffer);
    }
}
