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

package stroom.refdata.offheapstore;

import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;
import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

class RefDataValueSerde implements
        Serde<RefDataValue>,
        Serializer<RefDataValue>,
        Deserializer<RefDataValue> {

    @Override
    public RefDataValue deserialize(final ByteBuffer byteBuffer) {
        final byte typeId = byteBuffer.get();

        // deserialize according to the value of typeId
        final RefDataValue value;
        if (typeId == FastInfosetValue.TYPE_ID) {
            value = FastInfosetValue.fromByteBuffer(byteBuffer);
        } else if (typeId == StringValue.TYPE_ID){
            value = StringValue.fromByteBuffer(byteBuffer);
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", Byte.toString(typeId)));
        }

        return value;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        byteBuffer.put(refDataValue.getTypeId());
        refDataValue.putValue(byteBuffer);
        byteBuffer.flip();
    }

    public static Class<? extends RefDataValue> determineType(final byte bTypeId) {
        if (bTypeId == FastInfosetValue.TYPE_ID) {
            return FastInfosetValue.class;
        } else if (bTypeId == StringValue.TYPE_ID){
            return StringValue.class;
        } else {
            throw new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", Byte.toString(bTypeId)));
        }
    }
}
