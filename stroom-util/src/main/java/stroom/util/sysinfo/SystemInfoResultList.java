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

package stroom.util.sysinfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

// Wraps a list to avoid json ser/deser issues with generic types
@JsonInclude(Include.NON_DEFAULT)
@JsonPropertyOrder(alphabetic = true)
public class SystemInfoResultList {

    @JsonProperty("results")
    private final List<SystemInfoResult> results;

    @JsonCreator
    public SystemInfoResultList(@JsonProperty("results") final List<SystemInfoResult> results) {
        this.results = results;
    }

    public static SystemInfoResultList of(final List<SystemInfoResult> results) {
        return new SystemInfoResultList(results);
    }

    public static SystemInfoResultList of(final SystemInfoResult... results) {
        return new SystemInfoResultList(List.of(results));
    }

    public List<SystemInfoResult> getResults() {
        return results;
    }

    @Override
    public String toString() {
        return "SystemInfoResultList{" +
                "results=" + results +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SystemInfoResultList that = (SystemInfoResultList) o;
        return Objects.equals(results, that.results);
    }

    @Override
    public int hashCode() {
        return Objects.hash(results);
    }
}
