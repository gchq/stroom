package stroom.refdata.onheapstore;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.util.concurrent.Striped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.refdata.offheapstore.AbstractRefDataStore;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.ProcessingState;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.TypedByteBuffer;
import stroom.util.logging.LambdaLogger;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

class RefDataOnHeapStore extends AbstractRefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOnHeapStore.class);

    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Map<KeyValueMapKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;

    // For synchronising access to the data belonging to a MapDefinition
    private final Striped<Lock> refStreamDefStripedReentrantLock;

    RefDataOnHeapStore() {
        processingInfoMap = new ConcurrentHashMap<>();
        keyValueMap = new ConcurrentHashMap<>();
        rangeValueNestedMap = new ConcurrentHashMap<>();
        refStreamDefStripedReentrantLock = Striped.lazyWeakLock(100);
    }


    @Override
    public Optional<RefDataProcessingInfo> getAndTouchProcessingInfo(final RefStreamDefinition refStreamDefinition) {

        return Optional.ofNullable(processingInfoMap.computeIfPresent(
                refStreamDefinition,
                (refStreamDef, refDataProcessingInfo) ->
                        refDataProcessingInfo.updateLastAccessedTime()));
    }

    @Override
    public boolean isDataLoaded(final RefStreamDefinition refStreamDefinition) {
        RefDataProcessingInfo refDataProcessingInfo = processingInfoMap.get(refStreamDefinition);
        if (refDataProcessingInfo == null ||
                !refDataProcessingInfo.getProcessingState().equals(ProcessingState.COMPLETE)) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        return keyValueMap.containsKey(mapDefinition) || rangeValueNestedMap.containsKey(mapDefinition);
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition, final String key) {
        // try the KV store first

        Optional<RefDataValue> result;
        KeyValueMapKey keyValueMapKey = new KeyValueMapKey(mapDefinition, key);
        RefDataValue refDataValue = keyValueMap.get(keyValueMapKey);
        if (refDataValue != null) {
            result = Optional.of(refDataValue);
        } else {
            // not found in kv map so look in the range map
            try {
                // speculative lookup in the range store. At this point we don't know if we have
                // any ranges for this mapdef or not, but either way we need a call to LMDB so
                // just do the range lookup
                final long keyLong = Long.parseLong(key);

                // look up our long key in the range store to see if it is part of a range
                NavigableMap<Range<Long>, RefDataValue> rangeSubMap = rangeValueNestedMap.get(mapDefinition);
                result = getValueByRange(rangeSubMap, keyLong);

            } catch (NumberFormatException e) {
                // key could not be converted to a long, either this mapdef has no ranges or
                // an invalid key was used. See if we have any ranges at all for this mapdef
                // to determine whether to error or not.
                boolean doesStoreContainRanges = rangeValueNestedMap.containsKey(mapDefinition);
                if (doesStoreContainRanges) {
                    // we have ranges for this map def so we would expect to be able to convert the key
                    throw new RuntimeException(LambdaLogger.buildMessage(
                            "Key {} cannot be used with the range store as it cannot be converted to a long", key), e);
                }
                // no ranges for this map def so the fact that we could not convert the key to a long
                // is not a problem. Do nothing.
                result = Optional.empty();
            }
        }
        return result;
    }

    private Optional<RefDataValue> getValueByRange(NavigableMap<Range<Long>, RefDataValue> rangeSubMap, final long key) {

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

        for (Map.Entry<Range<Long>, RefDataValue> entry : tailMap.entrySet()) {
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
    public boolean consumeValueBytes(final MapDefinition mapDefinition, final String key, final Consumer<TypedByteBuffer> valueBytesConsumer) {
        return false;
    }

    @Override
    public long getKeyValueEntryCount() {
        return keyValueMap.size();
    }

    @Override
    public long getKeyRangeValueEntryCount() {
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

        throw new UnsupportedOperationException("Not implemented");

    }

    public void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition, final Runnable work) {

    }

    @Override
    public void logAllContents() {
        logAllContents(LOGGER::debug);
    }

    @Override
    public void logAllContents(final Consumer<String> logEntryConsumer) {

        logEntryConsumer.accept(LambdaLogger.buildMessage("Dumping contents of processingInfoMap"));

        processingInfoMap.forEach((k, v) ->
                logEntryConsumer.accept(LambdaLogger.buildMessage("{} => {}", k, v)));

        logEntryConsumer.accept(LambdaLogger.buildMessage("Dumping contents of keyValueMap"));

        keyValueMap.forEach((k, v) ->
                logEntryConsumer.accept(LambdaLogger.buildMessage("{} => {}", k, v)));

        logEntryConsumer.accept(LambdaLogger.buildMessage("Dumping contents of rangeValueNestedMap"));

        rangeValueNestedMap.forEach((k, v) -> {
            logEntryConsumer.accept(LambdaLogger.buildMessage("{} =>", k));
            v.forEach((subKey, subValue) ->
                    logEntryConsumer.accept(LambdaLogger.buildMessage("   {} => {}", subKey, subValue)));
        });
    }

    @Override
    public HealthCheck.Result getHealth() {
        return null;
    }

    /**
     * Intended only for testing use.
     */
    void setLastAccessedTime(final RefStreamDefinition refStreamDefinition, long timeMs) {

        processingInfoMap.compute(refStreamDefinition, (refStreamDef, refDataProcessingInfo) -> {
            if (refDataProcessingInfo != null) {
                return new RefDataProcessingInfo(
                        refDataProcessingInfo.getCreateTimeEpochMs(),
                        timeMs,
                        refDataProcessingInfo.getEffectiveTimeEpochMs(),
                        refDataProcessingInfo.getProcessingState());
            } else {
                throw new RuntimeException(LambdaLogger.buildMessage(
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
    protected RefDataLoader loader(final RefStreamDefinition refStreamDefinition, final long effectiveTimeMs) {

        return new OnHeapRefDataLoader(
                refStreamDefinition,
                effectiveTimeMs,
                refStreamDefStripedReentrantLock,
                processingInfoMap,
                keyValueMap,
                rangeValueNestedMap);
    }
}
