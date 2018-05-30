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

import org.lmdbjava.Env;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.serdes.MapDefinitionSerde;
import stroom.refdata.offheapstore.serdes.UIDSerde;

import java.nio.ByteBuffer;

public class MapUidForwardDb extends AbstractLmdbDb<MapDefinitionSerde, UIDSerde> {

    private static final String DB_NAME = "MapUidForward";

    public MapUidForwardDb(final Env<ByteBuffer> lmdbEnvironment,
                           final Serde<MapDefinitionSerde> keySerde,
                           final Serde<UIDSerde> valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    public interface Factory {
        MapUidForwardDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
