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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.HasDisplayValue;
import stroom.docstore.shared.Doc;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "classification", "encoding", "contextEncoding", "retentionDayAge", "reference", "streamType", "feedStatus"})
@JsonInclude(Include.NON_DEFAULT)
public class FeedDoc extends Doc {
    public static final String DOCUMENT_TYPE = "Feed";

    @JsonProperty
    private String description;
    @JsonProperty
    private String classification;
    @JsonProperty
    private String encoding;
    @JsonProperty
    private String contextEncoding;
    @JsonProperty
    private Integer retentionDayAge;
    @JsonProperty
    private boolean reference;
    @JsonProperty
    private String streamType;
    @JsonProperty
    private FeedStatus status;

    public FeedDoc() {
    }

    public FeedDoc(final String name) {
        setName(name);
    }

    @JsonCreator
    public FeedDoc(@JsonProperty("type") final String type,
                   @JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("version") final String version,
                   @JsonProperty("createTime") final Long createTime,
                   @JsonProperty("updateTime") final Long updateTime,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("description") final String description,
                   @JsonProperty("classification") final String classification,
                   @JsonProperty("encoding") final String encoding,
                   @JsonProperty("contextEncoding") final String contextEncoding,
                   @JsonProperty("retentionDayAge") final Integer retentionDayAge,
                   @JsonProperty("reference") final boolean reference,
                   @JsonProperty("streamType") final String streamType,
                   @JsonProperty("feedStatus") final FeedStatus status) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.classification = classification;
        this.encoding = encoding;
        this.contextEncoding = contextEncoding;
        this.retentionDayAge = retentionDayAge;
        this.reference = reference;
        this.streamType = streamType;
        this.status = status;
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
                streamType = StreamTypeNames.RAW_REFERENCE;
            } else {
                streamType = StreamTypeNames.RAW_EVENTS;
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
        if (status == null) {
            return FeedStatus.RECEIVE;
        }

        return status;
    }

    public void setStatus(final FeedStatus feedStatus) {
        this.status = feedStatus;
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
