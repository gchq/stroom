/*
 * Copyright 2016 Crown Copyright
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

package stroom.refdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.entity.DocumentPermissionCache;
import stroom.feed.shared.Feed;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.MultiRefDataValueProxy;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.RefDataValueProxy;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.StringValue;
import stroom.security.Security;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;

public class ReferenceData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceData.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ReferenceData.class);

    // Actually 11.5 days but this is fine for the purposes of reference data.
    private static final long APPROX_TEN_DAYS = 1000000000;

    // Maps can be nested during the look up process e.g. "MAP1/MAP2"
    private static final String NEST_SEPARATOR = "/";
    private static final int MINIMUM_BYTE_COUNT = 10;
//    private final Map<String, CachedMapStore> nestedStreamCache = new HashMap<>();
//    private final Map<MapStoreCacheKey, MapStore> localMapStoreCache = new HashMap<>();

    private EffectiveStreamCache effectiveStreamCache;
    //    private MapStoreCache mapStoreCache;
    private final FeedHolder feedHolder;
    private final StreamHolder streamHolder;
    private final ContextDataLoader contextDataLoader;
    private final DocumentPermissionCache documentPermissionCache;
    private final Map<PipelineReference, Boolean> localDocumentPermissionCache = new HashMap<>();
    private final ReferenceDataLoader referenceDataLoader;
    private final RefDataStore refDataStore;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final PipelineStore pipelineStore;
    private final Security security;

    @Inject
    ReferenceData(final EffectiveStreamCache effectiveStreamCache,
                  final FeedHolder feedHolder,
                  final StreamHolder streamHolder,
                  final ContextDataLoader contextDataLoader,
                  final DocumentPermissionCache documentPermissionCache,
                  final ReferenceDataLoader referenceDataLoader,
                  final RefDataStore refDataStore,
                  final RefDataLoaderHolder refDataLoaderHolder,
                  final Security security,
                  @Named("cachedPipelineStore") final PipelineStore pipelineStore) {

        this.effectiveStreamCache = effectiveStreamCache;
        this.feedHolder = feedHolder;
        this.streamHolder = streamHolder;
        this.contextDataLoader = contextDataLoader;
        this.documentPermissionCache = documentPermissionCache;
        this.referenceDataLoader = referenceDataLoader;
        this.refDataStore = refDataStore;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.pipelineStore = pipelineStore;
        this.security = security;
    }

    /**
     * <p>
     * Given a {@link LookupIdentifier} and a list of ref data pipelines, ensure that
     * the data required to perform the lookup is in the ref store. This method will not
     * perform the lookup, instead it will populate the {@link ReferenceDataResult} with
     * a proxy object that can later be used to do the lookup.
     * </p>
     *
     * @param pipelineReferences The references to look for reference data in.
     * @param lookupIdentifier   The identifier to lookup in the reference data
     * @param result             The reference result object containing the proxy object for performing the lookup
     */
    public void ensureReferenceDataAvailability(final List<PipelineReference> pipelineReferences,
                                                final LookupIdentifier lookupIdentifier,
                                                final ReferenceDataResult result) {

        LOGGER.trace("ensureReferenceDataAvailability({}, {}", pipelineReferences, lookupIdentifier);

        // Do we have a nested token?

//        final int splitPos = mapName.indexOf(NEST_SEPARATOR);
        if (lookupIdentifier.isMapNested()) {
            LOGGER.trace("lookupIdentifier is nested {}", lookupIdentifier);
//        if (splitPos != -1) {
            // Yes ... pull out the first map
//            final String startMap = mapName.substring(0, splitPos);
//            final String nextMap = mapName.substring(splitPos + NEST_SEPARATOR.length());

            // Look up the KV then use that to recurse
            doGetValue(pipelineReferences, lookupIdentifier, result);

            final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy();
            Optional<RefDataValue> optValue = refDataValueProxy.supplyValue();
            // This is a nested map so we are expecting the value of the first map to be a simple
            // string so we can use it as the key for the next map

            if (!optValue.isPresent()) {
                LOGGER.trace("sub-map not found for {}", lookupIdentifier);
                // map broken ... no link found
                result.log(Severity.WARNING, () -> "No map found for '" + lookupIdentifier + "'");
            } else {
                final RefDataValue refDataValue = optValue.get();
                try {
                    final String mapName = ((StringValue) refDataValue).getValue();
                    LOGGER.trace("Found sub-map {}", mapName);
                    // use the value from this lookup as the key for the nested map
                    LookupIdentifier nestedIdentifier = lookupIdentifier.getNestedLookupIdentifier(mapName);

                    ensureReferenceDataAvailability(pipelineReferences, nestedIdentifier, result);
                } catch (ClassCastException e) {
                    result.log(Severity.ERROR, () -> LambdaLogger.buildMessage("Value is the wrong type, expected: {}, found: {}",
                            StringValue.class.getName(), refDataValue.getClass().getName()));
                }
            }
        } else {
            LOGGER.trace("lookupIdentifier is not nested {}", lookupIdentifier);
            // non-nested map so just do a lookup
            doGetValue(pipelineReferences, lookupIdentifier, result);
        }
    }

    private void doGetValue(final List<PipelineReference> pipelineReferences,
                            final LookupIdentifier lookupIdentifier,
                            final ReferenceDataResult referenceDataResult) {

        List<RefDataValueProxy> refDataValueProxies = new ArrayList<>();

        // A data feed can have multiple ref pipelines associated with it and we don't know which
        // contains the map/key we are after. None-all could. At the moment we ensure the data is loaded
        // for the effective stream of all associated ref pipelines. Given that the user probably included
        // multiple ref pipelines for a reason it is probably reasonable to load them all, then do the lookups.
        for (final PipelineReference pipelineReference : pipelineReferences) {
            LOGGER.trace("doGetValue - processing pipelineReference {} for {}", pipelineReference, lookupIdentifier);
            // Handle context data differently loading it from the
            // current stream context.
            if (pipelineReference.getStreamType() != null
                    && StreamType.CONTEXT.getName().equals(pipelineReference.getStreamType())) {

                getNestedStreamEventList(
                        pipelineReference,
                        lookupIdentifier.getPrimaryMapName(),
                        lookupIdentifier.getKey(),
                        referenceDataResult);
            } else {
                getExternalEventList(
                        pipelineReference,
                        lookupIdentifier.getEventTime(),
                        lookupIdentifier.getPrimaryMapName(),
                        lookupIdentifier.getKey(),
                        referenceDataResult);
            }

            // We are dealing with multiple ref pipelines so collect all the value proxies
            if (pipelineReferences.size() > 1 && referenceDataResult.getRefDataValueProxy() != null) {
                refDataValueProxies.add(referenceDataResult.getRefDataValueProxy());
            }
        }
        // We are dealing with multiple ref pipelines so replace the current value proxy with a
        // multi one that will perform a lookup on each one in turn
        if (!refDataValueProxies.isEmpty()) {
            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage(
                    "Replacing value proxy with multi proxy ({})", refDataValueProxies.size()));
            referenceDataResult.setRefDataValueProxy(new MultiRefDataValueProxy(refDataValueProxies));
        }
    }

    /**
     * Get an event list from a stream that is a nested child of the current
     * stream context and is therefore not effective time sensitive.
     */
    private void getNestedStreamEventList(final PipelineReference pipelineReference,
                                          final String mapName,
                                          final String keyName,
                                          final ReferenceDataResult result) {

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("getNestedStreamEventList called, pipe: {}, map {}, key {}",
                pipelineReference.getName(),
                mapName,
                keyName));
        try {
            // Get nested stream.
            final String streamTypeString = pipelineReference.getStreamType();
            final long streamNo = streamHolder.getStreamNo();

            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("StreamId {}, parentStreamId {}",
                    streamHolder.getStream().getId(),
                    streamHolder.getStream().getParentStreamId()));

            // the parent stream appears to be null at this point so just use the stream id
            final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineReference.getPipeline(),
                    getPipelineVersion(pipelineReference),
//                    streamHolder.getStream().getParentStreamId());
                    streamHolder.getStream().getId());


            // TODO we may want to implement some sort of on-heap store that fronts our off-heap store
            // Thus all writes/reads go through the on-heap store first and only data that is deemed
            // too big for the on-heap store gets passed down to the off-heap store. This would reduce
            // the disk io and storage for small or transient data and reduce the number of write txns
            // used.  This may be an unnecessary optimisation.

            // Establish if we have the data for the context stream in the store
            final boolean isEffectiveStreamDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);

            //TODO what happens if we need to overwrite?

            if (!isEffectiveStreamDataLoaded) {
                // data is not in the store so load it

                StreamType streamType = null;
                for (final StreamType st : StreamType.initialValues()) {
                    if (st.getName().equals(streamTypeString)) {
                        streamType = st;
                        break;
                    }
                }
                final StreamSourceInputStreamProvider provider = streamHolder.getProvider(streamType);
                // There may not be a provider for this stream type if we do not
                // have any context data stream.
                if (provider != null) {
                    final StreamSourceInputStream inputStream = provider.getStream(streamNo);
                    loadContextData(streamHolder.getStream(), inputStream, pipelineReference.getPipeline());
                }
            }

            // Define a proxy object to allow callers to get the required value from the store
            // now that we know that the stream that may contain it is in there.
            final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
            final RefDataValueProxy refDataValueProxy = refDataStore.getValueProxy(mapDefinition, keyName);
            result.setRefDataValueProxy(refDataValueProxy);

//            // do the lookup to get the proxy for the value
//            final Optional<RefDataValueProxy> optRefDataValueProxy = refDataStore.getValueProxy(
//                    new MapDefinition(refStreamDefinition, mapName),
//                    keyName);
//            result.setRefDataValueProxy();
//
//            if (optRefDataValueProxy.isPresent()) {
//                result.setRefDataValueProxy(optRefDataValueProxy.get());
//                result.log(Severity.INFO, () -> "Found value for context data");
//            } else {
//                result.log(Severity.WARNING, () -> LambdaLogger.buildMessage(
//                        "No value proxy found when we have just loaded it, map {}, key {}",
//                        mapName, keyName));
//            }

            // the data is now in the store so get the value proxy

//            CachedMapStore cachedMapStore = nestedStreamCache.get(streamTypeString);
//            MapStore mapStore = null;
//
//            if (cachedMapStore != null && cachedMapStore.getStreamNo() == streamNo) {
//                mapStore = cachedMapStore.getMapStore();
//            } else {
//                StreamType streamType = null;
//                for (final StreamType st : StreamType.initialValues()) {
//                    if (st.getName().equals(streamTypeString)) {
//                        streamType = st;
//                        break;
//                    }
//                }
//                final StreamSourceInputStreamProvider provider = streamHolder.getProvider(streamType);
//                // There may not be a provider for this stream type if we do not
//                // have any context data stream.
//                if (provider != null) {
//                    final StreamSourceInputStream inputStream = provider.getStream(streamNo);
//
//                    mapStore = getContextData(streamHolder.getStream(), inputStream, pipelineReference.getPipeline());
//                }
//
//                cachedMapStore = new CachedMapStore(streamNo, mapStore);
//                nestedStreamCache.put(streamTypeString, cachedMapStore);
//            }
//
//            if (mapStore != null) {
//                result.log(Severity.INFO, () -> "Retrieved map store from context data");
//
//                if (mapStore.getErrorReceiver() != null) {
//                    mapStore.getErrorReceiver().replay(result);
//                }
//
//                final ValueProxy<EventListValue> eventListProxy = mapStore.getEventListProxy(mapName, keyName);
//                if (eventListProxy != null) {
//                    result.log(Severity.WARNING, () -> "Map store has no reference data for context data");
//                } else {
//                    result.log(Severity.INFO, () -> "Map store contains reference data for context data");
//                }
//
//                result.setRefDataValueProxy(eventListProxy);
//            } else {
//                result.log(Severity.WARNING, () -> "No map store can be retrieved for context data");
//            }
        } catch (final IOException e) {
            result.log(Severity.ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private void loadContextData(
            final Stream stream,
            final StreamSourceInputStream contextStream,
            final DocRef contextPipeline) {

        if (contextStream != null) {
            // Check the size of the input stream.
            final long byteCount = contextStream.size();
            // Only use context data if we actually have some.
            if (byteCount > MINIMUM_BYTE_COUNT) {
                // build a mapstore from the context stream
                contextDataLoader.load(contextStream, stream, feedHolder.getFeed(), contextPipeline);
            }
        }
    }

//    private MapStore getContextData(final Stream stream, final StreamSourceInputStream contextStream,
//                                    final DocRef contextPipeline) {
//        if (contextStream != null) {
//            // Check the size of the input stream.
//            final long byteCount = contextStream.size();
//            // Only use context data if we actually have some.
//            if (byteCount > MINIMUM_BYTE_COUNT) {
//                // build a mapstore from the context stream
//                return contextDataLoader.load(contextStream, stream, feedHolder.getFeed(), contextPipeline);
//            }
//        }
//
//        return new MapStoreImpl();
//    }

    /**
     * Get an event list from a store level/non nested stream that is sensitive
     * to effective time.
     */
    private void getExternalEventList(final PipelineReference pipelineReference,
                                      final long time,
                                      final String mapName,
                                      final String keyName,
                                      final ReferenceDataResult result) {
        // Create a window of approx 10 days to cache effective streams.
        // First round down the time to the nearest 10 days approx (actually more like 11.5, one billion milliseconds).
        final long fromMs = (time / APPROX_TEN_DAYS) * APPROX_TEN_DAYS;
        final long toMs = fromMs + APPROX_TEN_DAYS;

        // Make sure the reference feed is persistent otherwise lookups will
        // fail as the equals method will only test for feeds that are the
        // same object instance rather than id.
        if (pipelineReference.getFeed() == null ||
                pipelineReference.getFeed().getUuid() == null ||
                pipelineReference.getFeed().getUuid().isEmpty() ||
                pipelineReference.getStreamType() == null ||
                pipelineReference.getStreamType().isEmpty()) {
            result.log(Severity.ERROR, () ->
                    LambdaLogger.buildMessage("pipelineReference is not fully formed, {}", pipelineReference));
        }

        // Check that the current user has permission to read the stream.
        final boolean hasPermission = localDocumentPermissionCache.computeIfAbsent(pipelineReference, k ->
                documentPermissionCache == null ||
                        documentPermissionCache.hasDocumentPermission(
                                Feed.ENTITY_TYPE,
                                pipelineReference.getFeed().getUuid(),
                                DocumentPermissionNames.USE));


        if (hasPermission) {
            // Create a key to find a set of effective times in the pool.
            final EffectiveStreamKey effectiveStreamKey = new EffectiveStreamKey(
                    pipelineReference.getFeed(),
                    pipelineReference.getStreamType(),
                    fromMs,
                    toMs);

            // Try and fetch a tree set of effective streams for this key.
            final NavigableSet<EffectiveStream> streamSet = effectiveStreamCache.get(effectiveStreamKey);

            if (streamSet != null && streamSet.size() > 0) {
                result.log(Severity.INFO, () -> "Got " + streamSet.size() + " effective streams (" + effectiveStreamKey + ")");

                // Try and find the stream before the requested time that is less
                // than or equal to it.
                final EffectiveStream effectiveStream = streamSet.floor(new EffectiveStream(0, time));
                // If we have an effective time then use it.
                if (effectiveStream != null) {

                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            pipelineReference.getPipeline(),
                            getPipelineVersion(pipelineReference),
                            effectiveStream.getStreamId());

                    // First check the pipeline scoped object to save us hitting the store for every lookup in a
                    // pipeline process run.
                    if (refDataLoaderHolder.isRefStreamAvailable(refStreamDefinition)) {
                        // we have already loaded or confirmed the load state for this refStream in this
                        // pipeline process so no need to try again
                        LOGGER.debug("refStreamDefinition {} is available for use", refStreamDefinition);
                    } else {
                        // we don't know what the load state is for this refStreamDefinition so need to find out
                        // by querying the store
                        final boolean isEffectiveStreamDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);

                        if (!isEffectiveStreamDataLoaded) {
                            // we don't have the complete data so kick off a process to load it all
                            LOGGER.debug("Creating task to load reference data {}", refStreamDefinition);

                            // initiate a load of the ref data for this stream

                            security.asProcessingUser(() ->
                                    referenceDataLoader.load(refStreamDefinition));

                            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                                    "Loaded {} refStreamDefinition", refStreamDefinition));

                            // mark these ref stream defs as available for future lookups within this pipeline process
                            refDataLoaderHolder.markRefStreamAsAvailable(refStreamDefinition);
                        }
                    }

                    // now we should have the required data in the store (unless the max age is set far to small)
                    final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                    final RefDataValueProxy refDataValueProxy = refDataStore.getValueProxy(mapDefinition, keyName);
                    result.setRefDataValueProxy(refDataValueProxy);
                } else {
                    result.log(Severity.WARNING, () -> "No effective streams can be found in the returned set (" + effectiveStreamKey + ")");
                }
            } else {
                result.log(Severity.WARNING, () -> "No effective streams can be found (" + effectiveStreamKey + ")");
            }
        } else {
            result.log(Severity.ERROR, () -> "User does not have permission to use data from feed '" + pipelineReference.getFeed().getName() + "'");
        }
    }


    private String getPipelineVersion(PipelineReference pipelineReference) {
        try {
            return pipelineStore.readDocument(pipelineReference.getPipeline()).getVersion();
        } catch (Exception e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "pipelineReference not found in store {}", pipelineReference), e);
        }
    }

//    private MapStore getMapStore(final MapStoreCacheKey mapStoreCacheKey) {
//        // Try the local cache first as we may have already got this map.
//        MapStore mapStore = localMapStoreCache.get(mapStoreCacheKey);
//
//        // If we didn't get a local cache then look in the pool.
//        if (mapStore == null) {
//            // Get the map store cache associated with this effective feed.
//            mapStore = mapStoreCache.get(mapStoreCacheKey);
//            // Cache this item locally for use later on.
//            localMapStoreCache.put(mapStoreCacheKey, mapStore);
//        }
//
//        return mapStore;
//    }

    /**
     * This method puts a key value pair into the appropriate map based on a map
     * name, feed name and the date that the reference data is effective from.
     * It remains here only for test purposes.
     */
//    void put(final MapStoreCacheKey mapStoreCacheKey, final MapStore mapStore) {
//        localMapStoreCache.put(mapStoreCacheKey, mapStore);
//    }

    void setEffectiveStreamCache(final EffectiveStreamCache effectiveStreamcache) {
        this.effectiveStreamCache = effectiveStreamcache;
    }

//    void setMapStorePool(final MapStoreCache mapStorePool) {
//        this.mapStoreCache = mapStorePool;
//    }
//    void setMapStorePool(final MapStoreCache mapStorePool) {
//        this.mapStoreCache = mapStorePool;
//    }

//    private static class CachedMapStore {
//        private final long streamNo;
//        private final MapStore mapStore;
//        private final int hashCode;
//
//        CachedMapStore(final long streamNo, final MapStore mapStore) {
//            this.streamNo = streamNo;
//            this.mapStore = mapStore;
//            hashCode = Long.hashCode(streamNo);
//        }
//
//        public long getStreamNo() {
//            return streamNo;
//        }

//    private static class CachedMapStore {
//        private final long streamNo;
//        private final MapStore mapStore;
//
//        CachedMapStore(final long streamNo, final MapStore mapStore) {
//            this.streamNo = streamNo;
//            this.mapStore = mapStore;
//        }
//
//        public long getStreamNo() {
//            return streamNo;
//        }
//
//        public MapStore getMapStore() {
//            return mapStore;
//        }
//
//        @Override
//        public int hashCode() {
//            return (int) streamNo;
//        }
//
//        @Override
//        public boolean equals(final Object obj) {
//            if (obj == null || !(obj instanceof CachedMapStore)) {
//                return false;
//            }
//
//            final CachedMapStore cachedMapStore = (CachedMapStore) obj;
//
//            return cachedMapStore.streamNo == streamNo;
//        }
//    }
//        public MapStore getMapStore() {
//            return mapStore;
//        }

//        @Override
//        public boolean equals(final Object o) {
//            if (this == o) return true;
//            if (o == null || getClass() != o.getClass()) return false;
//            final CachedMapStore that = (CachedMapStore) o;
//            return streamNo == that.streamNo;
//        }
//
//        @Override
//        public int hashCode() {
//            return hashCode;
//        }
//    }
}