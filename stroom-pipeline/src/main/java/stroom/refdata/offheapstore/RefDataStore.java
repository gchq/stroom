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

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public interface RefDataStore {

    Optional<RefDataProcessingInfo> getProcessingInfo(final RefStreamDefinition refStreamDefinition);

    /**
     * Returns true if all the data for the passed stream definition has been successfully loaded into the
     * store and is available for use.
     */
    boolean isDataLoaded(final RefStreamDefinition refStreamDefinition);

//    /**
//     * Performs a lookup using the passed mapDefinition and key and if not found will call the refDataValueSupplier
//     * to create a new entry for that mapDefinition, key and value. The check-and-put will be done in an atomic way
//     * so no external synchronisation is required.
//     */
//    void put(final MapDefinition mapDefinition,
//             final String key,
//             final Supplier<RefDataValue> refDataValueSupplier,
//             final boolean overwriteExistingValue);
//
//    /**
//     * Performs a lookup using the passed mapDefinition and keyRange and if not found will call the refDataValueSupplier
//     * to create a new entry for that mapDefinition, keyRange and value. The check-and-put will be done in an atomic way
//     * so no external synchronisation is required.
//     */
//    void put(final MapDefinition mapDefinition,
//             final Range<Long> keyRange,
//             final Supplier<RefDataValue> refDataValueSupplier,
//             final boolean overwriteExistingValue);

    /**
     * Gets a value from the store for the passed mapDefinition and key. If not found returns an empty {@link Optional}.
     */
    Optional<RefDataValue> getValue(final MapDefinition mapDefinition,
                                    final String key);

    /**
     * Looks up the passed key and mapDefinition in the store and if found returns a proxy to the
     * actual value. The proxy allows the value to be read/processed/mapped inside a transaction
     * to avoid unnecessary copying of data.
     */
    RefDataValueProxy getValueProxy(final MapDefinition mapDefinition,
                                    final String key);

    Optional<RefDataValue> getValue(final ValueStoreKey valueStoreKey);


    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueConsumer to
     * the found value. If no value is found the valueConsumer is not called
     */
    void consumeValue(final MapDefinition mapDefinition,
                      final String key,
                      final Consumer<RefDataValue> valueConsumer);

    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueBytesConsumer to
     * the found value. If no value is found the valueBytesConsumer is not called
     */
    void consumeValueBytes(final MapDefinition mapDefinition,
                           final String key,
                           final Consumer<ByteBuffer> valueBytesConsumer);

    void consumeValue(final ValueStoreKey valueStoreKey,
                      final Consumer<RefDataValue> valueConsumer);

    void consumeBytes(final ValueStoreKey valueStoreKey,
                      final Consumer<ByteBuffer> valueConsumer);

    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueMapper to
     * the found value, returning the value in an {@link Optional}. If no value is found an empty
     * {@link Optional} is returned. The valueMapper will be applied inside a transaction.
     */
    <T> Optional<T> map(final MapDefinition mapDefinition,
                        final String key,
                        final Function<RefDataValue, T> valueMapper);

    <T> Optional<T> map(final ValueStoreKey valueStoreKey,
                        final Function<RefDataValue, T> valueMapper);

    <T> Optional<T> mapBytes(final ValueStoreKey valueStoreKey,
                             final Function<ByteBuffer, T> valueMapper);

    RefDataLoader loader(final RefStreamDefinition refStreamDefinition,
                         final long effectiveTimeMs);

    /**
     * Will initiate a new {@link RefDataLoader} for the passed {@link RefStreamDefinition} and effectiveTimeMs.
     * The passed {@link Consumer} will be called with the new {@link RefDataLoader} only if
     * the {@link RefDataProcessingInfo} for the {@link RefStreamDefinition} is not marked as complete. This test
     * will be performed under a lock on the passed {@link RefStreamDefinition}.
     */
    void doWithLoader(final RefStreamDefinition refStreamDefinition,
                      final long effectiveTimeMs,
                      final Consumer<RefDataLoader> work);

    long getKeyValueEntryCount();

    long getKeyRangeValueEntryCount();

    void purgeOldData();

    void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition,
                                       final Runnable work);
}
