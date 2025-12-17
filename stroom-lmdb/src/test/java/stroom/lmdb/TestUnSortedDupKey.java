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

import stroom.lmdb.UnSortedDupKey.UnsortedDupKeyFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnSortedDupKey {

    @Test
    void getKey() {
        final UnsortedDupKeyFactory<String> factory = UnSortedDupKey.createFactory(String.class, 4);
        final UnSortedDupKey<String> key1 = factory.createUnsortedKey("keyA");
        final UnSortedDupKey<String> key2 = factory.createUnsortedKey("keyA");
        // Same "keyA" but the diff ids should make them unique
        assertThat(key1)
                .isNotEqualTo(key2);
        assertThat(key1.getKey())
                .isEqualTo(key2.getKey());
        assertThat(key1.hashCode())
                .isNotEqualTo(key2.hashCode());
        assertThat(key1.getId())
                .isEqualTo(0);
        assertThat(key2.getId())
                .isEqualTo(1);

        final UnSortedDupKey<String> key3 = factory.createUnsortedKey("keyB");

        // Different "key..."
        assertThat(key1)
                .isNotEqualTo(key3);
        assertThat(key1.getKey())
                .isNotEqualTo(key3.getKey());
        assertThat(key3.getId())
                .isEqualTo(2);
    }
}
