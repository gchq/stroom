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

package stroom.refdata.store;

import stroom.refdata.store.offheapstore.TypedByteBuffer;

import java.util.Optional;
import java.util.function.Consumer;

public interface RefDataValueProxy {
    /**
     * Materialise the value that this is proxying. The consumeValue() method should be preferred
     * as this method will involve the added cost of copying the contents of the value.
     *
     * @return An optional value, as the value may may not exist or has been purged.
     */
    Optional<RefDataValue> supplyValue();

    /**
     * If a reference data entry exists for this {@link RefDataValueProxy} pass its value to the consumer
     * as a {@link TypedByteBuffer}.
     * @param typedByteBufferConsumer
     * @return True if the entry is found and the consumer is called.
     */
    boolean consumeBytes(Consumer<TypedByteBuffer> typedByteBufferConsumer);
}
