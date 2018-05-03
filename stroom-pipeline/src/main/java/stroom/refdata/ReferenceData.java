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

import stroom.entity.DocumentPermissionCache;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.query.api.v2.DocRef;
import stroom.refdata.saxevents.EventListValue;
import stroom.refdata.saxevents.StringValue;
import stroom.refdata.saxevents.ValueProxy;
import stroom.security.shared.DocumentPermissionNames;
import stroom.streamstore.fs.serializable.StreamSourceInputStream;
import stroom.streamstore.fs.serializable.StreamSourceInputStreamProvider;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.logging.LambdaLogger;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;

public class ReferenceData {
    // Actually 11.5 days but this is fine for the purposes of reference data.
    private static final long APPROX_TEN_DAYS = 1000000000;

    // Maps can be nested during the look up process e.g. "MAP1/MAP2"
    private static final String NEST_SEPARATOR = "/";
    private static final int MINIMUM_BYTE_COUNT = 10;
    private final Map<String, CachedMapStore> nestedStreamCache = new HashMap<>();
    private final Map<MapStoreCacheKey, MapStore> localMapStoreCache = new HashMap<>();

    private EffectiveStreamCache effectiveStreamCache;
    private MapStoreCache mapStoreCache;
    private final FeedHolder feedHolder;
    private final StreamHolder streamHolder;
    private final ContextDataLoader contextDataLoader;
    private final DocumentPermissionCache documentPermissionCache;

    @Inject
    ReferenceData(final EffectiveStreamCache effectiveStreamCache,
                  final MapStoreCache mapStoreCache,
                  final FeedHolder feedHolder,
                  final StreamHolder streamHolder,
                  final ContextDataLoader contextDataLoader,
                  final DocumentPermissionCache documentPermissionCache) {
        this.effectiveStreamCache = effectiveStreamCache;
        this.mapStoreCache = mapStoreCache;
        this.feedHolder = feedHolder;
        this.streamHolder = streamHolder;
        this.contextDataLoader = contextDataLoader;
        this.documentPermissionCache = documentPermissionCache;
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

            final ValueProxy<EventListValue> valueProxy = result.getEventListProxy();
            Optional<EventListValue> optValue = valueProxy.supplyValue();
            // This is a nested map so we are expecting the value of the first map to be a simple
            // string so we can use it as the key for the next map

            if (!optValue.isPresent()) {
                // map broken ... no link found
                result.log(Severity.WARNING, () -> "No map found for '" + startMap + "'");
            } else {
                final EventListValue eventListValue = optValue.get();
                try {
                    final StringValue stringValue = (StringValue) eventListValue;
                    getValue(pipelineReferences, time, nextMap, stringValue.getValue(), result);
                } catch (ClassCastException e) {
                    result.log(Severity.ERROR, () -> LambdaLogger.buildMessage("Value is the wrong type, expected: {}, found: {}",
                            StringValue.class.getName(), eventListValue.getClass().getName()));
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
            if (referenceDataResult.getEventListProxy() != null) {
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
        try {
            // Get nested stream.
            final String streamTypeString = pipelineReference.getStreamType();
            final long streamNo = streamHolder.getStreamNo();
            CachedMapStore cachedMapStore = nestedStreamCache.get(streamTypeString);
            MapStore mapStore = null;

            if (cachedMapStore != null && cachedMapStore.getStreamNo() == streamNo) {
                mapStore = cachedMapStore.getMapStore();
            } else {
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
                    mapStore = getContextData(streamHolder.getStream(), inputStream, pipelineReference.getPipeline());
                }

                cachedMapStore = new CachedMapStore(streamNo, mapStore);
                nestedStreamCache.put(streamTypeString, cachedMapStore);
            }

            if (mapStore != null) {
                result.log(Severity.INFO, () -> "Retrieved map store from context data");

                if (mapStore.getErrorReceiver() != null) {
                    mapStore.getErrorReceiver().replay(result);
                }

                //TODO is this an NPEventList or a SimpleEventList?
                final ValueProxy<EventListValue> eventListProxy = mapStore.getEventListProxy(mapName, keyName);
                if (eventListProxy != null) {
                    result.log(Severity.WARNING, () -> "Map store has no reference data for context data");
                } else {
                    result.log(Severity.INFO, () -> "Map store contains reference data for context data");
                }

                result.setEventListProxy(eventListProxy);
            } else {
                result.log(Severity.WARNING, () -> "No map store can be retrieved for context data");
            }
        } catch (final IOException e) {
            result.log(Severity.ERROR, null, getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    private MapStore getContextData(final Stream stream,
                                    final StreamSourceInputStream contextStream,
                                    final DocRef contextPipeline) {
        if (contextStream != null) {
            // Check the size of the input stream.
            final long byteCount = contextStream.size();
            // Only use context data if we actually have some.
            if (byteCount > MINIMUM_BYTE_COUNT) {
                // build a mapstore from the context stream
                return contextDataLoader.load(contextStream, stream, feedHolder.getFeed(), contextPipeline);
            }
        }

        return new MapStoreImpl();
    }

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
        assert pipelineReference.getFeed() != null && pipelineReference.getFeed().getUuid() != null && pipelineReference.getFeed().getUuid().length() > 0
                && pipelineReference.getStreamType() != null && pipelineReference.getStreamType().length() > 0;

        // Check that the current user has permission to read the stream.
        if (documentPermissionCache == null || documentPermissionCache.hasDocumentPermission(Feed.ENTITY_TYPE, pipelineReference.getFeed().getUuid(), DocumentPermissionNames.USE)) {
            // Create a key to find a set of effective times in the pool.
            final EffectiveStreamKey effectiveStreamKey = new EffectiveStreamKey(pipelineReference.getFeed(),
                    pipelineReference.getStreamType(), fromMs, toMs);
            // Try and fetch a tree set of effective streams for this key.
            final NavigableSet<EffectiveStream> streamSet = effectiveStreamCache.get(effectiveStreamKey);

            if (streamSet != null && streamSet.size() > 0) {
                result.log(Severity.INFO, () -> "Got " + streamSet.size() + " effective streams (" + effectiveStreamKey + ")");

                // Try and find the stream before the requested time that is less
                // than or equal to it.
                final EffectiveStream effectiveStream = streamSet.floor(new EffectiveStream(0, time));
                // If we have an effective time then use it.
                if (effectiveStream != null) {
                    // Now try and get reference data for the feed at this time.
                    final MapStoreCacheKey mapStorePoolKey = new MapStoreCacheKey(pipelineReference.getPipeline(),
                            effectiveStream.getStreamId());
                    // Get the map store associated with this effective feed.
                    final MapStore mapStore = getMapStore(mapStorePoolKey);
                    if (mapStore != null) {
                        result.log(Severity.INFO, () -> "Retrieved map store (" + effectiveStream + ")");

                        if (mapStore.getErrorReceiver() != null) {
                            mapStore.getErrorReceiver().replay(result);
                        }

                        final ValueProxy<EventListValue> valueProxy = mapStore.getEventListProxy(mapName, keyName);
                        if (valueProxy != null) {
                            result.log(Severity.INFO, () -> "Map store contains reference data (" + effectiveStream + ")");

                            result.setEventListProxy(valueProxy);

                        } else {
                            result.log(Severity.WARNING, () -> "Map store has no reference data (" + effectiveStream + ")");
                        }
                    } else {
                        result.log(Severity.WARNING, () -> "No map store can be retrieved (" + effectiveStream + ")");
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

    private MapStore getMapStore(final MapStoreCacheKey mapStoreCacheKey) {
        // Try the local cache first as we may have already got this map.
        MapStore mapStore = localMapStoreCache.get(mapStoreCacheKey);

        // If we didn't get a local cache then look in the pool.
        if (mapStore == null) {
            // Get the map store cache associated with this effective feed.
            mapStore = mapStoreCache.get(mapStoreCacheKey);
            // Cache this item locally for use later on.
            localMapStoreCache.put(mapStoreCacheKey, mapStore);
        }

        return mapStore;
    }

    /**
     * This method puts a key value pair into the appropriate map based on a map
     * name, feed name and the date that the reference data is effective from.
     * It remains here only for test purposes.
     */
    void put(final MapStoreCacheKey mapStoreCacheKey, final MapStore mapStore) {
        localMapStoreCache.put(mapStoreCacheKey, mapStore);
    }

    void setEffectiveStreamCache(final EffectiveStreamCache effectiveStreamcache) {
        this.effectiveStreamCache = effectiveStreamcache;
    }

    void setMapStorePool(final MapStoreCache mapStorePool) {
        this.mapStoreCache = mapStorePool;
    }

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
