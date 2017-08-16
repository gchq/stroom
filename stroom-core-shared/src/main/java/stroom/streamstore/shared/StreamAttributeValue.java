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

import stroom.entity.shared.BaseEntityBig;
import stroom.entity.shared.SQLNameConstants;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;

/**
 * List of all known stream types within the system.
 */
@Entity
@Table(name = "STRM_ATR_VAL")
public class StreamAttributeValue extends BaseEntityBig {
    public static final String TABLE_NAME = SQLNameConstants.STREAM + SEP + SQLNameConstants.ATTRIBUTE + SEP
            + SQLNameConstants.VALUE;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String VALUE_STRING = SQLNameConstants.VALUE + SEP + SQLNameConstants.STRING;
    public static final String VALUE_NUMBER = SQLNameConstants.VALUE + SEP + SQLNameConstants.NUMBER;
    public static final String STREAM_ID = SQLNameConstants.STREAM + ID_SUFFIX;
    public static final String CREATE_MS = SQLNameConstants.CREATE + SQLNameConstants.MS_SUFFIX;
    public static final String STREAM_ATTRIBUTE_KEY_ID = SQLNameConstants.STREAM + SEP + SQLNameConstants.ATTRIBUTE
            + SEP + SQLNameConstants.KEY + ID_SUFFIX;
    public static final String ENTITY_TYPE = "StreamAttributeValue";
    private static final long serialVersionUID = 8411148988526703262L;
    private long streamId;
    private long streamAttributeKeyId;
    private long createMs;
    private String valueString;
    private Long valueNumber;

    public StreamAttributeValue() {
    }

    public StreamAttributeValue(final Stream stream, final StreamAttributeKey streamAttributeKey, final String value) {
        this.streamId = stream.getId();
        this.streamAttributeKeyId = streamAttributeKey.getId();
        if (streamAttributeKey.getFieldType().isNumeric()) {
            this.valueNumber = Long.parseLong(value);
        } else {
            this.valueString = value;
        }
        this.createMs = System.currentTimeMillis();
    }

    @Transient
    @Override
    public String getType() {
        return ENTITY_TYPE;
    }

    @Column(name = VALUE_STRING, nullable = true)
    public String getValueString() {
        return valueString;
    }

    public void setValueString(String value) {
        this.valueString = value;
    }

    @Column(name = VALUE_NUMBER, nullable = true)
    public Long getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(Long value) {
        this.valueNumber = value;
    }

    @Column(name = CREATE_MS, columnDefinition = BIGINT_UNSIGNED, nullable = false)
    public long getCreateMs() {
        return createMs;
    }

    public void setCreateMs(long createMs) {
        this.createMs = createMs;
    }

    @Column(name = STREAM_ID, nullable = false)
    public long getStreamId() {
        return streamId;
    }

    public void setStreamId(long streamId) {
        this.streamId = streamId;
    }

    @Column(name = STREAM_ATTRIBUTE_KEY_ID, nullable = false, columnDefinition = NORMAL_KEY_DEF)
    public long getStreamAttributeKeyId() {
        return streamAttributeKeyId;
    }

    public void setStreamAttributeKeyId(long streamAttributeKeyId) {
        this.streamAttributeKeyId = streamAttributeKeyId;
    }
}
