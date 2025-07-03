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

import stroom.docs.shared.Description;
import stroom.docstore.shared.Doc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

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
        "settings"
})
@JsonInclude(Include.NON_NULL)
public class PlanBDoc extends Doc {

    public static final String TYPE = "PlanB";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.PLAN_B_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final StateType stateType;
    @JsonProperty
    private final AbstractPlanBSettings settings;

    public PlanBDoc() {
        description = null;
        stateType = null;
        settings = null;
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
            @JsonProperty("settings") final AbstractPlanBSettings settings) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.stateType = stateType;
        this.settings = settings;
    }

    public String getDescription() {
        return description;
    }

    public StateType getStateType() {
        return stateType;
    }

    public AbstractPlanBSettings getSettings() {
        return settings;
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
        return Objects.equals(description, doc.description) &&
               stateType == doc.stateType &&
               Objects.equals(settings, doc.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                description,
                stateType,
                settings);
    }

    @Override
    public String toString() {
        return "StateDoc{" +
               "description='" + description + '\'' +
               ", stateType=" + stateType +
               ", settings=" + settings +
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
        private AbstractPlanBSettings settings;

        public Builder() {
        }

        public Builder(final PlanBDoc doc) {
            super(doc);
            this.description = doc.description;
            this.stateType = doc.stateType;
            this.settings = doc.settings;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder stateType(final StateType stateType) {
            this.stateType = stateType;
            return self();
        }

        public Builder settings(final AbstractPlanBSettings settings) {
            this.settings = settings;
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
                    settings);
        }
    }
}
