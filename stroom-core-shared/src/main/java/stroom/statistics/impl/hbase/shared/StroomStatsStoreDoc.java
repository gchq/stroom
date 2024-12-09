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

package stroom.statistics.impl.hbase.shared;

import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Description(
        "The Stroom-Stats Store Document is deprecated and should not be used."
)
@JsonPropertyOrder({
        "uuid",
        "name",
        "uniqueName",
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
public class StroomStatsStoreDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "StroomStatsStore";
    public static final SvgImage ICON = SvgImage.DOCUMENT_STROOM_STATS_STORE;

    private static final EventStoreTimeIntervalEnum DEFAULT_PRECISION_INTERVAL = EventStoreTimeIntervalEnum.HOUR;

    @JsonProperty
    private String description;
    @JsonProperty
    private StatisticType statisticType;
    @JsonProperty
    private StatisticRollUpType rollUpType;
    @JsonProperty
    private EventStoreTimeIntervalEnum precision;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private StroomStatsStoreEntityData config;

    public StroomStatsStoreDoc() {
        statisticType = StatisticType.COUNT;
        rollUpType = StatisticRollUpType.NONE;
        precision = DEFAULT_PRECISION_INTERVAL;
    }

    @JsonCreator
    public StroomStatsStoreDoc(@JsonProperty("uuid") final String uuid,
                               @JsonProperty("name") final String name,
                               @JsonProperty("uniqueName") final String uniqueName,
                               @JsonProperty("version") final String version,
                               @JsonProperty("createTimeMs") final Long createTimeMs,
                               @JsonProperty("updateTimeMs") final Long updateTimeMs,
                               @JsonProperty("createUser") final String createUser,
                               @JsonProperty("updateUser") final String updateUser,
                               @JsonProperty("description") final String description,
                               @JsonProperty("statisticType") final StatisticType statisticType,
                               @JsonProperty("rollUpType") final StatisticRollUpType rollUpType,
                               @JsonProperty("precision") final EventStoreTimeIntervalEnum precision,
                               @JsonProperty("enabled") final boolean enabled,
                               @JsonProperty("config") final StroomStatsStoreEntityData config) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
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
            this.precision = DEFAULT_PRECISION_INTERVAL;
        }
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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

    public EventStoreTimeIntervalEnum getPrecision() {
        return precision;
    }

    public void setPrecision(final EventStoreTimeIntervalEnum precision) {
        this.precision = precision;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
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
        final StroomStatsStoreDoc that = (StroomStatsStoreDoc) o;
        return Objects.equals(description, that.description) &&
               statisticType == that.statisticType &&
               rollUpType == that.rollUpType &&
               precision == that.precision &&
               Objects.equals(enabled, that.enabled) &&
               Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, statisticType, rollUpType, precision, enabled, config);
    }
}
