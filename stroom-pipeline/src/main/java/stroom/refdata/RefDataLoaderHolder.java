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
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefStreamDefinition;

import java.util.HashSet;
import java.util.Set;

@PipelineScoped
public class RefDataLoaderHolder {
    private RefDataLoader refDataLoader;

    // Set to keep track of which ref streams have been loaded, re-loaded or confirmed
    // to be already loaded within this pipeline processing instance
    private Set<RefStreamDefinition> loadedRefStreamDefinitions = new HashSet<>();

    RefDataLoaderHolder(final RefDataLoader refDataLoader) {
        this.refDataLoader = refDataLoader;
    }

    public RefDataLoader getRefDataLoader() {
        return refDataLoader;
    }

    public void setRefDataLoader(final RefDataLoader refDataLoader) {
        this.refDataLoader = refDataLoader;
    }

    public void markRefStreamAsLoaded(final RefStreamDefinition refStreamDefinition) {
        loadedRefStreamDefinitions.add(refStreamDefinition);
    }

    public boolean isRefStreamLoaded(final RefStreamDefinition refStreamDefinition) {
        return loadedRefStreamDefinitions.contains(refStreamDefinition);
    }
}
