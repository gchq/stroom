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

    Optional<ProcessingInfo> getProcessingInfo(final RefStreamDefinition refStreamDefinition);

    //TODO consider a bulk put method or a builder type class to check/load them all in one txn
    /**
     * Performs a lookup using the passed mapDefinition and key and if not found will call the refDataValueSupplier
     * to create a new entry for that mapDefinition, key and value. The check-and-put will be done in an atomic way
     * so no external synchronisation is required.
     */
    void put(final MapDefinition mapDefinition,
             final String key,
             final Supplier<RefDataValue> refDataValueSupplier,
             final boolean overwriteExistingValue);

    /**
     * Performs a lookup using the passed mapDefinition and keyRange and if not found will call the refDataValueSupplier
     * to create a new entry for that mapDefinition, keyRange and value. The check-and-put will be done in an atomic way
     * so no external synchronisation is required.
     */
    void put(final MapDefinition mapDefinition,
             final Range<Long> keyRange,
             final Supplier<RefDataValue> refDataValueSupplier,
             final boolean overwriteExistingValue);

    Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                    final String key);

    Optional<RefDataValue> getValue(final ValueStoreKey valueStoreKey);

    void consumeValue(final MapDefinition mapDefinition,
                      final String key,
                      final Consumer<RefDataValue> valueConsumer);

    void consumeValue(final ValueStoreKey valueStoreKey,
                      final Consumer<RefDataValue> valueConsumer);

    void consumeBytes(final ValueStoreKey valueStoreKey,
                      final Consumer<ByteBuffer> valueConsumer);

    <T> Optional<T> map(final MapDefinition mapDefinition,
                        final String key,
                        final Function<RefDataValue, T> valueMapper);

    <T> Optional<T> map(final ValueStoreKey valueStoreKey,
                        final Function<RefDataValue, T> valueMapper);

    <T> Optional<T> mapBytes(final ValueStoreKey valueStoreKey,
                             final Function<ByteBuffer, T> valueMapper);
}
