/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.docref.DocRef.TypedBuilder;
import stroom.docref.HasDisplayValue;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@Description(
        "The {{< glossary \"Feed\" >}} is Stroom's way of compartmentalising data that has been ingested or " +
        "created by a [Pipeline]({{< relref \"#pipeline\" >}}).\n" +
        "Ingested data must specify the Feed that is it destined for.\n\n" +
        "The Feed Document defines the character encoding for the data in the Feed, the type of data that " +
        "will be received into it (e.g. `Raw Events`) and optionally a Volume Group to use for " +
        "data storage.\n" +
        "The Feed Document can also control the ingest of data using its `Feed Status` property and " +
        "be used for viewing data that belonging to that feed.")
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "classification",
        "encoding",
        "contextEncoding",
        "retentionDayAge",
        "reference",
        "streamType",
        "dataFormat",
        "contextFormat",
        "schema",
        "schemaVersion",
        "status",
        "volumeGroup"})
@JsonInclude(Include.NON_NULL)
public class FeedDoc extends AbstractDoc {

    public static final String TYPE = "Feed";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.FEED_DOCUMENT_TYPE;

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
    private String dataFormat;
    @JsonProperty
    private String contextFormat;
    @JsonProperty
    private String schema;
    @JsonProperty
    private String schemaVersion;
    @JsonProperty
    private FeedStatus status;
    @JsonProperty
    private String volumeGroup;

    @JsonCreator
    public FeedDoc(@JsonProperty("uuid") final String uuid,
                   @JsonProperty("name") final String name,
                   @JsonProperty("version") final String version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("description") final String description,
                   @JsonProperty("classification") final String classification,
                   @JsonProperty("encoding") final String encoding,
                   @JsonProperty("contextEncoding") final String contextEncoding,
                   @JsonProperty("retentionDayAge") final Integer retentionDayAge,
                   @JsonProperty("reference") final boolean reference,
                   @JsonProperty("streamType") final String streamType,
                   @JsonProperty("dataFormat") final String dataFormat,
                   @JsonProperty("contextFormat") final String contextFormat,
                   @JsonProperty("schema") final String schema,
                   @JsonProperty("schemaVersion") final String schemaVersion,
                   @JsonProperty("status") final FeedStatus status,
                   @JsonProperty("volumeGroup") final String volumeGroup) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.classification = classification;
        this.encoding = encoding;
        this.contextEncoding = contextEncoding;
        this.retentionDayAge = retentionDayAge;
        this.reference = reference;
        this.streamType = streamType;
        this.status = status;
        this.dataFormat = dataFormat;
        this.contextFormat = contextFormat;
        this.schema = schema;
        this.schemaVersion = schemaVersion;
        this.volumeGroup = volumeGroup;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
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

    public String getDataFormat() {
        return dataFormat;
    }

    public void setDataFormat(final String dataFormat) {
        this.dataFormat = dataFormat;
    }

    public String getContextFormat() {
        return contextFormat;
    }

    public void setContextFormat(final String contextFormat) {
        this.contextFormat = contextFormat;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(final String schema) {
        this.schema = schema;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(final String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getVolumeGroup() {
        return volumeGroup;
    }

    public void setVolumeGroup(final String volumeGroup) {
        this.volumeGroup = volumeGroup;
    }

    public enum FeedStatus implements HasDisplayValue, HasPrimitiveValue {
        RECEIVE("Receive", 1),
        REJECT("Reject", 2),
        DROP("Drop", 3);

        public static final PrimitiveValueConverter<FeedStatus> PRIMITIVE_VALUE_CONVERTER =
                PrimitiveValueConverter.create(FeedStatus.class, FeedStatus.values());
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

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractBuilder<FeedDoc, FeedDoc.Builder> {

        private String description;
        private String classification;
        private String encoding;
        private String contextEncoding;
        private Integer retentionDayAge;
        private boolean reference;
        private String streamType;
        private String dataFormat;
        private String contextFormat;
        private String schema;
        private String schemaVersion;
        private FeedStatus status;
        private String volumeGroup;

        private Builder() {
        }

        private Builder(final FeedDoc copy) {
            super(copy);
            this.description = copy.getDescription();
            this.classification = copy.getClassification();
            this.encoding = copy.getEncoding();
            this.contextEncoding = copy.getContextEncoding();
            this.retentionDayAge = copy.getRetentionDayAge();
            this.reference = copy.isReference();
            this.streamType = copy.getStreamType();
            this.dataFormat = copy.getDataFormat();
            this.contextFormat = copy.getContextFormat();
            this.schema = copy.getSchema();
            this.schemaVersion = copy.getSchemaVersion();
            this.status = copy.getStatus();
            this.volumeGroup = copy.getVolumeGroup();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder classification(final String classification) {
            this.classification = classification;
            return self();
        }

        public Builder encoding(final String encoding) {
            this.encoding = encoding;
            return self();
        }

        public Builder contextEncoding(final String contextEncoding) {
            this.contextEncoding = contextEncoding;
            return self();
        }

        public Builder retentionDayAge(final Integer retentionDayAge) {
            this.retentionDayAge = retentionDayAge;
            return self();
        }

        public Builder reference(final boolean reference) {
            this.reference = reference;
            return self();
        }

        public Builder streamType(final String streamType) {
            this.streamType = streamType;
            return self();
        }

        public Builder dataFormat(final String dataFormat) {
            this.dataFormat = dataFormat;
            return self();
        }

        public Builder contextFormat(final String contextFormat) {
            this.contextFormat = contextFormat;
            return self();
        }

        public Builder schema(final String schema) {
            this.schema = schema;
            return self();
        }

        public Builder schemaVersion(final String schemaVersion) {
            this.schemaVersion = schemaVersion;
            return self();
        }

        public Builder status(final FeedStatus status) {
            this.status = status;
            return self();
        }

        public Builder volumeGroup(final String volumeGroup) {
            this.volumeGroup = volumeGroup;
            return self();
        }

        public FeedDoc build() {
            return new FeedDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    classification,
                    encoding,
                    contextEncoding,
                    retentionDayAge,
                    reference,
                    streamType,
                    dataFormat,
                    contextFormat,
                    schema,
                    schemaVersion,
                    status,
                    volumeGroup);
        }
    }
}
