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
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"string", "integer", "long", "boolean", "entity"})
@SuppressWarnings({"checkstyle:membername", "checkstyle:parametername"})
public class PipelinePropertyValue implements Copyable<PipelinePropertyValue> {

    @JsonProperty("string")
    protected String string;
    @JsonProperty("integer")
    protected Integer integer;
    @JsonProperty("long")
    @XmlElement(name = "long")
    protected Long _long;
    @JsonProperty("boolean")
    @XmlElement(name = "boolean")
    protected Boolean _boolean;
    @JsonProperty("entity")
    @XmlElement(name = "entity")
    protected DocRef entity;

    public PipelinePropertyValue() {
    }

    @JsonCreator
    public PipelinePropertyValue(@JsonProperty("string") final String string,
                                 @JsonProperty("integer") final Integer integer,
                                 @JsonProperty("long") final Long _long,
                                 @JsonProperty("boolean") final Boolean _boolean,
                                 @JsonProperty("entity") final DocRef entity) {
        this.string = string;
        this.integer = integer;
        this._long = _long;
        this._boolean = _boolean;
        this.entity = entity;
    }

    public PipelinePropertyValue(final String string) {
        this.string = string;
    }

    public PipelinePropertyValue(final Integer integer) {
        this.integer = integer;
    }

    public PipelinePropertyValue(final Long _long) {
        this._long = _long;
    }

    public PipelinePropertyValue(final Boolean _boolean) {
        this._boolean = _boolean;
    }

    public PipelinePropertyValue(final DocRef entity) {
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

    public Boolean getBoolean() {
        return _boolean;
    }

    public void setBoolean(final Boolean value) {
        this._boolean = value;
    }

    public DocRef getEntity() {
        return entity;
    }

    public void setEntity(final DocRef value) {
        this.entity = value;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelinePropertyValue that = (PipelinePropertyValue) o;
        return Objects.equals(string, that.string) &&
                Objects.equals(integer, that.integer) &&
                Objects.equals(_long, that._long) &&
                Objects.equals(_boolean, that._boolean) &&
                Objects.equals(entity, that.entity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(string, integer, _long, _boolean, entity);
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
    public void copyFrom(final PipelinePropertyValue from) {
        this.string = from.string;
        this.integer = from.integer;
        this._long = from._long;
        this._boolean = from._boolean;
        this.entity = from.entity;
    }
}
