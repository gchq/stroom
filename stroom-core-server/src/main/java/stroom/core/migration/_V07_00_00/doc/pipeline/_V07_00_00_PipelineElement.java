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

package stroom.core.migration._V07_00_00.doc.pipeline;

import stroom.core.migration._V07_00_00.docref._V07_00_00_SharedObject;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

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
public class _V07_00_00_PipelineElement
        implements Comparable<_V07_00_00_PipelineElement>, _V07_00_00_SharedObject {
    private static final long serialVersionUID = -8891708244423055172L;

    @XmlTransient
    private _V07_00_00_PipelineElementType elementType;
    @XmlTransient
    private _V07_00_00_SourcePipeline source;

    @XmlElement(required = true)
    private String id;
    @XmlElement(required = true)
    private String type;

    public _V07_00_00_PipelineElement() {
    }

    public _V07_00_00_PipelineElement(final String id, final String type) {
        this.id = id;
        this.type = type;
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

    public _V07_00_00_SourcePipeline getSource() {
        return source;
    }

    public void setSource(final _V07_00_00_SourcePipeline source) {
        this.source = source;
    }

    @Override
    public int compareTo(final _V07_00_00_PipelineElement o) {
        return id.compareTo(o.id);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof _V07_00_00_PipelineElement)) {
            return false;
        }

        // Make sure types are the same if they have been set.
        final _V07_00_00_PipelineElement element = (_V07_00_00_PipelineElement) obj;
        if (type != null && element.type != null && !type.equals(element.type)) {
            return false;
        }

        return id.equals(element.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "id=" + id + ", type=" + type;
    }

    public _V07_00_00_PipelineElementType getElementType() {
        return elementType;
    }

    public void setElementType(final _V07_00_00_PipelineElementType elementType) {
        this.elementType = elementType;
    }
}
