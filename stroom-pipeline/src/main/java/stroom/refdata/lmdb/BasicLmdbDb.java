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

package stroom.refdata.lmdb;

import org.lmdbjava.Env;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.ByteBufferPool;

import java.nio.ByteBuffer;

public class BasicLmdbDb<K,V> extends AbstractLmdbDb<K, V> {

    private final String name;

    public BasicLmdbDb(final Env<ByteBuffer> lmdbEnvironment,
                       final ByteBufferPool byteBufferPool,
                final Serde<K> keySerde,
                       final Serde<V> valueSerde,
                       final String dbName) {
        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, dbName);
        this.name = dbName;
    }

    @Override
    public String getDbName() {
        return name;
    }
}
