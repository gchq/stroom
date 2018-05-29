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

package stroom.stats.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.statistics.shared.StatisticType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
public class StroomStatsStoreDoc extends Doc {
    private static final long serialVersionUID = -1667372785365881297L;

    public static final String DOCUMENT_TYPE = "StroomStatsStore";

    private static final EventStoreTimeIntervalEnum DEFAULT_PRECISION_INTERVAL = EventStoreTimeIntervalEnum.HOUR;

    private String description;
    private StatisticType statisticType = StatisticType.COUNT;
    private StatisticRollUpType statisticRollUpType = StatisticRollUpType.NONE;
    private EventStoreTimeIntervalEnum precision;
    private Boolean enabled;
    private StroomStatsStoreEntityData config;

    public StroomStatsStoreDoc() {
        setDefaults();
    }

    private void setDefaults() {
        this.statisticType = StatisticType.COUNT;
        this.statisticRollUpType = StatisticRollUpType.NONE;
        setPrecision(DEFAULT_PRECISION_INTERVAL);
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
        return statisticRollUpType;
    }

    public void setRollUpType(final StatisticRollUpType rollUpType) {
        this.statisticRollUpType = rollUpType;
    }

    public EventStoreTimeIntervalEnum getPrecision() {
        return precision;
    }

    public void setPrecision(final EventStoreTimeIntervalEnum precision) {
        this.precision = precision;
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final Boolean enabled) {
        this.enabled = enabled;
    }

    public StroomStatsStoreEntityData getConfig() {
        return config;
    }

    public void setConfig(final StroomStatsStoreEntityData statisticDataSourceDataObject) {
        this.config = statisticDataSourceDataObject;
    }

    @JsonIgnore
    public int getStatisticFieldCount() {
        return config == null ? 0 : config.getStatisticFields().size();
    }

    @JsonIgnore
    public List<StatisticField> getStatisticFields() {
        if (config != null) {
            return config.getStatisticFields();
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final StroomStatsStoreDoc that = (StroomStatsStoreDoc) o;
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
