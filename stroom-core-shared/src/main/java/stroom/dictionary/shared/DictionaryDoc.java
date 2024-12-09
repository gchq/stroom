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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.AbstractDoc;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@Description(
        "A Dictionary is essentially a list of 'words', where each 'word' is separated by a new line.\n" +
                "Dictionaries can be used in filter expressions, i.e. `IN DICTIONARY`.\n" +
                "They allow for the reuse of the same set of values across many search expressions.\n" +
                "Dictionaries also support inheritance so one dictionary can import the contents of other " +
                "dictionaries.")
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
        "data",
        "imports"})
@JsonInclude(Include.NON_NULL)
public class DictionaryDoc extends AbstractDoc {

    public static final String DOCUMENT_TYPE = "Dictionary";
    public static final SvgImage ICON = SvgImage.DOCUMENT_DICTIONARY;

    @JsonProperty
    private String description;
    @JsonProperty
    private String data;
    @JsonProperty
    private List<DocRef> imports;

    public DictionaryDoc() {
    }

    @JsonCreator
    public DictionaryDoc(@JsonProperty("uuid") final String uuid,
                         @JsonProperty("name") final String name,
                         @JsonProperty("uniqueName") final String uniqueName,
                         @JsonProperty("version") final String version,
                         @JsonProperty("createTimeMs") final Long createTimeMs,
                         @JsonProperty("updateTimeMs") final Long updateTimeMs,
                         @JsonProperty("createUser") final String createUser,
                         @JsonProperty("updateUser") final String updateUser,
                         @JsonProperty("description") final String description,
                         @JsonProperty("data") final String data,
                         @JsonProperty("imports") final List<DocRef> imports) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.data = data;
        this.imports = imports;
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
