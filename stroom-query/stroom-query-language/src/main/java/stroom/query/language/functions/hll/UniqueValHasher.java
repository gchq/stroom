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

package stroom.query.language.functions.hll;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValSerialiser;
import stroom.query.language.functions.ref.KryoDataWriter;

import com.esotericsoftware.kryo.io.Output;
import net.openhft.hashing.LongHashFunction;

/// Hashes values so that the unique ones can be counted.
///
/// The hash is the 64-bit XXH3 of the type and the whole value as written by [ValSerialiser], so values have the
/// same hash if they are equal. Strings are not cut to the maximum stored length before they are hashed.
///
/// Hashes are stored and are sent between nodes, so the hash of a value must not change.
public final class UniqueValHasher {

    private static final int INITIAL_BUFFER_SIZE = 64;
    private static final int UNLIMITED_BUFFER_SIZE = -1;

    private UniqueValHasher() {
        // Static methods only.
    }

    /// Hash a value.
    ///
    /// @param val The value to hash.
    /// @return The 64-bit hash of the value.
    public static long hash(final Val val) {
        try (final Output output = new Output(INITIAL_BUFFER_SIZE, UNLIMITED_BUFFER_SIZE)) {
            try (final KryoDataWriter writer = new KryoDataWriter(output)) {
                ValSerialiser.write(writer, val);
                return LongHashFunction.xx3().hashBytes(output.getBuffer(), 0, output.position());
            }
        }
    }
}
