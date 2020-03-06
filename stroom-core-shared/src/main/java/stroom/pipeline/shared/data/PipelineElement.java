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

package stroom.pipeline.shared.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import java.util.Objects;

/**
 * <p>
 * Java class for Element complex type.
 * <p>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * <pre>
 * &lt;complexType name="Element">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="type" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Element", propOrder = {"id", "type"})
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"elementType", "source", "id", "type"})
public class PipelineElement implements Comparable<PipelineElement> {
    @XmlTransient
    @JsonProperty
    private PipelineElementType elementType;

    @XmlElement(required = true)
    @JsonProperty
    private String id;
    @XmlElement(required = true)
    @JsonProperty
    private String type;

    public PipelineElement() {
    }

    public PipelineElement(final String id, final String type) {
        this.id = id;
        this.type = type;
    }

    @JsonCreator
    public PipelineElement(@JsonProperty("elementType") final PipelineElementType elementType,
                           @JsonProperty("id") final String id,
                           @JsonProperty("type") final String type) {
        this.elementType = elementType;
        this.id = id;
        this.type = type;
    }

    public PipelineElementType getElementType() {
        return elementType;
    }

    public void setElementType(final PipelineElementType elementType) {
        this.elementType = elementType;
    }

    public String getId() {
        return id;
    }

    public void setId(final String value) {
        this.id = value;
    }

    public String getType() {
        return type;
    }

    public void setType(final String value) {
        this.type = value;
    }

    @Override
    public int compareTo(final PipelineElement o) {
        return id.compareTo(o.id);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PipelineElement that = (PipelineElement) o;
        return id.equals(that.id) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return "id=" + id + ", type=" + type;
    }
}
