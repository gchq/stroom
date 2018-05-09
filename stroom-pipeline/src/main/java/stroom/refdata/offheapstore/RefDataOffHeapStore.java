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

package stroom.refdata.offheapstore;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class RefDataOffHeapStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    private static final String KEY_VALUE_STORE_DB_NAME = "KeyValueStore";
    private static final String RANGE_STORE_DB_NAME = "RangeStore";
    private static final String VALUE_STORE_DB_NAME = "ValueStore";
    private static final String MAP_UID_STORE_FORWARD_DB_NAME = "MapUidStoreForward";
    private static final String MAP_UID_STORE_BACKWARD_DB_NAME = "MapUidStoreBackward";
    private static final String PROCESSED_STREAMS_DB_NAME = "ProcessedMapsStore";

    private final Path dbDir;
    private final long maxSize;

    private final Env<ByteBuffer> env;
    private final  keyValueStoreDb;
    private final  rangeStoreDb;
    private final  valueStoreDb;
    private final  mapUidStoreForwardDb;
    private final  mapUidStoreBackwardDb;
    private final  processedMapsStoreDb;

    /**
     * @param dbDir The directory the LMDB environment will be created in, it must already exist
     * @param maxSize The max size in bytes of the environment. This should be less than the available
     *                disk space for dbDir. This size covers all DBs created in this environment.
     */
    RefDataOffHeapStore(final Path dbDir, final long maxSize) {
        this.dbDir = dbDir;
        this.maxSize = maxSize;

        LOGGER.debug("Creating LMDB environment with maxSize: {}, dbDir {}", maxSize, dbDir.toAbsolutePath().toString());
        env = Env.<ByteBuffer>create()
                .setMapSize(maxSize)
                .setMaxDbs(1)
                .open(dbDir.toFile());

        // create all the databases
        keyValueStoreDbi = openDbi(env, KEY_VALUE_STORE_DB_NAME);
        rangeStoreDbi = openDbi(env, RANGE_STORE_DB_NAME);
        valueStoreDbi = openDbi(env, VALUE_STORE_DB_NAME);
        mapUidStoreForwardDbi = openDbi(env, MAP_UID_STORE_FORWARD_DB_NAME);
        mapUidStoreBackwardDbi = openDbi(env, MAP_UID_STORE_BACKWARD_DB_NAME);
        processedMapsStoreDbi = openDbi(env, PROCESSED_STREAMS_DB_NAME);
    }




    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env, final String name) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        return env.openDbi(name, DbiFlags.MDB_CREATE);
    }


}
