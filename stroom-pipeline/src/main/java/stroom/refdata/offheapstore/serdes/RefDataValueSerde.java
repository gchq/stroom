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
import stroom.refdata.offheapstore.TypedByteBuffer;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

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
        return mapWithSubSerde(byteBuffer, (subSerde, subBuffer) ->
                subSerde.deserialize(subBuffer));
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final RefDataValue refDataValue) {
        byteBuffer.put((byte) refDataValue.getTypeId());
        getSubSerde(refDataValue.getTypeId()).serialize(byteBuffer, refDataValue);
        // rely on the subSerde flipping the buffer
    }

    private <T> T mapWithSubSerde(final ByteBuffer byteBuffer,
                                  final BiFunction<RefDatValueSubSerde, ByteBuffer, T> subBufferFunction) {
        // work out what type of serde we need from the source buffer
        // then create a new buffer from the content after the type ID
        // put the source buffer back to how it was
        int typeId = getTypeId(byteBuffer);
        ByteBuffer subBuffer = byteBuffer.slice();
        byteBuffer.rewind();
        RefDatValueSubSerde subSerde = getSubSerde(typeId);

        return subBufferFunction.apply(subSerde, subBuffer);
    }

    private RefDatValueSubSerde getSubSerde(int typeId) {
        RefDatValueSubSerde subSerde = typeToSerdeMap.get(typeId);
        Objects.requireNonNull(subSerde, () ->
                LambdaLogger.buildMessage("Unexpected typeId value {}", typeId));
        return subSerde;
    }

    ByteBuffer getSubBuffer(final ByteBuffer byteBuffer) {
        byteBuffer.position(TYPE_ID_BYTES);
        ByteBuffer subBuffer = byteBuffer.slice();
        byteBuffer.rewind();
        return subBuffer;
    }

    RefDatValueSubSerde getSubSerde(final ByteBuffer byteBuffer) {
        return getSubSerde(extractTypeId(byteBuffer));
    }

    /**
     * Compares the value portion of each of the passed {@link ByteBuffer} instances.
     *
     * @return True if the bytes of the value portion of each buffer are equal
     */
    public boolean areValuesEqual(final ByteBuffer thisBuffer, final ByteBuffer thatBuffer) {
        final int thisTypeId = getTypeId(thisBuffer);
        final int thatTypeId = getTypeId(thatBuffer);

        if (thisTypeId != thatTypeId) {
            throw new RuntimeException(LambdaLogger.buildMessage("Type IDs do not match, this {}, that {}",
                    thisTypeId, thatTypeId));
        }
        final ByteBuffer thisSubBuffer = thisBuffer.slice();
        final ByteBuffer thatSubBuffer = thatBuffer.slice();
        // put the original buffers back to where they were
        thisBuffer.rewind();
        thatBuffer.rewind();

        // slice the buffers so the sub-serdes only see the content they know about
        return getSubSerde(thisTypeId).areValuesEqual(thisSubBuffer, thatSubBuffer);
    }

    public int updateReferenceCount(final ByteBuffer valueBuffer, final int referenceCountDelta) {
        final int typeId = getTypeId(valueBuffer);
        final ByteBuffer subBuffer = valueBuffer.slice();
        valueBuffer.rewind();
        return getSubSerde(typeId).updateReferenceCount(subBuffer, referenceCountDelta);
    }

    public int incrementReferenceCount(final ByteBuffer valueBuffer) {
        return updateReferenceCount(valueBuffer, 1);
    }

    public int decrementReferenceCount(final ByteBuffer valueBuffer) {
        return updateReferenceCount(valueBuffer, -1);
    }

    /**
     * Extracts the type ID from passed {@link ByteBuffer}. Does not change the buffer's position.
     */
    public static int extractTypeId(final ByteBuffer valueBuffer) {
        return valueBuffer.getInt(TYPE_ID_OFFSET);
    }

    /**
     * Gets the type ID from passed {@link ByteBuffer}. Moves the buffer's position past the type id.
     * Assumes the buffer is in the correct position.
     */
    public static int getTypeId(final ByteBuffer valueBuffer) {
        // single byte cast to int
        return valueBuffer.get();
    }

    public ByteBuffer extractValueBuffer(final ByteBuffer byteBuffer) {
        return mapWithSubSerde(byteBuffer, (subSerde, subBuffer) ->
                subSerde.extractValueBuffer(subBuffer));
    }

    public TypedByteBuffer extractTypedValueBuffer(final ByteBuffer byteBuffer) {
        int typeId = getTypeId(byteBuffer);
        ByteBuffer subBuffer = byteBuffer.slice();
        byteBuffer.rewind();
        ByteBuffer valueBuffer = getSubSerde(typeId).extractValueBuffer(subBuffer);
        return new TypedByteBuffer(typeId, valueBuffer);
    }
}
