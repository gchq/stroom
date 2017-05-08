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

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "HBASE_STAT_DAT_SRC", uniqueConstraints = @UniqueConstraint(columnNames = { "NAME", "ENGINE_NAME" }) )
public class HBaseStatisticStoreEntity extends DocumentEntity implements StatisticStore {
    public static final String ENTITY_TYPE = "StatisticStore";
    public static final String ENTITY_TYPE_FOR_DISPLAY = "Statistic Store";
    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_MIN_VALUE = "Min Statistic Value";
    public static final String FIELD_NAME_MAX_VALUE = "Max Statistic Value";
    public static final String FIELD_NAME_PRECISION = "Precision";
    public static final String FIELD_NAME_PRECISION_MS = "Precision ms";
    // Hibernate table/column names
    public static final String TABLE_NAME = SQLNameConstants.STATISTIC + SEP + SQLNameConstants.DATA + SEP
            + SQLNameConstants.SOURCE;
    public static final String ENGINE_NAME = SQLNameConstants.ENGINE + SEP + SQLNameConstants.NAME;
    public static final String STATISTIC_TYPE = SQLNameConstants.STATISTIC + SEP + SQLNameConstants.TYPE;
    public static final String PRECISION = SQLNameConstants.PRECISION;
    public static final String ROLLUP_TYPE = SQLNameConstants.ROLLUP + SEP + SQLNameConstants.TYPE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String NOT_SET = "NOT SET";
    public static final Long DEFAULT_PRECISION = EventStoreTimeIntervalEnum.HOUR.columnInterval();
    public static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";

    private static final long serialVersionUID = -649286188919707915L;

    private String description;
    private String engineName;
    private byte pStatisticType;
    private byte pRollUpType;
    private Long precision;
    private boolean enabled = false;

    private String data;
    private StatisticsDataSourceData statisticsDataSourceDataObject;

    public HBaseStatisticStoreEntity() {
        setDefaults();
    }

    private void setDefaults() {
        this.pStatisticType = StatisticType.COUNT.getPrimitiveValue();
        this.pRollUpType = StatisticRollUpType.NONE.getPrimitiveValue();
        this.engineName = NOT_SET;
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

    @Column(name = ENGINE_NAME, nullable = false)
    public String getEngineName() {
        return engineName;
    }

    public void setEngineName(final String engineName) {
        this.engineName = engineName;
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
    public Long getPrecision() {
        return precision;
    }

    public void setPrecision(final Long precision) {
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
