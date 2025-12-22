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

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.ProcessingState;
import stroom.pipeline.refdata.store.RefDataLoader;
import stroom.pipeline.refdata.store.RefDataProcessingInfo;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefStreamDefinition;
import stroom.pipeline.refdata.store.StagingValue;
import stroom.pipeline.refdata.store.StagingValueOutputStream;
import stroom.pipeline.refdata.store.StringValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

class OnHeapRefDataLoader implements RefDataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnHeapRefDataLoader.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OnHeapRefDataLoader.class);

    private static final Comparator<Range<Long>> RANGE_COMPARATOR = Comparator
            .comparingLong(Range::getFrom);

    private final RefStreamDefinition refStreamDefinition;
    private final long effectiveTimeMs;
    private boolean overwriteExisting = false;
    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Set<MapDefinition> mapDefinitions;
    private final Map<KeyValueMapKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;
    private final RefDataStore refDataStore;
    private final Set<String> loadedMapNames = new HashSet<>();

    private int putsCounter = 0;
    private int successfulPutsCounter = 0;
    private Instant startTime = Instant.EPOCH;
    private LoaderState currentLoaderState = LoaderState.NEW;

    private KeyPutOutcomeHandler keyPutOutcomeHandler = null;
    private RangePutOutcomeHandler rangePutOutcomeHandler = null;

    private enum LoaderState {
        NEW,
        INITIALISED,
        COMPLETED,
        CLOSED
    }

    OnHeapRefDataLoader(final RefStreamDefinition refStreamDefinition,
                        final long effectiveTimeMs,
                        final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap,
                        final Set<MapDefinition> mapDefinitions,
                        final Map<KeyValueMapKey, RefDataValue> keyValueMap,
                        final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap,
                        final RefDataStore refDataStore) {

        this.refStreamDefinition = refStreamDefinition;
        this.effectiveTimeMs = effectiveTimeMs;
        this.processingInfoMap = processingInfoMap;
        this.mapDefinitions = mapDefinitions;
        this.keyValueMap = keyValueMap;
        this.rangeValueNestedMap = rangeValueNestedMap;
        this.refDataStore = refDataStore;
    }

    @Override
    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    @Override
    public PutOutcome initialise(final boolean overwriteExisting) {
        LOGGER.debug("initialise called, overwriteExisting: {}", overwriteExisting);
        checkCurrentState(LoaderState.NEW);

        this.overwriteExisting = overwriteExisting;
        startTime = Instant.now();

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                effectiveTimeMs,
                ProcessingState.LOAD_IN_PROGRESS);

        final PutOutcome putOutcome = putProcessingInfo(refStreamDefinition, refDataProcessingInfo);

        currentLoaderState = LoaderState.INITIALISED;
        return putOutcome;
    }

    @Override
    public void markPutsComplete() {
        LOGGER.trace("markPutsComplete() called");
    }

    @Override
    public void completeProcessing(final ProcessingState processingState) {
        Objects.requireNonNull(processingState);
        LOGGER.trace("Completing processing with state {} (put count {})", processingState, putsCounter);

        if (!VALID_COMPLETION_STATES.contains(processingState)) {
            throw new RuntimeException(LogUtil.message("Invalid processing state {}, should be one of {}",
                    processingState,
                    VALID_COMPLETION_STATES));
        }

        startTime = Instant.now();

        if (LoaderState.COMPLETED.equals(currentLoaderState)) {
            LOGGER.debug("Loader already completed, doing nothing");
        } else {
            checkCurrentState(LoaderState.INITIALISED);

            // Set the processing info record to COMPLETE and update the last update time
            updateProcessingState(
                    refStreamDefinition,
                    processingState,
                    true);

            final String mapNames = String.join(",", loadedMapNames);
            final Duration loadDuration = Duration.between(startTime, Instant.now());

            if (processingState.equals(ProcessingState.COMPLETE)) {
                // INFO is a bit noisy for context loads
                LOGGER.debug("Successfully loaded {} entries out of {} attempts with map names [{}] in {} for {}",
                        successfulPutsCounter,
                        putsCounter,
                        mapNames,
                        loadDuration,
                        refStreamDefinition);
            } else {
                LOGGER.error("Failed to load {} entries out of {} attempts with outcome {}, " +
                             "map names [{}] in {} for {}",
                        successfulPutsCounter,
                        putsCounter,
                        processingState,
                        mapNames,
                        loadDuration,
                        refStreamDefinition);
            }


//        LAMBDA_LOGGER.doIfTraceEnabled(() ->
//                refDataStore.logAllContents(LOGGER::trace));

            currentLoaderState = LoaderState.COMPLETED;
        }
    }

    @Override
    public void setCommitInterval(final int putsBeforeCommit) {
        // do noting, no concept of commit interval
    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final String key,
                    final StagingValue refDataValue) {

        checkCurrentState(LoaderState.INITIALISED);
        final KeyValueMapKey mapKey = new KeyValueMapKey(mapDefinition, key);

        LAMBDA_LOGGER.trace(() ->
                LogUtil.message("containsKey == {}", keyValueMap.containsKey(mapKey)));

        final PutOutcome putOutcome = putRefEntryWithOutcome(
                keyValueMap,
                mapKey,
                refDataValue,
                overwriteExisting);

        recordPut(mapDefinition, putOutcome.isSuccess());

        LAMBDA_LOGGER.trace(() -> LogUtil.message("put completed for {} {} {}, size now {}",
                mapDefinition, key, refDataValue, keyValueMap.size()));

        NullSafe.consume(keyPutOutcomeHandler, handler -> handler.handleOutcome(
                () -> mapDefinition,
                key,
                putOutcome));
    }

    @Override
    public void put(final MapDefinition mapDefinition,
                    final Range<Long> keyRange,
                    final StagingValue refDataValue) {

        checkCurrentState(LoaderState.INITIALISED);
        // ensure we have a sub map for our mapDef
        final NavigableMap<Range<Long>, RefDataValue> subMap = rangeValueNestedMap.computeIfAbsent(
                mapDefinition,
                k -> new TreeMap<>(RANGE_COMPARATOR.reversed()));

        final PutOutcome putOutcome = putRefEntryWithOutcome(
                subMap,
                keyRange,
                refDataValue,
                overwriteExisting);

        recordPut(mapDefinition, putOutcome.isSuccess());
        LAMBDA_LOGGER.trace(() -> LogUtil.message("put completed for {} {} {}, size now {}",
                mapDefinition, keyRange, refDataValue,
                Optional.ofNullable(rangeValueNestedMap.get(mapDefinition))
                        .map(NavigableMap::size)
                        .orElse(0)));

        NullSafe.consume(rangePutOutcomeHandler, handler -> handler.handleOutcome(
                () -> mapDefinition,
                keyRange,
                putOutcome));
    }

    @Override
    public void setKeyPutOutcomeHandler(final KeyPutOutcomeHandler keyPutOutcomeHandler) {
        this.keyPutOutcomeHandler = keyPutOutcomeHandler;
    }

    @Override
    public void setRangePutOutcomeHandler(final RangePutOutcomeHandler rangePutOutcomeHandler) {
        this.rangePutOutcomeHandler = rangePutOutcomeHandler;
    }

    private void recordPut(final MapDefinition mapDefinition, final boolean wasValuePut) {
        if (wasValuePut) {
            successfulPutsCounter++;
        }
        putsCounter++;
        mapDefinitions.add(mapDefinition);
        loadedMapNames.add(mapDefinition.getMapName());
    }

    @Override
    public void close() throws Exception {
        // nothing to close
    }

    private void updateProcessingState(final RefStreamDefinition refStreamDefinition,
                                       final ProcessingState newProcessingState,
                                       final boolean touchLastAccessedTime) {

        processingInfoMap.compute(refStreamDefinition, (refStreamDef, refDataProcessingInfo) -> {
            if (refDataProcessingInfo != null) {
                final RefDataProcessingInfo newRefDataProcessingInfo = refDataProcessingInfo
                        .cloneWithNewState(newProcessingState, touchLastAccessedTime);
                return newRefDataProcessingInfo;
            } else {
                throw new RuntimeException(LogUtil.message(
                        "No processing info entry found for {}", refStreamDefinition));
            }
        });
    }

    private PutOutcome putProcessingInfo(final RefStreamDefinition refStreamDefinition,
                                         final RefDataProcessingInfo refDataProcessingInfo) {

//        final boolean keyExists = processingInfoMap.containsKey(refStreamDefinition);

        final PutOutcome putOutcome = putWithOutcome(
                processingInfoMap,
                refStreamDefinition,
                refDataProcessingInfo,
                overwriteExisting);
//        if (overwriteExisting) {
//            processingInfoMap.put(refStreamDefinition, refDataProcessingInfo);
//            putOutcome = keyExists
//                    ? PutOutcome.replacedEntry()
//                    : PutOutcome.newEntry();
//        } else {
//            if (keyExists) {
//                putOutcome = PutOutcome.failed();
//            } else {
//                processingInfoMap.put(refStreamDefinition, refDataProcessingInfo);
//                putOutcome = PutOutcome.newEntry();
//            }
//        }
        return putOutcome;
    }

    private void checkCurrentState(final LoaderState... validStates) {
        boolean isCurrentStateValid = false;
        for (final LoaderState loaderState : validStates) {
            if (currentLoaderState.equals(loaderState)) {
                isCurrentStateValid = true;
                break;
            }
        }
        if (!isCurrentStateValid) {
            throw new IllegalStateException(LogUtil.message("Current loader state: {}, valid states: {}",
                    currentLoaderState, Arrays.toString(validStates)));
        }
    }

    private <K> PutOutcome putRefEntryWithOutcome(final Map<K, RefDataValue> map,
                                                  final K key,
                                                  final RefDataValue refDataValue,
                                                  final boolean overwriteExisting) {
        final boolean keyExists = map.containsKey(key);
        final PutOutcome putOutcome;
        final RefDataValue refDataValueCopy = convertToOnHeap(refDataValue);
        if (keyExists) {
            if (!overwriteExisting) {
                putOutcome = PutOutcome.failed();
            } else {
                if (refDataValueCopy.isNullValue()) {
                    map.remove(key);
                    putOutcome = PutOutcome.replacedEntry();
                } else {
                    putOutcome = putWithOutcome(map, key, refDataValueCopy, true);
                }
            }
        } else {
            if (refDataValueCopy.isNullValue()) {
                putOutcome = PutOutcome.success();
            } else {
                putOutcome = putWithOutcome(map, key, refDataValueCopy, overwriteExisting);
            }

        }
        return putOutcome;
    }

    private RefDataValue convertToOnHeap(final RefDataValue refDataValue) {
        Objects.requireNonNull(refDataValue);
        RefDataValue newRefDataValue = refDataValue;
        // Ensure nulls are consistent
        if (newRefDataValue.isNullValue() && !(newRefDataValue instanceof NullValue)) {
            newRefDataValue = NullValue.getInstance();

        } else if (newRefDataValue instanceof final StagingValueOutputStream stagingValueOutputStream) {
            final int typeId = stagingValueOutputStream.getTypeId();
            newRefDataValue = switch (typeId) {
                case NullValue.TYPE_ID -> NullValue.getInstance();
                case StringValue.TYPE_ID -> new StringValue(stagingValueOutputStream);
                case FastInfosetValue.TYPE_ID -> {
                    final ByteBuffer heapBuffer = ByteBuffer.allocate(stagingValueOutputStream.getValueBuffer()
                            .remaining());
                    ByteBufferUtils.copy(stagingValueOutputStream.getValueBuffer(), heapBuffer);
                    yield new FastInfosetValue(
                            heapBuffer,
                            stagingValueOutputStream.getValueHashCode(),
                            stagingValueOutputStream.getValueStoreHashAlgorithm());
                }
                default -> throw new RuntimeException("Unexpected type " + typeId);
            };
        } else if (newRefDataValue instanceof final FastInfosetValue fastInfosetValue) {
            // FastInfosetValue may contain a buffer that is reused, so we need to copy
            // it into a new heap buffer
            LOGGER.debug("Copying fastInfosetValue to a heap based buffer");
            newRefDataValue = fastInfosetValue.copy(() ->
                    ByteBuffer.allocate(fastInfosetValue.size()));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Returning a {}, {}",
                    newRefDataValue.getClass().getSimpleName(), newRefDataValue);
        }

        return newRefDataValue;
    }

    private <K, V> PutOutcome putWithOutcome(final Map<K, V> map,
                                             final K key,
                                             final V value,
                                             final boolean overwriteExisting) {

        final PutOutcome putOutcome;
        if (overwriteExisting) {
            final V prevValue = map.put(key, value);
            putOutcome = prevValue == null
                    ? PutOutcome.newEntry()
                    : PutOutcome.replacedEntry();
        } else {
            final V prevValue = map.putIfAbsent(key, value);
            putOutcome = prevValue == null
                    ? PutOutcome.newEntry()
                    : PutOutcome.failed();
        }

        LOGGER.trace("Put key {}, value {}, outcome {}", key, value, putOutcome);

        return putOutcome;
    }
}
