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

package stroom.visualisation.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@Description(
        "Defines a data visualisation that can be used in a [Dashboard]({{< relref \"#dashboard\" >}}) " +
        "Document.\n" +
        "The Visualisation defines the settings that will be available to the user when it is embedded in a " +
        "Dashboard.\n" +
        "A Visualisation is dependent on a [Script]({{< relref \"#script\" >}}) Document for the Javascript " +
        "code to make it work.")
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
        "functionName",
        "scriptRef",
        "settings"})
@JsonInclude(Include.NON_NULL)
public class VisualisationDoc extends AbstractDoc {

    public static final String TYPE = "Visualisation";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.VISUALISATION_DOCUMENT_TYPE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final String functionName;
    @JsonProperty
    private final DocRef scriptRef;
    @JsonProperty
    private final String settings;

    @JsonCreator
    public VisualisationDoc(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("description") final String description,
                            @JsonProperty("functionName") final String functionName,
                            @JsonProperty("scriptRef") final DocRef scriptRef,
                            @JsonProperty("settings") final String settings) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.functionName = functionName;
        this.scriptRef = scriptRef;
        this.settings = settings;
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

    public String getFunctionName() {
        return functionName;
    }

    public DocRef getScriptRef() {
        return scriptRef;
    }

    public String getSettings() {
        return settings;
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
        final VisualisationDoc that = (VisualisationDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(functionName, that.functionName) &&
               Objects.equals(scriptRef, that.scriptRef) &&
               Objects.equals(settings, that.settings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, functionName, scriptRef, settings);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractBuilder<VisualisationDoc, Builder> {

        private String description;
        private String functionName;
        private DocRef scriptRef;
        private String settings;

        private Builder() {
        }

        private Builder(final VisualisationDoc visualisationDoc) {
            super(visualisationDoc);
            this.description = visualisationDoc.description;
            this.functionName = visualisationDoc.functionName;
            this.scriptRef = visualisationDoc.scriptRef;
            this.settings = visualisationDoc.settings;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder functionName(final String functionName) {
            this.functionName = functionName;
            return self();
        }

        public Builder scriptRef(final DocRef scriptRef) {
            this.scriptRef = scriptRef;
            return self();
        }

        public Builder settings(final String settings) {
            this.settings = settings;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public VisualisationDoc build() {
            return new VisualisationDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    functionName,
                    scriptRef,
                    settings);
        }
    }
}
