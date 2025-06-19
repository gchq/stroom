package stroom.state.impl.pipeline;

import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.MultiRefDataValueProxy;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.UnknownRefDataValue;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.state.impl.dao.TemporalState;
import stroom.util.logging.LogUtil;

import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class StateValueProxy implements RefDataValueProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateValueProxy.class);

    private final TemporalState state;
    private final MapDefinition mapDefinition;

    // This will be set with mapDefinition if we have a successful lookup with it, else stays null
    private MapDefinition successfulMapDefinition = null;

    public StateValueProxy(final TemporalState state,
                           final MapDefinition mapDefinition) {
        this.state = state;
        this.mapDefinition = mapDefinition;
    }

    @Override
    public String getKey() {
        return state.key();
    }

    @Override
    public String getMapName() {
        return mapDefinition.getMapName();
    }

    @Override
    public List<MapDefinition> getMapDefinitions() {
        return Collections.singletonList(mapDefinition);
    }

    @Override
    public Optional<MapDefinition> getSuccessfulMapDefinition() {
        return Optional.ofNullable(successfulMapDefinition);
    }

    @Override
    public Optional<RefDataValue> supplyValue() {
        switch (state.typeId()) {
            case StringValue.TYPE_ID -> {
                return Optional.of(new StringValue(state.getValueAsString()));
            }
            case FastInfosetValue.TYPE_ID -> {
                return Optional.of(new FastInfosetValue(state.value().duplicate()));
            }
            case NullValue.TYPE_ID -> {
                return Optional.of(NullValue.getInstance());
            }
            default -> {
                return Optional.of(new UnknownRefDataValue(state.value().duplicate()));
            }
        }
    }

    @Override
    public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
        typedByteBufferConsumer.accept(new TypedByteBuffer(state.typeId(), state.value().duplicate()));
        return true;
    }

    @Override
    public boolean consumeValue(final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
        LOGGER.trace("consume(...)");

        // get the consumer appropriate to the refDataStore that this proxy came from. The refDataStore knows
        // what its values look like (e.g. heap objects or bytebuffers)
        final RefDataValueProxyConsumer refDataValueProxyConsumer = refDataValueProxyConsumerFactory
                .getConsumer(StorageType.OFF_HEAP);

        try {
            final boolean wasFound = refDataValueProxyConsumer.consume(this);
            if (wasFound) {
                successfulMapDefinition = mapDefinition;
            }
            return wasFound;
        } catch (final XPathException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error consuming reference data value for key [{}], {}: {}",
                    state.key(), mapDefinition, e.getMessage()), e);
        }
    }

    @Override
    public RefDataValueProxy merge(final RefDataValueProxy additionalProxy) {
        return MultiRefDataValueProxy.merge(this, additionalProxy);
    }
}
