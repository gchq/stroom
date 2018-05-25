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

import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;

public class RefDataValueSerde implements
        Serde<RefDataValue>,
        Serializer<RefDataValue>,
        Deserializer<RefDataValue> {

    private final Map<Integer, Serde<RefDataValue>> typeToSerdeMap;

    @Inject
    RefDataValueSerde(final Map<Integer, Serde<RefDataValue>> typeToSerdeMap) {
        this.typeToSerdeMap = typeToSerdeMap;
    }

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {
        final int typeId = byteBuffer.get();
        return getSubSerde(typeId).deserialize(byteBuffer);
        // rely on the subSerde flipping the buffer
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        byteBuffer.put((byte) refDataValue.getTypeId());
        getSubSerde(refDataValue.getTypeId()).deserialize(byteBuffer);
        // rely on the subSerde flipping the buffer
    }

    private Serde<RefDataValue> getSubSerde(int typeId) {
        return Optional.ofNullable(typeToSerdeMap.get(typeId))
                .orElseThrow(() -> new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", typeId)));
    }

//    public static Class<? extends RefDataValue> determineType(final byte bTypeId) {
//        if (bTypeId == FastInfosetValue.TYPE_ID) {
//            return FastInfosetValue.class;
//        } else if (bTypeId == StringValue.TYPE_ID){
//            return StringValue.class;
//        } else {
//            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", Byte.toString(bTypeId)));
//        }
//    }
}
