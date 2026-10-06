/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.rest;

import java.nio.charset.StandardCharsets;

/// SHA-1 of text, as the gwt-suite corpus hashes request bodies in its keys. It is plain Java
/// (the browser's `crypto.subtle` is asynchronous and `java.security` isn't emulated by GWT), so
/// works both in GWT and in JVM tests. Request bodies are small, so speed doesn't matter.
final class Sha1 {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Sha1() {
    }

    /// @param text Some text, which is hashed as UTF-8 as Node's `hash.update(string)` does.
    /// @return The SHA-1 of the text as 40 lower case hex digits.
    static String hex(final String text) {
        final int[] digest = digest(text.getBytes(StandardCharsets.UTF_8));
        final StringBuilder sb = new StringBuilder(40);
        for (final int word : digest) {
            for (int shift = 28; shift >= 0; shift -= 4) {
                sb.append(HEX[(word >>> shift) & 0xf]);
            }
        }
        return sb.toString();
    }

    private static int[] digest(final byte[] message) {
        // Pad to a multiple of 64 bytes: 0x80, zeros, then the length in bits as 8 bytes
        final int paddedLength = ((message.length + 8) / 64 + 1) * 64;
        final byte[] padded = new byte[paddedLength];
        System.arraycopy(message, 0, padded, 0, message.length);
        padded[message.length] = (byte) 0x80;
        final long bitLength = (long) message.length * 8;
        for (int i = 0; i < 8; i++) {
            padded[paddedLength - 1 - i] = (byte) (bitLength >>> (8 * i));
        }

        int h0 = 0x67452301;
        int h1 = 0xEFCDAB89;
        int h2 = 0x98BADCFE;
        int h3 = 0x10325476;
        int h4 = 0xC3D2E1F0;
        final int[] w = new int[80];
        for (int chunk = 0; chunk < paddedLength; chunk += 64) {
            for (int i = 0; i < 16; i++) {
                final int offset = chunk + i * 4;
                w[i] = ((padded[offset] & 0xff) << 24)
                        | ((padded[offset + 1] & 0xff) << 16)
                        | ((padded[offset + 2] & 0xff) << 8)
                        | (padded[offset + 3] & 0xff);
            }
            for (int i = 16; i < 80; i++) {
                w[i] = rotateLeft(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
            }
            int a = h0;
            int b = h1;
            int c = h2;
            int d = h3;
            int e = h4;
            for (int i = 0; i < 80; i++) {
                final int f;
                final int k;
                if (i < 20) {
                    f = (b & c) | (~b & d);
                    k = 0x5A827999;
                } else if (i < 40) {
                    f = b ^ c ^ d;
                    k = 0x6ED9EBA1;
                } else if (i < 60) {
                    f = (b & c) | (b & d) | (c & d);
                    k = 0x8F1BBCDC;
                } else {
                    f = b ^ c ^ d;
                    k = 0xCA62C1D6;
                }
                final int temp = rotateLeft(a, 5) + f + e + k + w[i];
                e = d;
                d = c;
                c = rotateLeft(b, 30);
                b = a;
                a = temp;
            }
            h0 += a;
            h1 += b;
            h2 += c;
            h3 += d;
            h4 += e;
        }
        return new int[]{h0, h1, h2, h3, h4};
    }

    private static int rotateLeft(final int value, final int distance) {
        return (value << distance) | (value >>> (32 - distance));
    }
}
