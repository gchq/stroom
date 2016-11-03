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

package stroom.feed.shared;

import stroom.entity.shared.DocumentEntity;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.entity.shared.SQLNameConstants;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.HasDisplayValue;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

/**
 * <p>
 * Class used hold feed definitions.
 * </p>
 */
@Entity
@Table(name = "FD", uniqueConstraints = @UniqueConstraint(columnNames = { SQLNameConstants.NAME }) )
public class Feed extends DocumentEntity {
    public static final String TABLE_NAME = SQLNameConstants.FEED;
    public static final String FOREIGN_KEY = FK_PREFIX + TABLE_NAME + ID_SUFFIX;
    public static final String FOREIGN_KEY_EVENT_FEED = FK_PREFIX + SQLNameConstants.EVENT + SEP + SQLNameConstants.FEED
            + ID_SUFFIX;
    public static final String FOREIGN_KEY_REFERENCE_FEED = FK_PREFIX + SQLNameConstants.REFERENCE + SEP
            + SQLNameConstants.FEED + ID_SUFFIX;
    public static final String CLASSIFICATION = SQLNameConstants.CLASSIFICATION;
    public static final String ENCODING = SQLNameConstants.ENCODING;
    public static final String CONTEXT_ENCODING = SQLNameConstants.CONTEXT + SEP + SQLNameConstants.ENCODING;
    public static final String STATUS = SQLNameConstants.STATUS;
    public static final String REFERENCE = SQLNameConstants.REFERENCE;
    public static final String RETENTION_DAY_AGE = SQLNameConstants.RETENTION + SEP + SQLNameConstants.DAY + SEP
            + SQLNameConstants.AGE;
    public static final String ENTITY_TYPE = "Feed";

    private static final long serialVersionUID = -5311839753276287820L;

    private String description;
    private String classification;
    private String encoding;
    private String contextEncoding;
    private Integer retentionDayAge;
    private boolean reference;
    private StreamType streamType;
    private byte pstatus = FeedStatus.RECEIVE.getPrimitiveValue();

    public Feed() {
        // Default constructor necessary for GWT serialisation.
    }

    public Feed(final String name) {
        setName(name);
    }

    public static final Feed createStub(final long pk) {
        final Feed feed = new Feed();
        feed.setStub(pk);
        return feed;
    }

    @Override
    public void prePersist() {
        // This became mandatory in 3.2 ... handle case when still null
        if (getStreamType() == null) {
            if (isReference()) {
                setStreamType(StreamType.RAW_REFERENCE);
            } else {
                setStreamType(StreamType.RAW_EVENTS);
            }
        }
        super.prePersist();
    }

    @Column(name = SQLNameConstants.DESCRIPTION)
    @Lob
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = StreamType.FOREIGN_KEY)
    public StreamType getStreamType() {
        return streamType;
    }

    public void setStreamType(final StreamType streamType) {
        this.streamType = streamType;
    }

    @Column(name = CLASSIFICATION)
    public String getClassification() {
        return classification;
    }

    public void setClassification(final String classification) {
        this.classification = classification;
    }

    @Column(name = ENCODING)
    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    @Column(name = STATUS, nullable = false)
    public byte getPstatus() {
        return pstatus;
    }

    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    @Transient
    public FeedStatus getStatus() {
        return FeedStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pstatus);
    }

    public void setStatus(final FeedStatus feedStatus) {
        this.pstatus = feedStatus.getPrimitiveValue();
    }

    @Transient
    public boolean isReceive() {
        return !FeedStatus.REJECT.equals(getStatus());
    }

    @Column(name = CONTEXT_ENCODING)
    public String getContextEncoding() {
        return contextEncoding;
    }

    public void setContextEncoding(final String contextEncoding) {
        this.contextEncoding = contextEncoding;
    }

    @Column(name = RETENTION_DAY_AGE, columnDefinition = INT_UNSIGNED)
    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public void setRetentionDayAge(final Integer retentionDayAge) {
        this.retentionDayAge = retentionDayAge;
    }

    @Column(name = REFERENCE, nullable = false)
    public boolean isReference() {
        return reference;
    }

    public void setReference(final boolean reference) {
        this.reference = reference;
    }

    @Transient
    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }

    public enum FeedStatus implements HasDisplayValue, HasPrimitiveValue {
        RECEIVE("Receive", 1), REJECT("Reject", 2), DROP("Drop", 3);

        public static final PrimitiveValueConverter<FeedStatus> PRIMITIVE_VALUE_CONVERTER = new PrimitiveValueConverter<>(
                FeedStatus.values());
        private final String displayValue;
        private final byte primitiveValue;

        FeedStatus(final String displayValue, final int primitiveValue) {
            this.displayValue = displayValue;
            this.primitiveValue = (byte) primitiveValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public byte getPrimitiveValue() {
            return primitiveValue;
        }
    }
}
