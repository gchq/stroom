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

package stroom.core.db.migration._V07_00_00.doc.dictionary;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.db.migration._V07_00_00.docstore.shared._V07_00_00_Doc;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "imports"})
public class _V07_00_00_DictionaryDoc extends _V07_00_00_Doc {
    private static final long serialVersionUID = -4208920620555926044L;

    public static final String DOCUMENT_TYPE = "Dictionary";

    private String description;
    @JsonIgnore
    private String data;
    private List<_V07_00_00_DocRef> imports;

    public _V07_00_00_DictionaryDoc() {
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

    public List<_V07_00_00_DocRef> getImports() {
        return imports;
    }

    public void setImports(final List<_V07_00_00_DocRef> imports) {
        this.imports = imports;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final _V07_00_00_DictionaryDoc that = (_V07_00_00_DictionaryDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(data, that.data) &&
                Objects.equals(imports, that.imports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data, imports);
    }
}
