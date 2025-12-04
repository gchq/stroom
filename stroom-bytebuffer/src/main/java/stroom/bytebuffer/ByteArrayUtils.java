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

package stroom.bytebuffer;

import jakarta.xml.bind.DatatypeConverter;

public class ByteArrayUtils {

    private static final char[] HEX_CHARS_UPPER = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * Private constructor to prevent instantiation
     */
    private ByteArrayUtils() {
        // Do nothing, should never be called.
    }

    /**
     * Returns a string representation of a byte array
     *
     * @param arr The byte array
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
            final int endOffsetEx = offset + length;
            for (int i = offset; i < endOffsetEx; i++) {
                sb.append(arr[i]);
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteArrayToHex(final byte b) {
        return byteArrayToHex(new byte[]{b}, 0, 1);
    }

    /**
     * Converts a byte array into a hex representation with a space between each
     * byte e.g 00 00 01 00 05 59 B3
     *
     * @param arr The byte array to convert
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
            final int endOffsetEx = offset + length;
            for (int i = offset; i < endOffsetEx; i++) {
                final byte[] oneByteArr = new byte[1];
                oneByteArr[0] = arr[i];
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteArrayToAllForms(final byte b) {
        return byteArrayToAllForms(new byte[]{b}, 0, 1);
    }

    /**
     * @return The array represented in hex, decimal and 'hbase' forms. The
     * hbase form is mix of ascii and deciaml, so an ascii char if the
     * byte value exists in the ascii table
     */
    public static String byteArrayToAllForms(final byte[] arr) {
        return byteArrayToAllForms(arr, 0, arr.length);
    }

    /**
     * @return The array represented in hex, decimal and 'hbase' forms. The
     * hbase form is mix of ascii and deciaml, so an ascii char if the
     * byte value exists in the ascii table
     */
    public static String byteArrayToAllForms(final byte[] arr, final int offset, final int length) {
        if (arr != null) {
            return ByteArrayUtils.byteArrayToHex(arr, offset, length) + " (hex) | "
                   + ByteArrayUtils.byteArrayToString(arr, offset, length) + " (dec) | "
                   + toStringBinary(arr, offset, length) + " (hbase)";
        } else {
            return "NULL";
        }
    }

    /**
     * This method is a copy of
     * org.apache.hadoop.hbase.util.Bytes#toStringBinary(byte[], int, int)
     * which is licenced under Apache License, Version 2.0
     * <p>
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements.  See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership.  The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License.  You may obtain a copy of the License at
     * <p>
     * http://www.apache.org/licenses/LICENSE-2.0
     * <p>
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     * <p>
     * Write a printable representation of a byte array. Non-printable
     * characters are hex escaped in the format \\x%02X, eg:
     * \x00 \x05 etc
     *
     * @param b   array to write out
     * @param off offset to start at
     * @param len length to write
     * @return string output
     */
    public static String toStringBinary(final byte[] b, final int off, int len) {
        final StringBuilder result = new StringBuilder();
        // Just in case we are passed a 'len' that is > buffer length...
        if (off >= b.length) {
            return result.toString();
        }
        if (off + len > b.length) {
            len = b.length - off;
        }
        for (int i = off; i < off + len; ++i) {
            final int ch = b[i] & 0xFF;
            if (ch >= ' ' && ch <= '~' && ch != '\\') {
                result.append((char) ch);
            } else {
                result.append("\\x");
                result.append(HEX_CHARS_UPPER[ch / 0x10]);
                result.append(HEX_CHARS_UPPER[ch % 0x10]);
            }
        }
        return result.toString();
    }
}
