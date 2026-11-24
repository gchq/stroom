/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.cache;

public class PoolKey<K> {

    /// The pool id of a key that doesn't belong to any pool, e.g. for an item that isn't pooled.
    static final long NO_POOL_ID = 0;

    private final K key;
    private final long poolId;

    /// Creates a key that doesn't belong to any pool.
    ///
    /// @param key The key of the item.
    public PoolKey(final K key) {
        this(key, NO_POOL_ID);
    }

    PoolKey(final K key, final long poolId) {
        this.key = key;
        this.poolId = poolId;
    }

    public K getKey() {
        return key;
    }

    /// @return The id of the pool this key was created by, or [#NO_POOL_ID] if it wasn't created by one.
    long getPoolId() {
        return poolId;
    }

    // Lack of hashcode is intentional so object instance id is used
}
