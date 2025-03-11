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

package stroom.legacy.model_6_1;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTime",
        "updateTime",
        "createUser",
        "updateUser",
        "description",
        "data",
        "imports"})
@XmlRootElement(name = "dictionary")
@XmlType(name = "DictionaryDoc", propOrder = {
        "type",
        "uuid",
        "name",
        "version",
        "createTime",
        "updateTime",
        "createUser",
        "updateUser",
        "description",
        "data",
        "imports"})
@Deprecated
public class OldDictionaryDoc extends Doc {

    public static final String ENTITY_TYPE = "Dictionary";

    private static final long serialVersionUID = -4208920620555926044L;

    @XmlElement(name = "description")
    private String description;
    @XmlElement(name = "data")
    private String data;
    @XmlElementWrapper(name = "imports")
    @XmlElement(name = "docRef")
    private List<DocRef> imports;

    public OldDictionaryDoc() {
        // Default constructor for GWT serialisation.
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
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final OldDictionaryDoc that = (OldDictionaryDoc) o;
        return Objects.equals(description, that.description) &&
               Objects.equals(data, that.data) &&
               Objects.equals(imports, that.imports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data, imports);
    }
}
