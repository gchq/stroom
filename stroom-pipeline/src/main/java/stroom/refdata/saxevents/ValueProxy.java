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

package stroom.refdata.saxevents;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class ValueProxy<V extends AbstractPoolValue> {

    // held for the purpose of testing equality of a ValueProxy and
    // for calling methods on the instance
    private final OffHeapInternPool<V> pool;
    private final Key key;
    // held so we know what type of value we are proxying for
    private final Class<?> valueClazz;

    //used to keep a track of how many object hold a reference to this valueProxy
    private final AtomicInteger referenceCount = new AtomicInteger(0);

    ValueProxy(final OffHeapInternPool<V> pool, final Key key, final Class<?> valueClazz) {
        Objects.requireNonNull(pool);
        this.pool = Objects.requireNonNull(pool);
        this.key = Objects.requireNonNull(key);
        this.valueClazz = valueClazz;
    }

    /**
     * Materialise the value that this is proxying. The useValue() method should be preferred
     * as this method will involve the added cost of copying the contents of the value.
     * @return An optional value, as the value may have been evicted from the pool. Callers
     * should expect to handle this possibility.
     */
    public Optional<V> supplyValue() {
        return pool.get(this);
    }

    <T> Optional<T> mapValue(final Function<ByteBuffer, T> valueMapper) {
        return pool.mapValue(this, valueMapper);
    }

    void consumeValue(final Consumer<ByteBuffer> valueConsumer) {
        pool.consumeValue(this, valueConsumer);
    }

    public void inrementReferenceCount(){
        referenceCount.incrementAndGet();
    }

    public void decrementReferenceCount(){
        referenceCount.decrementAndGet();
    }

    int getReferenceCount() {
        return referenceCount.get();
    }

    Key getKey() {
        return key;
    }

    Class<?> getValueClazz() {
        return valueClazz;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ValueProxy<?> that = (ValueProxy<?>) o;
        return Objects.equals(pool, that.pool) &&
                Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pool, key);
    }

    @Override
    public String toString() {
        return "ValueProxy{" +
                "pool=" + pool +
                ", key=" + key +
                '}';
    }
}
