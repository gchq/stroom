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

package stroom.core.db.migration._V07_00_00.doc.feed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;
import stroom.core.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_HasPrimitiveValue;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_PrimitiveValueConverter;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "classification", "encoding", "contextEncoding", "retentionDayAge", "reference", "streamType", "feedStatus"})
public class _V07_00_00_FeedDoc extends _V07_00_00_Doc {
    public static final String DOCUMENT_TYPE = "Feed";

    private static final long serialVersionUID = -5311839753276287820L;

    private String description;
    private String classification;
    private String encoding;
    private String contextEncoding;
    private Integer retentionDayAge;
    private boolean reference;
    private String streamType;
    private _V07_00_00_FeedStatus feedStatus;

    public _V07_00_00_FeedDoc() {
    }

    public _V07_00_00_FeedDoc(final String name) {
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
                streamType = _V07_00_00_StreamTypeNames.RAW_REFERENCE;
            } else {
                streamType = _V07_00_00_StreamTypeNames.RAW_EVENTS;
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

    public _V07_00_00_FeedStatus getStatus() {
        if (feedStatus == null) {
            return _V07_00_00_FeedStatus.RECEIVE;
        }

        return feedStatus;
    }

    public void setStatus(final _V07_00_00_FeedStatus feedStatus) {
        this.feedStatus = feedStatus;
    }

    @JsonIgnore
    public boolean isReceive() {
        return !_V07_00_00_FeedStatus.REJECT.equals(getStatus());
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

    public enum _V07_00_00_FeedStatus implements _V07_00_00_HasDisplayValue, _V07_00_00_HasPrimitiveValue {
        RECEIVE("Receive", 1), REJECT("Reject", 2), DROP("Drop", 3);

        public static final _V07_00_00_PrimitiveValueConverter<_V07_00_00_FeedStatus> PRIMITIVE_VALUE_CONVERTER = new _V07_00_00_PrimitiveValueConverter<>(
                _V07_00_00_FeedStatus.values());
        private final String displayValue;
        private final byte primitiveValue;

        _V07_00_00_FeedStatus(final String displayValue, final int primitiveValue) {
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

    private static class _V07_00_00_StreamTypeNames {
        /**
         * Saved raw version for the archive.
         */
        public static final String RAW_EVENTS = "Raw Events";
        /**
         * Saved raw version for the archive.
         */
        public static final String RAW_REFERENCE = "Raw Reference";
        /**
         * Processed events Data files.
         */
        public static final String EVENTS = "Events";
        /**
         * Processed reference Data files.
         */
        public static final String REFERENCE = "Reference";
        /**
         * Processed Data files conforming to the Records XMLSchema.
         */
        public static final String RECORDS = "Records";
        /**
         * Meta stream data
         */
        public static final String META = "Meta Data";
        /**
         * Processed events Data files.
         */
        public static final String ERROR = "Error";
        /**
         * Context file for use with an events file.
         */
        public static final String CONTEXT = "Context";
    }
}
