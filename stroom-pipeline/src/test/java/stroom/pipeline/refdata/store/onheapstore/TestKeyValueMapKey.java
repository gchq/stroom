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

package stroom.pipeline.refdata.store.onheapstore;


import stroom.pipeline.refdata.store.MapDefinition;
import stroom.pipeline.refdata.store.RefStreamDefinition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeyValueMapKey {

    @Test
    void testEqualsAndHashcode() {
        final KeyValueMapKey key1 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        final KeyValueMapKey key2 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    void testEqualsAndHashcode_differentKey() {
        final KeyValueMapKey key1 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKeyxxxxxx");

        final KeyValueMapKey key2 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.hashCode()).isNotEqualTo(key2.hashCode());
    }
}
