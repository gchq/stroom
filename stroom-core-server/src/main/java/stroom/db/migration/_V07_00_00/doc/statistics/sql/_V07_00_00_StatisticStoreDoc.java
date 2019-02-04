/*
 * Copyright 2016 Crown Copyright
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

package stroom.db.migration._V07_00_00.doc.statistics.sql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
public class _V07_00_00_StatisticStoreDoc extends _V07_00_00_Doc implements _V07_00_00_StatisticStore {
    public static final String DOCUMENT_TYPE = "StatisticStore";

    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_PRECISION_MS = "Precision ms";

    private static final Long DEFAULT_PRECISION = _V07_00_00_EventStoreTimeIntervalEnum.HOUR.columnInterval();

    private static final long serialVersionUID = -649286188919707915L;

    private String description;
    private _V07_00_00_StatisticType statisticType;
    private _V07_00_00_StatisticRollUpType rollUpType;
    private Long precision;
    private Boolean enabled;
    private _V07_00_00_StatisticsDataSourceData config;

    public _V07_00_00_StatisticStoreDoc() {
        setDefaults();
    }

    private void setDefaults() {
        this.statisticType = _V07_00_00_StatisticType.COUNT;
        this.rollUpType = _V07_00_00_StatisticRollUpType.NONE;
        this.precision = DEFAULT_PRECISION;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public _V07_00_00_StatisticType getStatisticType() {
        return statisticType;
    }

    public void setStatisticType(final _V07_00_00_StatisticType statisticType) {
        this.statisticType = statisticType;
    }

    public _V07_00_00_StatisticRollUpType getRollUpType() {
        return rollUpType;
    }

    public void setRollUpType(final _V07_00_00_StatisticRollUpType rollUpType) {
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

    public _V07_00_00_StatisticsDataSourceData getConfig() {
        return config;
    }

    public void setConfig(final _V07_00_00_StatisticsDataSourceData config) {
        this.config = config;
    }

    @JsonIgnore
    public boolean isValidField(final String fieldName) {
        if (config == null) {
            return false;
        } else if (config.getStatisticFields() == null) {
            return false;
        } else if (config.getStatisticFields().size() == 0) {
            return false;
        } else {
            return config.getStatisticFields().contains(new _V07_00_00_StatisticField(fieldName));
        }
    }

    @JsonIgnore
    public boolean isRollUpCombinationSupported(final Set<String> rolledUpFieldNames) {
        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (getRollUpType().equals(_V07_00_00_StatisticRollUpType.NONE)) {
            return false;
        }

        if (getRollUpType().equals(_V07_00_00_StatisticRollUpType.ALL)) {
            return true;
        }

        // rolledUpFieldNames not empty if we get here

        if (config == null) {
            throw new RuntimeException(
                    "isRollUpCombinationSupported called with non-empty list but data source has no statistic fields or custom roll up masks");
        }

        return config.isRollUpCombinationSupported(rolledUpFieldNames);
    }

    @JsonIgnore
    public Integer getPositionInFieldList(final String fieldName) {
        return config.getFieldPositionInList(fieldName);
    }

    @JsonIgnore
    public List<String> getFieldNames() {
        if (config != null) {
            final List<String> fieldNames = new ArrayList<>();
            for (final _V07_00_00_StatisticField statisticField : config.getStatisticFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

    @JsonIgnore
    public int getStatisticFieldCount() {
        return config == null ? 0 : config.getStatisticFields().size();
    }

    @JsonIgnore
    public List<_V07_00_00_StatisticField> getStatisticFields() {
        if (config != null) {
            return config.getStatisticFields();
        } else {
            return Collections.emptyList();
        }
    }

    @JsonIgnore
    public Set<_V07_00_00_CustomRollUpMask> getCustomRollUpMasks() {
        if (config != null) {
            return config.getCustomRollUpMasks();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final _V07_00_00_StatisticStoreDoc that = (_V07_00_00_StatisticStoreDoc) o;
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
}
