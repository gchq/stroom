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

package stroom.lmdb;

import stroom.lmdb.serde.UnsignedLong;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A generic wrapper round a key to make duplicate keys/entries unique. This is to enable storing of
 * entries in strict insert order. Any duplicate keys or whole entries will be stored and not overwritten.
 * A unique ID value (of configurable byte length) is appended to the end of the key to provide the uniqueness.
 * Care needs to be taken to ensure you will not blow the limit of the ID value.
 * <p>
 * Key format:
 * <pre>{@code
 * <delegate key><id>
 * }</pre>
 * </p>
 *
 * @param <K>
 */
public class UnSortedDupKey<K> {

    public static final int DEFAULT_ID_BYTE_LENGTH = 4;

    private final K key;
    private final UnsignedLong id;

    public UnSortedDupKey(final K key, final UnsignedLong id) {
        this.key = Objects.requireNonNull(key);
        this.id = Objects.requireNonNull(id);
    }

    public K getKey() {
        return key;
    }

    public long getId() {
        return id.getValue();
    }

    public static <K> UnsortedDupKeyFactory<K> createFactory(final TypeLiteral<K> keyType, final int idLength) {
        return new UnsortedDupKeyFactory<>(keyType, idLength);
    }

    public static <K> UnsortedDupKeyFactory<K> createFactory(final TypeLiteral<K> keyType) {
        return createFactory(keyType, DEFAULT_ID_BYTE_LENGTH);
    }

    public static <K> UnsortedDupKeyFactory<K> createFactory(final Class<K> keyClass, final int idLength) {
        final Key<K> key = Key.get(keyClass);
        return new UnsortedDupKeyFactory<>(key.getTypeLiteral(), idLength);
    }

    public static <K> UnsortedDupKeyFactory<K> createFactory(final Class<K> keyClass) {
        return createFactory(keyClass, DEFAULT_ID_BYTE_LENGTH);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnSortedDupKey<?> that = (UnSortedDupKey<?>) o;
        return key.equals(that.key) && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, id);
    }

    @Override
    public String toString() {
        return key + " (id: " + id.getValue() + ")";
    }


    // --------------------------------------------------------------------------------


    public static class UnsortedDupKeyFactory<K> {

        private final AtomicReference<UnsignedLong> idRef;
        @SuppressWarnings({"FieldCanBeLocal", "unused"}) // Used for generics typing
        private final TypeLiteral<K> keyType;
        private final int idByteLength;

        private UnsortedDupKeyFactory(final TypeLiteral<K> keyType, final int idByteLength) {
            this.idRef = new AtomicReference<>(null);
            this.keyType = keyType;
            this.idByteLength = idByteLength;
        }

        /**
         * Each call to this will return a {@link UnSortedDupKey} with a unique id part
         * ensuring that duplicate keys are unique.
         */
        public UnSortedDupKey<K> createUnsortedKey(final K key) {
            final UnsignedLong newId = idRef.updateAndGet(unsignedLong ->
                    unsignedLong == null
                            ? new UnsignedLong(0, idByteLength)
                            : unsignedLong.increment());
            return new UnSortedDupKey<>(key, newId);
        }
    }
}
