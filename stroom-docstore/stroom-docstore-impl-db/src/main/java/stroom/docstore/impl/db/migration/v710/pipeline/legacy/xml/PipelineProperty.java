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

package stroom.docstore.impl.db.migration.v710.pipeline.legacy.xml;

import stroom.util.shared.CompareBuilder;
import stroom.util.shared.Copyable;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Objects;

/**
 * <p>
 * Java class for Property complex type.
 * <p>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * <pre>
 * &lt;complexType name="Property">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="element" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="value" type="{http://www.example.org/pipeline-data}Value" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Property", propOrder = {"element", "name", "value"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"element", "name", "value"})
public class PipelineProperty implements Comparable<PipelineProperty>, Copyable<PipelineProperty> {

    @XmlElement(required = true)
    @JsonProperty
    private String element;
    @XmlElement(required = true)
    @JsonProperty
    private String name;
    @JsonProperty
    private PipelinePropertyValue value;

    public PipelineProperty() {
    }

    public PipelineProperty(final String element, final String name) {
        this.element = element;
        this.name = name;
    }

    @JsonCreator
    public PipelineProperty(@JsonProperty("element") final String element,
                            @JsonProperty("name") final String name,
                            @JsonProperty("value") final PipelinePropertyValue value) {
        this.element = element;
        this.name = name;
        this.value = value;
    }

    public String getElement() {
        return element;
    }

    public void setElement(final String value) {
        this.element = value;
    }

    public String getName() {
        return name;
    }

    public void setName(final String value) {
        this.name = value;
    }

    public PipelinePropertyValue getValue() {
        return value;
    }

    public void setValue(final PipelinePropertyValue value) {
        this.value = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineProperty that = (PipelineProperty) o;
        return element.equals(that.element) &&
               name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, name);
    }

    @Override
    public int compareTo(final PipelineProperty o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(element, o.element);
        builder.append(name, o.name);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "element=" + element + ", name=" + name + ", value=" + value;
    }

    @Override
    public void copyFrom(final PipelineProperty other) {
        this.element = other.element;
        this.name = other.name;
        if (other.value == null) {
            this.value = null;
        } else {
            this.value = new PipelinePropertyValue();
            this.value.copyFrom(other.value);
        }
    }
}
