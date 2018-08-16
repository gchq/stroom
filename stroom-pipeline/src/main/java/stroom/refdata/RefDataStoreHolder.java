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

package stroom.refdata;

import stroom.guice.PipelineScoped;
import stroom.refdata.store.RefDataStore;
import stroom.refdata.store.RefDataStoreProvider;

import javax.inject.Inject;

@PipelineScoped
public class RefDataStoreHolder {

    private final RefDataStoreProvider refDataStoreProvider;
    private final RefDataStore offHeapRefDataStore;

    private RefDataStore onHeapRefDataStore = null;

    @Inject
    RefDataStoreHolder(final RefDataStoreProvider refDataStoreProvider) {
        this.refDataStoreProvider = refDataStoreProvider;
        this.offHeapRefDataStore = refDataStoreProvider.getOffHeapStore();
    }

    /**
     * Gets a shared off heap store for long term storage of re-usable reference data
     */
    public RefDataStore getOffHeapRefDataStore() {
        return offHeapRefDataStore;
    }

    /**
     * Gets a pipeline scoped on-heap store for storing transient context data for the life
     * of the pipeline process.
     */
    public RefDataStore getOnHeapRefDataStore() {

        // on demand creation of a RefDataStore for this pipeline scope
        if (onHeapRefDataStore == null) {
            onHeapRefDataStore = refDataStoreProvider.createOnHeapStore();
        }
        return onHeapRefDataStore;
    }
}
