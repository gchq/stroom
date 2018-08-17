package stroom.refdata.store.onheapstore;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Range;
import stroom.properties.StroomPropertyService;
import stroom.refdata.store.RefDataStore;
import stroom.refdata.store.AbstractRefDataStore;
import stroom.refdata.store.MapDefinition;
import stroom.refdata.store.ProcessingState;
import stroom.refdata.store.RefDataLoader;
import stroom.refdata.store.RefDataProcessingInfo;
import stroom.refdata.store.RefDataValue;
import stroom.refdata.store.RefStreamDefinition;
import stroom.refdata.store.offheapstore.TypedByteBuffer;
import stroom.refdata.store.offheapstore.serdes.GenericRefDataValueSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Consumer;

/**
 * A heap based implementation of the {@link RefDataStore}
 * That is intended for use with reference data with a very short life, i.e. context
 * data that is transient and only required for the life of a single pipeline processor
 * task. As such is is not thread safe and only intended to be used to by a single thread.
 * It also does not support purge operations as it is expected that the store will be created,
 * populated, used then destroyed when no longer needed
 */
@NotThreadSafe
public class RefDataOnHeapStore extends AbstractRefDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefDataOnHeapStore.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(RefDataOnHeapStore.class);

    private static final int BUFFER_ENLARGEMENT_RETRY_COUNT = 20;

    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Set<MapDefinition> mapDefinitions;
    private final Map<KeyValueMapKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;
    private final GenericRefDataValueSerde genericRefDataValueSerde;

    private ByteBuffer valueBuffer = null;

    public RefDataOnHeapStore(final GenericRefDataValueSerde genericRefDataValueSerde,
                              final StroomPropertyService stroomPropertyService) {

        this.genericRefDataValueSerde = genericRefDataValueSerde;

        this.processingInfoMap = new HashMap<>();
        this.keyValueMap = new HashMap<>();
        this.rangeValueNestedMap = new HashMap<>();
        this.mapDefinitions = new HashSet<>();
    }

    @Override
    public StorageType getStorageType() {
        return StorageType.ON_HEAP;
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
        final boolean isDataLoaded = Optional.ofNullable(processingInfoMap.get(refStreamDefinition))
                .filter(processingInfo ->
                        processingInfo.getProcessingState().equals(ProcessingState.COMPLETE))
                .isPresent();
        LOGGER.trace("isDataLoaded({}) returning {}", refStreamDefinition, isDataLoaded);
        return isDataLoaded;
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
        final Optional<RefDataValue> result2 = result;
        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("getValue({}, {}) returning {}",
                mapDefinition, key, result2));
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
    public boolean consumeValueBytes(final MapDefinition mapDefinition,
                                     final String key,
                                     final Consumer<TypedByteBuffer> valueBytesConsumer) {

        throw new UnsupportedOperationException("This implementation doesn't deal in bytes");

        // TODO we are dealing with on-heap data so we shouldn't be involved with bytes/ByteBuffers
        // for the moment this will do as the fast infoset values will be bytes anyway so it is just
        // some small string serialisation.

//        boolean wasConsumed = getValue(mapDefinition, key)
//                .filter(refDataValue -> {
//                    // the ByteBuffer in here is shared and owned by 'this' so it should not
//                    // be used outside this consumer
//                    TypedByteBuffer typedByteBuffer = buildTypedByteBuffer(refDataValue);
//
//                    valueBytesConsumer.accept(typedByteBuffer);
//
//                    // abuse of the filter() method, as not really filtering, so always return true
//                    return true;
//                })
//                .isPresent();
//        LOGGER.trace("consumeValueBytes({}, {}, ...) returning {}", mapDefinition, key, wasConsumed);
//        return wasConsumed;
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
        throw new UnsupportedOperationException("Purge functionality is not supported for the on-heap store");
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
                processingInfoMap,
                mapDefinitions,
                keyValueMap,
                rangeValueNestedMap,
                this);
    }

    private TypedByteBuffer buildTypedByteBuffer(final RefDataValue refDataValue) {
        int tryCount = 0;
        int minCapacity = genericRefDataValueSerde.getBufferCapacity();
        boolean success = false;

        ByteBuffer valueBuffer = null;

        // we have no idea how big the serialised form is
        while (tryCount++ < BUFFER_ENLARGEMENT_RETRY_COUNT) {
            valueBuffer = getValueBuffer(minCapacity);
            try {
                genericRefDataValueSerde.serialize(valueBuffer, refDataValue);
                success = true;
                break;
            } catch (BufferOverflowException e) {
                //double the capacity and try again
                minCapacity = minCapacity * 2;
            }
        }

        if (!success) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Failed to serialise {} after {} attempts and a capacity of {}",
                    refDataValue, tryCount, minCapacity));
        }
        return new TypedByteBuffer(refDataValue.getTypeId(), valueBuffer);
    }

    private ByteBuffer getValueBuffer(final int minCapacity) {
        if (valueBuffer != null && minCapacity <= valueBuffer.capacity()) {
            valueBuffer.clear();
            return valueBuffer;
        } else {
            valueBuffer = ByteBuffer.allocate(minCapacity);
        }
        return valueBuffer;
    }
}
