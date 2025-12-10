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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class CacheIdentity implements Comparable<CacheIdentity> {

    // Note: it is possible to have two caches with the same config, e.g. StatisticsDataSourceCacheImpl
    @JsonProperty
    private final String cacheName;
    @JsonProperty
    private final PropertyPath basePropertyPath;

    @JsonCreator
    public CacheIdentity(@JsonProperty("cacheName") final String cacheName,
                         @JsonProperty("basePropertyPath") final PropertyPath basePropertyPath) {
        this.cacheName = cacheName;
        this.basePropertyPath = basePropertyPath;
    }

    public String getCacheName() {
        return cacheName;
    }

    public PropertyPath getBasePropertyPath() {
        return basePropertyPath;
    }

    @Override
    public String toString() {
        return "CacheIdentity{" +
               "cacheName='" + cacheName + '\'' +
               ", basePropertyPath=" + basePropertyPath +
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
        final CacheIdentity that = (CacheIdentity) o;
        return Objects.equals(cacheName, that.cacheName) && Objects.equals(basePropertyPath,
                that.basePropertyPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cacheName, basePropertyPath);
    }

    @Override
    public int compareTo(final CacheIdentity other) {
        final int compareResult = this.cacheName.compareTo(other.cacheName);
        if (compareResult != 0) {
            return compareResult;
        } else {
            return this.basePropertyPath.compareTo(other.basePropertyPath);
        }
    }
}
