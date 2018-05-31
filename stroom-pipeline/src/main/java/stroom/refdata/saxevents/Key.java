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

package stroom.refdata.saxevents;

import com.google.common.base.Preconditions;
import stroom.refdata.lmdb.LmdbUtils;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A surrogate key for a value that implements hashCode(). It is recomended that the
 * hashcode of the value is held on the instance rather than computing it on the fly
 * each time to save processing.
 */
class Key {

    private static final int SIZE_IN_BYTES = Integer.BYTES + Integer.BYTES;
    private static final int DEFAULT_UNIQUE_ID = 0;
    private static final int MIN_UNIQUE_ID = DEFAULT_UNIQUE_ID;
    private static final int MAX_UNIQUE_ID = Integer.MAX_VALUE;

    // The hashcode of the value that this key points to
    private final int valueHashCode;
    // An ID to provide uniqueness in the event of a hash-clash
    private final int uniqueId;

    /**
     * @return A key with the lowest possible uniqueId for valueHashCode
     */
    static Key lowestKey(final int valueHashCode) {
        return new Key(valueHashCode, MIN_UNIQUE_ID);
    }

    /**
     * @return A key with the highest possible uniqueId for valueHashCode
     */
    static Key highestKey(final int valueHashCode) {
        return new Key(valueHashCode, MAX_UNIQUE_ID);
    }

    Key(final int valueHashCode, final int uniqueId) {
        // Due to the way keys are sorted, negative unique ids are not supported
        Preconditions.checkArgument(uniqueId >= 0);
        this.valueHashCode = valueHashCode;
        this.uniqueId = uniqueId;
    }

    /**
     * @return A new Key instance with the next unique ID. Should be used with some form of
     * concurrency protection to avoid multiple keys with the same ID.
     */
    Key nextKey() {
        return new Key(valueHashCode, uniqueId + 1);
    }

    int getValueHashCode() {
        return valueHashCode;
    }

    int getUniqueId() {
        return uniqueId;
    }

    /**
     * Put the key's content in the passed byteBuffer
     */
    ByteBuffer putContent(final ByteBuffer byteBuffer) {
        return byteBuffer
                .putInt(valueHashCode)
                .putInt(uniqueId);
    }

    static Key fromByteBuffer(final ByteBuffer byteBuffer) {
        int hashCode = byteBuffer.getInt();
        int uniqueId = byteBuffer.getInt();
        byteBuffer.flip();
        return new Key(hashCode, uniqueId);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Key key = (Key) o;
        return valueHashCode == key.valueHashCode &&
                uniqueId == key.uniqueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(valueHashCode, uniqueId);
    }


    @Override
    public String toString() {
        return "Key{" +
                "valueHashCode=" + valueHashCode +
                ", uniqueId=" + uniqueId +
                ", bytes=" + getBytesString() +
                '}';
    }

    private String getBytesString() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(SIZE_IN_BYTES);
        putContent(byteBuffer);
        return LmdbUtils.byteArrayToHex(byteBuffer.array());
    }
}
