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

package stroom.refdata.offheapstore.serdes;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import stroom.refdata.offheapstore.UID;

/**
 * Utility methods for working with Kryo to ensure consistency
 */
public class RefDataSerdeUtils {

    private RefDataSerdeUtils() {
    }

    static void writeTimeMs(final Output output, final long timeMs) {
        //TODO need to be sure this is written in correct endian-ness so lexicographical scanning works
        output.writeLong(timeMs);
    }

    static long readTimeMs(final Input input) {
        //TODO need to be sure this is written in correct endian-ness so lexicographical scanning works
        return input.readLong();
    }

}
