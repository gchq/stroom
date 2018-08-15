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

package stroom.refdata.onheapstore;

import org.junit.Test;
import stroom.refdata.offheapstore.MapDefinition;
import stroom.refdata.offheapstore.RefStreamDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class TestKeyValueMapKey {

    @Test
    public void testEqualsAndHashcode() {
        KeyValueMapKey key1 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        KeyValueMapKey key2 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
    }

    @Test
    public void testEqualsAndHashcode_differentKey() {
        KeyValueMapKey key1 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKeyxxxxxx");

        KeyValueMapKey key2 = new KeyValueMapKey(
                new MapDefinition(
                        new RefStreamDefinition("uid1", "ver1", 123L),
                        "myMap"),
                "myKey");

        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1.hashCode()).isNotEqualTo(key2.hashCode());
    }
}