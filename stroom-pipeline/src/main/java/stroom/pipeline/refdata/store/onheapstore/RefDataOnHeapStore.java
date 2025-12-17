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

package stroom.pipeline.refdata.store.onheapstore;

import stroom.pipeline.refdata.store.AbstractRefDataStore;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.time.StroomDuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A heap based implementation of the {@link RefDataStore}
 * that is intended for use with reference data with a very short life, i.e. context
 * data that is transient and only required for the life of a single pipeline processor
 * task. As such it is not thread safe and only intended to be used to by a single thread.
 * It also does not support purge operations as it is expected that the store will be created,
 * populated, used, then destroyed when no longer needed.
 */
public class RefDataOnHeapStore extends AbstractRefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOnHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOnHeapStore.class);

    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Set<MapDefinition> mapDefinitions;
    private final Map<KeyValueMapKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;

    public RefDataOnHeapStore() {

        this.processingInfoMap = new HashMap<>();
        this.keyValueMap = new HashMap<>();
        this.rangeValueNestedMap = new HashMap<>();
        this.mapDefinitions = new HashSet<>();
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.ON_HEAP;
    }

//    @Override
//    public Optional<RefDataProcessingInfo> getAndTouchProcessingInfo(final RefStreamDefinition refStreamDefinition) {
//
//        return Optional.ofNullable(processingInfoMap.computeIfPresent(
//                refStreamDefinition,
//                (refStreamDef, refDataProcessingInfo) ->
//                        refDataProcessingInfo.updateLastAccessedTime()));
//    }

    @Override
    public Optional<ProcessingState> getLoadState(final RefStreamDefinition refStreamDefinition) {
        final Optional<ProcessingState> optProcessingState =
                Optional.ofNullable(processingInfoMap.get(refStreamDefinition))
                        .map(RefDataProcessingInfo::getProcessingState);
        LOGGER.trace("isDataLoaded({}) returning {}", refStreamDefinition, optProcessingState);
        return optProcessingState;
    }

    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        final boolean exists = mapDefinitions.contains(mapDefinition);

        LOGGER.trace("exists({}) returning {}", mapDefinition, exists);
        return exists;
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition, final String key) {
        // try the KV store first

        Optional<RefDataValue> result;
        final KeyValueMapKey keyValueMapKey = new KeyValueMapKey(mapDefinition, key);
        final RefDataValue refDataValue = keyValueMap.get(keyValueMapKey);
        if (refDataValue != null) {
            result = Optional.of(refDataValue);
        } else {
            // not found in kv map so look in the range map
            try {
                // speculative lookup in the range store. At this point we don't know if we have
                // any ranges for this mapdef or not, but either way we need a call to LMDB so
                // just do the range lookup
                final long keyLong = Long.parseLong(key);

                // Look up our long key in the range store to see if it is part of a range.
                // It is possible for an event stream to have no associated context stream, or
                // the context stream may not contain the map and/or key requested in the lookup
                final NavigableMap<Range<Long>, RefDataValue> rangeSubMap = rangeValueNestedMap.get(mapDefinition);
                if (NullSafe.hasEntries(rangeSubMap)) {
                    result = getValueByRange(rangeSubMap, keyLong);
                } else {
                    result = Optional.empty();
                }

            } catch (final NumberFormatException e) {
                // key could not be converted to a long, either this mapdef has no ranges or
                // an invalid key was used. See if we have any ranges at all for this mapdef
                // to determine whether to error or not.
                final boolean doesStoreContainRanges = rangeValueNestedMap.containsKey(mapDefinition);
                if (doesStoreContainRanges) {
                    // we have ranges for this map def, so we would expect to be able to convert the key
                    throw new RuntimeException(LogUtil.message(
                            "Key {} cannot be used with the range store as it cannot be converted to a long",
                            key),
                            e);
                }
                // no ranges for this map def so the fact that we could not convert the key to a long
                // is not a problem. Do nothing.
                result = Optional.empty();
            }
        }
        final Optional<RefDataValue> result2 = result;
        LAMBDA_LOGGER.trace(() -> LogUtil.message("getValue({}, {}) returning {}", mapDefinition, key, result2));
        return result;
    }

    @Override
    public Set<String> getMapNames(final RefStreamDefinition refStreamDefinition) {
        Objects.requireNonNull(refStreamDefinition);
        return mapDefinitions.stream()
                .filter(mapDefinition ->
                        mapDefinition.getRefStreamDefinition().equals(refStreamDefinition))
                .map(MapDefinition::getMapName)
                .collect(Collectors.toSet());
    }

    private Optional<RefDataValue> getValueByRange(final NavigableMap<Range<Long>, RefDataValue> rangeSubMap,
                                                   final long key) {

        // We want to scan backwards over all keys with the passed mapDefinitionUid,
        // starting with a range from == key. E.g. with the following data
        // in the order it appears in the map

        // 400-500
        // 300-400
        // 200-300
        // 100-200

        // key  | DB keys returned
        // -----|-----------------
        // 50   | [no rows]
        // 150  | 100-200
        // 300  | 300-400, 200-300, 100-200
        // 1000 | 400-500, 300-400, 200-300, 100-200

        RefDataValue refDataValue = null;

        // the to part doesn't matter as the comparator only considers the from part
        final Range<Long> startKey = new Range<>(key, null);

        // create a sub-set of the map starting at the key of interest or the next smallest
        // range from value. Note the map is reverse sorted to increase confusion.
        final SortedMap<Range<Long>, RefDataValue> tailMap = rangeSubMap.tailMap(startKey);
        for (final Map.Entry<Range<Long>, RefDataValue> entry : tailMap.entrySet()) {
            // see if our key is in the found range
            if (entry.getKey().contains(key)) {
                // found our entry
                refDataValue = entry.getValue();
                LOGGER.trace("Found our match {}", entry);
                break;
            }
        }

        if (refDataValue == null) {
            LOGGER.trace("No match found");
        }

        return Optional.ofNullable(refDataValue);
    }

    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {

        throw new UnsupportedOperationException(
                "This implementation doesn't support this method as the values are heap objects ");
    }

    @Override
    public List<RefStoreEntry> list(final int limit) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<RefStoreEntry> list(final int limit,
                                    final Predicate<RefStoreEntry> filter) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    //    @Override
//    public <T> T consumeEntryStream(final Function<Stream<RefStoreEntry>, T> streamFunction) {
//        throw new UnsupportedOperationException("Not yet implemented");
//    }
//
    @Override
    public void consumeEntries(final Predicate<RefStoreEntry> filter,
                               final Predicate<RefStoreEntry> takeWhile,
                               final Consumer<RefStoreEntry> entryConsumer) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit,
                                                           final Predicate<ProcessingInfoResponse> filter) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<ProcessingInfoResponse> listProcessingInfo(final int limit) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public long getKeyValueEntryCount() {
        return keyValueMap.size();
    }

    @Override
    public long getRangeValueEntryCount() {
        return rangeValueNestedMap.entrySet()
                .stream()
                .mapToLong(entry -> entry.getValue().size())
                .sum();
    }

    @Override
    public long getProcessingInfoEntryCount() {
        return processingInfoMap.size();
    }

    @Override
    public void purgeOldData() {
        throw new UnsupportedOperationException("Purge functionality is not supported for the on-heap store " +
                                                "as the data is transient");
    }

    @Override
    public void purgeOldData(final StroomDuration purgeAge) {
        throw new UnsupportedOperationException("Purge functionality is not supported for the on-heap store " +
                                                "as the data is transient");
    }

    @Override
    public void purge(final long refStreamId, final long partIndex) {
        throw new UnsupportedOperationException("Purge functionality is not supported for the on-heap store " +
                                                "as the data is transient");
    }

    @Override
    public void logAllContents() {
        logAllContents(LOGGER::debug);
    }

    @Override
    public void logAllContents(final Consumer<String> logEntryConsumer) {

        logEntryConsumer.accept(LogUtil.message("Dumping contents of processingInfoMap"));

        processingInfoMap.forEach((k, v) ->
                logEntryConsumer.accept(LogUtil.message("{} => {}", k, v)));

        logEntryConsumer.accept(LogUtil.message("Dumping contents of keyValueMap"));

        keyValueMap.forEach((k, v) ->
                logEntryConsumer.accept(LogUtil.message("{} => {}", k, v)));

        logEntryConsumer.accept(LogUtil.message("Dumping contents of rangeValueNestedMap"));

        rangeValueNestedMap.forEach((k, v) -> {
            logEntryConsumer.accept(LogUtil.message("{} =>", k));
            v.forEach((subKey, subValue) ->
                    logEntryConsumer.accept(LogUtil.message("   {} => {}", subKey, subValue)));
        });
    }

    /**
     * Intended only for testing use.
     */
    void setLastAccessedTime(final RefStreamDefinition refStreamDefinition, final long timeMs) {

        processingInfoMap.compute(refStreamDefinition, (refStreamDef, refDataProcessingInfo) -> {
            if (refDataProcessingInfo != null) {
                return new RefDataProcessingInfo(
                        refDataProcessingInfo.getCreateTimeEpochMs(),
                        timeMs,
                        refDataProcessingInfo.getEffectiveTimeEpochMs(),
                        refDataProcessingInfo.getProcessingState());
            } else {
                throw new RuntimeException(LogUtil.message(
                        "No processing info entry found for {}", refStreamDefinition));
            }
        });
    }

    /**
     * Get an instance of a {@link RefDataLoader} for bulk loading multiple entries for a given
     * {@link RefStreamDefinition} and its associated effectiveTimeMs. The {@link RefDataLoader}
     * should be used in a try with resources block to ensure any transactions are closed, e.g.
     * <pre>try (RefDataLoader refDataLoader = refDataOffHeapStore.getLoader(...)) { ... }</pre>
     *
     * @param refStreamDefinition
     * @param effectiveTimeMs
     */
    @Override
    protected RefDataLoader createLoader(final RefStreamDefinition refStreamDefinition,
                                         final long effectiveTimeMs) {

        return new OnHeapRefDataLoader(
                refStreamDefinition,
                effectiveTimeMs,
                processingInfoMap,
                mapDefinitions,
                keyValueMap,
                rangeValueNestedMap,
                this);
    }

    @Override
    public long getSizeOnDisk() {
        // On heap so zero size on disk
        return 0;
    }

    @Override
    public String getName() {
        return "On Heap";
    }
}
