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

package stroom.core.db.migration._V07_00_00.doc.pipeline;

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_Copyable;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_CompareBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

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
public class _V07_00_00_PipelineProperty
        implements Comparable<_V07_00_00_PipelineProperty>, _V07_00_00_SharedObject, _V07_00_00_Copyable<_V07_00_00_PipelineProperty> {
    private static final long serialVersionUID = -4634337435985272473L;

    @XmlTransient
    private _V07_00_00_PipelinePropertyType propertyType;
    @XmlTransient
    private _V07_00_00_SourcePipeline source;

    @XmlElement(required = true)
    private String element;
    @XmlElement(required = true)
    private String name;
    private _V07_00_00_PipelinePropertyValue value;

    public _V07_00_00_PipelineProperty() {
    }

    public _V07_00_00_PipelineProperty(final String element, final String name) {
        this.element = element;
        this.name = name;
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

    public _V07_00_00_PipelinePropertyValue getValue() {
        return value;
    }

    public void setValue(final _V07_00_00_PipelinePropertyValue value) {
        this.value = value;
    }

    public _V07_00_00_SourcePipeline getSource() {
        return source;
    }

    public void setSource(final _V07_00_00_SourcePipeline source) {
        this.source = source;
    }

    @Override
    public int hashCode() {
        final _V07_00_00_HashCodeBuilder builder = new _V07_00_00_HashCodeBuilder();
        builder.append(element);
        builder.append(name);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof _V07_00_00_PipelineProperty)) {
            return false;
        }

        final _V07_00_00_PipelineProperty property = (_V07_00_00_PipelineProperty) o;
        final _V07_00_00_EqualsBuilder builder = new _V07_00_00_EqualsBuilder();
        builder.append(element, property.element);
        builder.append(name, property.name);
        return builder.isEquals();
    }

    @Override
    public int compareTo(final _V07_00_00_PipelineProperty o) {
        final _V07_00_00_CompareBuilder builder = new _V07_00_00_CompareBuilder();
        builder.append(element, o.element);
        builder.append(name, o.name);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "element=" + element + ", name=" + name + ", value=" + value;
    }

    public _V07_00_00_PipelinePropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(final _V07_00_00_PipelinePropertyType propertyType) {
        this.propertyType = propertyType;
    }

    @Override
    public void copyFrom(final _V07_00_00_PipelineProperty other) {
        this.propertyType = other.propertyType;
        this.source = other.source;
        this.element = other.element;
        this.name = other.name;
        if (other.value == null) {
            this.value = null;
        } else {
            this.value = new _V07_00_00_PipelinePropertyValue();
            this.value.copyFrom(other.value);
        }
    }
}
