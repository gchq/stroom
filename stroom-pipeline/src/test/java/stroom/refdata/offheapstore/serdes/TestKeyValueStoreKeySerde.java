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

package stroom.refdata.offheapstore.serdes;

import org.junit.Test;
import stroom.refdata.offheapstore.KeyValueStoreKey;
import stroom.refdata.offheapstore.UID;

public class TestKeyValueStoreKeySerde extends AbstractSerdeTest {

    @Test
    public void serializeDeserialize() {
        final UID uid = UID.of(0, 1, 2, 3);
        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(
                uid,
                "myKey");

        doSerialisationDeserialisationTest(keyValueStoreKey, KeyValueStoreKeySerde::new);
    }

    @Test
    public void serializeDeserialize_emptyString() {
        final UID uid = UID.of(0, 1, 2, 3);
        final KeyValueStoreKey keyValueStoreKey = new KeyValueStoreKey(
                uid,
                "");

        doSerialisationDeserialisationTest(keyValueStoreKey, KeyValueStoreKeySerde::new);
    }
}