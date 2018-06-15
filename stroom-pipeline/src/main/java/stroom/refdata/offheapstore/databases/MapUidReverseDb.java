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

package stroom.refdata.offheapstore.databases;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.CursorIterator;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.serdes.MapDefinitionSerde;
import stroom.refdata.offheapstore.serdes.UIDSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;

public class MapUidReverseDb extends AbstractLmdbDb<UID, MapDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapUidReverseDb.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(MapUidReverseDb.class);

    private static final String DB_NAME = "MapUidBackward";

    @Inject
    public MapUidReverseDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                           final ByteBufferPool byteBufferPool,
                           final UIDSerde keySerde,
                           final MapDefinitionSerde valueSerde) {
        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
    }

    public Optional<ByteBuffer> getHighestUid(final Txn<ByteBuffer> txn) {
        // scan backwards over all entries to find the first (i.e. highest) key
        Optional<ByteBuffer> optHighestUid = Optional.empty();
        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, KeyRange.allBackward())) {
            if (cursorIterator.hasNext()) {
                final CursorIterator.KeyVal<ByteBuffer> highestKeyVal = cursorIterator.next();
                optHighestUid = Optional.of(highestKeyVal.key());
                LAMBDA_LOGGER.trace(() ->
                        LambdaLogger.buildMessage("highestKey: {}", ByteArrayUtils.byteBufferInfo(highestKeyVal.key())));
            }
        }
        return optHighestUid;
    }

    public void putReverseEntry(final Txn<ByteBuffer> writeTxn,
                                final ByteBuffer uidKeyBuffer,
                                final ByteBuffer mapDefinitionValueBuffer) {

        boolean didPutSuceed = put(writeTxn, uidKeyBuffer, mapDefinitionValueBuffer, false);
        if (!didPutSuceed) {
            throw new RuntimeException(LambdaLogger.buildMessage("Failed to put mapDefinition {}, uid {}",
                    ByteArrayUtils.byteBufferInfo(uidKeyBuffer),
                    ByteArrayUtils.byteBufferInfo(mapDefinitionValueBuffer)));
        }

    }



    public interface Factory {
        MapUidReverseDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
