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

package stroom.streamstore.shared;

import stroom.entity.shared.AuditedEntity;
import stroom.entity.shared.HasName;
import stroom.entity.shared.SQLNameConstants;
import stroom.util.shared.HasDisplayValue;
import stroom.util.shared.ModelStringUtil;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.Comparator;

/**
 * List of all known stream types within the system.
 */
@Entity
@Table(name = "STRM_ATR_KEY")
public class StreamAttributeKey extends AuditedEntity implements HasName, HasDisplayValue {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.ATTRIBUTE + SEP
            + SQLNameConstants.KEY;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamAttributeKey";
    public static final String NAME = SQLNameConstants.NAME;
    public static final String FIELD_TYPE = SQLNameConstants.FIELD + SQLNameConstants.TYPE_SUFFIX;
    private static final long serialVersionUID = 8411148988526703262L;
    private String name;
    private byte pfieldType = StreamAttributeFieldUse.FIELD.getPrimitiveValue();

    public StreamAttributeKey() {
        // Default constructor necessary for GWT serialisation.
    }

    public StreamAttributeKey(final String name, final StreamAttributeFieldUse fieldType) {
        setName(name);
        setFieldType(fieldType);
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }

    @Override
    @Column(name = NAME, nullable = false, unique = true)
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Column(name = FIELD_TYPE, nullable = false, unique = false)
    public byte getPfieldType() {
        return pfieldType;
    }

    public void setPfieldType(final byte pfieldType) {
        this.pfieldType = pfieldType;
    }

    @Transient
    public StreamAttributeFieldUse getFieldType() {
        return StreamAttributeFieldUse.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pfieldType);
    }

    public void setFieldType(final StreamAttributeFieldUse fieldType) {
        pfieldType = fieldType.getPrimitiveValue();
    }

    public String format(final StreamAttributeMap map, final StreamAttributeKey key) {
        String valueString = map.getAttributeValue(key);

        if (valueString == null) {
            return null;
        }

        try {
            if (StreamAttributeFieldUse.COUNT_IN_DURATION_FIELD.equals(getFieldType())) {
                final long valueLong = Long.valueOf(valueString);
                valueString = ModelStringUtil.formatCsv(valueLong);

            } else if (StreamAttributeFieldUse.SIZE_FIELD.equals(getFieldType())) {
                valueString = ModelStringUtil.formatIECByteSizeString(Long.valueOf(valueString));

            } else if (StreamAttributeFieldUse.NUMERIC_FIELD.equals(getFieldType())) {
                valueString = ModelStringUtil.formatCsv(Long.valueOf(valueString));

            } else if (StreamAttributeFieldUse.DURATION_FIELD.equals(getFieldType())) {
                final long valueLong = Long.valueOf(valueString);

                valueString = ModelStringUtil.formatDurationString(valueLong) + " ("
                        + ModelStringUtil.formatCsv(valueLong) + " ms)";
            }
        } catch (final RuntimeException e) {
        }

        return valueString;
    }

    @Override
    @Transient
    public String getDisplayValue() {
        return getName();
    }

    public static class NameComparator implements Comparator<StreamAttributeKey>, Serializable {
        private static final long serialVersionUID = 8059670820914357799L;

        @Override
        public int compare(final StreamAttributeKey arg0, final StreamAttributeKey arg1) {
            return arg0.getName().compareTo(arg1.getName());
        }
    }
}
