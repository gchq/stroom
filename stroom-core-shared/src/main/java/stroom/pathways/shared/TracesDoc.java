/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.planb.shared.AbstractPlanBDoc;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
        "stateType",
        "settings"
})
@JsonInclude(Include.NON_NULL)
public class TracesDoc extends AbstractPlanBDoc {

    public static final String TYPE = "Traces";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.TRACES_DOCUMENT_TYPE;

    @JsonProperty("hasSharedFileStoreData")
    @JsonInclude(Include.NON_NULL)
    private final Boolean hasSharedFileStoreData;

    @JsonCreator
    public TracesDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("stateType") final StateType stateType,
            @JsonProperty("settings") final AbstractPlanBSettings settings,
            @JsonProperty("hasSharedFileStoreData") final Boolean hasSharedFileStoreData) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser,
                description, stateType == null ? StateType.TRACE : stateType, settings);
        if (settings != null && !(settings instanceof TraceSettings)) {
            throw new IllegalArgumentException(
                    "TracesDoc requires TraceSettings, got: " +
                    settings.getClass().getSimpleName());
        }
        this.hasSharedFileStoreData = hasSharedFileStoreData;
    }

    public boolean hasSharedFileStoreData() {
        return Boolean.TRUE.equals(hasSharedFileStoreData);
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
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return "TracesDoc{" +
               "type='" + getType() + '\'' +
               ", uuid='" + getUuid() + '\'' +
               ", name='" + getName() + '\'' +
               ", description='" + getDescription() + '\'' +
               ", stateType=" + getStateType() +
               ", settings=" + getSettings() +
               ", shardCount=" + getShardCount() +
               ", sharedPath='" + getSharedPath() + '\'' +
               '}';
    }

    public Builder copyTraces() {
        return new Builder(this);
    }

    public static Builder tracesBuilder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractPlanBDoc.AbstractBuilder<TracesDoc, Builder> {

        // hasSharedFileStoreData is intentionally not copied — it is always recomputed server-side.
        private Boolean hasSharedFileStoreData;

        private Builder() {
            this.stateType = StateType.TRACE;
        }

        private Builder(final TracesDoc tracesDoc) {
            super(tracesDoc);
        }

        public Builder hasSharedFileStoreData(final Boolean hasSharedFileStoreData) {
            this.hasSharedFileStoreData = hasSharedFileStoreData;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TracesDoc build() {
            return new TracesDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    stateType,
                    settings,
                    hasSharedFileStoreData);
        }
    }
}
