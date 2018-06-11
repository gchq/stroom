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

    public static final int TYPE_ID_OFFSET = 0;
    public static final int TYPE_ID_BYTES = 1;

    private final Map<Integer, RefDatValueSubSerde> typeToSerdeMap;

    @Inject
    public RefDataValueSerde(final Map<Integer, RefDatValueSubSerde> typeToSerdeMap) {
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
        getSubSerde(refDataValue.getTypeId()).serialize(byteBuffer, refDataValue);
        // rely on the subSerde flipping the buffer
    }

    private RefDatValueSubSerde getSubSerde(int typeId) {
        return Optional.ofNullable(typeToSerdeMap.get(typeId))
                .orElseThrow(() -> new RuntimeException(LambdaLogger.buildMessage("Unexpected typeId value {}", typeId)));
    }

    /**
     * Compares the value portion of each of the passed {@link ByteBuffer} instances.
     * @return True if the bytes of the value portion of each buffer are equal
     */
    public boolean areValuesEqual(final ByteBuffer thisValue, final ByteBuffer thatValue) {
        final int thisTypeId = thisValue.get(TYPE_ID_OFFSET);
        final int thatTypeId = thatValue.get(TYPE_ID_OFFSET);
        if (thisTypeId != thatTypeId) {
            throw new RuntimeException(LambdaLogger.buildMessage("Type IDs do not match, this {}, that {}",
                    thisTypeId, thatTypeId));
        }
        return getSubSerde(thisTypeId).areValuesEqual(thisValue, thatValue);
    }

    public int updateReferenceCount(final ByteBuffer valueBuffer, int referenceCountDelta) {
        int typeId = valueBuffer.get(TYPE_ID_OFFSET);
        return getSubSerde(typeId).updateReferenceCount(valueBuffer, referenceCountDelta);
    }


}
