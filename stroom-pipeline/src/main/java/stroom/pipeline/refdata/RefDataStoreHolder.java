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
import stroom.util.NullSafe;
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
    // Note this is pipeline scope so exists for the life of the pipeline process.
    private final Map<RefStreamDefinition, Set<String>> refStreamDefToMapNamesMap = new HashMap<>();

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

    /**
     * Check if the map exists in this {@link RefStreamDefinition} to determine if we need to bother
     * doing a lookup. If we haven't loaded refStreamDefinition yet then return true.
     * This saves having to do multiple pointless gets on the store to find there is no map
     */
    boolean isLookupNeeded(final RefStreamDefinition refStreamDefinition,
                           final String mapName) {
        // This map is populated post load once the maps in the stream are known.
        // A stream may contain 1-* map names.
        final Set<String> mapNames = refStreamDefToMapNamesMap.get(refStreamDefinition);
        final boolean isLookupNeeded;
        if (mapNames == null) {
            // Not done a lookup yet so can't be sure what maps are in the stream, therefore
            // we must do a lookup
            isLookupNeeded = true;
        } else {
            // A previous lookup has discovered what maps are in the stream so use that info
            isLookupNeeded = mapNames.contains(mapName);
        }

        LOGGER.trace(() -> LogUtil.message(
                "isLookupNeeded: {}, mapName: {}, mapNames: {} for refStreamDefinition: {}",
                isLookupNeeded,
                mapName,
                NullSafe.toStringOrElse(mapNames, "<NOT YET KNOWN>"),
                refStreamDefinition));

        return isLookupNeeded;
    }

    /**
     * Cache the set of map names known to exist in this {@link RefStreamDefinition}.
     */
    void addKnownMapNames(final RefDataStore refDataStore,
                          final RefStreamDefinition refStreamDefinition) {

        if (!refStreamDefToMapNamesMap.containsKey(refStreamDefinition)) {
            final Optional<ProcessingState> optLoadState = refDataStore.getLoadState(refStreamDefinition);

            // Make sure the load was good
            if (optLoadState.filter(ProcessingState.COMPLETE::equals).isPresent()) {
                final Set<String> mapNames = refDataStore.getMapNames(refStreamDefinition);

                // Debug as should only be done once per stream
                LOGGER.debug(() -> LogUtil.message(
                        "Putting mapNames: {} for refStreamDefinition: {}",
                        mapNames,
                        refStreamDefinition));

                refStreamDefToMapNamesMap.put(refStreamDefinition, mapNames);
            } else {
                LOGGER.trace("Load not complete, optLoadState: {}", optLoadState);
            }
        } else {
            LOGGER.trace(() -> LogUtil.message("RefStreamDefinition {} already known, available maps: {}",
                    refStreamDefinition, refStreamDefToMapNamesMap.get(refStreamDefinition)));
        }
    }
}
