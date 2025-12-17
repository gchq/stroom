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

import stroom.pipeline.refdata.store.offheapstore.DelegatingRefDataOffHeapStore;
import stroom.pipeline.refdata.store.onheapstore.RefDataOnHeapStore;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RefDataStoreFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataStoreFactory.class);

    private final DelegatingRefDataOffHeapStore offHeapRefDataStore;

    @Inject
    RefDataStoreFactory(final DelegatingRefDataOffHeapStore refDataOffHeapStore) {

        // eager creation of the off heap ref data store, so we know up front if there are any problems creating it
        this.offHeapRefDataStore = refDataOffHeapStore;
    }

    /**
     * @return A store that will delegate to feed specific stores. Use this if when you are
     * querying multiple ref streams.
     */
    public RefDataStore getOffHeapStore() {
        return offHeapRefDataStore;
    }

    /**
     * @return A store specific to the feed of this refStreamDefinition. Use this when you know you
     * are only dealing with a single ref stream, e.g. during a load.
     */
    public RefDataStore getOffHeapStore(final RefStreamDefinition refStreamDefinition) {
        return offHeapRefDataStore.getEffectiveStore(refStreamDefinition);
    }

    /**
     * @return A store specific to the named feed.
     */
    public RefDataStore getOffHeapStore(final String feedName) {
        return offHeapRefDataStore.getEffectiveStore(feedName);
    }

    /**
     * @return A new transient on heap store for use only in the life of a pipeline.
     */
    public RefDataStore createOnHeapStore() {
        return new RefDataOnHeapStore();
    }

    public void purgeOldData() {
        this.offHeapRefDataStore.purgeOldData();
        // nothing to purge in the heap stores as they are transient objects
    }
}
