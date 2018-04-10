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

package stroom.statistics.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.common.StatisticRollUpType;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
@XmlRootElement(name = "stroomStatsStore")
@XmlType(name = "StroomStatsStoreDoc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "statisticType", "rollUpType", "precision", "enabled", "config"})
public class StatisticStoreDoc extends Doc implements StatisticStore {
    public static final String DOCUMENT_TYPE = "StatisticStore";
    public static final String ENTITY_TYPE_FOR_DISPLAY = "Statistic Store";
    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_PRECISION_MS = "Precision ms";


//    private static final Map<StatisticType, List<String>> STATIC_FIELDS_MAP = new HashMap<>();

    private static final Long DEFAULT_PRECISION = EventStoreTimeIntervalEnum.HOUR.columnInterval();

    private static final long serialVersionUID = -649286188919707915L;

//    static {
//        STATIC_FIELDS_MAP.put(StatisticType.COUNT, Arrays.asList(
//                FIELD_NAME_DATE_TIME,
//                FIELD_NAME_COUNT,
//                FIELD_NAME_PRECISION_MS
//        ));
//        STATIC_FIELDS_MAP.put(StatisticType.VALUE, Arrays.asList(
//                FIELD_NAME_DATE_TIME,
//                FIELD_NAME_VALUE,
//                FIELD_NAME_COUNT,
//                FIELD_NAME_PRECISION_MS
//        ));
//    }

    private String description;
    private StatisticType statisticType;
    private StatisticRollUpType rollUpType;
    private Long precision;
    private Boolean enabled;
    private StatisticsDataSourceData config;

    public StatisticStoreDoc() {
        setDefaults();
    }

    private void setDefaults() {
        this.statisticType = StatisticType.COUNT;
        this.rollUpType = StatisticRollUpType.NONE;
        this.precision = DEFAULT_PRECISION;
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

    @JsonIgnore
    public boolean isValidField(final String fieldName) {
        if (config == null) {
            return false;
        } else if (config.getStatisticFields() == null) {
            return false;
        } else if (config.getStatisticFields().size() == 0) {
            return false;
        } else {
            return config.getStatisticFields().contains(new StatisticField(fieldName));
        }
    }

    @JsonIgnore
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
            for (final StatisticField statisticField : config.getStatisticFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

//    @JsonIgnore
//    public List<String> getAllFieldNames() {
//        List<String> allFieldNames = new ArrayList<>(STATIC_FIELDS_MAP.get(getStatisticType()));
//        allFieldNames.addAll(getFieldNames());
//        return allFieldNames;
//    }

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
}
