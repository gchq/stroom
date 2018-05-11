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
import stroom.entity.shared.Range;
import stroom.refdata.lmdb.BasicLmdbDb;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class RefDataOffHeapStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOffHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOffHeapStore.class);

    private static final String KEY_VALUE_STORE_DB_NAME = "KeyValueStore";
    private static final String RANGE_STORE_DB_NAME = "RangeStore";
    private static final String VALUE_STORE_DB_NAME = "ValueStore";
    private static final String MAP_UID_STORE_FORWARD_DB_NAME = "MapUidStoreForward";
    private static final String MAP_UID_STORE_BACKWARD_DB_NAME = "MapUidStoreBackward";
    private static final String PROCESSING_INFO_DB_NAME = "ProcessingInfoMapsStore";

    private final Path dbDir;
    private final long maxSize;

    private final Env<ByteBuffer> env;

    // the DBs that make up the store
    private final BasicLmdbDb<KeyValueStoreKey, ValueStoreKey> keyValueStoreDb;
    private final BasicLmdbDb<RangeStoreKey, ValueStoreKey> rangeStoreDb;
    private final BasicLmdbDb<ValueStoreKey, RefDataValue> valueStoreDb;
    private final BasicLmdbDb<MapDefinition, UID> mapUidStoreForwardDb;
    private final BasicLmdbDb<UID, MapDefinition> mapUidStoreBackwardDb;
    private final BasicLmdbDb<UID, ProcessingInfo> processedMapsStoreDb;

    /**
     * @param dbDir   The directory the LMDB environment will be created in, it must already exist
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
        this.keyValueStoreDb = new BasicLmdbDb<>(
                env, new KeyValueStoreKeySerde(), new ValueStoreKeySerde(), KEY_VALUE_STORE_DB_NAME);
        this.rangeStoreDb = new BasicLmdbDb<>(
                env, new RangeStoreKeySerde(), new ValueStoreKeySerde(), RANGE_STORE_DB_NAME);
        this.valueStoreDb = new BasicLmdbDb<>(
                env, new ValueStoreKeySerde(), new RefDataValueSerde(), VALUE_STORE_DB_NAME);
        this.mapUidStoreForwardDb = new BasicLmdbDb<>(
                env, new MapDefinitionSerde(), new UIDSerde(), MAP_UID_STORE_FORWARD_DB_NAME);
        this.mapUidStoreBackwardDb = new BasicLmdbDb<>(
                env, new UIDSerde(), new MapDefinitionSerde(), MAP_UID_STORE_BACKWARD_DB_NAME);
        this.processedMapsStoreDb = new BasicLmdbDb<>(
                env, new UIDSerde(), new ProcessingInfoSerde(), PROCESSING_INFO_DB_NAME);
    }

    /**
     * Performs a lookup using the passed mapDefinition and key and if not found will call the refDataValueSupplier
     * to create a new entry for that mapDefinition, key and value. The check-and-put will be done in an atomic way
     * so no external synchronisation is required.
     */
    //TODO consider a bulk put method or a builder type class to check/load them all in one txn
    public void putIfAbsent(final MapDefinition mapDefinition,
                            final String key,
                            final Supplier<RefDataValue> refDataValueSupplier) {

    }

    /**
     * Performs a lookup using the passed mapDefinition and keyRange and if not found will call the refDataValueSupplier
     * to create a new entry for that mapDefinition, keyRange and value. The check-and-put will be done in an atomic way
     * so no external synchronisation is required.
     */
    public void putIfAbsent(final MapDefinition mapDefinition,
                            final Range<Long> keyRange,
                            final Supplier<RefDataValue> refDataValueSupplier) {

    }

    /**
     * Gets a value from the store for the passed mapDefinition and key. If not found returns an empty {@link Optional}.
     */
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                           final String key) {
        return Optional.empty();
    }

    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueConsumer to
     * the found value. If no value is found the valueConsumer is not called
     */
    public void useValue(final MapDefinition mapDefinition,
                         final String key,
                         final Consumer<RefDataValue> valueConsumer) {


    }

    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueMapper to
     * the found value, returning the value in an {@link Optional}. If no value is found an empty
     * {@link Optional} is returned. The valueMapper will be applied inside a transaction.
     */
    public <T> Optional<T> map(final MapDefinition mapDefinition,
                               final String key,
                               final Function<RefDataValue, T> valueMapper) {

        return Optional.empty();
    }


    private static Dbi<ByteBuffer> openDbi(final Env<ByteBuffer> env, final String name) {
        LOGGER.debug("Opening LMDB database with name: {}", name);
        return env.openDbi(name, DbiFlags.MDB_CREATE);
    }


    public static class RefDataLoader {

        
    }

}
