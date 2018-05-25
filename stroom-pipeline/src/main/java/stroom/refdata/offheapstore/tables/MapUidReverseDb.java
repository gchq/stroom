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

package stroom.refdata.offheapstore.tables;

import com.google.inject.assistedinject.Assisted;
import org.lmdbjava.Env;
import stroom.refdata.lmdb.AbstractLmdbDb;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.UID;

import javax.inject.Inject;
import java.nio.ByteBuffer;

public class MapUidReverseDb extends AbstractLmdbDb<UID, MapDefinition> {

    private static final String DB_NAME = "MapUidBackward";

    @Inject
    public MapUidReverseDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                           final Serde<UID> keySerde,
                           final Serde<MapDefinition> valueSerde) {
        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    public interface Factory {
        MapUidReverseDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
