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

package stroom.script.shared;

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.HasData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        "uuid",
        "name",
        "uniqueName",
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

    public static final String DOCUMENT_TYPE = "Script";
    public static final SvgImage ICON = SvgImage.DOCUMENT_SCRIPT;

    @JsonProperty
    private String description;
    @JsonProperty
    private List<DocRef> dependencies;
    @JsonProperty
    private String data;

    public ScriptDoc() {
    }

    @JsonCreator
    public ScriptDoc(@JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("uniqueName") final String uniqueName,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("description") final String description,
                     @JsonProperty("dependencies") final List<DocRef> dependencies,
                     @JsonProperty("data") final String data) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.dependencies = dependencies;
        this.data = data;
    }

    @JsonIgnore
    @Override
    public final String getType() {
        return DOCUMENT_TYPE;
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
}
