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

import stroom.entity.shared.Range;

public interface RefDataLoader extends AutoCloseable {

    /**
     * @return The {@link RefStreamDefinition} that this loader is loading data for
     */
    RefStreamDefinition getRefStreamDefinition();

    /**
     * Creates the initial ProcessingInfo entry to mark this stream definition
     * as having a load in progress.
     * @param overwriteExisting If true, allows duplicate keys to override existing values
     */
    boolean initialise(final boolean overwriteExisting);

    /**
     * Completes the load, committing any outstanding work and marking the ProcessingInfo
     * entry as complete for this stream definition
     */
    void completeProcessing();

    /**
     * Set the number of puts to perform before committing the operations so far
     */
    void setCommitInterval(final int putsBeforeCommit);

    /**
     * Put an entry into the key/value store. The overwriteExisting setting of the loader
     * will govern how duplicates are handled.
     * @param mapDefinition The {@link MapDefinition} that this entry is associated with
     * @param key The key
     * @param refDataValue The value
     * @return True if the entry was put into the store
     */
    boolean put(final MapDefinition mapDefinition,
                final String key,
                final RefDataValue refDataValue);

    /**
     * Put an entry into the range/value store. The overwriteExisting setting of the loader
     * will govern how duplicates are handled.
     * @param mapDefinition The {@link MapDefinition} that this entry is associated with
     * @param keyRange The key range that the value is associate with
     * @param refDataValue The value
     * @return True if the entry was put into the store
     */
    boolean put(final MapDefinition mapDefinition,
                final Range<Long> keyRange,
                final RefDataValue refDataValue);
}
