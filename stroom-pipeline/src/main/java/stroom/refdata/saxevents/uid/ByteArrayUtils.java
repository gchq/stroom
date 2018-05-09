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

package stroom.refdata.saxevents.uid;

import org.apache.hadoop.hbase.util.Bytes;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;

public class ByteArrayUtils {
    /**
     * Private constructor to prevent instantiation
     */
    private ByteArrayUtils() {
        // Do nothing, should never be called.
    }

    /**
     * Returns a string representation of a byte array
     *
     * @param arr
     *            The byte array
     * @return A space delimited series of byte values
     */
    public static String byteArrayToString(final byte[] arr) {
        final StringBuilder sb = new StringBuilder();
        for (final byte b : arr) {
            sb.append(b);
            sb.append(" ");
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteArrayToString(final byte[] arr, final int offset, final int length) {
        final StringBuilder sb = new StringBuilder();
        if (arr != null) {
            int endOffsetEx = offset + length;
            for (int i = offset; i < endOffsetEx; i++) {
                sb.append(arr[i]);
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        return byteArrayToString(Bytes.getBytes(byteBuffer));
    }

    /**
     * Converts a byte array into a hex representation with a space between each
     * byte e.g 00 00 01 00 05 59 B3
     *
     * @param arr
     *            The byte array to convert
     * @return The byte array as a string of hex values separated by a spaces
     */
    public static String byteArrayToHex(final byte[] arr) {
        final StringBuilder sb = new StringBuilder();
        if (arr != null) {
            for (final byte b : arr) {
                final byte[] oneByteArr = new byte[1];
                oneByteArr[0] = b;
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteArrayToHex(final byte[] arr, final int offset, final int length) {
        final StringBuilder sb = new StringBuilder();
        if (arr != null) {
            int endOffsetEx = offset + length;
            for (int i = offset; i < endOffsetEx; i++) {
                final byte[] oneByteArr = new byte[1];
                oneByteArr[0] = arr[i];
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteBufferToHex(final ByteBuffer byteBuffer) {
        return byteArrayToHex(Bytes.getBytes(byteBuffer));
    }

    /**
     * @param arr
     * @return The array represented in hex, decimal and 'hbase' forms. The
     *         hbase form is mix of ascii and deciaml, so an ascii char if the
     *         byte value exists in the ascii table
     */
    public static String byteArrayToAllForms(final byte[] arr) {
        return byteArrayToAllForms(arr, 0, arr.length);
    }

    /**
     * @return The array represented in hex, decimal and 'hbase' forms. The
     *         hbase form is mix of ascii and deciaml, so an ascii char if the
     *         byte value exists in the ascii table
     */
    public static String byteArrayToAllForms(final byte[] arr, final int offset, final int length) {
        if (arr != null) {
            return ByteArrayUtils.byteArrayToHex(arr, offset, length) + " (hex) | " + ByteArrayUtils.byteArrayToString(arr, offset, length) + " (dec) | "
                    + Bytes.toStringBinary(arr, offset, length) + " (hbase)";
        } else {
            return "NULL";
        }
    }
    public static String byteBufferToAllForms(final ByteBuffer byteBuffer) {
        return byteArrayToAllForms(Bytes.getBytes(byteBuffer));
    }
}
