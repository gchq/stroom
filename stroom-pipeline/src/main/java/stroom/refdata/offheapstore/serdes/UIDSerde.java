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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;
import stroom.refdata.offheapstore.UID;

import java.nio.ByteBuffer;

public class UIDSerde implements Serde<UID>, Serializer<UID>, Deserializer<UID> {

    @Override
    public UID deserialize(final ByteBuffer byteBuffer) {
        UID uid = getUid(byteBuffer);
        byteBuffer.flip();
        return uid;
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final UID uid) {
        writeUid(byteBuffer, uid);
        byteBuffer.flip();
    }

    /**
     * Reads a {@link UID} from the passed {@link ByteBuffer} advancing the position in
     * the process. The {@link ByteBuffer} is not flipped.
     * The returned buffer is just a view onto part of the original buffer.
     */
    public static UID getUid(final ByteBuffer byteBuffer) {
        // create a buffer that only covers the UID part, to allow for de-serialising a UID
        // from a buffer that contains other data

        final ByteBuffer dupBuffer = byteBuffer.duplicate();
        dupBuffer.limit(dupBuffer.position() + UID.UID_ARRAY_LENGTH);

        final UID uid = UID.wrap(dupBuffer);
        // no need to flip as we are just wrapping the original buffer

        //advance the position of the passed buffer now that we have read the UID from it
        byteBuffer.position(byteBuffer.position() + UID.UID_ARRAY_LENGTH);
        return uid;
    }

    /**
     * Reads a {@link UID} from the passed {@link ByteBuffer}. The passed {@link ByteBuffer}
     * is not muted in the process.
     * The returned buffer is just a view onto part of the original buffer.
     */
    public static UID extractUid(final ByteBuffer byteBuffer) {
        // create a buffer that only covers the UID part, to allow for de-serialising a UID
        // from a buffer that contains other data

        final ByteBuffer dupBuffer = byteBuffer.duplicate();
        dupBuffer.limit(dupBuffer.position() + UID.UID_ARRAY_LENGTH);

        final UID uid = UID.wrap(dupBuffer);
        // no need to flip as we are just wrapping the original buffer

        return uid;
    }

    /**
     * Writes a {@link UID} to the passed {@link ByteBuffer}, advancing the position but
     * not flipping it.
     */
    public static void writeUid(final ByteBuffer byteBuffer, final UID uid) {
        byteBuffer.put(uid.getBackingBuffer());
    }


    @Override
    public int getBufferCapacity() {
        return UID.UID_ARRAY_LENGTH;
    }

    public static class UIDKryoSerializer extends com.esotericsoftware.kryo.Serializer<UID> {

        {
            setAcceptsNull(true);
        }

        @Override
        public void write(final Kryo kryo, final Output output, final UID uid) {
            final ByteBuffer uidBuffer = uid.getBackingBuffer();
            while (uidBuffer.hasRemaining()) {
                output.writeByte(uidBuffer.get());
            }
        }

        @Override
        public UID read(final Kryo kryo, final Input input, final Class<UID> type) {
            // TODO cnstantly creating a new ByteBuffer here is not ideal
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH);
            for (int i = 0; i < 4; i++) {
                byteBuffer.put(input.readByte());
            }
            byteBuffer.flip();

            return UID.wrap(byteBuffer);
        }
    }
}
