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
import stroom.refdata.offheapstore.KeyValueStoreKey;
import stroom.refdata.offheapstore.serdes.KeyValueStoreKeySerde;
import stroom.refdata.offheapstore.ValueStoreKey;
import stroom.refdata.offheapstore.serdes.ValueStoreKeySerde;

import javax.inject.Inject;
import java.nio.ByteBuffer;

public class KeyValueStoreDb extends AbstractLmdbDb<KeyValueStoreKey, ValueStoreKey> {

    private static final String DB_NAME = "KeyValueStore";

    @Inject
    KeyValueStoreDb(@Assisted final Env<ByteBuffer> lmdbEnvironment,
                    final KeyValueStoreKeySerde keySerde,
                    final ValueStoreKeySerde valueSerde) {

        super(lmdbEnvironment, keySerde, valueSerde, DB_NAME);
    }

    public interface Factory {
        KeyValueStoreDb create(final Env<ByteBuffer> lmdbEnvironment);
    }
}
