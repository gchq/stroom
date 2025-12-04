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

package stroom.receive.common;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * A collection of {@link HashedDataFeedKey}
 */
@JsonPropertyOrder(alphabetic = true)
public class HashedDataFeedKeys {

    private final List<HashedDataFeedKey> hashedDataFeedKeys;

    @JsonCreator
    public HashedDataFeedKeys(@JsonProperty("dataFeedKeys") final List<HashedDataFeedKey> hashedDataFeedKeys) {
        this.hashedDataFeedKeys = hashedDataFeedKeys;
    }

    public List<HashedDataFeedKey> getDataFeedKeys() {
        return NullSafe.list(hashedDataFeedKeys);
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final HashedDataFeedKeys that = (HashedDataFeedKeys) object;
        return Objects.equals(hashedDataFeedKeys, that.hashedDataFeedKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedDataFeedKeys);
    }

    @Override
    public String toString() {
        return "DataFeedKeys{" +
               "dataFeedKeys=" + hashedDataFeedKeys +
               '}';
    }
}
