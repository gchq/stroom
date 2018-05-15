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

import stroom.entity.shared.Range;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface RefDataStore {
    Optional<ProcessingInfo> getProcessingInfo(MapDefinition mapDefinition);

    //TODO consider a bulk put method or a builder type class to check/load them all in one txn
    void putIfAbsent(MapDefinition mapDefinition,
                     String key,
                     Supplier<RefDataValue> refDataValueSupplier);

    void putIfAbsent(MapDefinition mapDefinition,
                     Range<Long> keyRange,
                     Supplier<RefDataValue> refDataValueSupplier);

    Optional<RefDataValue> getValue(MapDefinition mapDefinition,
                                    String key);

    Optional<RefDataValue> getValue(ValueStoreKey valueStoreKey);

    void consumeValue(MapDefinition mapDefinition,
                      String key,
                      Consumer<RefDataValue> valueConsumer);

    void consumeValue(ValueStoreKey valueStoreKey,
                      Consumer<RefDataValue> valueConsumer);

    void consumeBytes(ValueStoreKey valueStoreKey,
                      Consumer<ByteBuffer> valueConsumer);

    <T> Optional<T> map(MapDefinition mapDefinition,
                        String key,
                        Function<RefDataValue, T> valueMapper);

    <T> Optional<T> map(ValueStoreKey valueStoreKey,
                        Function<RefDataValue, T> valueMapper);

    <T> Optional<T> mapBytes(ValueStoreKey valueStoreKey,
                             Function<ByteBuffer, T> valueMapper);
}
