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

package stroom.refdata.offheapstore;

import stroom.refdata.lmdb.serde.Deserializer;
import stroom.refdata.lmdb.serde.Serde;
import stroom.refdata.lmdb.serde.Serializer;

import java.nio.ByteBuffer;

public class UIDSerde implements Serde<UID>, Serializer<UID>, Deserializer<UID> {

    @Override
    public UID deserialize(final ByteBuffer byteBuffer) {
        return UID.from(byteBuffer);
    }

    @Override
    public void serialize(final ByteBuffer byteBuffer, final UID uid) {
        //TODO may need to allocate a capacity equal to the max key size
        byteBuffer.put(uid.getBackingArray(), uid.getOffset(), UID.length());
        byteBuffer.flip();
    }
}
