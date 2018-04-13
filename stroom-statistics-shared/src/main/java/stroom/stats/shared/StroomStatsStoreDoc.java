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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
@XmlRootElement(name = "stroomStatsStore")
@XmlType(name = "StroomStatsStoreDoc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
public class StroomStatsStoreDoc extends Doc {
    private static final long serialVersionUID = -1667372785365881297L;

    public static final String DOCUMENT_TYPE = "StroomStatsStore";

    private static final EventStoreTimeIntervalEnum DEFAULT_PRECISION_INTERVAL = EventStoreTimeIntervalEnum.HOUR;

    @XmlElement(name = "description")
    private String description;
    @XmlElement(name = "statisticType")
    private StatisticType statisticType = StatisticType.COUNT;
    @XmlElement(name = "statisticRollUpType")
    private StatisticRollUpType statisticRollUpType = StatisticRollUpType.NONE;
    @XmlElement(name = "precision")
    private EventStoreTimeIntervalEnum precision;
    @XmlElement(name = "enabled")
    private Boolean enabled;
    @XmlElement(name = "config")
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

    //    @Transient
//    public boolean isValidField(final String fieldName) {
//        if (stroomStatsStoreDataObject == null) {
//            return false;
//        } else if (stroomStatsStoreDataObject.getStatisticFields() == null) {
//            return false;
//        } else if (stroomStatsStoreDataObject.getStatisticFields().size() == 0) {
//            return false;
//        } else {
//            return stroomStatsStoreDataObject.getStatisticFields().contains(new StatisticField(fieldName));
//        }
//    }
//
//    @Transient
//    public boolean isRollUpCombinationSupported(final Set<String> rolledUpFieldNames) {
//        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
//            return true;
//        }
//
//        if (!rolledUpFieldNames.isEmpty() && getRollUpType().equals(StatisticRollUpType.NONE)) {
//            return false;
//        }
//
//        if (getRollUpType().equals(StatisticRollUpType.ALL)) {
//            return true;
//        }
//
//        // rolledUpFieldNames not empty if we get here
//
//        if (stroomStatsStoreDataObject == null) {
//            throw new RuntimeException(
//                    "isRollUpCombinationSupported called with non-empty list but data source has no statistic fields or custom roll up masks");
//        }
//
//        return stroomStatsStoreDataObject.isRollUpCombinationSupported(rolledUpFieldNames);
//    }
//
//    @Transient
//    public Integer getPositionInFieldList(final String fieldName) {
//        return stroomStatsStoreDataObject.getFieldPositionInList(fieldName);
//    }
//
//    @Transient
//    public List<String> getFieldNames() {
//        if (stroomStatsStoreDataObject != null) {
//            final List<String> fieldNames = new ArrayList<>();
//            for (final StatisticField statisticField : stroomStatsStoreDataObject.getStatisticFields()) {
//                fieldNames.add(statisticField.getFieldName());
//            }
//            return fieldNames;
//        } else {
//            return Collections.emptyList();
//        }
//    }
//
    @XmlTransient
    @JsonIgnore
    public int getStatisticFieldCount() {
        return config == null ? 0 : config.getStatisticFields().size();
    }

    @XmlTransient
    @JsonIgnore
    public List<StatisticField> getStatisticFields() {
        if (config != null) {
            return config.getStatisticFields();
        } else {
            return Collections.emptyList();
        }
    }

    @XmlTransient
    @JsonIgnore
    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        if (config != null) {
            return config.getCustomRollUpMasks();
        } else {
            return Collections.emptySet();
        }
    }

//    @Override
//    public String toString() {
//        return "StroomStatsStoreEntity{" +
//                "description='" + description + '\'' +
//                ", StatisticType=" + getStatisticType() +
//                ", RollUpType=" + getRollUpType() +
//                ", precision='" + precision + '\'' +
//                ", enabled=" + enabled +
//                ", data='" + data + '\'' +
//                ", stroomStatsStoreDataObject=" + stroomStatsStoreDataObject +
//                '}';
//    }


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
