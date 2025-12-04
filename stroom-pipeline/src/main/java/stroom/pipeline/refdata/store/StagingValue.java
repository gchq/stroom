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

package stroom.pipeline.refdata.store;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * <pre>{@code
 * <type ID int><hash code long><value bytes................>
 * }</pre>
 */
public interface StagingValue extends RefDataValue {

    int TYPE_ID_OFFSET = 0;
    int VALUE_HASH_OFFSET = TYPE_ID_OFFSET + Byte.BYTES;
    int VALUE_OFFSET = VALUE_HASH_OFFSET + Long.BYTES;
    int META_LENGTH = Byte.BYTES + Long.BYTES;

    /**
     * @return The hash of the value
     */
    long getValueHashCode();

    /**
     * @return The type of the value
     */
    byte getTypeId();

    /**
     * @return The size in bytes
     */
    int size();

    /**
     * @return A buffer containing just the value part
     */
    ByteBuffer getValueBuffer();

    /**
     * @return The whole buffer containing type ID, value hash and value
     */
    ByteBuffer getFullByteBuffer();

    /**
     * Copies the contents of this into a new {@link StagingValue} backed by the supplied buffer.
     * Only call this after you have finished writing to the output stream.
     */
    StagingValue copy(final Supplier<ByteBuffer> byteBufferSupplier);
}
