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

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A wrapper over a {@link ByteBuffer} that contains a UID. A UID is a fixed width
 * set of bytes (see UID_ARRAY_LENGTH) that forms a unique identifier. The underlying
 * {@link ByteBuffer} MUST not be mutated.
 */
public class UID {
    //    public class UID implements Comparable<UID> {
    // this is the width of the byte array used for storing the Unique ID
    // values.
    // Changing this value would require any data stored using UIDs to be
    // migrated to the new byte array length
    public static final int UID_ARRAY_LENGTH = 4;

//    /**
//     * A UID byte array containing all zeros that can be used when the UID for a
//     * string cannot be found. It is CRITICAL that a UID cannot be created in
//     * the table with this byte array
//     */
//    public static final UID NOT_FOUND_UID = new UID(new byte[UID_ARRAY_LENGTH]);
//    public static final byte[] NOT_FOUND_UID_BYTES = NOT_FOUND_UID.getUidBytes();

    private final ByteBuffer byteBuffer;

    private UID(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * Wraps a new UID around the passed {@link ByteBuffer} without copying.
     */
    public static UID wrap(final ByteBuffer byteBuffer) {
        Preconditions.checkArgument(byteBuffer.remaining() == UID_ARRAY_LENGTH,
                "Bytebuffer should have %s bytes remaining", UID_ARRAY_LENGTH);
        return new UID(byteBuffer);
    }

    /**
     * Copies the content of the {@link ByteBuffer} into a new directly allocated {@link ByteBuffer}
     * and wraps the new UID around that {@link ByteBuffer}
     */
    public static UID copyOfDirect(final ByteBuffer byteBuffer) {
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(UID_ARRAY_LENGTH);
        newBuffer.put(byteBuffer);
        newBuffer.flip();
        return new UID(newBuffer);
    }

    /**
     * Mostly for use in testing, e.g. <pre>UID uid = UID.of(0, 0, 1, 0);</pre>
     */
    public static UID of(final int... byteValues) {
        Preconditions.checkArgument(byteValues.length == UID_ARRAY_LENGTH);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(UID_ARRAY_LENGTH);
        for (int i = 0; i < UID_ARRAY_LENGTH; i++) {
            byte b = (byte) byteValues[i];
            byteBuffer.put(b);
        }
        byteBuffer.flip();
        return new UID(byteBuffer);
    }

    public static UID of(final long value) {
        return new UID(createUidBuffer(value));
    }

    public UID clone() {
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(UID_ARRAY_LENGTH);
        newBuffer.put(byteBuffer);
        newBuffer.flip();
        return new UID(newBuffer);
    }

    public long getValue() {
        long val = UnsignedBytes.get(byteBuffer);
        byteBuffer.flip();
        return val;
    }

    public UID nextUid() {
        long currVal = getValue();
        return UID.of(currVal + 1);
    }

//    /**
//     * Wraps a new UID around the UID section of the passed array. The array is NOT copied so must not be mutated.
//     */
//    public static UID from(final byte[] bytes, final int offset) {
//        return new UID(bytes, offset);
//    }

    /**
     * @return A duplicate of the backing buffer for the unique ID. The returned buffer should not be mutated.
     */
    public ByteBuffer getBackingBuffer() {
        return byteBuffer.duplicate();
    }

//    /**
//     * @return A copy of the UID part of the backing array unless the UID part is all of the array,
//     * in which case a reference to the backing array is returned
//     */
//    public byte[] getUidBytes() {
//        if (bytes.length == UID_ARRAY_LENGTH) {
//            return bytes;
//        } else {
//            return Bytes.copy(bytes, offset, UID_ARRAY_LENGTH);
//        }
//    }

//    /**
//     * @return The offset that the UID starts at in the backing array
//     */
//    public int getOffset() {
//        return offset;
//    }

    /**
     * @return The length of the UID itself, rather than the backing array which may be longer
     */
    public static int length() {
        return UID_ARRAY_LENGTH;
    }


    @Override
    public String toString() {
        return ByteArrayUtils.byteBufferToHex(byteBuffer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UID uid = (UID) o;
        return Objects.equals(byteBuffer, uid.byteBuffer);
    }

    @Override
    public int hashCode() {

        return Objects.hash(byteBuffer);
    }

    //    /**
//     * @return The array represented in hex, decimal and 'hbase' forms. The
//     * hbase form is mix of ascii and deciaml, so an ascii char if the
//     * byte value exists in the ascii table
//     */
//    public String toAllForms() {
//        return ByteArrayUtils.byteArrayToAllForms(bytes, offset, UID_ARRAY_LENGTH);
//    }

//    @Override
//    public int compareTo(final UID that) {
//        ByteBufferUtils.
//        return Bytes.compareTo(this.bytes, this.offset, UID_ARRAY_LENGTH, that.bytes, that.offset, UID_ARRAY_LENGTH);
//    }

//    /**
//     * Compare this UID to a portion of another array startign at a given offset
//     *
//     * @param otherBytes
//     * @param otherOffset
//     * @return
//     */
//    public int compareTo(final byte[] otherBytes, final int otherOffset) {
//        return Bytes.compareTo(this.bytes, this.offset, UID_ARRAY_LENGTH, otherBytes, otherOffset, UID_ARRAY_LENGTH);
//    }

    private static ByteBuffer createUidBuffer(final long id) {
        // UIDs are fixed width so we can create a buffer with the exact capacity
        final ByteBuffer byteBuffer = createEmptyUidBuffer();
        UnsignedBytes.put(byteBuffer, UID.UID_ARRAY_LENGTH, id);
        byteBuffer.flip();
        return byteBuffer;
    }

    private static ByteBuffer createEmptyUidBuffer() {
        return ByteBuffer.allocateDirect(UID.UID_ARRAY_LENGTH);
    }

}
