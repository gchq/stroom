/*
 * Copyright 2017 Crown Copyright
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

import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_SharedObject;
import stroom.core.db.migration._V07_00_00.entity.shared._V07_00_00_Copyable;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_EqualsBuilder;
import stroom.core.db.migration._V07_00_00.util.shared._V07_00_00_HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

/**
 * <p>
 * Java class for Value complex type.
 * <p>
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 * <p>
 * <pre>
 * &lt;complexType name="Value">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice>
 *         &lt;element name="string" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="integer" type="{http://www.w3.org/2001/XMLSchema}int"/>
 *         &lt;element name="boolean" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *         &lt;element name="entityReference" type="{http://www.example.org/pipeline-data}EntityReference"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Value", propOrder = {"string", "integer", "_long", "_boolean", "entity"})
public class _V07_00_00_PipelinePropertyValue
        implements _V07_00_00_SharedObject, _V07_00_00_Copyable<_V07_00_00_PipelinePropertyValue> {
    private static final long serialVersionUID = 1247638056133627349L;

    protected String string;
    protected Integer integer;
    @XmlElement(name = "long")
    protected Long _long;
    @XmlElement(name = "boolean")
    protected Boolean _boolean;
    @XmlElement(name = "entity")
    protected _V07_00_00_DocRef entity;

    public _V07_00_00_PipelinePropertyValue() {
        // Default constructor necessary for GWT serialisation.
    }

    public _V07_00_00_PipelinePropertyValue(final String string) {
        this.string = string;
    }

    public _V07_00_00_PipelinePropertyValue(final Integer integer) {
        this.integer = integer;
    }

    public _V07_00_00_PipelinePropertyValue(final Long _long) {
        this._long = _long;
    }

    public _V07_00_00_PipelinePropertyValue(final Boolean _boolean) {
        this._boolean = _boolean;
    }

    public _V07_00_00_PipelinePropertyValue(final _V07_00_00_DocRef entity) {
        this.entity = entity;
    }

    public String getString() {
        return string;
    }

    public void setString(final String value) {
        this.string = value;
    }

    public Integer getInteger() {
        return integer;
    }

    public void setInteger(final Integer value) {
        this.integer = value;
    }

    public Long getLong() {
        return _long;
    }

    public void setLong(final Long value) {
        this._long = value;
    }

    public Boolean isBoolean() {
        return _boolean;
    }

    public void setBoolean(final Boolean value) {
        this._boolean = value;
    }

    public _V07_00_00_DocRef getEntity() {
        return entity;
    }

    public void setEntity(final _V07_00_00_DocRef value) {
        this.entity = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof _V07_00_00_PipelinePropertyValue)) {
            return false;
        }

        final _V07_00_00_PipelinePropertyValue pipelinePropertyValue = (_V07_00_00_PipelinePropertyValue) o;
        final _V07_00_00_EqualsBuilder builder = new _V07_00_00_EqualsBuilder();
        builder.append(string, pipelinePropertyValue.string);
        builder.append(integer, pipelinePropertyValue.integer);
        builder.append(_long, pipelinePropertyValue._long);
        builder.append(_boolean, pipelinePropertyValue._boolean);
        builder.append(entity, pipelinePropertyValue.entity);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final _V07_00_00_HashCodeBuilder builder = new _V07_00_00_HashCodeBuilder();
        builder.append(string);
        builder.append(integer);
        builder.append(_long);
        builder.append(_boolean);
        builder.append(entity);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        if (string != null) {
            return string;
        } else if (integer != null) {
            return integer.toString();
        } else if (_long != null) {
            return _long.toString();
        } else if (_boolean != null) {
            return _boolean.toString();
        } else if (entity != null) {
            if (entity.getName() != null) {
                return entity.getName();
            }

            return entity.toString();
        }
        return "";
    }

    @Override
    public void copyFrom(final _V07_00_00_PipelinePropertyValue from) {
        this.string = from.string;
        this.integer = from.integer;
        this._long = from._long;
        this._boolean = from._boolean;
        this.entity = from.entity;
    }
}
