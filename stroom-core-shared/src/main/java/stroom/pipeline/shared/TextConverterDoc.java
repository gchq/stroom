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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docstore.shared.Doc;
import stroom.entity.shared.HasData;
import stroom.util.shared.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "converterType"})
@XmlRootElement(name = "textConverter")
@XmlType(name = "TextConverterDoc", propOrder = {"type", "uuid", "name", "version", "createTime", "updateTime", "createUser", "updateUser", "description", "converterType"})
public class TextConverterDoc extends Doc implements HasData {
    public static final String ENTITY_TYPE = "TextConverter";

    private static final long serialVersionUID = 4519634323788508083L;

    @XmlElement(name = "description")
    private String description;
    @XmlTransient
    @JsonIgnore
    private String data;
    @XmlElement(name = "converterType")
    private TextConverterType converterType = TextConverterType.NONE;

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
//
//    /**
//     * @return generic UI drop down value
//     */
//    @Transient
//    @Override
//    public String getDisplayValue() {
//        return String.valueOf(getName());
//    }
//
//    @Override
//    public void copyFrom(final TextConverterDoc textConverter) {
//        this.setDescription(textConverter.description);
//        this.setPConverterType(textConverter.pConverterType);
//        this.setData(textConverter.data);
//
//        super.copyFrom(textConverter);
//    }
//
//    @Transient
//    @Override
//    public final String getType() {
//        return ENTITY_TYPE;
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        final TextConverterDoc that = (TextConverterDoc) o;
        return Objects.equals(description, that.description) &&
                Objects.equals(data, that.data) &&
                Objects.equals(converterType, that.converterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, data, converterType);
    }

    public enum TextConverterType implements HasDisplayValue {
        NONE("None"), DATA_SPLITTER("Data Splitter"), XML_FRAGMENT("XML Fragment");

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
