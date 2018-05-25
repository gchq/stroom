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

import org.apache.hadoop.hbase.util.Bytes;

import java.nio.ByteBuffer;

/**
 * A holder for a byte[] representation of a Unique ID. The object is backed by a
 * byte[] that either is the complete Unique ID, or the Unique ID is a subset of it.
 * The backing array must not be mutated!
 */
public class UID implements Comparable<UID> {
    // this is the width of the byte array used for storing the Unique ID
    // values.
    // Changing this value would require any data stored using UIDs to be
    // migrated to the new byte array length
    public static final int UID_ARRAY_LENGTH = 4;

    /**
     * A UID byte array containing all zeros that can be used when the UID for a
     * string cannot be found. It is CRITICAL that a UID cannot be created in
     * the table with this byte array
     */
    public static final UID NOT_FOUND_UID = new UID(new byte[UID_ARRAY_LENGTH]);
    public static final byte[] NOT_FOUND_UID_BYTES = NOT_FOUND_UID.getUidBytes();

    private final byte[] bytes;
    private final int offset;

    /**
     * Object representing part of a byte[] that forms a Unique Identifier
     * Does NOT copy the passed bytes, so the passed bytes must not be changed
     * @param bytes The array of bytes that contains the UID bytes
     * @param offset The offset in bytes where the UID part begins
     */
    public UID(final byte[] bytes, final int offset) {
        this.bytes = bytes;
        this.offset = offset;
//        this.hashCode = buildHashCode();
    }

    /**
     * Object representing a byte[] that forms a Unique Identifier
     * Does NOT copy the passed bytes, so the passed bytes must not be changed
     * @param bytes A byte[] of length UNIQUE_ID_BYTE_ARRAY_WIDTH representing a UID
     */
    public UID(final byte[] bytes) {
        this(bytes, 0);
    }

    /**
     * Wraps a new UID around the passed array. The array is NOT copied so must not be mutated.
     */
    public static UID from(final byte[] bytes) {
        return new UID(bytes);
    }

    /**
     * Copies the content of the {@link ByteBuffer} into a new array and wraps the new UID around
     * that array
     */
    public static UID from(final ByteBuffer byteBuffer) {
        byte[] bytes = new byte[UID.length()];
        byteBuffer.get(bytes);
        return new UID(bytes);
    }

    /**
     * Wraps a new UID around the UID section of the passed array. The array is NOT copied so must not be mutated.
     */
    public static UID from(final byte[] bytes, final int offset) {
        return new UID(bytes, offset);
    }

    /**
     * @return The backing array for the unique ID which will either be a whole or sub-set
     * of the backing array. The backing array should not be mutated. This method is exposed to allow
     * processing of the UID by methods that take array/offset/length
     */
    public byte[] getBackingArray() {
        return bytes;
    }

    /**
     * @return A copy of the UID part of the backing array unless the UID part is all of the array,
     * in which case a reference to the backing array is returned
     */
    public byte[] getUidBytes() {
        if (bytes.length == UID_ARRAY_LENGTH) {
            return  bytes;
        } else {
            return Bytes.copy(bytes, offset, UID_ARRAY_LENGTH);
        }
    }

    /**
     * @return The offset that the UID starts at in the backing array
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return The length of the UID itself, rather than the backing array which may be longer
     */
    public static int length() {
        return UID_ARRAY_LENGTH;
    }

    @Override
    public int hashCode() {
//        return hashCode;
        return buildHashCode();
    }

    private int buildHashCode() {
        return Bytes.hashCode(bytes, offset, UID_ARRAY_LENGTH);
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final UID that = (UID) obj;
        //equality based only on the part of the backing array of interest
        if (!Bytes.equals(this.bytes, this.offset, UID_ARRAY_LENGTH, that.bytes, that.offset, UID_ARRAY_LENGTH)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return ByteArrayUtils.byteArrayToHex(bytes, offset, UID_ARRAY_LENGTH);
    }

    /**
     * @return The array represented in hex, decimal and 'hbase' forms. The
     *         hbase form is mix of ascii and deciaml, so an ascii char if the
     *         byte value exists in the ascii table
     */
    public String toAllForms() {
        return ByteArrayUtils.byteArrayToAllForms(bytes, offset, UID_ARRAY_LENGTH);
    }

    @Override
    public int compareTo(final UID that) {
        return Bytes.compareTo(this.bytes, this.offset, UID_ARRAY_LENGTH, that.bytes, that.offset, UID_ARRAY_LENGTH);
    }

    /**
     * Compare this UID to a portion of another array startign at a given offset
     * @param otherBytes
     * @param otherOffset
     * @return
     */
    public int compareTo(final byte[] otherBytes, final int otherOffset) {
        return Bytes.compareTo(this.bytes, this.offset, UID_ARRAY_LENGTH, otherBytes, otherOffset, UID_ARRAY_LENGTH);
    }
}
