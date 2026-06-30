/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.db;

import net.openhft.hashing.LongHashFunction;

import java.nio.charset.StandardCharsets;

public final class ShardKeyRouter {

    private ShardKeyRouter() {
        // Utility class
    }

    /**
     * Computes the shard index for a given key byte array.
     * Uses xxHash3 (64-bit) with modulo for uniform distribution.
     */
    public static int computeShardIndex(final byte[] keyBytes, final int shardCount) {
        if (shardCount <= 1) {
            return 0;
        }
        final long hash = LongHashFunction.xx3().hashBytes(keyBytes);
        return Math.floorMod(hash, shardCount);
    }

    /**
     * Computes the shard index for a given String key.
     */
    public static int computeShardIndex(final String key, final int shardCount) {
        if (shardCount <= 1) {
            return 0;
        }
        return computeShardIndex(key.getBytes(StandardCharsets.UTF_8), shardCount);
    }

    /**
     * Computes the shard index for a given long value (e.g. keyStart or trace ID hash).
     */
    public static int computeShardIndex(final long value, final int shardCount) {
        if (shardCount <= 1) {
            return 0;
        }
        final long hash = LongHashFunction.xx3().hashLong(value);
        return Math.floorMod(hash, shardCount);
    }
}
