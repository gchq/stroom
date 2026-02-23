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

package stroom.statistics.impl.sql.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"fields", "customRollUpMasks"})
@JsonInclude(Include.NON_NULL)
public class StatisticsDataSourceData {

    /**
     * Should be a SortedSet but GWT doesn't support that. Contents should be
     * sorted and not contain duplicates
     */
    @JsonProperty
    private final List<StatisticField> fields;

    /**
     * Held in a set to prevent duplicates.
     */
    @JsonProperty
    private final Set<CustomRollUpMask> customRollUpMasks;

    @JsonCreator
    public StatisticsDataSourceData(@JsonProperty("fields") final List<StatisticField> fields,
                                    @JsonProperty("customRollUpMasks") final Set<CustomRollUpMask> customRollUpMasks) {
        final List<StatisticField> sorted = new ArrayList<>(NullSafe.list(fields));
        sorted.sort(StatisticField::compareTo);
        this.fields = Collections.unmodifiableList(sorted);
        this.customRollUpMasks = Collections.unmodifiableSet(NullSafe.set(customRollUpMasks));
    }

    public List<StatisticField> getFields() {
        return fields;
    }

    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        return customRollUpMasks;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StatisticsDataSourceData that = (StatisticsDataSourceData) o;
        return Objects.equals(fields, that.fields) &&
               Objects.equals(customRollUpMasks, that.customRollUpMasks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, customRollUpMasks);
    }

    @Override
    public String toString() {
        return "StatisticsDataSourceData{" +
               "fields=" + fields +
               ", customRollUpMasks=" + customRollUpMasks +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private List<StatisticField> fields;
        private Set<CustomRollUpMask> customRollUpMasks;

        private Builder() {
            this.fields = new ArrayList<>();
            this.customRollUpMasks = new HashSet<>();
        }

        private Builder(final StatisticsDataSourceData data) {
            this.fields = data.fields != null
                    ? new ArrayList<>(data.fields)
                    : new ArrayList<>();
            this.customRollUpMasks = data.customRollUpMasks != null
                    ? new HashSet<>(data.customRollUpMasks)
                    : new HashSet<>();
        }

        public Builder fields(final List<StatisticField> fields) {
            this.fields = fields;
            return this;
        }

        public Builder customRollUpMasks(final Set<CustomRollUpMask> customRollUpMasks) {
            this.customRollUpMasks = customRollUpMasks;
            return this;
        }

        public StatisticsDataSourceData build() {
            return new StatisticsDataSourceData(
                    fields,
                    customRollUpMasks);
        }
    }
}
