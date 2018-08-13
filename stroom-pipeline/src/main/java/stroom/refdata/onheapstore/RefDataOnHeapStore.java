package stroom.refdata.onheapstore;

import com.codahale.metrics.health.HealthCheck;
import stroom.entity.shared.Range;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.RefDataLoader;
import stroom.refdata.offheapstore.RefDataProcessingInfo;
import stroom.refdata.offheapstore.RefDataStore;
import stroom.refdata.offheapstore.RefDataValue;
import stroom.refdata.offheapstore.RefDataValueProxy;
import stroom.refdata.offheapstore.RefStreamDefinition;
import stroom.refdata.offheapstore.TypedByteBuffer;

import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

class RefDataOnHeapStore implements RefDataStore {

    private final Map<RefStreamDefinition, RefDataProcessingInfo> processingInfoMap;
    private final Map<KeyValueStoreKey, RefDataValue> keyValueMap;
    private final Map<MapDefinition, NavigableMap<Range<Long>, RefDataValue>> rangeValueNestedMap;

    RefDataOnHeapStore() {
        processingInfoMap = new ConcurrentHashMap<>();
        keyValueMap = new ConcurrentHashMap<>();
        rangeValueNestedMap = new ConcurrentHashMap<>();
    }


    @Override
    public Optional<RefDataProcessingInfo> getAndTouchProcessingInfo(final RefStreamDefinition refStreamDefinition) {
        return Optional.empty();
    }

    @Override
    public boolean isDataLoaded(final RefStreamDefinition refStreamDefinition) {
        return false;
    }

    @Override
    public boolean exists(final MapDefinition mapDefinition) {
        return false;
    }

    @Override
    public Optional<RefDataValue> getValue(final MapDefinition mapDefinition, final String key) {
        return Optional.empty();
    }

    @Override
    public RefDataValueProxy getValueProxy(final MapDefinition mapDefinition, final String key) {
        return null;
    }

    @Override
    public boolean consumeValueBytes(final MapDefinition mapDefinition, final String key, final Consumer<TypedByteBuffer> valueBytesConsumer) {
        return false;
    }

    @Override
    public boolean doWithLoaderUnlessComplete(final RefStreamDefinition refStreamDefinition, final long effectiveTimeMs, final Consumer<RefDataLoader> work) {
        return false;
    }

    @Override
    public long getKeyValueEntryCount() {
        return 0;
    }

    @Override
    public long getKeyRangeValueEntryCount() {
        return 0;
    }

    @Override
    public long getProcessingInfoEntryCount() {
        return 0;
    }

    @Override
    public void purgeOldData() {

    }

    @Override
    public void doWithRefStreamDefinitionLock(final RefStreamDefinition refStreamDefinition, final Runnable work) {

    }

    @Override
    public HealthCheck.Result getHealth() {
        return null;
    }

}
