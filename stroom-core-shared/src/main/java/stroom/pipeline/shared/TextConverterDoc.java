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

package stroom.pipeline.shared;

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
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

import java.util.Objects;

@Description(
        "A Text Converter Document defines the specification for splitting text data into records/fields using " +
                "[Data Splitter]({{< relref \"docs/user-guide/data-splitter\" >}}) or for wrapping fragment XML " +
                "with a {{< pipe-elm \"XMLFragmentParser\" >}} pipeline element.\n" +
                "The content of the Document is either XML in the `data-splitter:3` namespace or a fragment parser " +
                "specification (see " +
                "[Pipeline Recipes]" +
                "({{< relref \"docs/user-guide/pipelines/recipies#xml-fragments-to-normalised-xml\" >}})).\n" +
                "\n" +
                "This Document is used by the following pipeline elements:\n" +
                "\n" +
                "* {{< pipe-elm \"DSParser\" >}}\n" +
                "* {{< pipe-elm \"XMLFragmentParser\" >}}\n" +
                "* {{< pipe-elm \"CombinedParser\" >}}"
)
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
        "converterType"})
@JsonInclude(Include.NON_NULL)
public class TextConverterDoc extends AbstractDoc implements HasData {

    public static final String DOCUMENT_TYPE = "TextConverter";
    public static final SvgImage ICON = SvgImage.DOCUMENT_TEXT_CONVERTER;

    @JsonProperty
    private String description;
    @JsonProperty
    private String data;
    @JsonProperty
    private TextConverterType converterType;

    public TextConverterDoc() {
        converterType = TextConverterType.NONE;
    }

    @JsonCreator
    public TextConverterDoc(@JsonProperty("uuid") final String uuid,
                            @JsonProperty("name") final String name,
                            @JsonProperty("uniqueName") final String uniqueName,
                            @JsonProperty("version") final String version,
                            @JsonProperty("createTimeMs") final Long createTimeMs,
                            @JsonProperty("updateTimeMs") final Long updateTimeMs,
                            @JsonProperty("createUser") final String createUser,
                            @JsonProperty("updateUser") final String updateUser,
                            @JsonProperty("description") final String description,
                            @JsonProperty("data") final String data,
                            @JsonProperty("converterType") final TextConverterType converterType) {
        super(uuid, name, uniqueName, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.data = data;
        this.converterType = converterType;

        if (converterType == null) {
            this.converterType = TextConverterType.NONE;
        }
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

    @Override
    public String getData() {
        return data;
    }

    @Override
    public void setData(final String data) {
        this.data = data;
    }

    public TextConverterType getConverterType() {
        return converterType;
    }

    public void setConverterType(final TextConverterType converterType) {
        this.converterType = converterType;
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
        final TextConverterDoc that = (TextConverterDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(data, that.data) &&
                Objects.equals(converterType, that.converterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data, converterType);
    }


    // --------------------------------------------------------------------------------


    public enum TextConverterType implements HasDisplayValue {
        NONE("None"),
        DATA_SPLITTER("Data Splitter"),
        XML_FRAGMENT("XML Fragment");

        private final String displayValue;

        TextConverterType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
