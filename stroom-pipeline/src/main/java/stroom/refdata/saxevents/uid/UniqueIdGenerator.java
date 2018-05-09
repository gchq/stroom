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


import java.util.Optional;

public interface UniqueIdGenerator {

    static byte[] convertToUid(long id, int width) {
        final byte[] uid = new byte[width];

        UnsignedBytes.put(uid, 0, width, id);

        return uid;
    }

    static byte[] getNextUid(byte[] uid, int width) {
        return getUidByOffset(uid, width, +1);
    }

    static byte[] getPrevUid(byte[] uid, int width) {
        return getUidByOffset(uid, width, -1);
    }

    static byte[] getUidByOffset(final byte[] uid, final int width, final int offset) {
        long val = UnsignedBytes.get(uid, 0, width);
        val += offset;
        return UnsignedBytes.toBytes(width, val);
    }

    Optional<UID> getId(String name);

    UID getOrCreateId(String name);

    Optional<String> getName(UID uid);

    int getWidth();
}
