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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;
import stroom.entity.shared.HasData;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "dependencies"})
public class ScriptDoc extends Doc implements HasData {
    private static final long serialVersionUID = 4519634323788508083L;

    public static final String DOCUMENT_TYPE = "Script";

    private String description;
    private List<DocRef> dependencies;

    @JsonIgnore
    private String data;

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
