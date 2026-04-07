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

package stroom.planb.impl.pipeline;

import stroom.pipeline.refdata.store.FastInfosetValue;
import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.MultiRefDataValueProxy;
import stroom.pipeline.refdata.store.NullValue;
import stroom.pipeline.refdata.store.RefDataStore.StorageType;
import stroom.pipeline.refdata.store.RefDataValue;
import stroom.pipeline.refdata.store.RefDataValueProxy;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.StringValue;
import stroom.pipeline.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.pipeline.refdata.store.offheapstore.TypedByteBuffer;
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValXml;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
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
        return state.key().toString();
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
        return switch (state.val().type()) {
            case XML -> Optional.of(new FastInfosetValue(ByteBuffer.wrap(((ValXml) state.val()).getBytes())));
            case NULL -> Optional.of(NullValue.getInstance());
            default -> Optional.of(new StringValue(NullSafe.getOrElse(state, TemporalState::val, Val::toString, "")));
        };
    }

    @Override
    public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
        switch (state.val().type()) {
            case XML -> typedByteBufferConsumer.accept(new TypedByteBuffer(
                    FastInfosetValue.TYPE_ID,
                    ByteBuffer.wrap(((ValXml) state.val()).getBytes())));
            case NULL -> {
            }
            default -> typedByteBufferConsumer.accept(new TypedByteBuffer(
                    StringValue.TYPE_ID,
                    ByteBuffer.wrap(ValSerdeUtil.getBytes(state.val()))));
        }
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
