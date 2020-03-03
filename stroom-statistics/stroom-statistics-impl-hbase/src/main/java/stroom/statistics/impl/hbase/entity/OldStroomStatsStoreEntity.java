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

package stroom.statistics.impl.hbase.entity;

import stroom.importexport.migration.DocumentEntity;
import stroom.importexport.shared.ExternalFile;
import stroom.statistics.impl.hbase.shared.CustomRollUpMask;
import stroom.statistics.impl.hbase.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.hbase.shared.StatisticField;
import stroom.statistics.impl.hbase.shared.StatisticRollUpType;
import stroom.statistics.impl.hbase.shared.StatisticType;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreEntityData;

import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Used for legacy migration
 **/
@Deprecated
public class OldStroomStatsStoreEntity extends DocumentEntity {
    public static final String ENTITY_TYPE = "StroomStatsStore";
    public static final String ENTITY_TYPE_FOR_DISPLAY = "Stroom-Stats Store";

    // Hibernate table/column names
    public static final EventStoreTimeIntervalEnum DEFAULT_PRECISION_INTERVAL = EventStoreTimeIntervalEnum.HOUR;
    public static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private static final long serialVersionUID = -1667372785365881297L;

    private String description;
    private byte pStatisticType;
    private byte pRollUpType;
    private String precision;
    private boolean enabled = false;

    private String data;
    private StroomStatsStoreEntityData stroomStatsStoreDataObject;

    public OldStroomStatsStoreEntity() {
        setDefaults();
    }

    private void setDefaults() {
        this.pStatisticType = StatisticType.COUNT.getPrimitiveValue();
        this.pRollUpType = StatisticRollUpType.NONE.getPrimitiveValue();
        setPrecision(DEFAULT_PRECISION_INTERVAL);
    }


    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getType() {
        return ENTITY_TYPE;
    }

    public byte getpStatisticType() {
        return pStatisticType;
    }

    public void setpStatisticType(final byte pStatisticType) {
        this.pStatisticType = pStatisticType;
    }

    public StatisticType getStatisticType() {
        return StatisticType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pStatisticType);
    }

    public void setStatisticType(final StatisticType statisticType) {
        this.pStatisticType = statisticType.getPrimitiveValue();
    }

    public byte getpRollUpType() {
        return pRollUpType;
    }

    public void setpRollUpType(final byte pRollUpType) {
        this.pRollUpType = pRollUpType;
    }

    public StatisticRollUpType getRollUpType() {
        return StatisticRollUpType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pRollUpType);
    }

    public void setRollUpType(final StatisticRollUpType rollUpType) {
        this.pRollUpType = rollUpType.getPrimitiveValue();
    }

    public String getPrecision() {
        return precision;
    }

    public void setPrecision(final EventStoreTimeIntervalEnum interval) {
        this.precision = interval.toString();
    }

    public EventStoreTimeIntervalEnum getPrecisionAsInterval() {
        return EventStoreTimeIntervalEnum.valueOf(precision);
    }

    public void setPrecision(final String precision) {
        this.precision = precision;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @XmlTransient
    public StroomStatsStoreEntityData getDataObject() {
        return stroomStatsStoreDataObject;
    }

    public void setDataObject(final StroomStatsStoreEntityData stroomStatsStoreDataObject) {
        stroomStatsStoreDataObject.reOrderStatisticFields();
        this.stroomStatsStoreDataObject = stroomStatsStoreDataObject;
    }

    public boolean isValidField(final String fieldName) {
        if (stroomStatsStoreDataObject == null) {
            return false;
        } else if (stroomStatsStoreDataObject.getFields() == null) {
            return false;
        } else if (stroomStatsStoreDataObject.getFields().size() == 0) {
            return false;
        } else {
            return stroomStatsStoreDataObject.getFields().contains(new StatisticField(fieldName));
        }
    }

    public boolean isRollUpCombinationSupported(final Set<String> rolledUpFieldNames) {
        if (rolledUpFieldNames == null || rolledUpFieldNames.isEmpty()) {
            return true;
        }

        if (!rolledUpFieldNames.isEmpty() && getRollUpType().equals(StatisticRollUpType.NONE)) {
            return false;
        }

        if (getRollUpType().equals(StatisticRollUpType.ALL)) {
            return true;
        }

        // rolledUpFieldNames not empty if we get here

        if (stroomStatsStoreDataObject == null) {
            throw new RuntimeException(
                    "isRollUpCombinationSupported called with non-empty list but data source has no statistic fields or custom roll up masks");
        }

        return stroomStatsStoreDataObject.isRollUpCombinationSupported(rolledUpFieldNames);
    }

    public Integer getPositionInFieldList(final String fieldName) {
        return stroomStatsStoreDataObject.getFieldPositionInList(fieldName);
    }

    public List<String> getFieldNames() {
        if (stroomStatsStoreDataObject != null) {
            final List<String> fieldNames = new ArrayList<>();
            for (final StatisticField statisticField : stroomStatsStoreDataObject.getFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

    public int getStatisticFieldCount() {
        return stroomStatsStoreDataObject == null ? 0 : stroomStatsStoreDataObject.getFields().size();
    }

    public List<StatisticField> getStatisticFields() {
        if (stroomStatsStoreDataObject != null) {
            return stroomStatsStoreDataObject.getFields();
        } else {
            return Collections.emptyList();
        }
    }

    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        if (stroomStatsStoreDataObject != null) {
            return stroomStatsStoreDataObject.getCustomRollUpMasks();
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    public String toString() {
        return "StroomStatsStoreEntity{" +
                "description='" + description + '\'' +
                ", StatisticType=" + getStatisticType() +
                ", RollUpType=" + getRollUpType() +
                ", precision='" + precision + '\'' +
                ", enabled=" + enabled +
                ", data='" + data + '\'' +
                ", stroomStatsStoreDataObject=" + stroomStatsStoreDataObject +
                '}';
    }
}
