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

package stroom.statistics.shared.hbase;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "STROOM_STATS_STORE", uniqueConstraints = @UniqueConstraint(columnNames = { "NAME" }) )
public class StroomStatsStoreEntity extends DocumentEntity implements StatisticStore {
    public static final String ENTITY_TYPE = "StroomStatsStore";
    public static final String ENTITY_TYPE_FOR_DISPLAY = "Stroom-Stats Store";

    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_MIN_VALUE = "Min Statistic Value";
    public static final String FIELD_NAME_MAX_VALUE = "Max Statistic Value";
    public static final String FIELD_NAME_PRECISION = "Precision";

    // Hibernate table/column names
    public static final String TABLE_NAME = "STROOM_STATS_STORE";
    public static final String STATISTIC_TYPE = SQLNameConstants.STATISTIC + SEP + SQLNameConstants.TYPE;
    public static final String PRECISION = SQLNameConstants.PRECISION;
    public static final String ROLLUP_TYPE = SQLNameConstants.ROLLUP + SEP + SQLNameConstants.TYPE;
    public static final String DEFAULT_PRECISION = EventStoreTimeIntervalEnum.HOUR.longName();
    public static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private static final long serialVersionUID = -1667372785365881297L;

    private String description;
    private byte pStatisticType;
    private byte pRollUpType;
    private String precision;
    private boolean enabled = false;

    private String data;
    private StatisticsDataSourceData statisticsDataSourceDataObject;

    public StroomStatsStoreEntity() {
        setDefaults();
    }

    private void setDefaults() {
        this.pStatisticType = StatisticType.COUNT.getPrimitiveValue();
        this.pRollUpType = StatisticRollUpType.NONE.getPrimitiveValue();
        this.precision = DEFAULT_PRECISION;
    }

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @Override
    @Transient
    public String getType() {
        return ENTITY_TYPE;
    }

    @Column(name = STATISTIC_TYPE, nullable = false)
    public byte getpStatisticType() {
        return pStatisticType;
    }

    public void setpStatisticType(final byte pStatisticType) {
        this.pStatisticType = pStatisticType;
    }

    @Transient
    public StatisticType getStatisticType() {
        return StatisticType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pStatisticType);
    }

    public void setStatisticType(final StatisticType statisticType) {
        this.pStatisticType = statisticType.getPrimitiveValue();
    }

    @Column(name = ROLLUP_TYPE, nullable = false)
    public byte getpRollUpType() {
        return pRollUpType;
    }

    public void setpRollUpType(final byte pRollUpType) {
        this.pRollUpType = pRollUpType;
    }

    @Transient
    public StatisticRollUpType getRollUpType() {
        return StatisticRollUpType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pRollUpType);
    }

    public void setRollUpType(final StatisticRollUpType rollUpType) {
        this.pRollUpType = rollUpType.getPrimitiveValue();
    }

    @Column(name = PRECISION, nullable = false)
    public String getPrecision() {
        return precision;
    }

    public void setPrecision(final String precision) {
        this.precision = precision;
    }

    @Column(name = SQLNameConstants.ENABLED, nullable = false)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Lob
    @Column(name = SQLNameConstants.DATA, length = Integer.MAX_VALUE)
    @ExternalFile
    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    @Transient
    @XmlTransient
    public StatisticsDataSourceData getStatisticDataSourceDataObject() {
        return statisticsDataSourceDataObject;
    }

    public void setStatisticDataSourceDataObject(final StatisticsDataSourceData statisticDataSourceDataObject) {
        this.statisticsDataSourceDataObject = statisticDataSourceDataObject;
    }

    @Transient
    public boolean isValidField(final String fieldName) {
        if (statisticsDataSourceDataObject == null) {
            return false;
        } else if (statisticsDataSourceDataObject.getStatisticFields() == null) {
            return false;
        } else if (statisticsDataSourceDataObject.getStatisticFields().size() == 0) {
            return false;
        } else {
            return statisticsDataSourceDataObject.getStatisticFields().contains(new StatisticField(fieldName));
        }
    }

    @Transient
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

        if (statisticsDataSourceDataObject == null) {
            throw new RuntimeException(
                    "isRollUpCombinationSupported called with non-empty list but data source has no statistic fields or custom roll up masks");
        }

        return statisticsDataSourceDataObject.isRollUpCombinationSupported(rolledUpFieldNames);
    }

    @Transient
    public Integer getPositionInFieldList(final String fieldName) {
        return statisticsDataSourceDataObject.getFieldPositionInList(fieldName);
    }

    @Transient
    public List<String> getFieldNames() {
        if (statisticsDataSourceDataObject != null) {
            final List<String> fieldNames = new ArrayList<String>();
            for (final StatisticField statisticField : statisticsDataSourceDataObject.getStatisticFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

    @Transient
    public int getStatisticFieldCount() {
        return statisticsDataSourceDataObject == null ? 0 : statisticsDataSourceDataObject.getStatisticFields().size();
    }

    @Transient
    public List<StatisticField> getStatisticFields() {
        if (statisticsDataSourceDataObject != null) {
            return statisticsDataSourceDataObject.getStatisticFields();
        } else {
            return Collections.emptyList();
        }
    }

    @Transient
    public Set<CustomRollUpMask> getCustomRollUpMasks() {
        if (statisticsDataSourceDataObject != null) {
            return statisticsDataSourceDataObject.getCustomRollUpMasks();
        } else {
            return Collections.emptySet();
        }
    }
}
