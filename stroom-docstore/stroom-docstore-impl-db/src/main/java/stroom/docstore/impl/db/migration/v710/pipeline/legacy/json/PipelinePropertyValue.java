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

package stroom.docstore.impl.db.migration.v710.pipeline.legacy.json;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"string", "integer", "long", "boolean", "entity"})
@SuppressWarnings({"checkstyle:membername", "checkstyle:parametername"})
public class PipelinePropertyValue {

    @JsonProperty("string")
    private final String string;
    @JsonProperty("integer")
    private final Integer integer;
    // Not final as setter is required for restygwt to work properly with this field name.
    @JsonProperty("long")
    private Long _long;
    // Not final as setter is required for restygwt to work properly with this field name.
    @JsonProperty("boolean")
    private Boolean _boolean;
    @JsonProperty("entity")
    private final DocRef entity;

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

    public PipelinePropertyValue() {
        this.string = null;
        this.integer = null;
        this._long = null;
        this._boolean = null;
        this.entity = null;
    }

    public PipelinePropertyValue(final String string) {
        this.string = string;
        this.integer = null;
        this._long = null;
        this._boolean = null;
        this.entity = null;
    }

    public PipelinePropertyValue(final Integer integer) {
        this.integer = integer;
        this.string = null;
        this._long = null;
        this._boolean = null;
        this.entity = null;
    }

    public PipelinePropertyValue(final Long _long) {
        this._long = _long;
        this.string = null;
        this.integer = null;
        this._boolean = null;
        this.entity = null;
    }

    public PipelinePropertyValue(final Boolean _boolean) {
        this._boolean = _boolean;
        this.string = null;
        this.integer = null;
        this._long = null;
        this.entity = null;
    }

    public PipelinePropertyValue(final DocRef entity) {
        this.entity = entity;
        this.string = null;
        this.integer = null;
        this._long = null;
        this._boolean = null;
    }

    public String getString() {
        return string;
    }

    public Integer getInteger() {
        return integer;
    }

    public Long getLong() {
        return _long;
    }

    // DO NOT USE: Required for restygwt to work properly with this field name.
    @Deprecated
    public void setLong(final Long _long) {
        this._long = _long;
    }

    public Boolean getBoolean() {
        return _boolean;
    }

    // DO NOT USE: Required for restygwt to work properly with this field name.
    @Deprecated
    public void setBoolean(final Boolean _boolean) {
        this._boolean = _boolean;
    }

    public DocRef getEntity() {
        return entity;
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
}
