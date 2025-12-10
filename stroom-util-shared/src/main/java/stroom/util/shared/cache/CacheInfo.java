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

package stroom.util.shared.cache;

import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CacheInfo {

    @JsonProperty
    private final String name;
    @JsonProperty
    private final PropertyPath basePropertyPath;
    @JsonProperty
    private final Map<String, String> map;
    @JsonProperty
    private String nodeName;

    @JsonCreator
    public CacheInfo(@JsonProperty("name") final String name,
                     @JsonProperty("basePropertyPath") final PropertyPath basePropertyPath,
                     @JsonProperty("map") final Map<String, String> map,
                     @JsonProperty("nodeName") final String nodeName) {
        this.name = name;
        this.basePropertyPath = basePropertyPath;
        this.map = map;
        this.nodeName = nodeName;
    }

    public CacheInfo(final String name,
                     final PropertyPath basePropertyPath,
                     final Map<String, String> map) {
        this.name = name;
        this.basePropertyPath = basePropertyPath;
        this.map = map;
        this.nodeName = null;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getMap() {
        return map;
    }

    public String getNodeName() {
        return nodeName;
    }

    /**
     * Copies the contents of this object to a new object with the addition of the
     * supplied nodeName
     */
    public CacheInfo withNodeName(final String nodeName) {
        return new CacheInfo(name, basePropertyPath, new HashMap<>(map), nodeName);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CacheInfo cacheInfo = (CacheInfo) o;
        return Objects.equals(name, cacheInfo.name) && Objects.equals(basePropertyPath,
                cacheInfo.basePropertyPath) && Objects.equals(map, cacheInfo.map) && Objects.equals(
                nodeName,
                cacheInfo.nodeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, basePropertyPath, map, nodeName);
    }
}
