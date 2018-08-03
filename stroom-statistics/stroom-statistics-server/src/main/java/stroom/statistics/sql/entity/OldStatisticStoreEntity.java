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

package stroom.statistics.sql.entity;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.ExternalFile;
import stroom.entity.shared.SQLNameConstants;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.common.StatisticField;
import stroom.statistics.shared.common.StatisticRollUpType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "STAT_DAT_SRC", uniqueConstraints = @UniqueConstraint(columnNames = {"NAME"}))
public class OldStatisticStoreEntity extends DocumentEntity {
    public static final String ENTITY_TYPE = "StatisticStore";
    public static final String ENTITY_TYPE_FOR_DISPLAY = "Statistic Store";
    // IndexFields names
    public static final String FIELD_NAME_DATE_TIME = "Date Time";
    public static final String FIELD_NAME_VALUE = "Statistic Value";
    public static final String FIELD_NAME_COUNT = "Statistic Count";
    public static final String FIELD_NAME_PRECISION_MS = "Precision ms";


    public static final Map<StatisticType, List<String>> STATIC_FIELDS_MAP = new HashMap<>();
    // Hibernate table/column names
    public static final String TABLE_NAME = SQLNameConstants.STATISTIC + SEP + SQLNameConstants.DATA + SEP
            + SQLNameConstants.SOURCE;
    public static final String STATISTIC_TYPE = SQLNameConstants.STATISTIC + SEP + SQLNameConstants.TYPE;
    public static final String PRECISION = SQLNameConstants.PRECISION;
    public static final String ROLLUP_TYPE = SQLNameConstants.ROLLUP + SEP + SQLNameConstants.TYPE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final Long DEFAULT_PRECISION = EventStoreTimeIntervalEnum.HOUR.columnInterval();
    public static final String DEFAULT_NAME_PATTERN_VALUE = "^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$";
    private static final long serialVersionUID = -649286188919707915L;

    static {
        STATIC_FIELDS_MAP.put(StatisticType.COUNT, Arrays.asList(
                FIELD_NAME_DATE_TIME,
                FIELD_NAME_COUNT,
                FIELD_NAME_PRECISION_MS
        ));
        STATIC_FIELDS_MAP.put(StatisticType.VALUE, Arrays.asList(
                FIELD_NAME_DATE_TIME,
                FIELD_NAME_VALUE,
                FIELD_NAME_COUNT,
                FIELD_NAME_PRECISION_MS
        ));
    }

    private String description;
    private byte pStatisticType;
    private byte pRollUpType;
    private Long precision;
    private boolean enabled = false;

    private String data;
    private StatisticsDataSourceData statisticsDataSourceDataObject;

    public OldStatisticStoreEntity() {
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
        // This is done here as the XML libs in the jdk appear to behave differently to the xerces one.
        // The jdk one respects XmlAccessType.FIELD while xerces does not, so sorting in the setter has
        // no affect now.
        statisticDataSourceDataObject.reOrderStatisticFields();
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
            final List<String> fieldNames = new ArrayList<>();
            for (final StatisticField statisticField : statisticsDataSourceDataObject.getStatisticFields()) {
                fieldNames.add(statisticField.getFieldName());
            }
            return fieldNames;
        } else {
            return Collections.emptyList();
        }
    }

    @Transient
    public List<String> getAllFieldNames() {
        List<String> allFieldNames = new ArrayList<>(STATIC_FIELDS_MAP.get(getStatisticType()));
        allFieldNames.addAll(getFieldNames());
        return allFieldNames;
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
