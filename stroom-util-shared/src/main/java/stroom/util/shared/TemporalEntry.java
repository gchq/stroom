/*
 * Copyright 2026 Crown Copyright
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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class TemporalEntry {

    @JsonProperty
    private final String map;
    @JsonProperty
    private final String key;
    @JsonProperty
    private final Long effectiveTimeMs;
    @JsonProperty
    private final String value;

    @JsonCreator
    public TemporalEntry(
            @JsonProperty("map") final String map,
            @JsonProperty("key") final String key,
            @JsonProperty("effectiveTimeMs") final Long effectiveTimeMs,
            @JsonProperty("value") final String value) {
        this.map = map;
        this.key = key;
        this.effectiveTimeMs = effectiveTimeMs;
        this.value = value;
    }

    public String getMap() {
        return map;
    }

    public String getKey() {
        return key;
    }

    public Long getEffectiveTimeMs() {
        return effectiveTimeMs;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TemporalEntry that = (TemporalEntry) o;
        return Objects.equals(map, that.map) &&
                Objects.equals(key, that.key) &&
                Objects.equals(effectiveTimeMs, that.effectiveTimeMs) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(map, key, effectiveTimeMs, value);
    }

    @Override
    public String toString() {
        return "TemporalEntry{" +
                "map='" + map + '\'' +
                ", key='" + key + '\'' +
                ", effectiveTimeMs=" + effectiveTimeMs +
                ", value='" + value + '\'' +
                '}';
    }
}
