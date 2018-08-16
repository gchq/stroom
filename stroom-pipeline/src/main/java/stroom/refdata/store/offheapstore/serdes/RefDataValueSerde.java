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

package stroom.refdata.store.offheapstore.serdes;

import stroom.refdata.store.offheapstore.lmdb.serde.Deserializer;
import stroom.refdata.store.offheapstore.lmdb.serde.Serde;
import stroom.refdata.store.offheapstore.lmdb.serde.Serializer;
import stroom.refdata.store.RefDataValue;

public interface RefDataValueSerde extends
        Serde<RefDataValue>,
        Serializer<RefDataValue>,
        Deserializer<RefDataValue> {


}
