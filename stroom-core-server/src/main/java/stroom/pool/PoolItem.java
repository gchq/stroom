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

package stroom.pool;

import java.util.concurrent.atomic.AtomicInteger;

public class PoolItem<V> {
    final Object key;
    final V value;
    final long creationTime;
    final AtomicInteger inUse = new AtomicInteger();

    volatile long lastUsed;

    public PoolItem(final Object key, final V value) {
        this.key = key;
        this.value = value;
        this.creationTime = System.currentTimeMillis();
        this.lastUsed = creationTime;
    }

    Object getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }
}
