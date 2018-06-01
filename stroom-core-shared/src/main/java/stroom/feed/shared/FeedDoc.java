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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.HasDisplayValue;
import stroom.docstore.shared.Doc;
import stroom.entity.shared.HasPrimitiveValue;
import stroom.entity.shared.PrimitiveValueConverter;
import stroom.streamstore.shared.StreamTypeEntity;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "classification", "encoding", "contextEncoding", "retentionDayAge", "reference", "streamType", "feedStatus"})
public class FeedDoc extends Doc {
    public static final String DOCUMENT_TYPE = "Feed";

    private static final long serialVersionUID = -5311839753276287820L;

    private String description;
    private String classification;
    private String encoding;
    private String contextEncoding;
    private Integer retentionDayAge;
    private boolean reference;
    private String streamType;
    private FeedStatus feedStatus;

    public FeedDoc() {
        // Default constructor necessary for GWT serialisation.
    }

    public FeedDoc(final String name) {
        setName(name);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getStreamType() {
        if (streamType == null) {
            if (reference) {
                streamType = StreamTypeEntity.RAW_REFERENCE.getName();
            } else {
                streamType = StreamTypeEntity.RAW_EVENTS.getName();
            }
        }

        return streamType;
    }

    public void setStreamType(final String streamType) {
        this.streamType = streamType;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(final String classification) {
        this.classification = classification;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(final String encoding) {
        this.encoding = encoding;
    }

    public FeedStatus getStatus() {
        if (feedStatus == null) {
            return FeedStatus.RECEIVE;
        }

        return feedStatus;
    }

    public void setStatus(final FeedStatus feedStatus) {
        this.feedStatus = feedStatus;
    }

    @JsonIgnore
    public boolean isReceive() {
        return !FeedStatus.REJECT.equals(getStatus());
    }

    public String getContextEncoding() {
        return contextEncoding;
    }

    public void setContextEncoding(final String contextEncoding) {
        this.contextEncoding = contextEncoding;
    }

    public Integer getRetentionDayAge() {
        return retentionDayAge;
    }

    public void setRetentionDayAge(final Integer retentionDayAge) {
        this.retentionDayAge = retentionDayAge;
    }

    public boolean isReference() {
        return reference;
    }

    public void setReference(final boolean reference) {
        this.reference = reference;
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
