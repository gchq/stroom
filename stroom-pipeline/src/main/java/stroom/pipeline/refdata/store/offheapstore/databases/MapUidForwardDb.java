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
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.serdes.MapDefinitionSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.UIDSerde;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class MapUidForwardDb extends AbstractLmdbDb<MapDefinition, UID> {

    public static final String DB_NAME = "MapUidForward";

    @Inject
    public MapUidForwardDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                           final ByteBufferPool byteBufferPool,
                           final MapDefinitionSerde keySerde,
                           final UIDSerde valueSerde) {

        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        lmdbEnvironment.registerDatabases(this);
    }

    public Optional<ByteBuffer> getUID(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        return Optional.ofNullable(getLmdbDbi().get(txn, keyBuffer));
    }

    public void putForwardEntry(final Txn<ByteBuffer> writeTxn,
                                final ByteBuffer mapDefinitionKeyBuffer,
                                final ByteBuffer uidValueBuffer) {

        final PutOutcome putOutcome = put(writeTxn, mapDefinitionKeyBuffer, uidValueBuffer, false);
        if (!putOutcome.isSuccess()) {
            throw new RuntimeException(LogUtil.message("Failed to put mapDefinition {}, uid {}",
                    ByteBufferUtils.byteBufferInfo(mapDefinitionKeyBuffer),
                    ByteBufferUtils.byteBufferInfo(uidValueBuffer)));
        }
    }

    public Optional<UID> getNextMapDefinition(final Txn<ByteBuffer> readTxn,
                                              final RefStreamDefinition refStreamDefinition,
                                              final Supplier<ByteBuffer> uidBufferSupplier) {

        Optional<UID> optMatchedMapUid = Optional.empty();
        final MapDefinition mapDefinitionWithNoMapName = new MapDefinition(refStreamDefinition);
        try (final PooledByteBuffer pooledStartKeyIncBuffer = getPooledKeyBuffer()) {
            final ByteBuffer startKeyIncBuffer = pooledStartKeyIncBuffer.getByteBuffer();

            getKeySerde().serialize(startKeyIncBuffer, mapDefinitionWithNoMapName);

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyIncBuffer);

            try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(readTxn, keyRange)) {
                for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {

                    // our startKeyIncBuffer contains only the refStreamDefinition part
                    // so ensure the key we get back from the cursor is prefixed with that
                    // else we are on a different refStreamDefinition
                    if (!ByteBufferUtils.containsPrefix(keyVal.key(), startKeyIncBuffer)) {
                        break;
                    }

                    // we want to use the bytebuffer outside the cursor so we must clone it first
                    final ByteBuffer newBuffer = uidBufferSupplier.get();
                    ByteBufferUtils.copy(keyVal.val(), newBuffer);
                    final UID mapUid = UID.wrap(newBuffer);
                    optMatchedMapUid = Optional.of(mapUid);
                    // got our match so break
                    break;
                }
            }
        }
        return optMatchedMapUid;
    }

    /**
     * Gets all store map names for a given refStreamDefinition.
     */
    public List<MapDefinition> getMapDefinitions(final Txn<ByteBuffer> readTxn,
                                                 final RefStreamDefinition refStreamDefinition) {

        final List<MapDefinition> mapDefinitions = new ArrayList<>();
        final MapDefinition mapDefinitionWithNoMapName = new MapDefinition(refStreamDefinition);

        try (final PooledByteBuffer pooledStartKeyIncBuffer = getPooledKeyBuffer()) {
            final ByteBuffer startKeyIncBuffer = pooledStartKeyIncBuffer.getByteBuffer();

            getKeySerde().serialize(startKeyIncBuffer, mapDefinitionWithNoMapName);

            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyIncBuffer);

            try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(readTxn, keyRange)) {
                for (final CursorIterable.KeyVal<ByteBuffer> keyVal : cursorIterable) {

                    // our startKeyIncBuffer contains only the refStreamDefinition part
                    // so ensure the key we get back from the cursor is prefixed with that
                    // else we are on a different refStreamDefinition
                    if (!ByteBufferUtils.containsPrefix(keyVal.key(), startKeyIncBuffer)) {
                        break;
                    }

                    final MapDefinition mapDefinition = deserializeKey(keyVal.key());
                    mapDefinitions.add(mapDefinition);
                }
            }
        }
        return mapDefinitions;
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        MapUidForwardDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
