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

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb.serde.Serde;

import org.lmdbjava.DbiFlags;

public class BasicLmdbDb<K, V> extends AbstractLmdbDb<K, V> {

    private final String name;

    public BasicLmdbDb(final LmdbEnv lmdbEnvironment,
                       final ByteBufferPool byteBufferPool,
                       final Serde<K> keySerde,
                       final Serde<V> valueSerde,
                       final String dbName,
                       final DbiFlags... dbiFlags) {
        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, dbName, dbiFlags);
        this.name = dbName;
    }

    @Override
    public String getDbName() {
        return name;
    }
}
