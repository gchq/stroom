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

package stroom.pipeline.refdata;

import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A pipeline scoped holder for a {@link RefDataLoader} and a place to store transient state relating to
 * the loading of reference data using a reference data pipeline.
 */
@PipelineScoped
public class RefDataLoaderHolder {

    private RefDataLoader refDataLoader;

    // Set to keep track of which ref streams have been loaded, re-loaded or confirmed
    // to be already loaded within this pipeline processing instance
    private final Set<RefStreamDefinition> availableRefStreamDefinitions = new HashSet<>();

    // Used to cache the pipeline versions for the life of the pipeline to prevent
    // repeated DB hits during lookups
    private final Map<DocRef, String> pipelineDocRefToVersionCache = new HashMap<>();

    public RefDataLoader getRefDataLoader() {
        return refDataLoader;
    }

    public void setRefDataLoader(final RefDataLoader refDataLoader) {
        this.refDataLoader = refDataLoader;
    }

    public void markRefStreamAsAvailable(final RefStreamDefinition refStreamDefinition) {
        availableRefStreamDefinitions.add(refStreamDefinition);
    }

    public boolean isRefStreamAvailable(final RefStreamDefinition refStreamDefinition) {
        return availableRefStreamDefinitions.contains(refStreamDefinition);
    }

    public String getPipelineVersion(final PipelineReference pipelineReference, final PipelineStore pipelineStore) {
        try {
            //only fetch the version once per pipeline process
            return pipelineDocRefToVersionCache.computeIfAbsent(pipelineReference.getPipeline(), docRef ->
                    pipelineStore.readDocument(pipelineReference.getPipeline()).getVersion());
        } catch (final Exception e) {
            throw new RuntimeException(LogUtil.message(
                    "pipelineReference not found in store {}", pipelineReference), e);
        }
    }
}
