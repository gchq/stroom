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
import stroom.refdata.lmdb.LmdbUtils;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.UnsignedBytes;

import javax.inject.Inject;
import java.nio.ByteBuffer;

public class MapUidReverseDb extends AbstractLmdbDb<UID, MapDefinition> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapUidReverseDb.class);

    private static final String DB_NAME = "MapUidBackward";

    public static final long maxId = UnsignedBytes.maxValue(UID.UID_ARRAY_LENGTH);

    @Inject
    public MapUidReverseDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                           final Serde<UID> keySerde,
                           final Serde<MapDefinition> valueSerde) {
        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    public long getNextId(final Txn<ByteBuffer> txn) {
        // default value if we have nothing in the DB
        long nextId = 0;
        // scan backwards over all entries to find the first (i.e. highest) key
        try (CursorIterator<ByteBuffer> cursorIterator = lmdbDbi.iterate(txn, KeyRange.allBackward())) {
            if (cursorIterator.hasNext()) {
                CursorIterator.KeyVal<ByteBuffer> highestKeyVal = cursorIterator.next();
                long highestId = UnsignedBytes.get(highestKeyVal.key());
                LOGGER.debug("highestId: {}", highestId);
                nextId = highestId + 1;
            }
        }
        return nextId;
    }



    public interface Factory {
        MapUidReverseDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
