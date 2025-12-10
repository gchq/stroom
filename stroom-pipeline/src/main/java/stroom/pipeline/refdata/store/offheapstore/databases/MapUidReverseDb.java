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
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.pipeline.refdata.store.offheapstore.UID;
import stroom.pipeline.refdata.store.offheapstore.serdes.MapDefinitionSerde;
import stroom.pipeline.refdata.store.offheapstore.serdes.UIDSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.inject.Inject;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class MapUidReverseDb extends AbstractLmdbDb<UID, MapDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapUidReverseDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapUidReverseDb.class);

    public static final String DB_NAME = "MapUidBackward";

    @Inject
    public MapUidReverseDb(@Assisted final RefDataLmdbEnv lmdbEnvironment,
                           final ByteBufferPool byteBufferPool,
                           final UIDSerde keySerde,
                           final MapDefinitionSerde valueSerde) {
        super(lmdbEnvironment.getEnvironment(), byteBufferPool, keySerde, valueSerde, DB_NAME);
        lmdbEnvironment.registerDatabases(this);
    }

    /**
     * @return A bytebuffer of the next UID, NOT owned by LMDB
     */
    public ByteBuffer getNextUid(final Txn<ByteBuffer> txn,
                                 final PooledByteBuffer newUidPooledBuffer) {
        final ByteBuffer nextUidBuffer = newUidPooledBuffer.getByteBuffer();
        // scan backwards over all entries to find the first (i.e. highest) key
        try (final CursorIterable<ByteBuffer> cursorIterable = getLmdbDbi().iterate(txn, KeyRange.allBackward())) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
            if (iterator.hasNext()) {
                final CursorIterable.KeyVal<ByteBuffer> highestKeyVal = iterator.next();

                final ByteBuffer highestUidBuffer = highestKeyVal.key();

                LAMBDA_LOGGER.trace(() ->
                        LogUtil.message("highestKey: {}", ByteBufferUtils.byteBufferInfo(highestUidBuffer)));

                // DB has a UID in it so create a new one that is one higher
                // in the pooled buffer
                // Need to use the key buffer before the txn uses a cursor elsewhere so
                // we write into our own buffer.
                UID.wrap(highestUidBuffer)
                        .writeNextUid(nextUidBuffer);
            } else {
                // Empty DB so create the lowest UID into the pooled buffer
                UID.writeMinimumValue(nextUidBuffer);
            }
        }
        // Not owned by LMDB
        return nextUidBuffer;
    }

    public void putReverseEntry(final Txn<ByteBuffer> writeTxn,
                                final ByteBuffer uidKeyBuffer,
                                final ByteBuffer mapDefinitionValueBuffer) {

        final PutOutcome putOutcome = put(writeTxn, uidKeyBuffer, mapDefinitionValueBuffer, false);
        if (!putOutcome.isSuccess()) {
            throw new RuntimeException(LogUtil.message("Failed to put mapDefinition {}, uid {}",
                    ByteBufferUtils.byteBufferInfo(uidKeyBuffer),
                    ByteBufferUtils.byteBufferInfo(mapDefinitionValueBuffer)));
        }

    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        MapUidReverseDb create(final RefDataLmdbEnv lmdbEnvironment);
    }
}
