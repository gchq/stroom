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

package stroom.pipeline.xsltfunctions;

class Bitmap {

    /**
     * @param bitmap The value to convert to a set of bit positions. It is converted to a bitmap
     *               and the position of each bit that has has a value of 1 is added to the return array.
     * @return The value represented as the bit positions that are set to 1.
     */
    static int[] getBits(final int bitmap) {
        int[] bits = new int[10];

        int value = bitmap;
        int bit = 0;
        int pos = 0;

        while (value > 0) {
            // If this bit is set then add it to the array.
            if ((value & 1) != 0) {
                // Grow the array if we need to.
                if (bits.length == pos) {
                    final int[] tmp = new int[pos * 2];
                    System.arraycopy(bits, 0, tmp, 0, pos);
                    bits = tmp;
                }

                bits[pos] = bit;
                pos++;
            }

            value = value >> 1;
            bit++;
        }

        // Trim the array
        final int[] trimmed = new int[pos];
        System.arraycopy(bits, 0, trimmed, 0, pos);

        return trimmed;
    }
}
