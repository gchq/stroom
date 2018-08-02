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

import stroom.docref.DocRef;
import stroom.guice.PipelineScoped;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.util.logging.LambdaLogger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@PipelineScoped
public class RefDataLoaderHolder {

    private RefDataLoader refDataLoader;

    // Set to keep track of which ref streams have been loaded, re-loaded or confirmed
    // to be already loaded within this pipeline processing instance
    private Set<RefStreamDefinition> availableRefStreamDefinitions = new HashSet<>();

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
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "pipelineReference not found in store {}", pipelineReference), e);
        }
    }
}
