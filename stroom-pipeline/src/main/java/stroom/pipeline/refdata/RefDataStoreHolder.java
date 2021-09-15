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

import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScoped;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;

@PipelineScoped
class RefDataStoreHolder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefDataStoreHolder.class);

    private final RefDataStoreFactory refDataStoreFactory;
    private final RefDataStore offHeapRefDataStore;

    // Hold the maps that are known to be in each stream, built up as we load each stream.
    private final Map<PipelineReference, Set<String>> pipeRefToMapNamesMap = new HashMap<>();

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

    boolean isLookupNeeded(final PipelineReference pipelineReference,
                           final String mapName) {
        // This map is populated post load once the maps in the stream are known.
        // A stream may contain 1-* map names.
        final Set<String> mapNames = pipeRefToMapNamesMap.get(pipelineReference);
        final boolean isLookupNeeded;
        if (mapNames == null) {
            // not done a load yet so can't be sure what maps are in the stream
            isLookupNeeded = true;
        } else {
            // We have done a load so see if the stream has our map
            isLookupNeeded = mapNames.contains(mapName);
        }

        LOGGER.debug(() -> LogUtil.message(
                "isLookupNeeded: {}, mapName: {}, mapNames: {} for feed: {}, streamType: {}, pipelineName: {}",
                isLookupNeeded,
                mapName,
                mapNames,
                pipelineReference.getFeed().getName(),
                pipelineReference.getStreamType(),
                pipelineReference.getPipeline().getName()));

        return isLookupNeeded;
    }

    void addKnownMapNames(final RefDataStore refDataStore,
                          final PipelineReference pipelineReference,
                          final RefStreamDefinition refStreamDefinition) {

        final Optional<ProcessingState> optLoadState = refDataStore.getLoadState(refStreamDefinition);

        // Make sure the load was good
        if (optLoadState.filter(ProcessingState.COMPLETE::equals).isPresent()) {
            final Set<String> mapNames = refDataStore.getMapNames(refStreamDefinition);

            LOGGER.debug(() -> LogUtil.message(
                    "Putting mapNames: {} for feed: {}, streamType: {}, pipelineName: {}",
                    mapNames,
                    pipelineReference.getFeed().getName(),
                    pipelineReference.getStreamType(),
                    pipelineReference.getPipeline().getName()));

            pipeRefToMapNamesMap.put(pipelineReference, mapNames);
        } else {
            LOGGER.debug("Load not complete, optLoadState: {}", optLoadState);
        }
    }
}
