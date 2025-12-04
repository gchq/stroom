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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.UnSortedDupKey;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.UnSortedDupKeySerde;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.offheapstore.KeyValueStoreKey;
import stroom.pipeline.refdata.store.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.StagingValueSerde;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;

public class KeyValueStagingDb
        extends AbstractLmdbDb<UnSortedDupKey<KeyValueStoreKey>, StagingValue> {

    public static final String DB_NAME = "KeyValueStaging";

    /**
     * @param lmdbEnvironment The LMDB {@link Env} to add this DB to.
     * @param byteBufferPool  A self loading pool of reusable ByteBuffers.
     * @param keySerde        The {@link Serde} to use for the keys.
     * @param valueSerde      The {@link Serde} to use for the values.
     */
    @Inject
    public KeyValueStagingDb(@Assisted final LmdbEnv lmdbEnvironment,
                             final ByteBufferPool byteBufferPool,
                             final KeyValueStoreKeySerde keySerde,
                             final StagingValueSerde valueSerde) {
        // Wrap the key in UnSortedDupKey so all entries are unique and stored
        // in strict insert order.
        super(lmdbEnvironment,
                byteBufferPool,
                new UnSortedDupKeySerde<>(keySerde),
                valueSerde,
                DB_NAME,
                DbiFlags.MDB_CREATE);
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        KeyValueStagingDb create(final LmdbEnv lmdbEnvironment);
    }
}
