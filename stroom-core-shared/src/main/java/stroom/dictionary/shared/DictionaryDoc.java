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

package stroom.dictionary.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;
import stroom.docstore.shared.Doc;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "data", "imports"})
@JsonInclude(Include.NON_NULL)
public class DictionaryDoc extends Doc {
    public static final String ENTITY_TYPE = "Dictionary";

    @JsonProperty
    private String description;
    @JsonProperty
    private String data;
    @JsonProperty
    private List<DocRef> imports;

    public DictionaryDoc() {
    }

    @JsonCreator
    public DictionaryDoc(@JsonProperty("type") final String type,
                         @JsonProperty("uuid") final String uuid,
                         @JsonProperty("name") final String name,
                         @JsonProperty("version") final String version,
                         @JsonProperty("createTime") final Long createTime,
                         @JsonProperty("updateTime") final Long updateTime,
                         @JsonProperty("createUser") final String createUser,
                         @JsonProperty("updateUser") final String updateUser,
                         @JsonProperty("description") final String description,
                         @JsonProperty("data") final String data,
                         @JsonProperty("imports") final List<DocRef> imports) {
        super(type, uuid, name, version, createTime, updateTime, createUser, updateUser);
        this.description = description;
        this.data = data;
        this.imports = imports;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public List<DocRef> getImports() {
        return imports;
    }

    public void setImports(final List<DocRef> imports) {
        this.imports = imports;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final DictionaryDoc that = (DictionaryDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(data, that.data) &&
                Objects.equals(imports, that.imports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data, imports);
    }
}
