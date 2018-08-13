package stroom.refdata.onheapstore;

import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

class OnHeapRefDataLoader implements RefDataLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OnHeapRefDataLoader.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(OnHeapRefDataLoader.class);

    private static final Comparator<Range<Long>> RANGE_COMPARATOR = Comparator
            .comparingLong(Range::getFrom);

    private final RefStreamDefinition refStreamDefinition;
    private final long effectiveTimeMs;
    private boolean overwriteExisting = false;
    private final Lock refStreamDefReentrantLock;
    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Map<KeyValueStoreKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;
    private final Set<MapDefinition> loadedMapDefinitions;

    private int putsCounter = 0;
    private int successfulPutsCounter = 0;
    private Instant startTime = Instant.EPOCH;
    private LoaderState currentLoaderState = LoaderState.NEW;

    private enum LoaderState {
        NEW,
        INITIALISED,
        COMPLETED,
        CLOSED
    }

    OnHeapRefDataLoader(final RefStreamDefinition refStreamDefinition,
                        final long effectiveTimeMs,
                        final Striped<Lock> refStreamDefStripedReentrantLock,
                        final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap,
                        final Map<KeyValueStoreKey, RefDataValue> keyValueMap,
                        final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap) {

        this.refStreamDefinition = refStreamDefinition;
        this.effectiveTimeMs = effectiveTimeMs;
        this.processingInfoMap = processingInfoMap;
        this.keyValueMap = keyValueMap;
        this.rangeValueNestedMap = rangeValueNestedMap;
        this.refStreamDefReentrantLock = refStreamDefStripedReentrantLock.get(refStreamDefinition);
        this.loadedMapDefinitions = new HashSet<>();

        LAMBDA_LOGGER.logDurationIfDebugEnabled(
                () -> {
                    try {
                        LOGGER.debug("Acquiring lock");
                        refStreamDefReentrantLock.lockInterruptibly();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Load aborted due to thread interruption");
                    }
                },
                () -> LambdaLogger.buildMessage("Acquiring lock for {}", refStreamDefinition));
    }

    @Override
    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    @Override
    public boolean initialise(final boolean overwriteExisting) {
        LOGGER.debug("initialise called, overwriteExisting: {}", overwriteExisting);
        checkCurrentState(LoaderState.NEW);

        this.overwriteExisting = overwriteExisting;
        startTime = Instant.now();

        final RefDataProcessingInfo refDataProcessingInfo = new RefDataProcessingInfo(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                effectiveTimeMs,
                ProcessingState.LOAD_IN_PROGRESS);

        boolean didPutSucceed = putProcessingInfo(refStreamDefinition, refDataProcessingInfo);

        currentLoaderState = LoaderState.INITIALISED;
        return didPutSucceed;
    }

    @Override
    public void completeProcessing() {
        startTime = Instant.now();

        checkCurrentState(LoaderState.INITIALISED);

        // Set the processing info record to COMPLETE and update the last update time
        updateProcessingState(
                refStreamDefinition, ProcessingState.COMPLETE, true);

        final String mapNames = loadedMapDefinitions
                .stream()
                .map(MapDefinition::getMapName)
                .collect(Collectors.joining(","));

        final Duration loadDuration = Duration.between(startTime, Instant.now());
        LOGGER.info("Successfully Loaded {} entries out of {} attempts with map names [{}] in {} for {}",
                successfulPutsCounter, putsCounter, mapNames, loadDuration, refStreamDefinition);
        currentLoaderState = LoaderState.COMPLETED;

    }

    @Override
    public void setCommitInterval(final int putsBeforeCommit) {
        // do noting, no concept of commit interval
    }

    @Override
    public boolean put(final MapDefinition mapDefinition,
                       final String key,
                       final RefDataValue refDataValue) {

        final KeyValueStoreKey mapKey = new KeyValueStoreKey(mapDefinition, key);

        boolean wasValuePut;
        if (overwriteExisting) {
            keyValueMap.put(mapKey, refDataValue);
            wasValuePut = true;
        } else {
            RefDataValue prevValue = keyValueMap.putIfAbsent(mapKey, refDataValue);
            wasValuePut = prevValue == null;
        }
        return wasValuePut;
    }

    @Override
    public boolean put(final MapDefinition mapDefinition,
                       final Range<Long> keyRange,
                       final RefDataValue refDataValue) {
        // ensure we have a sub map for our mapDef
        NavigableMap<Range<Long>, RefDataValue> subMap = rangeValueNestedMap.putIfAbsent(
                mapDefinition,
                new TreeMap<>(RANGE_COMPARATOR));

        boolean wasValuePut;
        if (overwriteExisting) {
            subMap.put(keyRange, refDataValue);
            wasValuePut = true;
        } else {
            RefDataValue prevValue = subMap.putIfAbsent(keyRange, refDataValue);
            wasValuePut = prevValue == null;
        }
        return wasValuePut;
    }

    @Override
    public void close() throws Exception {
        // nothing to close
    }

    private void updateProcessingState(final RefStreamDefinition refStreamDefinition,
                                      final ProcessingState newProcessingState,
                                      final boolean touchLastAccessedTime) {

        // TODO

    }

    private boolean putProcessingInfo(final RefStreamDefinition refStreamDefinition,
                                      final RefDataProcessingInfo refDataProcessingInfo) {

        // TODO
        return false;
    }

    private void checkCurrentState(final LoaderState... validStates) {
        boolean isCurrentStateValid = false;
        for (LoaderState loaderState : validStates) {
            if (currentLoaderState.equals(loaderState)) {
                isCurrentStateValid = true;
                break;
            }
        }
        if (!isCurrentStateValid) {
            throw new IllegalStateException(LambdaLogger.buildMessage("Current loader state: {}, valid states: {}",
                    currentLoaderState, Arrays.toString(validStates)));
        }
    }

}
