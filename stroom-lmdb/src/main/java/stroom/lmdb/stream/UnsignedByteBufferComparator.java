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

package stroom.lmdb.stream;

import java.nio.ByteBuffer;
import java.util.Comparator;

class UnsignedByteBufferComparator implements Comparator<ByteBuffer> {

    @Override
    public int compare(final ByteBuffer o1, final ByteBuffer o2) {
        // Find the first index where the two buffers don't match.
        final int i = o1.mismatch(o2);

        // If the length of both buffers are equal and mismatch is the length then return 0 for equal.
        final int thisPos = o1.position();
        final int thisRem = o1.limit() - thisPos;
        final int thatPos = o2.position();
        final int thatRem = o2.limit() - thatPos;
        if (thisRem == thatRem && i == thatRem) {
            return 0;
        }

        if (i >= 0 && i < thisRem && i < thatRem) {
            return Byte.compareUnsigned(o1.get(thisPos + i), o2.get(thatPos + i));
        }

        return thisRem - thatRem;
    }
}
