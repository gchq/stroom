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

package stroom.pipeline.refdata.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.refdata.store.offheapstore.RefDataOffHeapStore;
import stroom.pipeline.refdata.store.onheapstore.RefDataOnHeapStore;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RefDataStoreFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataStoreFactory.class);

    private final RefDataStore offHeapRefDataStore;

    @Inject
    RefDataStoreFactory(final RefDataOffHeapStore refDataOffHeapStore) {

        // eager creation of the off heap ref data store, so we know up front if there are any problems creating it
        this.offHeapRefDataStore = refDataOffHeapStore;
    }

    public RefDataStore getOffHeapStore() {
        return offHeapRefDataStore;
    }

    public RefDataStore createOnHeapStore() {
        return new RefDataOnHeapStore();
    }

    public void purgeOldData() {
        this.offHeapRefDataStore.purgeOldData();
        // nothing to purge in the heap stores as they are transient objects
    }

}
