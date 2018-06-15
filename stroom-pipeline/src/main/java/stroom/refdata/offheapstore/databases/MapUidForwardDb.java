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
import org.lmdbjava.Env;
import org.lmdbjava.Txn;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.offheapstore.ByteArrayUtils;
import stroom.refdata.offheapstore.ByteBufferPool;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.UID;
import stroom.refdata.offheapstore.serdes.MapDefinitionSerde;
import stroom.refdata.offheapstore.serdes.UIDSerde;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import java.nio.ByteBuffer;
import java.util.Optional;

public class MapUidForwardDb extends AbstractLmdbDb<MapDefinition, UID> {

    private static final String DB_NAME = "MapUidForward";

    @Inject
    public MapUidForwardDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                           final ByteBufferPool byteBufferPool,
                           final MapDefinitionSerde keySerde,
                           final UIDSerde valueSerde) {

        super(lmdbEnvironment, byteBufferPool, keySerde, valueSerde, DB_NAME);
    }

    public Optional<ByteBuffer> getUID(final Txn<ByteBuffer> txn, final ByteBuffer keyBuffer) {
        return Optional.ofNullable(lmdbDbi.get(txn, keyBuffer));
    }

    public void putForwardEntry(final Txn<ByteBuffer> writeTxn,
                                final ByteBuffer mapDefinitionKeyBuffer,
                                final ByteBuffer uidValueBuffer) {

        boolean didPutSuceed = put(writeTxn, mapDefinitionKeyBuffer, uidValueBuffer, false);
        if (!didPutSuceed) {
            throw new RuntimeException(LambdaLogger.buildMessage("Failed to put mapDefinition {}, uid {}",
                    ByteArrayUtils.byteBufferInfo(mapDefinitionKeyBuffer),
                    ByteArrayUtils.byteBufferInfo(uidValueBuffer)));
        }
    }



    public interface Factory {
        MapUidForwardDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
