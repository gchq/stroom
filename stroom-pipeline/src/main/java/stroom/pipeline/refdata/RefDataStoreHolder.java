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

package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.util.pipeline.scope.PipelineScoped;

import javax.inject.Inject;

@PipelineScoped
class RefDataStoreHolder {

    private final RefDataStoreFactory refDataStoreFactory;
    private final RefDataStore offHeapRefDataStore;

    private RefDataStore onHeapRefDataStore = null;

    @Inject
    RefDataStoreHolder(final RefDataStoreFactory refDataStoreFactory) {
        this.refDataStoreFactory = refDataStoreFactory;
        this.offHeapRefDataStore = refDataStoreFactory.getOffHeapStore();
    }

    /**
     * Gets a shared off heap store for long term storage of re-usable reference data
     */
    RefDataStore getOffHeapRefDataStore() {
        return offHeapRefDataStore;
    }

    /**
     * Gets a pipeline scoped on-heap store for storing transient context data for the life
     * of the pipeline process.
     */
    RefDataStore getOnHeapRefDataStore() {

        // on demand creation of a RefDataStore for this pipeline scope
        if (onHeapRefDataStore == null) {
            onHeapRefDataStore = refDataStoreFactory.createOnHeapStore();
        }
        return onHeapRefDataStore;
    }
}
