/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.store;

import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.refdata.store.offheapstore.RefDataValueProxyConsumer;
import stroom.refdata.store.offheapstore.TypedByteBuffer;
import stroom.util.logging.LambdaLogger;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class SingleRefDataValueProxy implements RefDataValueProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(SingleRefDataValueProxy.class);

    // held for the purpose of testing equality of a ValueProxy and
    // for calling methods on the instance
    private final RefDataStore refDataStore;
    private final MapDefinition mapDefinition;
    private final String key;

    public SingleRefDataValueProxy(final RefDataStore refDataStore,
                                   final MapDefinition mapDefinition,
                                   final String key) {

        this.refDataStore = Objects.requireNonNull(refDataStore);
        this.mapDefinition = Objects.requireNonNull(mapDefinition);
        this.key = Objects.requireNonNull(key);
    }


    /**
     * Materialise the value that this is proxying. The consumeValue() method should be preferred
     * as this method will involve the added cost of copying the contents of the value.
     *
     * @return An optional value, as the value may have been evicted from the pool. Callers
     * should expect to handle this possibility.
     */
    @Override
    public Optional<RefDataValue> supplyValue() {
        LOGGER.trace("supplyValue()");
        return refDataStore.getValue(mapDefinition, key);
    }

    @Override
    public RefDataStore.StorageType getStorageType() {
        return refDataStore.getStorageType();
    }

    /**
     * If a reference data entry exists for this {@link SingleRefDataValueProxy} pass its value to the consumer
     * as a {@link TypedByteBuffer}.
     * @param typedByteBufferConsumer
     * @return True if the entry is found and the consumer is called.
     */
    @Override
    public boolean consumeBytes(final Consumer<TypedByteBuffer> typedByteBufferConsumer) {
        LOGGER.trace("consumeBytes(...)");
        return refDataStore.consumeValueBytes(mapDefinition, key, typedByteBufferConsumer);
    }

    @Override
    public boolean consumeValue(final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory) {
        LOGGER.trace("consume(...)");

        // get the consumer appropriate to the refDataStore that this proxy came from. The refDataStore knows
        // what its values look like (e.g. heap objects or bytebuffers)
        final RefDataValueProxyConsumer refDataValueProxyConsumer = refDataValueProxyConsumerFactory
                .getConsumer(refDataStore.getStorageType());

        try {
            return refDataValueProxyConsumer.consume(this);
        } catch (XPathException e) {
            throw new RuntimeException(LambdaLogger.buildMessage(
                    "Error handing rerence data value: {}", e.getMessage()), e);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SingleRefDataValueProxy that = (SingleRefDataValueProxy) o;
        return Objects.equals(refDataStore, that.refDataStore) &&
                Objects.equals(mapDefinition, that.mapDefinition) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refDataStore, mapDefinition, key);
    }

    @Override
    public String toString() {
        return "RefDataValueProxy{" +
                "mapDefinition=" + mapDefinition +
                ", key='" + key + '\'' +
                '}';
    }
}
