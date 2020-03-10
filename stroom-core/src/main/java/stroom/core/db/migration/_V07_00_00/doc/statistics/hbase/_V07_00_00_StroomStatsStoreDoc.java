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

package stroom.core.db.migration._V07_00_00.doc.statistics.hbase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
public class _V07_00_00_StroomStatsStoreDoc extends _V07_00_00_Doc {
    private static final long serialVersionUID = -1667372785365881297L;

    public static final String DOCUMENT_TYPE = "StroomStatsStore";

    private static final _V07_00_00_EventStoreTimeIntervalEnum DEFAULT_PRECISION_INTERVAL = _V07_00_00_EventStoreTimeIntervalEnum.HOUR;

    private String description;
    private _V07_00_00_StatisticType statisticType;
    private _V07_00_00_StatisticRollUpType statisticRollUpType;
    private _V07_00_00_EventStoreTimeIntervalEnum precision;
    private Boolean enabled;
    private _V07_00_00_StroomStatsStoreEntityData config;

    public _V07_00_00_StroomStatsStoreDoc() {
        statisticType = _V07_00_00_StatisticType.COUNT;
        statisticRollUpType = _V07_00_00_StatisticRollUpType.NONE;
        precision = DEFAULT_PRECISION_INTERVAL;
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
        return statisticRollUpType;
    }

    public void setRollUpType(final _V07_00_00_StatisticRollUpType rollUpType) {
        this.statisticRollUpType = rollUpType;
    }

    public _V07_00_00_EventStoreTimeIntervalEnum getPrecision() {
        return precision;
    }

    public void setPrecision(final _V07_00_00_EventStoreTimeIntervalEnum precision) {
        this.precision = precision;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public _V07_00_00_StroomStatsStoreEntityData getConfig() {
        return config;
    }

    public void setConfig(final _V07_00_00_StroomStatsStoreEntityData statisticDataSourceDataObject) {
        this.config = statisticDataSourceDataObject;
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
        final _V07_00_00_StroomStatsStoreDoc that = (_V07_00_00_StroomStatsStoreDoc) o;
        return Objects.equals(description, that.description) &&
                statisticType == that.statisticType &&
                statisticRollUpType == that.statisticRollUpType &&
                precision == that.precision &&
                Objects.equals(enabled, that.enabled) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, statisticType, statisticRollUpType, precision, enabled, config);
    }
}
