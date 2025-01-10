/*
 * Copyright 2017 Crown Copyright
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

package stroom.planb.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.time.TimeUnit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description("Defines a place to store state")
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
        "condense",
        "condenseAge",
        "condenseTimeUnit",
        "retainForever",
        "retainAge",
        "retainTimeUnit"
})
@JsonInclude(Include.NON_NULL)
public class PlanBDoc extends Doc {

    public static final String TYPE = "PlanB";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PLAN_B_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private StateType stateType;
    @JsonProperty
    private boolean condense;
    @JsonProperty
    private int condenseAge;
    @JsonProperty
    private TimeUnit condenseTimeUnit;
    @JsonProperty
    private boolean retainForever;
    @JsonProperty
    private int retainAge;
    @JsonProperty
    private TimeUnit retainTimeUnit;

    public PlanBDoc() {
    }

    @JsonCreator
    public PlanBDoc(
            @JsonProperty("type") final String type,
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("stateType") final StateType stateType,
            @JsonProperty("condense") final boolean condense,
            @JsonProperty("condenseAge") final int condenseAge,
            @JsonProperty("condenseTimeUnit") final TimeUnit condenseTimeUnit,
            @JsonProperty("retainForever") final boolean retainForever,
            @JsonProperty("retainAge") final int retainAge,
            @JsonProperty("retainTimeUnit") final TimeUnit retainTimeUnit) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.stateType = stateType;
        this.condense = condense;
        this.condenseAge = condenseAge;
        this.condenseTimeUnit = condenseTimeUnit;
        this.retainForever = retainForever;
        this.retainAge = retainAge;
        this.retainTimeUnit = retainTimeUnit;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public StateType getStateType() {
        return stateType;
    }

    public void setStateType(final StateType stateType) {
        this.stateType = stateType;
    }

    public boolean isCondense() {
        return condense;
    }

    public void setCondense(final boolean condense) {
        this.condense = condense;
    }

    public int getCondenseAge() {
        return condenseAge;
    }

    public void setCondenseAge(final int condenseAge) {
        this.condenseAge = condenseAge;
    }

    public TimeUnit getCondenseTimeUnit() {
        return condenseTimeUnit;
    }

    public void setCondenseTimeUnit(final TimeUnit condenseTimeUnit) {
        this.condenseTimeUnit = condenseTimeUnit;
    }

    public boolean isRetainForever() {
        return retainForever;
    }

    public void setRetainForever(final boolean retainForever) {
        this.retainForever = retainForever;
    }

    public int getRetainAge() {
        return retainAge;
    }

    public void setRetainAge(final int retainAge) {
        this.retainAge = retainAge;
    }

    public TimeUnit getRetainTimeUnit() {
        return retainTimeUnit;
    }

    public void setRetainTimeUnit(final TimeUnit retainTimeUnit) {
        this.retainTimeUnit = retainTimeUnit;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PlanBDoc doc = (PlanBDoc) o;
        return condense == doc.condense &&
               condenseAge == doc.condenseAge &&
               retainForever == doc.retainForever &&
               retainAge == doc.retainAge &&
               Objects.equals(description, doc.description) &&
               stateType == doc.stateType &&
               condenseTimeUnit == doc.condenseTimeUnit &&
               retainTimeUnit == doc.retainTimeUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                stateType,
                condense,
                condenseAge,
                condenseTimeUnit,
                retainForever,
                retainAge,
                retainTimeUnit);
    }

    @Override
    public String toString() {
        return "StateDoc{" +
               "description='" + description + '\'' +
               ", stateType=" + stateType +
               ", condense=" + condense +
               ", condenseAge=" + condenseAge +
               ", condenseTimeUnit=" + condenseTimeUnit +
               ", retainForever=" + retainForever +
               ", retainAge=" + retainAge +
               ", retainTimeUnit=" + retainTimeUnit +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<PlanBDoc, PlanBDoc.Builder> {

        private String description;
        private StateType stateType;
        private boolean condense;
        private int condenseAge;
        private TimeUnit condenseTimeUnit;
        private boolean retainForever;
        private int retainAge;
        private TimeUnit retainTimeUnit;

        public Builder() {
        }

        public Builder(final PlanBDoc doc) {
            super(doc);
            this.description = doc.description;
            this.stateType = doc.stateType;
            this.condense = doc.condense;
            this.condenseAge = doc.condenseAge;
            this.condenseTimeUnit = doc.condenseTimeUnit;
            this.retainForever = doc.retainForever;
            this.retainAge = doc.retainAge;
            this.retainTimeUnit = doc.retainTimeUnit;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder stateType(final StateType stateType) {
            this.stateType = stateType;
            return self();
        }

        public Builder condense(final boolean condense) {
            this.condense = condense;
            return self();
        }

        public Builder condenseAge(final int condenseAge) {
            this.condenseAge = condenseAge;
            return self();
        }

        public Builder condenseTimeUnit(final TimeUnit condenseTimeUnit) {
            this.condenseTimeUnit = condenseTimeUnit;
            return self();
        }

        public Builder retainForever(final boolean retainForever) {
            this.retainForever = retainForever;
            return self();
        }

        public Builder retainAge(final int retainAge) {
            this.retainAge = retainAge;
            return self();
        }

        public Builder retainTimeUnit(final TimeUnit retainTimeUnit) {
            this.retainTimeUnit = retainTimeUnit;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public PlanBDoc build() {
            return new PlanBDoc(
                    type,
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    stateType,
                    condense,
                    condenseAge,
                    condenseTimeUnit,
                    retainForever,
                    retainAge,
                    retainTimeUnit);
        }
    }
}