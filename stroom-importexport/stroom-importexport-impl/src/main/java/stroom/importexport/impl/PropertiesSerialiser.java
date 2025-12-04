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

package stroom.importexport.impl;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Properties;

public final class PropertiesSerialiser {

    /**
     * A table of hex digits
     */
    private static final char[] hexDigit = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    public static Properties read(final InputStream inputStream) throws IOException {
        try {
            final Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } finally {
            inputStream.close();
        }
    }

    public static void write(final Properties properties, final OutputStream out)
            throws IOException {
        try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out, "8859_1"))) {
            final List<String> keys = properties
                    .keySet()
                    .stream()
                    .map(Object::toString)
                    .sorted()
                    .toList();

            for (String key : keys) {
                String val = properties.getProperty(key);
                if (val != null) {
                    key = saveConvert(key, true, true);
                    /* No need to escape embedded and trailing spaces for value, hence
                     * pass false to flag.
                     */
                    val = saveConvert(val, false, true);
                    bw.write(key + "=" + val);
                    bw.newLine();
                }
            }
        }
    }

    /**
     * Converts unicodes to encoded &#92;uxxxx and escapes
     * special characters with a preceding slash
     */
    private static String saveConvert(final String theString,
                                      final boolean escapeSpace,
                                      final boolean escapeUnicode) {
        final int len = theString.length();
        int bufLen = len * 2;
        if (bufLen < 0) {
            bufLen = Integer.MAX_VALUE;
        }
        final StringBuffer outBuffer = new StringBuffer(bufLen);

        for (int x = 0; x < len; x++) {
            final char aChar = theString.charAt(x);
            // Handle common case first, selecting largest block that
            // avoids the specials below
            if ((aChar > 61) && (aChar < 127)) {
                if (aChar == '\\') {
                    outBuffer.append('\\');
                    outBuffer.append('\\');
                    continue;
                }
                outBuffer.append(aChar);
                continue;
            }
            switch (aChar) {
                case ' ':
                    if (x == 0 || escapeSpace) {
                        outBuffer.append('\\');
                    }
                    outBuffer.append(' ');
                    break;
                case '\t':
                    outBuffer.append('\\');
                    outBuffer.append('t');
                    break;
                case '\n':
                    outBuffer.append('\\');
                    outBuffer.append('n');
                    break;
                case '\r':
                    outBuffer.append('\\');
                    outBuffer.append('r');
                    break;
                case '\f':
                    outBuffer.append('\\');
                    outBuffer.append('f');
                    break;
                case '=': // Fall through
                case ':': // Fall through
                case '#': // Fall through
                case '!':
                    outBuffer.append('\\');
                    outBuffer.append(aChar);
                    break;
                default:
                    if (((aChar < 0x0020) || (aChar > 0x007e)) & escapeUnicode) {
                        outBuffer.append('\\');
                        outBuffer.append('u');
                        outBuffer.append(toHex((aChar >> 12) & 0xF));
                        outBuffer.append(toHex((aChar >> 8) & 0xF));
                        outBuffer.append(toHex((aChar >> 4) & 0xF));
                        outBuffer.append(toHex(aChar & 0xF));
                    } else {
                        outBuffer.append(aChar);
                    }
            }
        }
        return outBuffer.toString();
    }

    /**
     * Convert a nibble to a hex character
     *
     * @param nibble the nibble to convert.
     */
    private static char toHex(final int nibble) {
        return hexDigit[(nibble & 0xF)];
    }


}
