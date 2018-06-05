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

package stroom.refdata.offheapstore;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class RefDataValueProxy {

    // held for the purpose of testing equality of a ValueProxy and
    // for calling methods on the instance
    private final RefDataStore refDataStore;
    private final ValueStoreKey valueStoreKey;

    RefDataValueProxy(final RefDataStore refDataStore,
                      final ValueStoreKey valueStoreKey) {

        Objects.requireNonNull(refDataStore);
        this.refDataStore = Objects.requireNonNull(refDataStore);
        this.valueStoreKey = Objects.requireNonNull(valueStoreKey);
    }

    /**
     * Materialise the value that this is proxying. The consumeValue() method should be preferred
     * as this method will involve the added cost of copying the contents of the value.
     *
     * @return An optional value, as the value may have been evicted from the pool. Callers
     * should expect to handle this possibility.
     */
    public Optional<RefDataValue> supplyValue() {
        return refDataStore.getValue(valueStoreKey);
    }

    public <T> Optional<T> mapValue(final Function<RefDataValue, T> valueMapper) {
        return refDataStore.map(valueStoreKey, valueMapper);
    }

    public <T> Optional<T> mapBytes(final Function<ByteBuffer, T> valueMapper) {
        return refDataStore.mapBytes(valueStoreKey, valueMapper);
    }

    public void consumeValue(final Consumer<RefDataValue> valueConsumer) {
        refDataStore.consumeValue(valueStoreKey, valueConsumer);
    }

    public void consumeBytes(final Consumer<ByteBuffer> bytesConsumer) {
        refDataStore.consumeBytes(valueStoreKey, bytesConsumer);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RefDataValueProxy that = (RefDataValueProxy) o;
        return Objects.equals(refDataStore, that.refDataStore) &&
                Objects.equals(valueStoreKey, that.valueStoreKey);
    }

    @Override
    public int hashCode() {

        return Objects.hash(refDataStore, valueStoreKey);
    }

    @Override
    public String toString() {
        return "RefDataValueProxy{" +
                "refDataStore=" + refDataStore +
                ", valueStoreKey=" + valueStoreKey +
                '}';
    }
}
