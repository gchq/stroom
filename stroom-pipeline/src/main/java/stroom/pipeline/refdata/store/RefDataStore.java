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

import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.util.time.StroomDuration;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface RefDataStore {

    /**
     * Returns true if all the data for the passed stream definition has been successfully loaded into the
     * store and is available for use. Will also touch the last accessed time on the record (if found).
     */
    default boolean isDataLoaded(final RefStreamDefinition refStreamDefinition) {
        return getLoadState(refStreamDefinition)
                .filter(processingState ->
                        processingState.equals(ProcessingState.COMPLETE))
                .isPresent();
    }

    /**
     * Returns the set of map names for the passed refStreamDefinition. Intended to be run
     * after a load.
     */
    Set<String> getMapNames(final RefStreamDefinition refStreamDefinition);

    /**
     * Get the load state for this refStreamDefinition if there is one.
     */
    Optional<ProcessingState> getLoadState(final RefStreamDefinition refStreamDefinition);

    /**
     * Returns true if this {@link MapDefinition} exists in the store. It makes no guarantees about the state
     * of the data.
     */
    boolean exists(final MapDefinition mapDefinition);

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


    /**
     * Performs a lookup using the passed mapDefinition and key and then applies the valueBytesConsumer to
     * the found value. If no value is found the valueBytesConsumer is not called. The consumer lambda must
     * copy the bytes in the buffer if it wants to use them outside the lambda. The consumer must understand
     * how to interpret the bytebuffer passed to it.
     */
    boolean consumeValueBytes(final MapDefinition mapDefinition,
                              final String key,
                              final Consumer<TypedByteBuffer> valueBytesConsumer);

    /**
     * Will initiate a new {@link RefDataLoader} for the passed {@link RefStreamDefinition} and effectiveTimeMs.
     * The passed {@link Consumer} will be called with the new {@link RefDataLoader} only if
     * the {@link RefDataProcessingInfo} for the {@link RefStreamDefinition} is not marked as complete. This test
     * will be performed under a lock on the passed {@link RefStreamDefinition}.
     *
     * @return True if work was executed
     */
    boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition,
                                       final long effectiveTimeMs,
                                       final Consumer<RefDataLoader> work);

    List<RefStoreEntry> list(final int limit);

    List<RefStoreEntry> list(final int limit,
                             final Predicate<RefStoreEntry> filter);

    /**
     * @param filter A filter or null if not filtering is needed.
     * @param takeWhile Must be thread safe. Keeps passing entries to the entryConsumer until this returns false.
     * @param entryConsumer May be called by multiple threads.
     */
    void consumeEntries(final Predicate<RefStoreEntry> filter,
                        final Predicate<RefStoreEntry> takeWhile,
                        final Consumer<RefStoreEntry> entryConsumer);

    List<ProcessingInfoResponse> listProcessingInfo(final int limit);

    List<ProcessingInfoResponse> listProcessingInfo(final int limit,
                                                    final Predicate<ProcessingInfoResponse> filter);

    long getKeyValueEntryCount();

    long getRangeValueEntryCount();

    long getProcessingInfoEntryCount();

    void purgeOldData();

    void purgeOldData(final StroomDuration purgeAge);

    /**
     * Purges this ref stream regardless of how recently it has been accessed
     */
    void purge(final long refStreamId, final long partIndex);

    default void purge(final long refStreamId) {
        purge(refStreamId, RefStreamDefinition.DEFAULT_PART_INDEX);
    }

    void logAllContents();

    void logAllContents(Consumer<String> logEntryConsumer);

    StorageType getStorageType();

    /**
     * @return The size of the store on disk in bytes.
     */
    long getSizeOnDisk();

    String getName();

    enum StorageType {
        ON_HEAP,
        OFF_HEAP
    }
}
