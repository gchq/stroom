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

package stroom.pipeline.refdata.store;

import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.util.Objects;

public class XxHashValueStoreHashAlgorithm implements ValueStoreHashAlgorithm {

    // NOTE if this hash function is changed then it will likely break ref data
    // as that stores hash values in LMDB.
    private static final LongHashFunction XX_HASH = LongHashFunction.xx();
    private static final long NULL_HASH = XX_HASH.hashVoid();

    @Override
    public long hash(final ByteBuffer byteBuffer) {
        Objects.requireNonNull(byteBuffer);
        if (byteBuffer.remaining() == 0) {
            return NULL_HASH;
        } else {
            return XX_HASH.hashBytes(byteBuffer);
        }
    }
}
