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
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScoped;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@PipelineScoped
class RefDataStoreHolder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefDataStoreHolder.class);

    private final RefDataStoreFactory refDataStoreFactory;
//    private final RefDataStore offHeapRefDataStore;

    // Hold the maps that are known to be in each stream, built up as we load each stream.
    // Note this is pipeline scope so exists for the life of the pipeline process.
    private final Map<RefStreamDefinition, Set<String>> refStreamDefToMapNamesMap = new HashMap<>();

    private RefDataStore onHeapRefDataStore = null;

    @Inject
    RefDataStoreHolder(final RefDataStoreFactory refDataStoreFactory) {
        this.refDataStoreFactory = refDataStoreFactory;
//        this.offHeapRefDataStore = refDataStoreFactory.getOffHeapStore();
    }

    /**
     * Gets a shared off heap store for long term storage of re-usable reference data
     */
    RefDataStore getOffHeapRefDataStore(final RefStreamDefinition refStreamDefinition) {
        return refDataStoreFactory.getOffHeapStore(refStreamDefinition);
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
     * doing a lookup. If we haven't loaded refStreamDefinition yet then return UNKNOWN.
     * This saves having to do multiple pointless gets on the store to find there is no map
     */
    MapAvailability getMapAvailabilityInStream(final PipelineReference pipelineReference,
                                               final RefStreamDefinition refStreamDefinition,
                                               final String mapName) {
        // This map is populated post load once the maps in the stream are known.
        // A stream may contain 1-* map names.
        final Set<String> mapNames = refStreamDefToMapNamesMap.get(refStreamDefinition);
        final MapAvailability mapAvailability = getMapAvailability(mapName, mapNames);

        LOGGER.trace(() -> LogUtil.message(
                "mapAvailability: {}, map: {}, available maps: {}, feed: {}, type: {}, stream: {}, partIdx: {}",
                mapAvailability,
                mapName,
                NullSafe.toStringOrElse(mapNames, "<NOT YET KNOWN>"),
                NullSafe.get(pipelineReference, PipelineReference::getFeed, DocRef::getName),
                NullSafe.get(pipelineReference, PipelineReference::getStreamType),
                NullSafe.get(refStreamDefinition, RefStreamDefinition::getStreamId),
                NullSafe.get(refStreamDefinition, RefStreamDefinition::getPartIndex)));

        return mapAvailability;
    }

    @NotNull
    static MapAvailability getMapAvailability(final String mapName, final Set<String> mapNames) {
        final MapAvailability mapAvailability;
        if (mapNames == null) {
            // Not done a lookup yet so can't be sure what maps are in the stream, therefore
            // we must assume that a lookup is required.
            mapAvailability = MapAvailability.UNKNOWN;
        } else {
            // The DB has been queried to find out what maps exist, so we can be sure whether a lookup
            // is needed or not.  If the map is known to not exist then no point in doing a lookup.
            mapAvailability = mapNames.contains(mapName)
                    ? MapAvailability.PRESENT
                    : MapAvailability.NOT_PRESENT;
        }
        return mapAvailability;
    }

    /**
     * Cache the set of map names known to exist in this {@link RefStreamDefinition}.
     */
    void addKnownMapNames(final RefDataStore refDataStore,
                          final RefStreamDefinition refStreamDefinition) {

        if (!refStreamDefToMapNamesMap.containsKey(refStreamDefinition)) {
            final Optional<ProcessingState> optLoadState = refDataStore.getLoadState(
                    refStreamDefinition);

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


    // --------------------------------------------------------------------------------


    /**
     * The availability of a named map in a reference data stream (i.e. {@link RefStreamDefinition}).
     */
    public enum MapAvailability {

        /**
         * We have established, via querying the DB that the map is present in the stream
         * so a lookup is required.
         */
        PRESENT(true),

        /**
         * We have established, via querying the DB that the map is NOT present in the stream
         * so there is no point in doing a lookup.
         */
        NOT_PRESENT(false),

        /**
         * We don't yet know if the map is in the stream or not, so we have to do a lookup.
         */
        UNKNOWN(true);

        private final boolean isLookupRequired;

        MapAvailability(final boolean isLookupRequired) {
            this.isLookupRequired = isLookupRequired;
        }

        /**
         * @return True if this {@link MapAvailability} means that a lookup in the database
         * is required.
         */
        public boolean isLookupRequired() {
            return isLookupRequired;
        }
    }
}
