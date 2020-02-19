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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.util.shared.HasData;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "dependencies", "data"})
@JsonInclude(Include.NON_DEFAULT)
public class ScriptDoc extends Doc implements HasData {
    public static final String DOCUMENT_TYPE = "Script";

    @JsonProperty
    private String description;
    @JsonProperty
    private List<DocRef> dependencies;
    @JsonProperty
    private String data;

    public ScriptDoc() {
    }

    @JsonCreator
    public ScriptDoc(@JsonProperty("type") final String type,
                     @JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTime") final Long createTime,
                     @JsonProperty("updateTime") final Long updateTime,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("description") final String description,
                     @JsonProperty("dependencies") final List<DocRef> dependencies,
                     @JsonProperty("data") final String data) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.dependencies = dependencies;
        this.data = data;
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
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
