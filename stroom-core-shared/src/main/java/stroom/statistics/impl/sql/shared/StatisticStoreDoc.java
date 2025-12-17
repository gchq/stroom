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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Description(
        "Defines a logical statistic store used to hold statistical data of a particular type and " +
        "aggregation window.\n" +
        "Statistics in Stroom is a way to capture counts or values from events and record how they change " +
        "over time, with the counts/values aggregated (sum/mean) across time windows.\n" +
        "\n" +
        "The Statistic Store Document configures the type of the statistic (Count or Value), the tags " +
        "that are used to qualify a statistic event and the size of the aggregation windows.\n" +
        "It also supports the definition of roll-ups that allow for aggregation over all values of a tag.\n" +
        "Tags can be things like `user`, `node`, `feed`, etc. and can be used to filter data when " +
        "querying the statistic store in a Dashboard/Query.\n" +
        "\n" +
        "It is used by the {{< pipe-elm \"StatisticsFilter\" >}} pipeline element."
)
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "statisticType",
        "rollUpType",
        "precision",
        "enabled",
        "config"})
@JsonInclude(Include.NON_NULL)
public class StatisticStoreDoc extends AbstractDoc implements StatisticStore {

    public static final String TYPE = "StatisticStore";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.STATISTIC_STORE_DOCUMENT_TYPE;

    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_PRECISION_MS = "Precision ms";

    private static final Long DEFAULT_PRECISION = EventStoreTimeIntervalEnum.HOUR.columnInterval();

    @JsonProperty("description")
    private String description;
    @JsonProperty("statisticType")
    private StatisticType statisticType;
    @JsonProperty("rollUpType")
    private StatisticRollUpType rollUpType;
    @JsonProperty("precision")
    private Long precision;
    @JsonProperty("enabled")
    private Boolean enabled;
    @JsonProperty("config")
    private StatisticsDataSourceData config;

    @JsonCreator
    public StatisticStoreDoc(@JsonProperty("uuid") final String uuid,
                             @JsonProperty("name") final String name,
                             @JsonProperty("version") final String version,
                             @JsonProperty("createTimeMs") final Long createTimeMs,
                             @JsonProperty("updateTimeMs") final Long updateTimeMs,
                             @JsonProperty("createUser") final String createUser,
                             @JsonProperty("updateUser") final String updateUser,
                             @JsonProperty("description") final String description,
                             @JsonProperty("statisticType") final StatisticType statisticType,
                             @JsonProperty("rollUpType") final StatisticRollUpType rollUpType,
                             @JsonProperty("precision") final Long precision,
                             @JsonProperty("enabled") final Boolean enabled,
                             @JsonProperty("config") final StatisticsDataSourceData config) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.statisticType = statisticType;
        this.rollUpType = rollUpType;
        this.precision = precision;
        this.enabled = enabled;
        this.config = config;

        if (this.statisticType == null) {
            this.statisticType = StatisticType.COUNT;
        }
        if (this.rollUpType == null) {
            this.rollUpType = StatisticRollUpType.NONE;
        }
        if (this.precision == null) {
            this.precision = DEFAULT_PRECISION;
        }
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public StatisticType getStatisticType() {
        return statisticType;
    }

    public void setStatisticType(final StatisticType statisticType) {
        this.statisticType = statisticType;
    }

    public StatisticRollUpType getRollUpType() {
        return rollUpType;
    }

    public void setRollUpType(final StatisticRollUpType rollUpType) {
        this.rollUpType = rollUpType;
    }

    public Long getPrecision() {
        return precision;
    }

    public void setPrecision(final Long precision) {
        this.precision = precision;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public StatisticsDataSourceData getConfig() {
        return config;
    }

    public void setConfig(final StatisticsDataSourceData config) {
        this.config = config;
    }

    public boolean isValidField(final String fieldName) {
        if (config == null) {
            return false;
        } else if (config.getFields() == null) {
            return false;
        } else if (config.getFields().size() == 0) {
            return false;
        } else {
            return config.getFields().contains(new StatisticField(fieldName));
        }
    }

    public boolean isRollUpCombinationSupported(final Set<String> rolledUpFieldNames) {
        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (getRollUpType().equals(StatisticRollUpType.NONE)) {
            return false;
        }

        if (getRollUpType().equals(StatisticRollUpType.ALL)) {
            return true;
        }

        // rolledUpFieldNames not empty if we get here

        if (config == null) {
            throw new RuntimeException("isRollUpCombinationSupported called with non-empty list but data source " +
                                       "has no statistic fields or custom roll up masks");
        }

        return config.isRollUpCombinationSupported(rolledUpFieldNames);
    }

    public Integer getPositionInFieldList(final String fieldName) {
        return config.getFieldPositionInList(fieldName);
    }

    @JsonIgnore
    public List<String> getFieldNames() {
        if (config != null) {
            final List<String> fieldNames = new ArrayList<>();
            for (final StatisticField statisticField : config.getFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

    @JsonIgnore
    public int getStatisticFieldCount() {
        return config == null
                ? 0
                : config.getFields().size();
    }

    @JsonIgnore
    public List<StatisticField> getStatisticFields() {
        if (config != null) {
            return config.getFields();
        } else {
            return Collections.emptyList();
        }
    }

    @JsonIgnore
    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        if (config != null) {
            return config.getCustomRollUpMasks();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final StatisticStoreDoc that = (StatisticStoreDoc) o;
        return Objects.equals(description, that.description) &&
               statisticType == that.statisticType &&
               rollUpType == that.rollUpType &&
               Objects.equals(precision, that.precision) &&
               Objects.equals(enabled, that.enabled) &&
               Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, statisticType, rollUpType, precision, enabled, config);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractDoc.AbstractBuilder<StatisticStoreDoc, StatisticStoreDoc.Builder> {

        private String description;
        private StatisticType statisticType = StatisticType.COUNT;
        private StatisticRollUpType rollUpType = StatisticRollUpType.NONE;
        private Long precision = DEFAULT_PRECISION;
        private Boolean enabled;
        private StatisticsDataSourceData config;

        private Builder() {
        }

        private Builder(final StatisticStoreDoc elasticIndexDoc) {
            super(elasticIndexDoc);
            this.description = elasticIndexDoc.description;
            this.statisticType = elasticIndexDoc.statisticType;
            this.rollUpType = elasticIndexDoc.rollUpType;
            this.precision = elasticIndexDoc.precision;
            this.enabled = elasticIndexDoc.enabled;
            this.config = elasticIndexDoc.config;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder statisticType(final StatisticType statisticType) {
            this.statisticType = statisticType;
            return self();
        }

        public Builder rollUpType(final StatisticRollUpType rollUpType) {
            this.rollUpType = rollUpType;
            return self();
        }

        public Builder precision(final Long precision) {
            this.precision = precision;
            return self();
        }

        public Builder enabled(final Boolean enabled) {
            this.enabled = enabled;
            return self();
        }

        public Builder config(final StatisticsDataSourceData config) {
            this.config = config;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public StatisticStoreDoc build() {
            return new StatisticStoreDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    statisticType,
                    rollUpType,
                    precision,
                    enabled,
                    config);
        }
    }
}
