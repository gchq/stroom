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

package stroom.script.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.util.shared.HasData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@Description(
        "Contains a Javascript script that is used as the source for a " +
        "[visualisation]({{< relref \"#visualisation\" >}}) Document.\n" +
        "Scripts can have dependencies on other Script Documents, e.g. to allow re-use of common code.")
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
        "dependencies",
        "data"})
@JsonInclude(Include.NON_NULL)
public class ScriptDoc extends AbstractDoc implements HasData {

    public static final String TYPE = "Script";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.SCRIPT_DOCUMENT_TYPE;

    @JsonProperty
    private String description;
    @JsonProperty
    private List<DocRef> dependencies;
    @JsonProperty
    private String data;

    @JsonCreator
    public ScriptDoc(@JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("description") final String description,
                     @JsonProperty("dependencies") final List<DocRef> dependencies,
                     @JsonProperty("data") final String data) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dependencies = dependencies;
        this.data = data;
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

    public List<DocRef> getDependencies() {
        return dependencies;
    }

    public void setDependencies(final List<DocRef> dependencies) {
        this.dependencies = dependencies;
    }

    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
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
        final ScriptDoc scriptDoc = (ScriptDoc) o;
        return Objects.equals(description, scriptDoc.description) &&
               Objects.equals(dependencies, scriptDoc.dependencies) &&
               Objects.equals(data, scriptDoc.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, dependencies, data);
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends AbstractDoc.AbstractBuilder<ScriptDoc, ScriptDoc.Builder> {

        private String description;
        private List<DocRef> dependencies;
        private String data;

        private Builder() {
        }

        private Builder(final ScriptDoc scriptDoc) {
            super(scriptDoc);
            this.description = scriptDoc.description;
            this.dependencies = scriptDoc.dependencies;
            this.data = scriptDoc.data;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder dependencies(final List<DocRef> dependencies) {
            this.dependencies = dependencies;
            return self();
        }

        public Builder data(final String data) {
            this.data = data;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public ScriptDoc build() {
            return new ScriptDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    dependencies,
                    data);
        }
    }
}
