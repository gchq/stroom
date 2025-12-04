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

import stroom.lmdb.PutOutcome;
import stroom.util.shared.Range;

import java.util.Set;
import java.util.function.Supplier;

public interface RefDataLoader extends AutoCloseable {

    Set<ProcessingState> VALID_COMPLETION_STATES = Set.of(
            ProcessingState.COMPLETE,
            ProcessingState.FAILED,
            ProcessingState.TERMINATED);

    /**
     * @return The {@link RefStreamDefinition} that this loader is loading data for
     */
    RefStreamDefinition getRefStreamDefinition();

    /**
     * Creates the initial ProcessingInfo entry to mark this stream definition
     * as having a load in progress.
     *
     * @param overwriteExisting If true, allows duplicate keys to override existing values
     * @return
     */
    PutOutcome initialise(final boolean overwriteExisting);

    /**
     * Call this when all calls to {@link RefDataLoader#put(MapDefinition, Range, StagingValue)}
     * and {@link RefDataLoader#put(MapDefinition, String, StagingValue)} have been completed.
     * This will initiate the transfer of entries from the staging store to the main ref store.
     * Call it before {@link RefDataLoader#completeProcessing(ProcessingState)}.
     */
    void markPutsComplete();

    /**
     * Completes the load, committing any outstanding work and marking the ProcessingInfo
     * entry as complete for this stream definition
     */
    default void completeProcessing() {
        completeProcessing(ProcessingState.COMPLETE);
    }

    /**
     * Completes the load, committing any outstanding work and marking the ProcessingInfo
     * entry with processingState for this stream definition
     */
    void completeProcessing(final ProcessingState processingState);

    /**
     * Set the number of puts to perform before committing the operations so far
     */
    void setCommitInterval(final int putsBeforeCommit);

    /**
     * Put an entry into the key/value store. The overwriteExisting setting of the loader
     * will govern how duplicates are handled.
     *
     * @param mapDefinition The {@link MapDefinition} that this entry is associated with
     * @param key           The key
     * @param refDataValue  The value
     */
    void put(final MapDefinition mapDefinition,
             final String key,
             final StagingValue refDataValue);

    /**
     * Put an entry into the range/value store. The overwriteExisting setting of the loader
     * will govern how duplicates are handled.
     *
     * @param mapDefinition The {@link MapDefinition} that this entry is associated with
     * @param keyRange      The key range that the value is associate with
     * @param refDataValue  The value
     */
    void put(final MapDefinition mapDefinition,
             final Range<Long> keyRange,
             final StagingValue refDataValue);

    /**
     * Provide a handler that will be called each time a staged entry is put to the store.
     */
    void setKeyPutOutcomeHandler(final KeyPutOutcomeHandler keyPutOutcomeHandler);

    /**
     * Provide a handler that will be called each time a staged entry is put to the store.
     */
    void setRangePutOutcomeHandler(final RangePutOutcomeHandler keyPutOutcomeHandler);


    // --------------------------------------------------------------------------------


    interface KeyPutOutcomeHandler {

        void handleOutcome(final Supplier<MapDefinition> mapDefinitionSupplier,
                           final String key,
                           final PutOutcome putOutcome);
    }


    // --------------------------------------------------------------------------------


    interface RangePutOutcomeHandler {

        void handleOutcome(final Supplier<MapDefinition> mapDefinitionSupplier,
                           final Range<Long> range,
                           final PutOutcome putOutcome);
    }
}
