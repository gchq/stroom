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

package stroom.util.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * Class that represents a key to a resource. This key has a name and a string
 * key.
 */
@JsonInclude(Include.NON_NULL)
public class ResourceKey {

    public static final String KEY = "key";
    public static final String NAME = "name";

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String name;

    @JsonCreator
    public ResourceKey(@JsonProperty("key") final String key,
                       @JsonProperty("name") final String name) {
        this.key = key;
        this.name = name;
    }

    /**
     * Creates a {@link ResourceKey} containing on the key UUID.
     * Only for use as a map key as equals/hashcode only use key.
     */
    public static ResourceKey createSearchKey(final String key) {
        // name is not used in the equals/hash
        return new ResourceKey(key, null);
    }

    public ResourceKey(final Map<String, String> map) {
        this.name = map.get(NAME);
        this.key = map.get(KEY);
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ResourceKey that = (ResourceKey) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }

    @Override
    public String toString() {
        return key;
    }
}
