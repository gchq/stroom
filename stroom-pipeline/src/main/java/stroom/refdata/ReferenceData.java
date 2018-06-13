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
import stroom.entity.DocumentPermissionCache;
import stroom.feed.shared.Feed;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.v2.DocRef;
import stroom.refdata.offheapstore.MapDefinition;
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
import java.util.List;
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
    private final ReferenceDataLoader referenceDataLoader;
    private final RefDataStore refDataStore;
    private final RefDataLoaderHolder refDataLoaderHolder;
    private final PipelineService pipelineService;
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
                  @Named("cachedPipelineService") final PipelineService pipelineService) {

        this.effectiveStreamCache = effectiveStreamCache;
        this.feedHolder = feedHolder;
        this.streamHolder = streamHolder;
        this.contextDataLoader = contextDataLoader;
        this.documentPermissionCache = documentPermissionCache;
        this.referenceDataLoader = referenceDataLoader;
        this.refDataStore = refDataStore;
        this.refDataLoaderHolder = refDataLoaderHolder;
        this.pipelineService = pipelineService;
        this.security = security;
    }

    /**
     * <p>
     * Given a date and a map name get some reference data value.
     * </p>
     *
     * @param pipelineReferences The references to look for reference data in.
     * @param time               The event time (or the time the reference data is valid for)
     * @param mapName            The map name
     * @param key                The key of the reference data
     * @param result             The reference result object.
     */
    public void getValue(final List<PipelineReference> pipelineReferences,
                         final long time,
                         final String mapName,
                         final String key,
                         final ReferenceDataResult result) {
        // Do we have a nested token?
        final int splitPos = mapName.indexOf(NEST_SEPARATOR);
        if (splitPos != -1) {
            // Yes ... pull out the first map
            final String startMap = mapName.substring(0, splitPos);
            final String nextMap = mapName.substring(splitPos + NEST_SEPARATOR.length());

            // Look up the KV then use that to recurse
            doGetValue(pipelineReferences, time, startMap, key, result);

            final RefDataValueProxy refDataValueProxy = result.getRefDataValueProxy();
            Optional<RefDataValue> optValue = refDataValueProxy.supplyValue();
            // This is a nested map so we are expecting the value of the first map to be a simple
            // string so we can use it as the key for the next map

            if (!optValue.isPresent()) {
                // map broken ... no link found
                result.log(Severity.WARNING, () -> "No map found for '" + startMap + "'");
            } else {
                final RefDataValue refDataValue = optValue.get();
                try {
                    final StringValue stringValue = (StringValue) refDataValue;
                    getValue(pipelineReferences, time, nextMap, stringValue.getValue(), result);
                } catch (ClassCastException e) {
                    result.log(Severity.ERROR, () -> LambdaLogger.buildMessage("Value is the wrong type, expected: {}, found: {}",
                            StringValue.class.getName(), refDataValue.getClass().getName()));
                }
            }
        } else {
            doGetValue(pipelineReferences, time, mapName, key, result);
        }
    }

    private void doGetValue(final List<PipelineReference> pipelineReferences,
                            final long time,
                            final String mapName,
                            final String keyName,
                            final ReferenceDataResult referenceDataResult) {
        for (final PipelineReference pipelineReference : pipelineReferences) {
            // Handle context data differently loading it from the
            // current stream context.
            if (pipelineReference.getStreamType() != null
                    && StreamType.CONTEXT.getName().equals(pipelineReference.getStreamType())) {
                getNestedStreamEventList(pipelineReference, mapName, keyName, referenceDataResult);
            } else {
                getExternalEventList(pipelineReference, time, mapName, keyName, referenceDataResult);
            }

            // If we have a list of events then we are done.
            if (referenceDataResult.getRefDataValueProxy() != null) {
                return;
            }
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

        LOGGER.trace("getNestedStreamEventList called, pipe: {}, map {}, key {}",
                pipelineReference.getName(),
                mapName,
                keyName);
        try {
            // Get nested stream.
            final String streamTypeString = pipelineReference.getStreamType();
            final long streamNo = streamHolder.getStreamNo();

            final PipelineEntity pipelineEntity = pipelineService.loadByUuid(
                    pipelineReference.getPipeline().getUuid());

            LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("StreamId {}, parentStreamId {}",
                    streamHolder.getStream().getId(),
                    streamHolder.getStream().getParentStreamId()));

            // this is a nested stream so use the parent stream Id
            final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                    pipelineReference.getPipeline(),
                    pipelineEntity.getVersion(),
                    streamHolder.getStream().getParentStreamId(), streamNo);



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

            // TODO do we need to look this up now? Why not just look up the value at the tinybuilder stage
            // do the lookup to get the proxy for the value
            final Optional<RefDataValueProxy> optRefDataValueProxy = refDataStore.getValueProxy(
                    new MapDefinition(refStreamDefinition, mapName),
                    keyName);

            if (optRefDataValueProxy.isPresent()) {
                result.setRefDataValueProxy(optRefDataValueProxy.get());
                result.log(Severity.INFO, () -> "Found value for context data");
            } else {
                result.log(Severity.WARNING, () -> LambdaLogger.buildMessage(
                        "No value proxy found when we have just loaded it, map {}, key {}",
                        mapName, keyName));
            }

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
                pipelineReference.getFeed().getUuid().isEmpty()  ||
                pipelineReference.getStreamType() == null ||
                pipelineReference.getStreamType().isEmpty()) {
            result.log(Severity.ERROR, () ->
                    LambdaLogger.buildMessage("pipelineReference is not fully formed, {}", pipelineReference));
        }

        // Check that the current user has permission to read the stream.
        if (documentPermissionCache == null || documentPermissionCache.hasDocumentPermission(
                Feed.ENTITY_TYPE,
                pipelineReference.getFeed().getUuid(),
                DocumentPermissionNames.USE)) {

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

                    final PipelineEntity pipelineEntity = pipelineService.loadByUuid(pipelineReference.getPipeline().getUuid());

                    //TODO should always be zero for a ref stream??
                    final long streamNo = 0;

                    final RefStreamDefinition refStreamDefinition = new RefStreamDefinition(
                            pipelineReference.getPipeline(),
                            pipelineEntity.getVersion(),
                            effectiveStream.getStreamId(), streamNo);

                    if (refDataLoaderHolder.isRefStreamAvailable(refStreamDefinition)) {
                        // we have already loaded or confirmed the load state for this refStream in this
                        // pipeline process so no need to try again
                    } else {
                        // we don't know what the load state is for this refStreamDefinition so need to find out

                        // establish if we have the data for the effective stream in the store
                        final boolean isEffectiveStreamDataLoaded = refDataStore.isDataLoaded(refStreamDefinition);

                        //TODO what happens if we need to overwrite?
                        // ideally we want to check the overwrite status of the refFilter here so we don't have to set
                        // up the whole pipeline before discovering overwrite is false.

                        if (!isEffectiveStreamDataLoaded) {
                            // we don't have the data so kick off a process to load it all in
                            LOGGER.debug("Loading effective stream {}", refStreamDefinition);

                            // initiate a load of the ref data for this stream
                            security.asProcessingUser(() ->
                                    referenceDataLoader.load(refStreamDefinition));
                        }
                    }

                    //TODO do we need to do this here?
                    // now we have the data so just do the lookup
                    final MapDefinition mapDefinition = new MapDefinition(refStreamDefinition, mapName);
                    final Optional<RefDataValueProxy> optRefDataValueProxy = refDataStore.getValueProxy(mapDefinition, keyName);

                    LOGGER.debug("Lookup for mapDefinition {}, key {}, returned {}", mapDefinition, keyName, optRefDataValueProxy);

                    if (optRefDataValueProxy.isPresent()) {
                        result.log(Severity.INFO, () -> "Map store contains reference data (" + effectiveStream + ")");
                        result.setRefDataValueProxy(optRefDataValueProxy.get());
                    } else {
                        result.log(Severity.WARNING, () -> "Map store has no reference data (" + effectiveStream + ")");
                    }
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

    private static class CachedMapStore {
        private final long streamNo;
        private final MapStore mapStore;

        CachedMapStore(final long streamNo, final MapStore mapStore) {
            this.streamNo = streamNo;
            this.mapStore = mapStore;
        }

        public long getStreamNo() {
            return streamNo;
        }

        public MapStore getMapStore() {
            return mapStore;
        }

        @Override
        public int hashCode() {
            return (int) streamNo;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof CachedMapStore)) {
                return false;
            }

            final CachedMapStore cachedMapStore = (CachedMapStore) obj;

            return cachedMapStore.streamNo == streamNo;
        }
    }
}
