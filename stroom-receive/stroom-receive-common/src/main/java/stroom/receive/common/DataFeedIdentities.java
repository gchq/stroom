/*
 * Copyright 2016-2026 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class DataFeedIdentities {

    @JsonProperty
    private final List<DataFeedIdentity> dataFeedIdentities;

    public DataFeedIdentities(@JsonProperty("dataFeedIdentity") final List<DataFeedIdentity> dataFeedIdentities) {
        this.dataFeedIdentities = NullSafe.removeNulls(dataFeedIdentities);
    }

    public List<DataFeedIdentity> getDataFeedIdentities() {
        return dataFeedIdentities;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return dataFeedIdentities.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFeedIdentities that = (DataFeedIdentities) o;
        return Objects.equals(dataFeedIdentities, that.dataFeedIdentities);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dataFeedIdentities);
    }

    @Override
    public String toString() {
        return "DataFeedIdentities{" +
               "dataFeedIdentities=" + dataFeedIdentities +
               '}';
    }
}
